import 'package:flutter/material.dart';

import '../constants/app_colors.dart';
import 'app_theme_data.dart';

class AppTheme {
  static ThemeData darkTheme({
    Color accentColor = AppColors.accent,
    AppThemeData? themeData,
  }) => ThemeData(
    brightness: Brightness.dark,
    scaffoldBackgroundColor: Colors.transparent,
    appBarTheme: const AppBarTheme(
      backgroundColor: Colors.transparent,
      foregroundColor: AppColors.white,
      centerTitle: true,
    ),
    colorScheme: ColorScheme.dark(
      primary: accentColor,
      secondary: AppColors.silver,
      surface: themeData?.surface ?? AppColors.surface,
      onPrimary: AppColors.white,
      onSecondary: AppColors.white,
      onSurface: AppColors.white,
    ),
    cardColor: themeData?.surface ?? AppColors.surface,
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

  static ThemeData lightTheme({
    Color accentColor = AppColors.accent,
    AppThemeData? themeData,
  }) => ThemeData(
    brightness: Brightness.light,
    scaffoldBackgroundColor: Colors.transparent,
    colorScheme: ColorScheme.light(
      primary: accentColor,
      secondary: AppColors.graphite,
      surface: Colors.white,
      onPrimary: AppColors.white,
      onSecondary: AppColors.white,
      onSurface: const Color(0xFF121722),
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
    filledButtonTheme: FilledButtonThemeData(
      style: FilledButton.styleFrom(
        backgroundColor: accentColor,
        foregroundColor: AppColors.white,
      ),
    ),
    appBarTheme: const AppBarTheme(
      backgroundColor: Colors.white,
      foregroundColor: Color(0xFF111827),
      centerTitle: true,
    ),
  );
}
