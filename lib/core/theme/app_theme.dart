import 'package:flutter/material.dart';

import '../constants/app_colors.dart';

class AppTheme {
  static ThemeData darkTheme() => ThemeData(
    brightness: Brightness.dark,
    scaffoldBackgroundColor: AppColors.background,
    appBarTheme: const AppBarTheme(
      backgroundColor: AppColors.background,
      foregroundColor: AppColors.white,
      centerTitle: true,
    ),
    colorScheme: const ColorScheme.dark(
      primary: AppColors.accent,
      secondary: AppColors.silver,
      surface: AppColors.surface,
      onPrimary: AppColors.white,
      onSecondary: AppColors.white,
      onSurface: AppColors.white,
    ),
    cardColor: AppColors.surface,
    dividerColor: AppColors.border,
    textTheme: const TextTheme(
      displayLarge: TextStyle(color: AppColors.white),
      displayMedium: TextStyle(color: AppColors.white),
      headlineLarge: TextStyle(color: AppColors.white),
      headlineMedium: TextStyle(color: AppColors.white),
      titleLarge: TextStyle(color: AppColors.white),
      titleMedium: TextStyle(color: AppColors.white),
      bodyLarge: TextStyle(color: AppColors.white),
      bodyMedium: TextStyle(color: AppColors.white),
      bodySmall: TextStyle(color: AppColors.muted),
      labelLarge: TextStyle(color: AppColors.white),
    ),
    iconTheme: const IconThemeData(color: AppColors.white),
    inputDecorationTheme: const InputDecorationTheme(
      filled: true,
      fillColor: AppColors.surfaceAlt,
      border: OutlineInputBorder(
        borderRadius: BorderRadius.all(Radius.circular(14)),
        borderSide: BorderSide(color: AppColors.border),
      ),
      enabledBorder: OutlineInputBorder(
        borderRadius: BorderRadius.all(Radius.circular(14)),
        borderSide: BorderSide(color: AppColors.border),
      ),
    ),
  );

  static ThemeData lightTheme() => ThemeData(
    brightness: Brightness.light,
    scaffoldBackgroundColor: const Color(0xFFF5F7FA),
    colorScheme: const ColorScheme.light(
      primary: AppColors.accent,
      secondary: AppColors.graphite,
      surface: Colors.white,
      onPrimary: AppColors.white,
      onSecondary: AppColors.white,
      onSurface: Color(0xFF121722),
    ),
    cardColor: Colors.white,
    textTheme: const TextTheme(
      displayLarge: TextStyle(color: Color(0xFF121722)),
      displayMedium: TextStyle(color: Color(0xFF121722)),
      headlineLarge: TextStyle(color: Color(0xFF121722)),
      headlineMedium: TextStyle(color: Color(0xFF121722)),
      titleLarge: TextStyle(color: Color(0xFF121722)),
      titleMedium: TextStyle(color: Color(0xFF121722)),
      bodyLarge: TextStyle(color: Color(0xFF121722)),
      bodyMedium: TextStyle(color: Color(0xFF121722)),
      bodySmall: TextStyle(color: Color(0xFF68717D)),
      labelLarge: TextStyle(color: Color(0xFF121722)),
    ),
    iconTheme: const IconThemeData(color: Color(0xFF121722)),
    appBarTheme: const AppBarTheme(
      backgroundColor: Colors.white,
      foregroundColor: Color(0xFF111827),
      centerTitle: true,
    ),
  );
}
