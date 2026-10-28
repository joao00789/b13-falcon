import 'dart:convert';

class CameraOverlaySettings {
  const CameraOverlaySettings({
    this.enabled = false,
    this.mode = 'overlay',
    this.shape = 'circle',
    this.cornerRadiusDp = 24,
    this.sizeDp = 140,
    this.mirror = true,
    this.borderColor = -1,
    this.borderStyle = 'solid',
    this.borderWidthDp = 4,
    this.gradientEnd = -16711936,
    this.glow = false,
    this.nameTagEnabled = false,
    this.nameTagText = 'Meu Canal',
    this.nameTagStyle = 'ribbon',
    this.nameTagBackgroundColor = -5301733,
    this.nameTagTextColor = -1,
    this.nameTagFont = 'sans-bold',
    this.nameTagPosition = 'bottomOverlap',
    this.imageUri,
    this.xFraction = 0.78,
    this.yFraction = 0.70,
    this.locked = false,
  });

  static const validShapes = {'circle', 'rounded', 'square'};
  static const validModes = {'overlay', 'baked'};
  static const validBorderStyles = {
    'solid',
    'dashed',
    'double',
    'gradient',
    'glow',
  };
  static const validNameTagStyles = {'ribbon', 'pill', 'bar'};
  static const validFonts = {'sans', 'sans-bold', 'serif', 'mono', 'condensed'};
  static const validNameTagPositions = {'bottomOverlap', 'below', 'above'};

  final bool enabled;
  final String mode;
  final String shape;
  final int cornerRadiusDp;
  final int sizeDp;
  final bool mirror;
  final int borderColor;
  final String borderStyle;
  final int borderWidthDp;
  final int gradientEnd;
  final bool glow;
  final bool nameTagEnabled;
  final String nameTagText;
  final String nameTagStyle;
  final int nameTagBackgroundColor;
  final int nameTagTextColor;
  final String nameTagFont;
  final String nameTagPosition;
  final String? imageUri;
  final double xFraction;
  final double yFraction;
  final bool locked;

  String get configJson => jsonEncode(toJson());

  CameraOverlaySettings copyWith({
    bool? enabled,
    String? mode,
    String? shape,
    int? cornerRadiusDp,
    int? sizeDp,
    bool? mirror,
    int? borderColor,
    String? borderStyle,
    int? borderWidthDp,
    int? gradientEnd,
    bool? glow,
    bool? nameTagEnabled,
    String? nameTagText,
    String? nameTagStyle,
    int? nameTagBackgroundColor,
    int? nameTagTextColor,
    String? nameTagFont,
    String? nameTagPosition,
    String? imageUri,
    bool clearImageUri = false,
    double? xFraction,
    double? yFraction,
    bool? locked,
  }) {
    return CameraOverlaySettings(
      enabled: enabled ?? this.enabled,
      mode: mode ?? this.mode,
      shape: shape ?? this.shape,
      cornerRadiusDp: cornerRadiusDp ?? this.cornerRadiusDp,
      sizeDp: sizeDp ?? this.sizeDp,
      mirror: mirror ?? this.mirror,
      borderColor: borderColor ?? this.borderColor,
      borderStyle: borderStyle ?? this.borderStyle,
      borderWidthDp: borderWidthDp ?? this.borderWidthDp,
      gradientEnd: gradientEnd ?? this.gradientEnd,
      glow: glow ?? this.glow,
      nameTagEnabled: nameTagEnabled ?? this.nameTagEnabled,
      nameTagText: nameTagText ?? this.nameTagText,
      nameTagStyle: nameTagStyle ?? this.nameTagStyle,
      nameTagBackgroundColor:
          nameTagBackgroundColor ?? this.nameTagBackgroundColor,
      nameTagTextColor: nameTagTextColor ?? this.nameTagTextColor,
      nameTagFont: nameTagFont ?? this.nameTagFont,
      nameTagPosition: nameTagPosition ?? this.nameTagPosition,
      imageUri: clearImageUri ? null : imageUri ?? this.imageUri,
      xFraction: xFraction ?? this.xFraction,
      yFraction: yFraction ?? this.yFraction,
      locked: locked ?? this.locked,
    );
  }

  Map<String, Object> toJson() => {
    'enabled': enabled,
    'mode': mode,
    'shape': shape,
    'cornerRadiusDp': cornerRadiusDp,
    'sizeDp': sizeDp,
    'mirror': mirror,
    'border': {
      'color': borderColor,
      'style': borderStyle,
      'widthDp': borderWidthDp,
      'gradientEnd': gradientEnd,
      'glow': glow,
    },
    'nameTag': {
      'enabled': nameTagEnabled,
      'text': nameTagText,
      'style': nameTagStyle,
      'bgColor': nameTagBackgroundColor,
      'textColor': nameTagTextColor,
      'font': nameTagFont,
      'position': nameTagPosition,
    },
    'imageUri': imageUri ?? '',
    'position': {
      'xFraction': xFraction,
      'yFraction': yFraction,
      'locked': locked,
    },
  };

  factory CameraOverlaySettings.fromJson(Object? value) {
    if (value is String) {
      value = jsonDecode(value);
    }
    if (value is! Map) return const CameraOverlaySettings();

    final border = value['border'] is Map ? value['border'] as Map : const {};
    final nameTag = value['nameTag'] is Map
        ? value['nameTag'] as Map
        : const {};
    final position = value['position'] is Map
        ? value['position'] as Map
        : const {};
    final shape = _readString(value['shape'], 'circle');
    final mode = _readString(value['mode'], 'overlay');
    final borderStyle = _readString(border['style'], 'solid');
    final nameTagStyle = _readString(nameTag['style'], 'ribbon');
    final font = _readString(nameTag['font'], 'sans-bold');
    final nameTagPosition = _readString(nameTag['position'], 'bottomOverlap');
    final imageUri = _readString(value['imageUri'], '').trim();

    return CameraOverlaySettings(
      enabled: _readBool(value['enabled'], false),
      mode: validModes.contains(mode) ? mode : 'overlay',
      shape: validShapes.contains(shape) ? shape : 'circle',
      cornerRadiusDp: _readInt(
        value['cornerRadiusDp'],
        24,
      ).clamp(0, 80).toInt(),
      sizeDp: _readInt(value['sizeDp'], 140).clamp(80, 260).toInt(),
      mirror: _readBool(value['mirror'], true),
      borderColor: _readColor(border['color'], -1),
      borderStyle: validBorderStyles.contains(borderStyle)
          ? borderStyle
          : 'solid',
      borderWidthDp: _readInt(border['widthDp'], 4).clamp(0, 16).toInt(),
      gradientEnd: _readColor(border['gradientEnd'], -16711936),
      glow: _readBool(border['glow'], false),
      nameTagEnabled: _readBool(nameTag['enabled'], false),
      nameTagText: _readName(nameTag['text']),
      nameTagStyle: validNameTagStyles.contains(nameTagStyle)
          ? nameTagStyle
          : 'ribbon',
      nameTagBackgroundColor: _readColor(nameTag['bgColor'], -5301733),
      nameTagTextColor: _readColor(nameTag['textColor'], -1),
      nameTagFont: validFonts.contains(font) ? font : 'sans-bold',
      nameTagPosition: validNameTagPositions.contains(nameTagPosition)
          ? nameTagPosition
          : 'bottomOverlap',
      imageUri: imageUri.startsWith('content://') ? imageUri : null,
      xFraction: _readFraction(position['xFraction'], 0.78),
      yFraction: _readFraction(position['yFraction'], 0.70),
      locked: _readBool(position['locked'], false),
    );
  }

  factory CameraOverlaySettings.fromLegacy(Map<String, dynamic> json) {
    final size = _readInt(
      json['cameraPreviewSizeDp'],
      _readInt(json['cameraSizeDp'], 140),
    );
    final legacyShape = _readString(json['cameraShape'], 'circle');
    return CameraOverlaySettings(
      enabled: _readBool(json['frontCameraEnabled'], false),
      shape: validShapes.contains(legacyShape) ? legacyShape : 'circle',
      sizeDp: size.clamp(80, 260).toInt(),
      borderColor: _readColor(json['cameraBorderColor'], -1),
      borderWidthDp: _readInt(
        json['cameraBorderWidthDp'],
        4,
      ).clamp(0, 16).toInt(),
    );
  }

  static int _readInt(Object? value, int fallback) =>
      value is num && value.isFinite ? value.round() : fallback;

  static String _readString(Object? value, String fallback) =>
      value is String ? value : fallback;

  static bool _readBool(Object? value, bool fallback) =>
      value is bool ? value : fallback;

  static int _readColor(Object? value, int fallback) {
    if (value is! num || !value.isFinite) return fallback;
    return value.round().toSigned(32);
  }

  static double _readFraction(Object? value, double fallback) {
    if (value is! num || !value.isFinite) return fallback;
    return value.toDouble().clamp(0, 1).toDouble();
  }

  static String _readName(Object? value) {
    if (value is! String) return 'Meu Canal';
    return String.fromCharCodes(value.runes.take(24));
  }
}
