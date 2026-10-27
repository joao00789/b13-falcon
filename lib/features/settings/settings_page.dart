import 'dart:math' as math;

import 'package:b13_falcon/core/constants/app_colors.dart';
import 'package:b13_falcon/core/theme/app_theme_data.dart';
import 'package:b13_falcon/core/widgets/entrance_animation.dart';
import 'package:b13_falcon/data/models/app_settings.dart';
import 'package:flutter/material.dart';

class SettingsPage extends StatelessWidget {
  const SettingsPage({
    super.key,
    required this.settings,
    required this.onChanged,
    required this.onReset,
    required this.onBack,
    required this.supportedResolutions,
    required this.supportedFps,
    required this.hevcSupported,
    this.sourceWidth = 1080,
    this.sourceHeight = 1920,
  });

  final AppSettings settings;
  final ValueChanged<AppSettings> onChanged;
  final VoidCallback onReset;
  final VoidCallback onBack;
  final List<String> supportedResolutions;
  final List<int> supportedFps;
  final bool hevcSupported;
  final int sourceWidth;
  final int sourceHeight;

  Future<void> _selectCameraBorderColor(BuildContext context) async {
    var selected = Color(settings.cameraOverlay.borderColor & 0xFFFFFFFF);
    var hsv = HSVColor.fromColor(selected);
    final hexController = TextEditingController(
      text: selected.toARGB32().toRadixString(16).padLeft(8, '0').toUpperCase(),
    );
    try {
      final result = await showDialog<Color>(
        context: context,
        builder: (context) => StatefulBuilder(
          builder: (context, setDialogState) => AlertDialog(
            title: const Text('Cor da borda'),
            content: SizedBox(
              width: 360,
              child: SingleChildScrollView(
                child: Column(
                  mainAxisSize: MainAxisSize.min,
                  children: [
                    Container(
                      width: 72,
                      height: 72,
                      decoration: BoxDecoration(
                        color: selected,
                        shape: BoxShape.circle,
                        border: Border.all(
                          color: Theme.of(context).colorScheme.outline,
                          width: 2,
                        ),
                      ),
                    ),
                    const SizedBox(height: 12),
                    TextField(
                      controller: hexController,
                      maxLength: 8,
                      decoration: const InputDecoration(
                        labelText: 'ARGB hexadecimal',
                        prefixText: '#',
                        helperText: 'Use 8 dígitos, ou 6 para cor opaca.',
                      ),
                      onChanged: (value) {
                        final digits = value.replaceAll('#', '').trim();
                        if (digits.length != 6 && digits.length != 8) return;
                        final parsed = int.tryParse(digits, radix: 16);
                        if (parsed == null) return;
                        final argb = digits.length == 6
                            ? (0xFF000000 | parsed)
                            : parsed;
                        setDialogState(() {
                          selected = Color(argb);
                          hsv = HSVColor.fromColor(selected);
                        });
                      },
                    ),
                    _hsvSlider(
                      label: 'Matiz',
                      value: hsv.hue,
                      max: 360,
                      divisions: 360,
                      onChanged: (value) {
                        setDialogState(() {
                          hsv = hsv.withHue(value);
                          selected = hsv.toColor();
                          hexController.text = selected
                              .toARGB32()
                              .toRadixString(16)
                              .padLeft(8, '0')
                              .toUpperCase();
                        });
                      },
                    ),
                    _hsvSlider(
                      label: 'Saturação',
                      value: hsv.saturation,
                      max: 1,
                      divisions: 100,
                      onChanged: (value) {
                        setDialogState(() {
                          hsv = hsv.withSaturation(value);
                          selected = hsv.toColor();
                          hexController.text = selected
                              .toARGB32()
                              .toRadixString(16)
                              .padLeft(8, '0')
                              .toUpperCase();
                        });
                      },
                    ),
                    _hsvSlider(
                      label: 'Brilho',
                      value: hsv.value,
                      max: 1,
                      divisions: 100,
                      onChanged: (value) {
                        setDialogState(() {
                          hsv = hsv.withValue(value);
                          selected = hsv.toColor();
                          hexController.text = selected
                              .toARGB32()
                              .toRadixString(16)
                              .padLeft(8, '0')
                              .toUpperCase();
                        });
                      },
                    ),
                    _hsvSlider(
                      label: 'Opacidade',
                      value: hsv.alpha,
                      max: 1,
                      divisions: 100,
                      onChanged: (value) {
                        setDialogState(() {
                          hsv = hsv.withAlpha(value);
                          selected = hsv.toColor();
                          hexController.text = selected
                              .toARGB32()
                              .toRadixString(16)
                              .padLeft(8, '0')
                              .toUpperCase();
                        });
                      },
                    ),
                  ],
                ),
              ),
            ),
            actions: [
              TextButton(
                onPressed: () => Navigator.pop(context),
                child: const Text('Cancelar'),
              ),
              FilledButton(
                onPressed: () => Navigator.pop(context, selected),
                child: const Text('Aplicar'),
              ),
            ],
          ),
        ),
      );
      if (result != null) {
        onChanged(
          settings.copyWith(
            cameraOverlay: settings.cameraOverlay.copyWith(
              borderColor: result.toARGB32().toSigned(32),
            ),
          ),
        );
      }
    } finally {
      hexController.dispose();
    }
  }

  Widget _hsvSlider({
    required String label,
    required double value,
    required double max,
    required int divisions,
    required ValueChanged<double> onChanged,
  }) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Text(label),
        Slider(
          min: 0,
          max: max,
          divisions: divisions,
          value: value.clamp(0, max).toDouble(),
          onChanged: onChanged,
        ),
      ],
    );
  }

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final availableFps = {...supportedFps, settings.preferredFps}.toList()
      ..sort();
    return Scaffold(
      appBar: AppBar(
        automaticallyImplyLeading: false,
        title: const Align(
          alignment: Alignment.centerLeft,
          child: Text('Configurações'),
        ),
      ),
      body: ListView(
        padding: const EdgeInsets.all(20),
        children: [
          EntranceAnimation(
            child: _SettingSwitch(
              title: 'Tema escuro',
              subtitle: 'Ativar ou desativar o tema principal.',
              value: settings.isDarkMode,
              onChanged: (value) =>
                  onChanged(settings.copyWith(isDarkMode: value)),
            ),
          ),
          const SizedBox(height: 12),
          EntranceAnimation(
            delay: const Duration(milliseconds: 35),
            child: Container(
              padding: const EdgeInsets.all(18),
              decoration: BoxDecoration(
                color: theme.colorScheme.surface.withValues(alpha: 0.88),
                borderRadius: BorderRadius.circular(18),
                border: Border.all(color: theme.dividerColor),
              ),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text('Temas animados', style: theme.textTheme.titleMedium),
                  const SizedBox(height: 12),
                  SizedBox(
                    height: 190,
                    child: ListView.separated(
                      scrollDirection: Axis.horizontal,
                      itemCount: AppThemeData.themes.length,
                      separatorBuilder: (_, _) => const SizedBox(width: 10),
                      itemBuilder: (context, index) {
                        final themeData = AppThemeData.themes[index];
                        return ThemePreviewCard(
                          themeData: themeData,
                          selected: settings.themeAccent == themeData.id,
                          onTap: () => onChanged(
                            settings.copyWith(themeAccent: themeData.id),
                          ),
                        );
                      },
                    ),
                  ),
                ],
              ),
            ),
          ),
          const SizedBox(height: 12),
          EntranceAnimation(
            delay: const Duration(milliseconds: 55),
            child: _SettingSwitch(
              title: 'Manter tela ligada',
              subtitle: 'Evitar que a tela desligue durante a gravação.',
              value: settings.keepScreenOn,
              onChanged: (value) =>
                  onChanged(settings.copyWith(keepScreenOn: value)),
            ),
          ),
          const SizedBox(height: 12),
          EntranceAnimation(
            delay: const Duration(milliseconds: 110),
            child: _SettingSwitch(
              title: 'Menu flutuante de gravação',
              subtitle:
                  'Uma bolha pequena com a logo do app. Toque para abrir os '
                  'controles e arraste para mudar de lugar.',
              value: settings.showFloatingControls,
              onChanged: (value) =>
                  onChanged(settings.copyWith(showFloatingControls: value)),
            ),
          ),
          const SizedBox(height: 12),
          EntranceAnimation(
            delay: const Duration(milliseconds: 165),
            child: Container(
              padding: const EdgeInsets.all(18),
              decoration: BoxDecoration(
                color: theme.colorScheme.surface,
                borderRadius: BorderRadius.circular(18),
                border: Border.all(color: theme.dividerColor),
              ),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text('Áudio da gravação', style: theme.textTheme.titleMedium),
                  const SizedBox(height: 6),
                  Text(
                    'O áudio interno depende do Android 10+ e de o jogo permitir '
                    'a captura. Alguns jogos podem bloqueá-la.',
                    style: theme.textTheme.bodySmall,
                  ),
                  const SizedBox(height: 12),
                  Wrap(
                    spacing: 8,
                    runSpacing: 8,
                    children:
                        const [
                          ('both', 'Interno + microfone'),
                          ('internal', 'Som do jogo'),
                          ('microphone', 'Microfone'),
                          ('none', 'Sem áudio'),
                        ].map((option) {
                          final value = option.$1;
                          final label = option.$2;
                          final selectedValue = AppSettings.normalizeAudioMode(
                            settings.audioMode,
                          );
                          return ChoiceChip(
                            label: Text(label),
                            selected: selectedValue == value,
                            onSelected: (_) =>
                                onChanged(settings.copyWith(audioMode: value)),
                          );
                        }).toList(),
                  ),
                ],
              ),
            ),
          ),
          const SizedBox(height: 16),
          EntranceAnimation(
            delay: const Duration(milliseconds: 190),
            child: Container(
              padding: const EdgeInsets.all(18),
              decoration: BoxDecoration(
                color: theme.colorScheme.surface,
                borderRadius: BorderRadius.circular(18),
                border: Border.all(color: theme.dividerColor),
              ),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Row(
                    mainAxisAlignment: MainAxisAlignment.spaceBetween,
                    children: [
                      Text(
                        'Opacidade da logo flutuante',
                        style: theme.textTheme.titleMedium,
                      ),
                      Text(
                        '${(settings.floatingLogoOpacity * 100).round()}%',
                        style: theme.textTheme.titleSmall?.copyWith(
                          color: theme.colorScheme.primary,
                          fontWeight: FontWeight.w700,
                        ),
                      ),
                    ],
                  ),
                  Slider(
                    min: 0,
                    max: 1,
                    divisions: 20,
                    value: settings.floatingLogoOpacity,
                    label: '${(settings.floatingLogoOpacity * 100).round()}%',
                    onChanged: (value) => onChanged(
                      settings.copyWith(floatingLogoOpacity: value),
                    ),
                  ),
                  Text(
                    'Com 0%, a bolha fica invisível, mas continua tocável. '
                    'Ao tocar, um contorno discreto indica onde ela está.',
                    style: theme.textTheme.bodySmall,
                  ),
                  const SizedBox(height: 10),
                  Row(
                    children: [
                      Expanded(
                        child: Text(
                          'Tamanho da bolha',
                          style: theme.textTheme.titleSmall,
                        ),
                      ),
                      Text('${settings.floatingLogoSizeDp} dp'),
                    ],
                  ),
                  Slider(
                    min: 32,
                    max: 96,
                    divisions: 16,
                    value: settings.floatingLogoSizeDp.toDouble(),
                    label: '${settings.floatingLogoSizeDp} dp',
                    onChanged: (value) => onChanged(
                      settings.copyWith(floatingLogoSizeDp: value.round()),
                    ),
                  ),
                ],
              ),
            ),
          ),
          const SizedBox(height: 16),
          EntranceAnimation(
            delay: const Duration(milliseconds: 205),
            child: Container(
              padding: const EdgeInsets.all(18),
              decoration: BoxDecoration(
                color: theme.colorScheme.surface,
                borderRadius: BorderRadius.circular(18),
                border: Border.all(color: theme.dividerColor),
              ),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text('Câmera frontal', style: theme.textTheme.titleMedium),
                  const SizedBox(height: 4),
                  Text(
                    'Inclua sua câmera no vídeo gravado. A composição usa o '
                    'encoder do aparelho e também mostra uma prévia flutuante '
                    'sobre outros apps; exige permissão de sobreposição e pode '
                    'reduzir o FPS em modelos mais fracos.',
                    style: theme.textTheme.bodySmall,
                  ),
                  const SizedBox(height: 10),
                  SwitchListTile.adaptive(
                    contentPadding: EdgeInsets.zero,
                    title: const Text('Gravar câmera frontal'),
                    subtitle: const Text(
                      'A imagem será incorporada ao MP4, não apenas exibida na tela.',
                    ),
                    value: settings.cameraOverlay.enabled,
                    onChanged: (value) => onChanged(
                      settings.copyWith(
                        cameraOverlay: settings.cameraOverlay.copyWith(
                          enabled: value,
                        ),
                      ),
                    ),
                  ),
                  if (settings.cameraOverlay.enabled) ...[
                    const SizedBox(height: 4),
                    Text(
                      'Formato da câmera',
                      style: theme.textTheme.titleSmall,
                    ),
                    const SizedBox(height: 6),
                    Wrap(
                      spacing: 8,
                      children:
                          const [
                            ('circle', 'Circular'),
                            ('rounded', 'Cantos arredondados'),
                          ].map((shape) {
                            final (value, label) = shape;
                            return ChoiceChip(
                              label: Text(label),
                              selected: settings.cameraOverlay.shape == value,
                              onSelected: (_) => onChanged(
                                settings.copyWith(
                                  cameraOverlay: settings.cameraOverlay
                                      .copyWith(shape: value),
                                ),
                              ),
                            );
                          }).toList(),
                    ),
                    const SizedBox(height: 8),
                    Text('Cor da borda', style: theme.textTheme.titleSmall),
                    const SizedBox(height: 6),
                    Wrap(
                      spacing: 10,
                      children:
                          const [
                            (-1, 'Branca'),
                            (0xFFB81C2B - 0x100000000, 'Falcon'),
                            (0xFF168AAD - 0x100000000, 'Ocean'),
                            (0xFF16865A - 0x100000000, 'Emerald'),
                            (0xFF7950B8 - 0x100000000, 'Violet'),
                            (0xFFC9A85A - 0x100000000, 'Black'),
                          ].map((option) {
                            final (value, label) = option;
                            final selected =
                                settings.cameraOverlay.borderColor == value;
                            return Tooltip(
                              message: label,
                              child: InkWell(
                                customBorder: const CircleBorder(),
                                onTap: () => onChanged(
                                  settings.copyWith(
                                    cameraOverlay: settings.cameraOverlay
                                        .copyWith(borderColor: value),
                                  ),
                                ),
                                child: AnimatedContainer(
                                  duration: const Duration(milliseconds: 160),
                                  width: 34,
                                  height: 34,
                                  decoration: BoxDecoration(
                                    shape: BoxShape.circle,
                                    color: Color(value & 0xFFFFFFFF),
                                    border: Border.all(
                                      color: selected
                                          ? theme.colorScheme.primary
                                          : theme.dividerColor,
                                      width: selected ? 3 : 1,
                                    ),
                                  ),
                                  child: selected
                                      ? Icon(
                                          Icons.check_rounded,
                                          size: 18,
                                          color:
                                              theme.brightness ==
                                                  Brightness.dark
                                              ? Colors.black
                                              : Colors.white,
                                        )
                                      : null,
                                ),
                              ),
                            );
                          }).toList(),
                    ),
                    const SizedBox(height: 8),
                    OutlinedButton.icon(
                      onPressed: () => _selectCameraBorderColor(context),
                      icon: const Icon(Icons.palette_outlined),
                      label: const Text('Escolher qualquer cor'),
                    ),
                    const SizedBox(height: 8),
                    Text('Estilo da borda', style: theme.textTheme.titleSmall),
                    const SizedBox(height: 6),
                    Wrap(
                      spacing: 8,
                      children:
                          const [
                            ('solid', 'Sólida'),
                            ('dashed', 'Tracejada'),
                            ('double', 'Dupla'),
                            ('gradient', 'Gradiente'),
                            ('glow', 'Brilho'),
                          ].map((style) {
                            final (value, label) = style;
                            return ChoiceChip(
                              label: Text(label),
                              selected:
                                  settings.cameraOverlay.borderStyle == value,
                              onSelected: (_) => onChanged(
                                settings.copyWith(
                                  cameraOverlay: settings.cameraOverlay
                                      .copyWith(borderStyle: value),
                                ),
                              ),
                            );
                          }).toList(),
                    ),
                    const SizedBox(height: 8),
                    Row(
                      children: [
                        Expanded(
                          child: Text(
                            'Espessura da borda',
                            style: theme.textTheme.titleSmall,
                          ),
                        ),
                        Text('${settings.cameraOverlay.borderWidthDp} dp'),
                      ],
                    ),
                    Slider(
                      min: 0,
                      max: 12,
                      divisions: 12,
                      value: settings.cameraOverlay.borderWidthDp.toDouble(),
                      label: '${settings.cameraOverlay.borderWidthDp} dp',
                      onChanged: (value) => onChanged(
                        settings.copyWith(
                          cameraOverlay: settings.cameraOverlay.copyWith(
                            borderWidthDp: value.round(),
                          ),
                        ),
                      ),
                    ),
                    const SizedBox(height: 4),
                    Row(
                      children: [
                        Expanded(
                          child: Text(
                            'Tamanho da prévia flutuante',
                            style: theme.textTheme.titleSmall,
                          ),
                        ),
                        Text('${settings.cameraOverlay.sizeDp} dp'),
                      ],
                    ),
                    Slider(
                      min: 80,
                      max: 260,
                      divisions: 18,
                      value: settings.cameraOverlay.sizeDp.toDouble(),
                      label: '${settings.cameraOverlay.sizeDp} dp',
                      onChanged: (value) => onChanged(
                        settings.copyWith(
                          cameraOverlay: settings.cameraOverlay.copyWith(
                            sizeDp: value.round(),
                          ),
                        ),
                      ),
                    ),
                  ],
                ],
              ),
            ),
          ),
          const SizedBox(height: 16),
          EntranceAnimation(
            delay: const Duration(milliseconds: 220),
            child: Container(
              padding: const EdgeInsets.all(18),
              decoration: BoxDecoration(
                color: theme.colorScheme.surface,
                borderRadius: BorderRadius.circular(18),
                border: Border.all(color: theme.dividerColor),
              ),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text('Resolução', style: theme.textTheme.titleMedium),
                  const SizedBox(height: 8),
                  Wrap(
                    spacing: 8,
                    runSpacing: 8,
                    children:
                        [
                              'Nativa',
                              '360p',
                              '480p',
                              '540p',
                              '720p',
                              '1080p',
                              '1440p',
                            ]
                            .where((option) {
                              final value = option == 'Nativa'
                                  ? 'native'
                                  : option;
                              return supportedResolutions.contains(value) ||
                                  value == settings.preferredResolution;
                            })
                            .map((option) {
                              final isSelected =
                                  settings.preferredResolution == option ||
                                  (option == 'Nativa' &&
                                      settings.preferredResolution == 'native');
                              return ChoiceChip(
                                label: Text(option),
                                selected: isSelected,
                                onSelected: (_) => onChanged(
                                  _updateAutomaticBitrate(
                                    settings.copyWith(
                                      preferredResolution: option == 'Nativa'
                                          ? 'native'
                                          : option,
                                    ),
                                  ),
                                ),
                              );
                            })
                            .toList(),
                  ),
                ],
              ),
            ),
          ),
          const SizedBox(height: 16),
          EntranceAnimation(
            delay: const Duration(milliseconds: 275),
            child: Container(
              padding: const EdgeInsets.all(18),
              decoration: BoxDecoration(
                color: theme.colorScheme.surface,
                borderRadius: BorderRadius.circular(18),
                border: Border.all(color: theme.dividerColor),
              ),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text('Taxa de quadros', style: theme.textTheme.titleMedium),
                  const SizedBox(height: 8),
                  Wrap(
                    spacing: 8,
                    children: availableFps
                        .map(
                          (fps) => ChoiceChip(
                            label: Text('$fps FPS'),
                            selected: settings.preferredFps == fps,
                            onSelected: (_) => onChanged(
                              _updateAutomaticBitrate(
                                settings.copyWith(preferredFps: fps),
                              ),
                            ),
                          ),
                        )
                        .toList(),
                  ),
                  const SizedBox(height: 18),
                  Text('Codec de vídeo', style: theme.textTheme.titleMedium),
                  const SizedBox(height: 8),
                  Wrap(
                    spacing: 8,
                    children:
                        [
                          ('h264', 'H.264'),
                          if (hevcSupported) ('hevc', 'HEVC / H.265'),
                        ].map((codec) {
                          final (value, label) = codec;
                          return ChoiceChip(
                            label: Text(label),
                            selected: settings.codec == value,
                            onSelected: (_) =>
                                onChanged(settings.copyWith(codec: value)),
                          );
                        }).toList(),
                  ),
                  const SizedBox(height: 18),
                  Text(
                    'Orientação do vídeo',
                    style: theme.textTheme.titleMedium,
                  ),
                  const SizedBox(height: 8),
                  Wrap(
                    spacing: 8,
                    children:
                        const [
                          ('auto', 'Automática'),
                          ('portrait', 'Retrato'),
                          ('landscape', 'Paisagem'),
                        ].map((orientation) {
                          final (value, label) = orientation;
                          return ChoiceChip(
                            label: Text(label),
                            selected: settings.orientation == value,
                            onSelected: (_) => onChanged(
                              settings.copyWith(orientation: value),
                            ),
                          );
                        }).toList(),
                  ),
                  const SizedBox(height: 18),
                  Text(
                    'Qualidade do vídeo',
                    style: theme.textTheme.titleMedium,
                  ),
                  const SizedBox(height: 8),
                  Wrap(
                    spacing: 8,
                    runSpacing: 8,
                    children:
                        const [
                          ('economy', 'Economia'),
                          ('balanced', 'Equilibrada'),
                          ('high', 'Alta'),
                          ('maximum', 'Máxima'),
                          ('custom', 'Personalizada'),
                        ].map((preset) {
                          final (value, label) = preset;
                          return ChoiceChip(
                            label: Text(label),
                            selected: settings.bitratePreset == value,
                            onSelected: (_) {
                              if (value == 'custom') {
                                onChanged(
                                  settings.copyWith(bitratePreset: value),
                                );
                              } else {
                                final bitrate = SettingsPage.recommendedBitrate(
                                  settings.preferredResolution,
                                  settings.preferredFps,
                                  value,
                                  sourceWidth: sourceWidth,
                                  sourceHeight: sourceHeight,
                                );
                                onChanged(
                                  settings.copyWith(
                                    bitratePreset: value,
                                    bitrateKbps: bitrate,
                                  ),
                                );
                              }
                            },
                          );
                        }).toList(),
                  ),
                  if (settings.bitratePreset == 'custom') ...[
                    const SizedBox(height: 16),
                    Row(
                      mainAxisAlignment: MainAxisAlignment.spaceBetween,
                      children: [
                        Text(
                          'Taxa de bits',
                          style: theme.textTheme.titleMedium,
                        ),
                        Text('${settings.bitrateKbps ~/ 1000} Mbps'),
                      ],
                    ),
                    Slider(
                      min: 4,
                      max: 80,
                      divisions: 19,
                      value: (settings.bitrateKbps / 1000)
                          .clamp(4, 80)
                          .toDouble(),
                      label: '${settings.bitrateKbps ~/ 1000} Mbps',
                      onChanged: (value) => onChanged(
                        settings.copyWith(
                          bitrateKbps: (value * 1000).round(),
                          bitratePreset: 'custom',
                        ),
                      ),
                    ),
                    Text(
                      'Valores maiores preservam mais detalhes, mas geram arquivos maiores.',
                      style: theme.textTheme.bodySmall,
                    ),
                  ],
                  const SizedBox(height: 8),
                  Text(
                    'Estimativa: ${(SettingsPage.estimatedMegabytesPerMinute(settings.bitrateKbps)).round()} MB por minuto.',
                    style: theme.textTheme.bodySmall,
                  ),
                ],
              ),
            ),
          ),
          const SizedBox(height: 14),
          Text(
            'A captura de áudio interno funciona no Android 10 ou superior e '
            'pode ser bloqueada pelo jogo. O microfone exige permissão.',
            style: theme.textTheme.bodySmall,
            textAlign: TextAlign.center,
          ),
          const SizedBox(height: 16),
          EntranceAnimation(
            delay: const Duration(milliseconds: 330),
            child: FilledButton.tonalIcon(
              onPressed: onReset,
              icon: const Icon(Icons.restore_rounded),
              label: const Text('Restaurar padrão'),
            ),
          ),
        ],
      ),
    );
  }

  static int recommendedBitrate(
    String resolution,
    int fps,
    String preset, {
    int sourceWidth = 1080,
    int sourceHeight = 1920,
  }) {
    const maxDimensions = {
      '360p': 640,
      '480p': 854,
      '540p': 960,
      '720p': 1280,
      '1080p': 1920,
      '1440p': 2560,
    };
    final requestedMaxDimension =
        maxDimensions[resolution] ?? math.max(sourceWidth, sourceHeight);
    final scale = math.min(
      1.0,
      requestedMaxDimension / math.max(sourceWidth, sourceHeight),
    );
    final width = (sourceWidth * scale).floor() ~/ 2 * 2;
    final height = (sourceHeight * scale).floor() ~/ 2 * 2;
    final bitsPerPixel = switch (preset) {
      'economy' => 0.08,
      'high' => 0.20,
      'maximum' => 0.30,
      _ => 0.14,
    };
    return (width * height * fps * bitsPerPixel / 1000).round().clamp(
      4000,
      80000,
    );
  }

  static double estimatedMegabytesPerMinute(int bitrateKbps) =>
      ((bitrateKbps + 128) * 60 / 8) / 1000;

  AppSettings _updateAutomaticBitrate(AppSettings nextSettings) {
    if (nextSettings.bitratePreset == 'custom') return nextSettings;
    return nextSettings.copyWith(
      bitrateKbps: recommendedBitrate(
        nextSettings.preferredResolution,
        nextSettings.preferredFps,
        nextSettings.bitratePreset,
        sourceWidth: sourceWidth,
        sourceHeight: sourceHeight,
      ),
    );
  }
}

class _SettingSwitch extends StatelessWidget {
  const _SettingSwitch({
    required this.title,
    required this.subtitle,
    required this.value,
    required this.onChanged,
  });

  final String title;
  final String subtitle;
  final bool value;
  final ValueChanged<bool> onChanged;

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.all(14),
      decoration: BoxDecoration(
        color: Theme.of(context).colorScheme.surface,
        borderRadius: BorderRadius.circular(18),
        border: Border.all(color: Theme.of(context).dividerColor),
      ),
      child: Row(
        children: [
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(title, style: Theme.of(context).textTheme.titleMedium),
                const SizedBox(height: 2),
                Text(
                  subtitle,
                  style: Theme.of(context).textTheme.bodySmall
                      ?.copyWith(color: AppColors.muted),
                ),
              ],
            ),
          ),
          Switch(value: value, onChanged: onChanged),
        ],
      ),
    );
  }
}
