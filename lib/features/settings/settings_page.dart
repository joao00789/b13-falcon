import 'package:b13_falcon/core/constants/app_colors.dart';
import 'package:b13_falcon/data/models/app_settings.dart';
import 'package:flutter/material.dart';

class SettingsPage extends StatelessWidget {
  const SettingsPage({
    super.key,
    required this.settings,
    required this.onChanged,
    required this.onReset,
    required this.onBack,
  });

  final AppSettings settings;
  final ValueChanged<AppSettings> onChanged;
  final VoidCallback onReset;
  final VoidCallback onBack;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    return Scaffold(
      appBar: AppBar(
        leading: IconButton(
          onPressed: onBack,
          tooltip: 'Voltar para início',
          icon: const Icon(Icons.arrow_back_rounded),
        ),
        title: const Text('Configurações'),
      ),
      body: ListView(
        padding: const EdgeInsets.all(20),
        children: [
          _SettingSwitch(
            title: 'Tema escuro',
            subtitle: 'Ativar ou desativar o tema principal.',
            value: settings.isDarkMode,
            onChanged: (value) =>
                onChanged(settings.copyWith(isDarkMode: value)),
          ),
          const SizedBox(height: 12),
          _SettingSwitch(
            title: 'Manter tela ligada',
            subtitle: 'Evitar que a tela desligue durante a gravação.',
            value: settings.keepScreenOn,
            onChanged: (value) =>
                onChanged(settings.copyWith(keepScreenOn: value)),
          ),
          const SizedBox(height: 12),
          _SettingSwitch(
            title: 'Menu flutuante de gravação',
            subtitle:
                'Pausar/retomar, parar e desenhar sobre outros apps. '
                'O pincel captura os toques até tocar em Concluir.',
            value: settings.showFloatingControls,
            onChanged: (value) =>
                onChanged(settings.copyWith(showFloatingControls: value)),
          ),
          const SizedBox(height: 16),
          Container(
            padding: const EdgeInsets.all(18),
            decoration: BoxDecoration(
              color: theme.colorScheme.surface,
              borderRadius: BorderRadius.circular(18),
              border: Border.all(color: theme.dividerColor),
            ),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text('Resolução preferida', style: theme.textTheme.titleMedium),
                const SizedBox(height: 8),
                Wrap(
                  spacing: 8,
                  runSpacing: 8,
                  children: ['480p', '720p', '1080p', '1440p']
                      .map(
                        (option) => ChoiceChip(
                          label: Text(option),
                          selected: settings.preferredResolution == option,
                          onSelected: (_) => onChanged(
                            settings.copyWith(preferredResolution: option),
                          ),
                        ),
                      )
                      .toList(),
                ),
              ],
            ),
          ),
          const SizedBox(height: 16),
          Container(
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
                  children: [24, 30, 60]
                      .map(
                        (fps) => ChoiceChip(
                          label: Text('$fps FPS'),
                          selected: settings.preferredFps == fps,
                          onSelected: (_) =>
                              onChanged(settings.copyWith(preferredFps: fps)),
                        ),
                      )
                      .toList(),
                ),
                const SizedBox(height: 18),
                Row(
                  mainAxisAlignment: MainAxisAlignment.spaceBetween,
                  children: [
                    Text('Taxa de bits', style: theme.textTheme.titleMedium),
                    Text('${settings.bitrateKbps ~/ 1000} Mbps'),
                  ],
                ),
                Slider(
                  min: 4,
                  max: 30,
                  divisions: 13,
                  value: (settings.bitrateKbps / 1000).clamp(4, 30).toDouble(),
                  label: '${settings.bitrateKbps ~/ 1000} Mbps',
                  onChanged: (value) => onChanged(
                    settings.copyWith(bitrateKbps: (value * 1000).round()),
                  ),
                ),
                Text(
                  'Valores maiores preservam mais detalhes, mas geram arquivos maiores.',
                  style: theme.textTheme.bodySmall,
                ),
              ],
            ),
          ),
          const SizedBox(height: 14),
          Text(
            'A gravação captura vídeo da tela. Áudio não está disponível nesta versão.',
            style: theme.textTheme.bodySmall,
            textAlign: TextAlign.center,
          ),
          const SizedBox(height: 16),
          FilledButton.tonalIcon(
            onPressed: onReset,
            icon: const Icon(Icons.restore_rounded),
            label: const Text('Restaurar padrão'),
          ),
        ],
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
