package com.b13.falcon

import android.content.SharedPreferences
import android.graphics.Color
import org.json.JSONObject

internal data class CameraOverlayConfig(
    val enabled: Boolean,
    val shape: String,
    val cornerRadiusDp: Int,
    val sizeDp: Int,
    val mirror: Boolean,
    val borderColor: Int,
    val borderStyle: String,
    val borderWidthDp: Int,
    val gradientEnd: Int,
    val glow: Boolean,
    val nameTagEnabled: Boolean,
    val nameTagText: String,
    val nameTagStyle: String,
    val nameTagBackgroundColor: Int,
    val nameTagTextColor: Int,
    val nameTagFont: String,
    val nameTagPosition: String,
    val xFraction: Float,
    val yFraction: Float,
    val locked: Boolean,
) {
    companion object {
        fun fromJson(json: String): CameraOverlayConfig {
            val root = JSONObject(json)
            val border = root.optJSONObject("border") ?: JSONObject()
            val nameTag = root.optJSONObject("nameTag") ?: JSONObject()
            val position = root.optJSONObject("position") ?: JSONObject()
            val shape = root.optString("shape", "circle")
            val borderStyle = border.optString("style", "solid")
            val nameTagStyle = nameTag.optString("style", "ribbon")
            val font = nameTag.optString("font", "sans-bold")
            val nameTagPosition = nameTag.optString("position", "bottomOverlap")
            val name = nameTag.optString("text", "Meu Canal")
                .codePoints()
                .limit(24)
                .toArray()
                .let { String(it, 0, it.size) }

            return CameraOverlayConfig(
                enabled = root.optBoolean("enabled", false),
                shape = shape.takeIf { it in setOf("circle", "rounded", "square") }
                    ?: "circle",
                cornerRadiusDp = root.optInt("cornerRadiusDp", 24).coerceIn(0, 80),
                sizeDp = root.optInt("sizeDp", 140).coerceIn(80, 260),
                mirror = root.optBoolean("mirror", true),
                borderColor = border.optInt("color", Color.WHITE),
                borderStyle = borderStyle.takeIf {
                    it in setOf("solid", "dashed", "double", "gradient", "glow")
                } ?: "solid",
                borderWidthDp = border.optInt("widthDp", 4).coerceIn(0, 16),
                gradientEnd = border.optInt("gradientEnd", Color.GREEN),
                glow = border.optBoolean("glow", false),
                nameTagEnabled = nameTag.optBoolean("enabled", false),
                nameTagText = name,
                nameTagStyle = nameTagStyle.takeIf {
                    it in setOf("ribbon", "pill", "bar")
                } ?: "ribbon",
                nameTagBackgroundColor = nameTag.optInt("bgColor", Color.DKGRAY),
                nameTagTextColor = nameTag.optInt("textColor", Color.WHITE),
                nameTagFont = font.takeIf {
                    it in setOf("sans", "sans-bold", "serif", "mono", "condensed")
                } ?: "sans-bold",
                nameTagPosition = nameTagPosition.takeIf {
                    it in setOf("bottomOverlap", "below", "above")
                } ?: "bottomOverlap",
                xFraction = position.optDouble("xFraction", 0.78)
                    .toFloat()
                    .coerceIn(0f, 1f),
                yFraction = position.optDouble("yFraction", 0.70)
                    .toFloat()
                    .coerceIn(0f, 1f),
                locked = position.optBoolean("locked", false),
            )
        }

        fun fromLegacyPreferences(preferences: SharedPreferences): String {
            val border = JSONObject()
                .put("color", preferences.getInt("cameraBorderColor", Color.WHITE))
                .put("style", "solid")
                .put("widthDp", preferences.getInt("cameraBorderWidthDp", 3).coerceIn(0, 16))
            return JSONObject()
                .put("enabled", preferences.getBoolean("frontCameraEnabled", false))
                .put("shape", preferences.getString("cameraShape", "circle"))
                .put(
                    "sizeDp",
                    preferences.getInt("cameraPreviewSizeDp", 140).coerceIn(80, 260),
                )
                .put("border", border)
                .toString()
        }

        fun loadFromPreferences(preferences: SharedPreferences): String {
            preferences.getString("cameraConfigJson", null)?.let { return it }
            return fromLegacyPreferences(preferences).also { migrated ->
                preferences.edit().putString("cameraConfigJson", migrated).apply()
            }
        }
    }
}
