import 'dart:math' as math;

import 'package:flutter/material.dart';

class AppThemeData {
  const AppThemeData({
    required this.id,
    required this.name,
    required this.accent,
    required this.secondary,
    required this.background,
    required this.surface,
    required this.particleColors,
    required this.buttonGradient,
    required this.particleStyle,
  });

  final String id;
  final String name;
  final Color accent;
  final Color secondary;
  final Color background;
  final Color surface;
  final List<Color> particleColors;
  final LinearGradient buttonGradient;
  final ThemeParticleStyle particleStyle;

  static const themes = <AppThemeData>[
    AppThemeData(
      id: 'falcon',
      name: 'Falcon',
      accent: Color(0xFFB81C2B),
      secondary: Color(0xFFFF743D),
      background: Color(0xFF14090D),
      surface: Color(0xFF251218),
      particleColors: [Color(0xFFFF743D), Color(0xFFFFC857), Color(0xFFE43737)],
      buttonGradient: LinearGradient(
        colors: [Color(0xFFE33C43), Color(0xFF9E1425)],
      ),
      particleStyle: ThemeParticleStyle.embers,
    ),
    AppThemeData(
      id: 'ocean',
      name: 'Ocean',
      accent: Color(0xFF168AAD),
      secondary: Color(0xFF62D8E8),
      background: Color(0xFF06151F),
      surface: Color(0xFF0D2635),
      particleColors: [Color(0xFF37C6DF), Color(0xFF8AE6E8), Color(0xFF168AAD)],
      buttonGradient: LinearGradient(
        colors: [Color(0xFF32B6D1), Color(0xFF116A91)],
      ),
      particleStyle: ThemeParticleStyle.bubbles,
    ),
    AppThemeData(
      id: 'emerald',
      name: 'Emerald',
      accent: Color(0xFF16865A),
      secondary: Color(0xFF8EDB80),
      background: Color(0xFF07170F),
      surface: Color(0xFF10291C),
      particleColors: [Color(0xFF9DEB72), Color(0xFF5ACB83), Color(0xFFC5F28A)],
      buttonGradient: LinearGradient(
        colors: [Color(0xFF35B675), Color(0xFF116542)],
      ),
      particleStyle: ThemeParticleStyle.fireflies,
    ),
    AppThemeData(
      id: 'violet',
      name: 'Violet',
      accent: Color(0xFF7950B8),
      secondary: Color(0xFFD2A5FF),
      background: Color(0xFF100B1E),
      surface: Color(0xFF211735),
      particleColors: [Color(0xFFE3BEFF), Color(0xFF9E7BFF), Color(0xFF5D4A9C)],
      buttonGradient: LinearGradient(
        colors: [Color(0xFF9A6DE0), Color(0xFF59378F)],
      ),
      particleStyle: ThemeParticleStyle.stars,
    ),
  ];

  static AppThemeData fromId(String id) =>
      themes.firstWhere((theme) => theme.id == id, orElse: () => themes.first);
}

enum ThemeParticleStyle { embers, bubbles, fireflies, stars }

class AnimatedThemeBackground extends StatefulWidget {
  const AnimatedThemeBackground({
    super.key,
    required this.themeData,
    required this.child,
    this.enabled = true,
    this.isDark = true,
  });

  final AppThemeData themeData;
  final Widget child;
  final bool enabled;
  final bool isDark;

  @override
  State<AnimatedThemeBackground> createState() =>
      _AnimatedThemeBackgroundState();
}

class _AnimatedThemeBackgroundState extends State<AnimatedThemeBackground>
    with SingleTickerProviderStateMixin, WidgetsBindingObserver {
  late final AnimationController _controller;
  bool _appResumed = true;

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addObserver(this);
    _controller = AnimationController(
      vsync: this,
      duration: const Duration(seconds: 18),
    )..repeat();
  }

  @override
  void didChangeAppLifecycleState(AppLifecycleState state) {
    _appResumed = state == AppLifecycleState.resumed;
    _syncAnimation();
  }

  @override
  void didUpdateWidget(covariant AnimatedThemeBackground oldWidget) {
    super.didUpdateWidget(oldWidget);
    _syncAnimation();
  }

  void _syncAnimation() {
    final reduceAnimations =
        MediaQuery.maybeOf(context)?.disableAnimations ?? false;
    if (widget.enabled && _appResumed && !reduceAnimations) {
      if (!_controller.isAnimating) _controller.repeat();
    } else {
      _controller.stop();
    }
  }

  @override
  Widget build(BuildContext context) {
    _syncAnimation();
    return Stack(
      fit: StackFit.expand,
      children: [
        RepaintBoundary(
          child: AnimatedBuilder(
            animation: _controller,
            builder: (context, _) => CustomPaint(
              painter: ThemeParticlePainter(
                data: widget.themeData,
                progress: _controller.value,
                isDark: widget.isDark,
              ),
            ),
          ),
        ),
        widget.child,
      ],
    );
  }

  @override
  void dispose() {
    WidgetsBinding.instance.removeObserver(this);
    _controller.dispose();
    super.dispose();
  }
}

class ThemeParticlePainter extends CustomPainter {
  const ThemeParticlePainter({
    required this.data,
    required this.progress,
    this.isDark = true,
  });

  final AppThemeData data;
  final double progress;
  final bool isDark;

  static const _particleCount = 36;

  @override
  void paint(Canvas canvas, Size size) {
    final backgroundPaint = Paint()
      ..shader = LinearGradient(
        begin: Alignment.topLeft,
        end: Alignment.bottomRight,
        colors: isDark
            ? [data.background, data.surface, data.background]
            : [
                Color.lerp(Colors.white, data.secondary, 0.12)!,
                Colors.white,
                Color.lerp(Colors.white, data.accent, 0.08)!,
              ],
        stops: const [0, 0.52, 1],
      ).createShader(Offset.zero & size);
    canvas.drawRect(Offset.zero & size, backgroundPaint);

    for (var index = 0; index < _particleCount; index++) {
      final seed = index * 37.17;
      final xFactor = _fraction(math.sin(seed * 1.7) * 43758.5453);
      final yFactor = _fraction(math.sin(seed * 2.3) * 24634.6345);
      final phase = _fraction(math.sin(seed * 0.71) * 19341.271);
      final travel = (progress + phase) % 1;
      final color = data.particleColors[index % data.particleColors.length];
      final paint = Paint()
        ..color = color.withValues(
          alpha: (isDark
              ? 0.12 + 0.27 * (1 - yFactor)
              : 0.08 + 0.12 * (1 - yFactor)),
        )
        ..maskFilter = const MaskFilter.blur(BlurStyle.normal, 2);
      final radius = 1.2 + (index % 4) * 0.7;
      final x =
          xFactor * size.width +
          math.sin((travel + phase) * math.pi * 2) * size.width * 0.035;
      final y = switch (data.particleStyle) {
        ThemeParticleStyle.embers ||
        ThemeParticleStyle.fireflies => size.height * (1 - travel),
        ThemeParticleStyle.bubbles => size.height * (1 - travel),
        ThemeParticleStyle.stars => yFactor * size.height,
      };
      if (data.particleStyle == ThemeParticleStyle.stars) {
        paint.color = color.withValues(
          alpha:
              0.14 +
              0.55 * (0.5 + 0.5 * math.sin((progress + phase) * math.pi * 8)),
        );
      }
      if (data.particleStyle == ThemeParticleStyle.bubbles) {
        paint
          ..style = PaintingStyle.stroke
          ..strokeWidth = 1;
      } else {
        paint.style = PaintingStyle.fill;
      }
      canvas.drawCircle(Offset(x, y), radius, paint);
    }
  }

  double _fraction(double value) => value - value.floorToDouble();

  @override
  bool shouldRepaint(covariant ThemeParticlePainter oldDelegate) =>
      oldDelegate.data.id != data.id ||
      oldDelegate.progress != progress ||
      oldDelegate.isDark != isDark;
}

class ScaleOnPress extends StatefulWidget {
  const ScaleOnPress({super.key, required this.child});

  final Widget child;

  @override
  State<ScaleOnPress> createState() => _ScaleOnPressState();
}

class _ScaleOnPressState extends State<ScaleOnPress> {
  bool _pressed = false;

  @override
  Widget build(BuildContext context) {
    final reduceAnimations = MediaQuery.of(context).disableAnimations;
    return Listener(
      onPointerDown: (_) => setState(() => _pressed = true),
      onPointerUp: (_) => setState(() => _pressed = false),
      onPointerCancel: (_) => setState(() => _pressed = false),
      child: AnimatedScale(
        scale: _pressed ? 0.96 : 1,
        duration: reduceAnimations
            ? Duration.zero
            : const Duration(milliseconds: 100),
        child: widget.child,
      ),
    );
  }
}

class ThemePreviewCard extends StatelessWidget {
  const ThemePreviewCard({
    super.key,
    required this.themeData,
    required this.selected,
    required this.onTap,
  });

  final AppThemeData themeData;
  final bool selected;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    final reduceAnimations = MediaQuery.of(context).disableAnimations;
    return AnimatedScale(
      scale: selected ? 1 : 0.96,
      duration: reduceAnimations
          ? Duration.zero
          : const Duration(milliseconds: 220),
      child: AnimatedContainer(
        duration: reduceAnimations
            ? Duration.zero
            : const Duration(milliseconds: 220),
        width: 228,
        decoration: BoxDecoration(
          borderRadius: BorderRadius.circular(20),
          border: Border.all(
            color: selected ? themeData.secondary : Colors.white24,
            width: selected ? 2.5 : 1,
          ),
          boxShadow: selected
              ? [
                  BoxShadow(
                    color: themeData.accent.withValues(alpha: 0.42),
                    blurRadius: 18,
                  ),
                ]
              : null,
        ),
        clipBehavior: Clip.antiAlias,
        child: Material(
          color: themeData.background,
          child: InkWell(
            onTap: onTap,
            child: SizedBox(
              height: 126,
              child: Stack(
                fit: StackFit.expand,
                children: [
                  AnimatedThemeBackground(
                    themeData: themeData,
                    enabled: !reduceAnimations,
                    isDark: true,
                    child: const SizedBox.expand(),
                  ),
                  Padding(
                    padding: const EdgeInsets.all(14),
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Row(
                          children: [
                            Expanded(
                              child: Text(
                                themeData.name,
                                style: const TextStyle(
                                  color: Colors.white,
                                  fontWeight: FontWeight.w800,
                                  fontSize: 16,
                                ),
                              ),
                            ),
                            if (selected)
                              Icon(
                                Icons.check_circle,
                                color: themeData.secondary,
                              ),
                          ],
                        ),
                        const Spacer(),
                        DecoratedBox(
                          decoration: BoxDecoration(
                            gradient: themeData.buttonGradient,
                            borderRadius: BorderRadius.circular(18),
                            boxShadow: [
                              BoxShadow(
                                color: themeData.accent.withValues(alpha: 0.45),
                                blurRadius: 12,
                              ),
                            ],
                          ),
                          child: const Padding(
                            padding: EdgeInsets.symmetric(
                              horizontal: 15,
                              vertical: 7,
                            ),
                            child: Row(
                              mainAxisSize: MainAxisSize.min,
                              children: [
                                Icon(
                                  Icons.fiber_manual_record,
                                  size: 14,
                                  color: Colors.white,
                                ),
                                SizedBox(width: 6),
                                Text(
                                  'Gravar',
                                  style: TextStyle(
                                    color: Colors.white,
                                    fontWeight: FontWeight.bold,
                                  ),
                                ),
                              ],
                            ),
                          ),
                        ),
                      ],
                    ),
                  ),
                ],
              ),
            ),
          ),
        ),
      ),
    );
  }
}
