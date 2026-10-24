import 'package:flutter/material.dart';

class AppColors {
  static const Color background = Color(0xFF0E1116);
  static const Color surface = Color(0xFF171D24);
  static const Color surfaceAlt = Color(0xFF1F272F);
  static const Color darkRed = Color(0xFF4D0E17);
  static const Color silver = Color(0xFFB7C0C8);
  static const Color white = Color(0xFFF4F7FA);
  static const Color muted = Color(0xFF8A939C);
  static const Color graphite = Color(0xFF2A3038);
  static const Color border = Color(0xFF303A42);
  static const Color accent = Color(0xFFB81C2B);
  static const Color success = Color(0xFF30B07B);
  static const Color warning = Color(0xFFE2A93E);

  static const LinearGradient panelGradient = LinearGradient(
    begin: Alignment.topLeft,
    end: Alignment.bottomRight,
    colors: [Color(0xFF1B222A), Color(0xFF10161E)],
  );
}
