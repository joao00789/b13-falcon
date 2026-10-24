import 'package:b13_falcon/core/constants/app_colors.dart';
import 'package:b13_falcon/core/constants/app_strings.dart';
import 'package:b13_falcon/core/utils/formatters.dart';
import 'package:b13_falcon/data/models/app_settings.dart';
import 'package:flutter/material.dart';

class HomePage extends StatelessWidget {
  const HomePage({
    super.key,
    required this.settings,
    required this.isRecording,
    required this.elapsed,
    required this.statusMessage,
    required this.onRecordToggle,
    required this.onPauseToggle,
    required this.onOpenLibrary,
    required this.onOpenSettings,
  });

  final AppSettings settings;
  final bool isRecording;
  final Duration elapsed;
  final String statusMessage;
  final VoidCallback onRecordToggle;
  final VoidCallback onPauseToggle;
  final VoidCallback onOpenLibrary;
  final VoidCallback onOpenSettings;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    return Scaffold(
      body: SafeArea(
        child: Padding(
          padding: const EdgeInsets.all(20),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: [
              Row(
                children: [
                  ClipRRect(
                    borderRadius: BorderRadius.circular(10),
                    child: Image.asset(
                      'logo B13 falcon.png',
                      width: 48,
                      height: 48,
                      fit: BoxFit.cover,
                      semanticLabel: 'Logo B13 FALCON',
                    ),
                  ),
                  const SizedBox(width: 12),
                  Expanded(
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Text(
                          AppStrings.appName,
                          style: theme.textTheme.headlineMedium?.copyWith(
                            fontWeight: FontWeight.w800,
                            letterSpacing: 1.2,
                          ),
                        ),
                        const SizedBox(height: 4),
                        Text(
                          'Gravador profissional de tela',
                          style: theme.textTheme.bodyMedium?.copyWith(
                            color: AppColors.muted,
                          ),
                        ),
                      ],
                    ),
                  ),
                  IconButton(
                    onPressed: onOpenSettings,
                    icon: const Icon(Icons.settings_outlined),
                    tooltip: 'Configurações',
                  ),
                ],
              ),
              const SizedBox(height: 18),
              Container(
                padding: const EdgeInsets.all(24),
                decoration: BoxDecoration(
                  color: AppColors.surface,
                  borderRadius: BorderRadius.circular(24),
                  border: Border.all(color: AppColors.border),
                ),
                child: Column(
                  children: [
                    Text(
                      isRecording ? 'Gravando agora' : 'Pronto para capturar',
                      style: theme.textTheme.titleMedium?.copyWith(
                        color: isRecording ? AppColors.white : AppColors.muted,
                        fontWeight: FontWeight.w600,
                      ),
                    ),
                    const SizedBox(height: 18),
                    Text(
                      Formatters.formatDuration(elapsed),
                      style: theme.textTheme.displayMedium?.copyWith(
                        fontWeight: FontWeight.w700,
                        letterSpacing: 1.2,
                        color: isRecording ? AppColors.white : AppColors.silver,
                      ),
                    ),
                    const SizedBox(height: 20),
                    Row(
                      children: [
                        Expanded(
                          child: SizedBox(
                            height: 60,
                            child: FilledButton.icon(
                              style: FilledButton.styleFrom(
                                backgroundColor: isRecording
                                    ? AppColors.darkRed
                                    : AppColors.accent,
                                foregroundColor: AppColors.white,
                                shape: RoundedRectangleBorder(
                                  borderRadius: BorderRadius.circular(18),
                                ),
                              ),
                              onPressed: onRecordToggle,
                              icon: Icon(
                                isRecording
                                    ? Icons.stop_rounded
                                    : Icons.fiber_manual_record_rounded,
                              ),
                              label: Text(isRecording ? 'Parar' : 'Gravar'),
                            ),
                          ),
                        ),
                        const SizedBox(width: 12),
                        if (isRecording)
                          Expanded(
                            child: SizedBox(
                              height: 60,
                              child: OutlinedButton.icon(
                                onPressed: onPauseToggle,
                                style: OutlinedButton.styleFrom(
                                  side: const BorderSide(
                                    color: AppColors.border,
                                  ),
                                  foregroundColor: AppColors.white,
                                  shape: RoundedRectangleBorder(
                                    borderRadius: BorderRadius.circular(18),
                                  ),
                                ),
                                icon: const Icon(Icons.pause_rounded),
                                label: const Text('Pausar'),
                              ),
                            ),
                          ),
                      ],
                    ),
                    const SizedBox(height: 18),
                    Text(
                      statusMessage,
                      textAlign: TextAlign.center,
                      style: theme.textTheme.bodyMedium?.copyWith(
                        color: AppColors.muted,
                      ),
                    ),
                  ],
                ),
              ),
              const SizedBox(height: 20),
              Row(
                children: [
                  Expanded(
                    child: _InfoCard(
                      title: 'Qualidade',
                      value: settings.preferredResolution,
                      icon: Icons.high_quality_rounded,
                    ),
                  ),
                  const SizedBox(width: 12),
                  Expanded(
                    child: _InfoCard(
                      title: 'Áudio',
                      value: settings.audioMode,
                      icon: Icons.mic_rounded,
                    ),
                  ),
                ],
              ),
              const SizedBox(height: 20),
              Row(
                mainAxisAlignment: MainAxisAlignment.spaceBetween,
                children: [
                  Text('Recentes', style: theme.textTheme.titleMedium),
                  TextButton(
                    onPressed: onOpenLibrary,
                    child: const Text('Biblioteca'),
                  ),
                ],
              ),
              const SizedBox(height: 12),
              Expanded(
                child: ListView(
                  children: const [
                    _RecentRecordingItem(
                      name: 'B13_FALCON_2026_10_24_01.mp4',
                      size: '42,5 MB',
                      duration: '00:04:12',
                    ),
                    _RecentRecordingItem(
                      name: 'B13_FALCON_2026_10_22_08.mp4',
                      size: '28,7 MB',
                      duration: '00:02:50',
                    ),
                    _RecentRecordingItem(
                      name: 'B13_FALCON_2026_10_19_21.mp4',
                      size: '57,2 MB',
                      duration: '00:05:33',
                    ),
                  ],
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }
}

class _InfoCard extends StatelessWidget {
  const _InfoCard({
    required this.title,
    required this.value,
    required this.icon,
  });

  final String title;
  final String value;
  final IconData icon;

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.all(16),
      decoration: BoxDecoration(
        color: AppColors.surface,
        borderRadius: BorderRadius.circular(16),
        border: Border.all(color: AppColors.border),
      ),
      child: Row(
        children: [
          Icon(icon, color: AppColors.silver),
          const SizedBox(width: 12),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(title, style: Theme.of(context).textTheme.bodySmall),
                Text(value, style: Theme.of(context).textTheme.titleMedium),
              ],
            ),
          ),
        ],
      ),
    );
  }
}

class _RecentRecordingItem extends StatelessWidget {
  const _RecentRecordingItem({
    required this.name,
    required this.size,
    required this.duration,
  });

  final String name;
  final String size;
  final String duration;

  @override
  Widget build(BuildContext context) {
    return Container(
      margin: const EdgeInsets.only(bottom: 12),
      padding: const EdgeInsets.all(14),
      decoration: BoxDecoration(
        color: AppColors.surface,
        borderRadius: BorderRadius.circular(16),
        border: Border.all(color: AppColors.border),
      ),
      child: Row(
        children: [
          Container(
            width: 62,
            height: 62,
            decoration: BoxDecoration(
              borderRadius: BorderRadius.circular(12),
              color: AppColors.graphite,
            ),
            child: const Icon(Icons.videocam_rounded, color: AppColors.silver),
          ),
          const SizedBox(width: 12),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(name, overflow: TextOverflow.ellipsis),
                const SizedBox(height: 4),
                Text(
                  '$duration • $size',
                  style: Theme.of(context).textTheme.bodySmall
                      ?.copyWith(color: AppColors.muted),
                ),
              ],
            ),
          ),
          IconButton(
            onPressed: () {},
            icon: const Icon(Icons.play_arrow_rounded),
          ),
        ],
      ),
    );
  }
}
