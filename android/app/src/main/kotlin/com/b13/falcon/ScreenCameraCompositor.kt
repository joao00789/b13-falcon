package com.b13.falcon

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.SurfaceTexture
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CaptureRequest
import android.opengl.EGL14
import android.opengl.EGLExt
import android.opengl.GLES11Ext
import android.opengl.GLES20
import android.opengl.Matrix
import android.os.Handler
import android.os.HandlerThread
import android.util.Size
import android.view.Surface
import androidx.core.content.ContextCompat
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.abs

internal class ScreenCameraCompositor(
    private val context: Context,
    private val encoderSurface: Surface,
    private val width: Int,
    private val height: Int,
    frameRate: Int,
    private val borderColor: Int,
    borderWidthDp: Int,
    private val shape: String,
    private val onFailure: (String) -> Unit,
) {
    companion object {
        private const val EGL_RECORDABLE_ANDROID = 0x3142
        private const val CAMERA_TARGET = GLES11Ext.GL_TEXTURE_EXTERNAL_OES
        private val VERTEX_SHADER = """
            attribute vec4 aPosition;
            attribute vec2 aTextureCoordinate;
            uniform mat4 uTextureMatrix;
            uniform vec2 uCropScale;
            varying vec2 vTextureCoordinate;
            varying vec2 vPosition;
            void main() {
                gl_Position = aPosition;
                vPosition = aPosition.xy;
                vTextureCoordinate =
                    (uTextureMatrix * vec4(
                        (aTextureCoordinate - vec2(0.5)) * uCropScale + vec2(0.5),
                        0.0,
                        1.0
                    )).xy;
            }
        """
        private val SCREEN_FRAGMENT_SHADER = """
            #extension GL_OES_EGL_image_external : require
            precision mediump float;
            uniform samplerExternalOES uTexture;
            varying vec2 vTextureCoordinate;
            void main() {
                gl_FragColor = texture2D(uTexture, vTextureCoordinate);
            }
        """
        private val CAMERA_FRAGMENT_SHADER = """
            #extension GL_OES_EGL_image_external : require
            precision mediump float;
            uniform samplerExternalOES uTexture;
            uniform vec4 uBorderColor;
            uniform float uBorderWidth;
            uniform int uShape;
            varying vec2 vTextureCoordinate;
            varying vec2 vPosition;
            void main() {
                float edgeDistance;
                if (uShape == 0) {
                    edgeDistance = length(vPosition) - 0.96;
                } else {
                    vec2 q = abs(vPosition) - vec2(0.72);
                    edgeDistance =
                        length(max(q, 0.0)) + min(max(q.x, q.y), 0.0) - 0.28;
                }
                if (edgeDistance > 0.0) discard;
                gl_FragColor = edgeDistance > -uBorderWidth
                    ? uBorderColor
                    : texture2D(uTexture, vTextureCoordinate);
            }
        """
        private val POSITIONS = floatArrayOf(
            -1f, -1f,
            1f, -1f,
            -1f, 1f,
            1f, 1f,
        )
        private val TEXTURE_COORDINATES = floatArrayOf(
            0f, 0f,
            1f, 0f,
            0f, 1f,
            1f, 1f,
        )
    }

    private val renderIntervalMs = (1000L / frameRate.coerceIn(15, 90)).coerceAtLeast(1)
    private val borderWidthDp = borderWidthDp.coerceIn(0, 12)
    private val frameAvailable = AtomicBoolean(false)
    private val released = AtomicBoolean(false)
    private val initialization = CountDownLatch(1)
    private val cameraManager = context.getSystemService(CameraManager::class.java)
    private val cameraSpec = findFrontCamera()
    private val cameraThread = HandlerThread("B13FalconCameraComposite").apply { start() }
    private val handler = Handler(cameraThread.looper)

    private var eglDisplay = EGL14.EGL_NO_DISPLAY
    private var eglContext = EGL14.EGL_NO_CONTEXT
    private var eglSurface = EGL14.EGL_NO_SURFACE
    private var eglConfig: android.opengl.EGLConfig? = null
    private var previewEglSurface = EGL14.EGL_NO_SURFACE
    private var screenTextureId = 0
    private var cameraTextureId = 0
    private var screenTexture: SurfaceTexture? = null
    private var cameraTexture: SurfaceTexture? = null
    private var screenSurface: Surface? = null
    private var cameraSurface: Surface? = null
    private var cameraDevice: CameraDevice? = null
    private var cameraSession: CameraCaptureSession? = null
    private var screenProgram = 0
    private var cameraProgram = 0
    private var screenTextureMatrix = FloatArray(16)
    private var cameraTextureMatrix = FloatArray(16)
    private var cameraStarted = false
    private var paused = false
    private var surfaceWidth = 0
    private var surfaceHeight = 0
    private var screenPositionBuffer: FloatBuffer? = null
    private var cameraPositionBuffer: FloatBuffer? = null
    private var textureCoordinateBuffer: FloatBuffer? = null

    val projectionSurface: Surface
        get() = screenSurface ?: throw IllegalStateException("Superfície de tela indisponível.")

    fun start() {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            throw SecurityException("Permita o acesso à câmera frontal para usar a sobreposição.")
        }
        val initialized = handler.post {
            try {
                initializeEgl()
                createInputTextures()
                startFrontCamera()
                handler.post(renderRunnable)
            } catch (error: Exception) {
                onFailure(error.message ?: "Não foi possível iniciar a câmera frontal.")
            } finally {
                initialization.countDown()
            }
        }
        if (!initialized || !initialization.await(5, TimeUnit.SECONDS)) {
            release()
            throw IOException("A composição da câmera não inicializou a tempo.")
        }
        if (screenSurface == null || cameraSurface == null) {
            release()
            throw IOException("Não foi possível criar as superfícies da câmera.")
        }
    }

    fun setPaused(value: Boolean) {
        handler.post { paused = value }
    }

    fun setPreviewSurface(surface: Surface?) {
        handler.post {
            if (released.get()) return@post
            if (previewEglSurface != EGL14.EGL_NO_SURFACE) {
                EGL14.eglDestroySurface(eglDisplay, previewEglSurface)
                previewEglSurface = EGL14.EGL_NO_SURFACE
            }
            if (surface != null && surface.isValid) {
                val config = eglConfig ?: return@post
                previewEglSurface = EGL14.eglCreateWindowSurface(
                    eglDisplay,
                    config,
                    surface,
                    intArrayOf(EGL14.EGL_NONE),
                    0,
                )
                if (previewEglSurface == EGL14.EGL_NO_SURFACE) {
                    onFailure("Não foi possível abrir a prévia flutuante da câmera.")
                }
            }
        }
    }

    fun release() {
        if (!released.compareAndSet(false, true)) return
        val done = CountDownLatch(1)
        if (handler.post {
                handler.removeCallbacks(renderRunnable)
                runCatching { cameraSession?.stopRepeating() }
                runCatching { cameraSession?.close() }
                cameraSession = null
                runCatching { cameraDevice?.close() }
                cameraDevice = null
                runCatching { cameraSurface?.release() }
                cameraSurface = null
                runCatching { screenSurface?.release() }
                screenSurface = null
                runCatching { cameraTexture?.release() }
                cameraTexture = null
                runCatching { screenTexture?.release() }
                screenTexture = null
                if (eglDisplay != EGL14.EGL_NO_DISPLAY) {
                    val hasCurrentContext = EGL14.eglMakeCurrent(
                        eglDisplay,
                        eglSurface,
                        eglSurface,
                        eglContext,
                    )
                    if (hasCurrentContext) {
                        if (screenTextureId != 0) {
                            GLES20.glDeleteTextures(1, intArrayOf(screenTextureId), 0)
                        }
                        if (cameraTextureId != 0) {
                            GLES20.glDeleteTextures(1, intArrayOf(cameraTextureId), 0)
                        }
                        if (screenProgram != 0) GLES20.glDeleteProgram(screenProgram)
                        if (cameraProgram != 0) GLES20.glDeleteProgram(cameraProgram)
                    }
                    if (previewEglSurface != EGL14.EGL_NO_SURFACE) {
                        EGL14.eglDestroySurface(eglDisplay, previewEglSurface)
                    }
                    if (eglSurface != EGL14.EGL_NO_SURFACE) {
                        EGL14.eglDestroySurface(eglDisplay, eglSurface)
                    }
                    if (eglContext != EGL14.EGL_NO_CONTEXT) {
                        EGL14.eglDestroyContext(eglDisplay, eglContext)
                    }
                    EGL14.eglTerminate(eglDisplay)
                }
                eglDisplay = EGL14.EGL_NO_DISPLAY
                eglContext = EGL14.EGL_NO_CONTEXT
                eglSurface = EGL14.EGL_NO_SURFACE
                previewEglSurface = EGL14.EGL_NO_SURFACE
                eglConfig = null
                screenTextureId = 0
                cameraTextureId = 0
                screenProgram = 0
                cameraProgram = 0
                done.countDown()
            }
        ) {
            done.await(2, TimeUnit.SECONDS)
        }
        cameraThread.quitSafely()
    }

    private fun findFrontCamera(): CameraSpec {
        val frontId = cameraManager.cameraIdList.firstOrNull { id ->
            cameraManager.getCameraCharacteristics(id)
                .get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_FRONT
        } ?: throw IOException("Este aparelho não possui uma câmera frontal disponível.")
        val characteristics = cameraManager.getCameraCharacteristics(frontId)
        val outputSizes = characteristics
            .get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
            ?.getOutputSizes(SurfaceTexture::class.java)
            .orEmpty()
        val size = outputSizes.minByOrNull { candidate ->
            abs(candidate.width - 640) + abs(candidate.height - 480)
        } ?: throw IOException("O aparelho não informou um formato compatível para a câmera.")
        return CameraSpec(frontId, size)
    }

    private fun initializeEgl() {
        eglDisplay = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
        if (eglDisplay == EGL14.EGL_NO_DISPLAY) {
            throw IOException("Não foi possível abrir o compositor gráfico.")
        }
        val version = IntArray(2)
        if (!EGL14.eglInitialize(eglDisplay, version, 0, version, 1)) {
            throw IOException("Não foi possível inicializar o compositor gráfico.")
        }
        val attributes = intArrayOf(
            EGL14.EGL_RED_SIZE, 8,
            EGL14.EGL_GREEN_SIZE, 8,
            EGL14.EGL_BLUE_SIZE, 8,
            EGL14.EGL_ALPHA_SIZE, 8,
            EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
            EGL_RECORDABLE_ANDROID, 1,
            EGL14.EGL_NONE,
        )
        val configs = arrayOfNulls<android.opengl.EGLConfig>(1)
        val configCount = IntArray(1)
        if (!EGL14.eglChooseConfig(
                eglDisplay,
                attributes,
                0,
                configs,
                0,
                configs.size,
                configCount,
                0,
            ) || configCount[0] == 0
        ) {
            throw IOException("Não há configuração EGL compatível com o encoder.")
        }
        val contextAttributes = intArrayOf(
            EGL14.EGL_CONTEXT_CLIENT_VERSION, 2,
            EGL14.EGL_NONE,
        )
        eglContext = EGL14.eglCreateContext(
            eglDisplay,
            configs[0],
            EGL14.EGL_NO_CONTEXT,
            contextAttributes,
            0,
        )
        val surfaceAttributes = intArrayOf(EGL14.EGL_NONE)
        eglSurface = EGL14.eglCreateWindowSurface(
            eglDisplay,
            configs[0],
            encoderSurface,
            surfaceAttributes,
            0,
        )
        eglConfig = configs[0]
        if (eglContext == EGL14.EGL_NO_CONTEXT ||
            eglSurface == EGL14.EGL_NO_SURFACE ||
            !EGL14.eglMakeCurrent(eglDisplay, eglSurface, eglSurface, eglContext)
        ) {
            throw IOException("Não foi possível conectar o compositor ao encoder de vídeo.")
        }
        val dimensions = IntArray(1)
        EGL14.eglQuerySurface(eglDisplay, eglSurface, EGL14.EGL_WIDTH, dimensions, 0)
        surfaceWidth = dimensions[0].takeIf { it > 0 } ?: width
        EGL14.eglQuerySurface(eglDisplay, eglSurface, EGL14.EGL_HEIGHT, dimensions, 0)
        surfaceHeight = dimensions[0].takeIf { it > 0 } ?: height
        screenProgram = createProgram(VERTEX_SHADER, SCREEN_FRAGMENT_SHADER)
        cameraProgram = createProgram(VERTEX_SHADER, CAMERA_FRAGMENT_SHADER)
        screenPositionBuffer = floatBuffer(POSITIONS)
        cameraPositionBuffer = floatBuffer(POSITIONS)
        textureCoordinateBuffer = floatBuffer(TEXTURE_COORDINATES)
    }

    private fun createInputTextures() {
        screenTextureId = createExternalTexture()
        cameraTextureId = createExternalTexture()
        screenTexture = SurfaceTexture(screenTextureId).apply {
            setDefaultBufferSize(width, height)
            setOnFrameAvailableListener({ frameAvailable.set(true) }, handler)
        }
        cameraTexture = SurfaceTexture(cameraTextureId).apply {
            setDefaultBufferSize(cameraSpec.size.width, cameraSpec.size.height)
            setOnFrameAvailableListener(
                {
                    cameraStarted = true
                    frameAvailable.set(true)
                },
                handler,
            )
        }
        screenSurface = Surface(screenTexture)
        cameraSurface = Surface(cameraTexture)
        Matrix.setIdentityM(screenTextureMatrix, 0)
        Matrix.setIdentityM(cameraTextureMatrix, 0)
    }

    private fun applyFrontCameraTransform(matrix: FloatArray) {
        val base = matrix.copyOf()
        val corrected = FloatArray(16)
        Matrix.setIdentityM(corrected, 0)
        Matrix.scaleM(corrected, 0, -1f, 1f, 1f)
        Matrix.rotateM(corrected, 0, 180f, 0f, 0f, 1f)
        Matrix.multiplyMM(corrected, 0, corrected, 0, base, 0)
        System.arraycopy(corrected, 0, matrix, 0, 16)
    }

    private fun startFrontCamera() {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            throw SecurityException("Permissão da câmera frontal não concedida.")
        }
        cameraManager.openCamera(
            cameraSpec.id,
            object : CameraDevice.StateCallback() {
                override fun onOpened(camera: CameraDevice) {
                    if (released.get()) {
                        camera.close()
                        return
                    }
                    cameraDevice = camera
                    val target = cameraSurface ?: return
                    camera.createCaptureSession(
                        listOf(target),
                        object : CameraCaptureSession.StateCallback() {
                            override fun onConfigured(session: CameraCaptureSession) {
                                if (released.get()) {
                                    session.close()
                                    return
                                }
                                cameraSession = session
                                try {
                                    val request = camera.createCaptureRequest(
                                        CameraDevice.TEMPLATE_RECORD,
                                    ).apply {
                                        addTarget(target)
                                        set(
                                            CaptureRequest.CONTROL_MODE,
                                            CaptureRequest.CONTROL_MODE_AUTO,
                                        )
                                        set(
                                            CaptureRequest.CONTROL_AF_MODE,
                                            CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_VIDEO,
                                        )
                                    }.build()
                                    session.setRepeatingRequest(request, null, handler)
                                } catch (error: Exception) {
                                    onFailure(error.message ?: "A câmera frontal não iniciou.")
                                }
                            }

                            override fun onConfigureFailed(session: CameraCaptureSession) {
                                onFailure("O aparelho não conseguiu configurar a câmera frontal.")
                            }
                        },
                        handler,
                    )
                }

                override fun onDisconnected(camera: CameraDevice) {
                    camera.close()
                    cameraDevice = null
                    if (!released.get()) onFailure("A câmera frontal foi desconectada.")
                }

                override fun onError(camera: CameraDevice, error: Int) {
                    camera.close()
                    cameraDevice = null
                    if (!released.get()) onFailure("A câmera frontal encontrou um erro ($error).")
                }
            },
            handler,
        )
    }

    private val renderRunnable = object : Runnable {
        override fun run() {
            if (released.get()) return
            if (!paused && frameAvailable.getAndSet(false)) {
                try {
                    screenTexture?.updateTexImage()
                    screenTexture?.getTransformMatrix(screenTextureMatrix)
                    if (cameraStarted) {
                        cameraTexture?.updateTexImage()
                        cameraTexture?.getTransformMatrix(cameraTextureMatrix)
                        applyFrontCameraTransform(cameraTextureMatrix)
                    }
                    renderFrame()
                } catch (error: Exception) {
                    onFailure(error.message ?: "Falha ao compor o vídeo com a câmera.")
                }
            }
            handler.postDelayed(this, renderIntervalMs)
        }
    }

    private fun renderFrame() {
        EGL14.eglMakeCurrent(eglDisplay, eglSurface, eglSurface, eglContext)
        GLES20.glViewport(0, 0, surfaceWidth, surfaceHeight)
        GLES20.glClearColor(0f, 0f, 0f, 1f)
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
        drawTexture(screenProgram, screenTextureId, screenTextureMatrix, false)
        if (cameraStarted) {
            val side = (minOf(surfaceWidth, surfaceHeight) * 0.27f).toInt()
            val margin = (minOf(surfaceWidth, surfaceHeight) * 0.035f).toInt()
            GLES20.glViewport(
                (surfaceWidth - side - margin).coerceAtLeast(0),
                margin,
                side,
                side,
            )
            drawTexture(cameraProgram, cameraTextureId, cameraTextureMatrix, true)
            GLES20.glViewport(0, 0, surfaceWidth, surfaceHeight)
        }
        EGLExt.eglPresentationTimeANDROID(
            eglDisplay,
            eglSurface,
            System.nanoTime(),
        )
        if (!EGL14.eglSwapBuffers(eglDisplay, eglSurface)) {
            throw IOException("O encoder rejeitou um quadro composto.")
        }
        if (cameraStarted && previewEglSurface != EGL14.EGL_NO_SURFACE &&
            EGL14.eglMakeCurrent(
                eglDisplay,
                previewEglSurface,
                previewEglSurface,
                eglContext,
            )
        ) {
            val dimensions = IntArray(1)
            EGL14.eglQuerySurface(
                eglDisplay,
                previewEglSurface,
                EGL14.EGL_WIDTH,
                dimensions,
                0,
            )
            val previewWidth = dimensions[0].coerceAtLeast(1)
            EGL14.eglQuerySurface(
                eglDisplay,
                previewEglSurface,
                EGL14.EGL_HEIGHT,
                dimensions,
                0,
            )
            val previewHeight = dimensions[0].coerceAtLeast(1)
            GLES20.glViewport(0, 0, previewWidth, previewHeight)
            GLES20.glClearColor(0f, 0f, 0f, 0f)
            GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
            drawTexture(
                cameraProgram,
                cameraTextureId,
                cameraTextureMatrix,
                true,
                previewWidth,
                previewHeight,
                1f,
            )
            if (!EGL14.eglSwapBuffers(eglDisplay, previewEglSurface)) {
                EGL14.eglDestroySurface(eglDisplay, previewEglSurface)
                previewEglSurface = EGL14.EGL_NO_SURFACE
                onFailure("A prévia flutuante da câmera foi desconectada.")
            }
        }
    }

    private fun drawTexture(
        program: Int,
        textureId: Int,
        textureMatrix: FloatArray,
        drawBorder: Boolean,
        frameWidth: Int = surfaceWidth,
        frameHeight: Int = surfaceHeight,
        cameraSizeFraction: Float = 0.27f,
    ) {
        GLES20.glUseProgram(program)
        val position = GLES20.glGetAttribLocation(program, "aPosition")
        val textureCoordinate = GLES20.glGetAttribLocation(program, "aTextureCoordinate")
        val textureMatrixLocation = GLES20.glGetUniformLocation(program, "uTextureMatrix")
        val positions = if (drawBorder) cameraPositionBuffer else screenPositionBuffer
        positions?.position(0)
        textureCoordinateBuffer?.position(0)
        GLES20.glEnableVertexAttribArray(position)
        GLES20.glVertexAttribPointer(position, 2, GLES20.GL_FLOAT, false, 0, positions)
        GLES20.glEnableVertexAttribArray(textureCoordinate)
        GLES20.glVertexAttribPointer(
            textureCoordinate,
            2,
            GLES20.GL_FLOAT,
            false,
            0,
            textureCoordinateBuffer,
        )
        GLES20.glUniformMatrix4fv(textureMatrixLocation, 1, false, textureMatrix, 0)
        val cropScaleLocation = GLES20.glGetUniformLocation(program, "uCropScale")
        val cameraAspect = cameraSpec.size.width.toFloat() / cameraSpec.size.height
        val cropScaleX = if (drawBorder && cameraAspect > 1f) 1f / cameraAspect else 1f
        val cropScaleY = if (drawBorder && cameraAspect < 1f) cameraAspect else 1f
        GLES20.glUniform2f(cropScaleLocation, cropScaleX, cropScaleY)
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(CAMERA_TARGET, textureId)
        GLES20.glUniform1i(GLES20.glGetUniformLocation(program, "uTexture"), 0)
        if (drawBorder) {
            val colorLocation = GLES20.glGetUniformLocation(program, "uBorderColor")
            GLES20.glUniform4f(
                colorLocation,
                android.graphics.Color.red(borderColor) / 255f,
                android.graphics.Color.green(borderColor) / 255f,
                android.graphics.Color.blue(borderColor) / 255f,
                android.graphics.Color.alpha(borderColor) / 255f,
            )
            val cameraSide = minOf(frameWidth, frameHeight) * cameraSizeFraction
            val borderPixels = borderWidthDp * context.resources.displayMetrics.density
            GLES20.glUniform1f(
                GLES20.glGetUniformLocation(program, "uBorderWidth"),
                (borderPixels * 2f / cameraSide).coerceIn(0f, 0.4f),
            )
            GLES20.glUniform1i(
                GLES20.glGetUniformLocation(program, "uShape"),
                if (shape == "circle") 0 else 1,
            )
        }
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
        GLES20.glDisableVertexAttribArray(position)
        GLES20.glDisableVertexAttribArray(textureCoordinate)
    }

    private fun createExternalTexture(): Int {
        val texture = IntArray(1)
        GLES20.glGenTextures(1, texture, 0)
        GLES20.glBindTexture(CAMERA_TARGET, texture[0])
        GLES20.glTexParameteri(CAMERA_TARGET, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(CAMERA_TARGET, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(CAMERA_TARGET, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(CAMERA_TARGET, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
        return texture[0]
    }

    private fun createProgram(vertexShader: String, fragmentShader: String): Int {
        val vertex = compileShader(GLES20.GL_VERTEX_SHADER, vertexShader)
        val fragment = compileShader(GLES20.GL_FRAGMENT_SHADER, fragmentShader)
        val program = GLES20.glCreateProgram()
        GLES20.glAttachShader(program, vertex)
        GLES20.glAttachShader(program, fragment)
        GLES20.glLinkProgram(program)
        val status = IntArray(1)
        GLES20.glGetProgramiv(program, GLES20.GL_LINK_STATUS, status, 0)
        if (status[0] == 0) {
            val error = GLES20.glGetProgramInfoLog(program)
            GLES20.glDeleteProgram(program)
            throw IOException("Não foi possível preparar o shader do encoder: $error")
        }
        GLES20.glDeleteShader(vertex)
        GLES20.glDeleteShader(fragment)
        return program
    }

    private fun compileShader(type: Int, source: String): Int {
        val shader = GLES20.glCreateShader(type)
        GLES20.glShaderSource(shader, source)
        GLES20.glCompileShader(shader)
        val status = IntArray(1)
        GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, status, 0)
        if (status[0] == 0) {
            val error = GLES20.glGetShaderInfoLog(shader)
            GLES20.glDeleteShader(shader)
            throw IOException("Não foi possível compilar o shader de composição: $error")
        }
        return shader
    }

    private fun floatBuffer(values: FloatArray): FloatBuffer =
        ByteBuffer.allocateDirect(values.size * Float.SIZE_BYTES)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
            .apply {
                put(values)
                position(0)
            }

    private data class CameraSpec(val id: String, val size: Size)
}
