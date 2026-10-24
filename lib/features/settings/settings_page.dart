import 'package:b13_falcon/core/constants/app_colors.dart';
import 'package:b13_falcon/data/models/app_settings.dart';
import 'package:flutter/material.dart';

class SettingsPage extends StatelessWidget {
  const SettingsPage({
    super.key,
    required this.settings,
    required this.onChanged,
    required this.onReset,
  });

  final AppSettings settings;
  final ValueChanged<AppSettings> onChanged;
  final VoidCallback onReset;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    return Scaffold(
      appBar: AppBar(title: const Text('Configurações')),
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
            title: 'Controles flutuantes',
            subtitle: 'Mostrar painel flutuante opcional.',
            value: settings.showFloatingControls,
            onChanged: (value) =>
                onChanged(settings.copyWith(showFloatingControls: value)),
          ),
          const SizedBox(height: 12),
          _SettingSwitch(
            title: 'Áudio interno',
            subtitle: 'Usar áudio interno quando disponível.',
            value: settings.useInternalAudio,
            onChanged: (value) =>
                onChanged(settings.copyWith(useInternalAudio: value)),
          ),
          const SizedBox(height: 16),
          Container(
            padding: const EdgeInsets.all(18),
            decoration: BoxDecoration(
              color: AppColors.surface,
              borderRadius: BorderRadius.circular(18),
              border: Border.all(color: AppColors.border),
            ),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text('Resolução preferida', style: theme.textTheme.titleMedium),
                const SizedBox(height: 8),
                Wrap(
                  spacing: 8,
                  children: ['480p', '720p', '1080p']
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
        color: AppColors.surface,
        borderRadius: BorderRadius.circular(18),
        border: Border.all(color: AppColors.border),
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
