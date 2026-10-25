import 'package:b13_falcon/core/constants/app_colors.dart';
import 'package:b13_falcon/core/constants/app_strings.dart';
import 'package:b13_falcon/core/utils/formatters.dart';
import 'package:b13_falcon/data/models/app_settings.dart';
import 'package:b13_falcon/data/models/saved_recording.dart';
import 'package:flutter/material.dart';

class HomePage extends StatelessWidget {
  const HomePage({
    super.key,
    required this.settings,
    required this.isRecording,
    required this.isPaused,
    required this.elapsed,
    required this.statusMessage,
    required this.recordings,
    required this.onRecordToggle,
    required this.onPauseToggle,
    required this.onOpenLibrary,
    required this.onOpenSettings,
    required this.onOpenRecording,
  });

  final AppSettings settings;
  final bool isRecording;
  final bool isPaused;
  final Duration elapsed;
  final String statusMessage;
  final List<SavedRecording> recordings;
  final VoidCallback onRecordToggle;
  final VoidCallback onPauseToggle;
  final VoidCallback onOpenLibrary;
  final VoidCallback onOpenSettings;
  final ValueChanged<SavedRecording> onOpenRecording;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final recent = recordings.take(4).toList();
    return Scaffold(
      body: SafeArea(
        child: Padding(
          padding: const EdgeInsets.fromLTRB(20, 14, 20, 12),
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
                        Text(
                          'Gravador de tela',
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
              const SizedBox(height: 16),
              Container(
                padding: const EdgeInsets.all(20),
                decoration: BoxDecoration(
                  color: theme.colorScheme.surface,
                  borderRadius: BorderRadius.circular(24),
                  border: Border.all(color: theme.dividerColor),
                ),
                child: Column(
                  children: [
                    Text(
                      isRecording
                          ? (isPaused ? 'Gravação pausada' : 'Gravando agora')
                          : 'Pronto para capturar',
                      style: theme.textTheme.titleMedium?.copyWith(
                        color: isRecording ? AppColors.white : AppColors.muted,
                        fontWeight: FontWeight.w600,
                      ),
                    ),
                    const SizedBox(height: 12),
                    Text(
                      Formatters.formatDuration(elapsed),
                      style: theme.textTheme.displayMedium?.copyWith(
                        fontWeight: FontWeight.w700,
                        letterSpacing: 1.2,
                        color: isRecording ? AppColors.white : AppColors.silver,
                      ),
                    ),
                    const SizedBox(height: 16),
                    Row(
                      children: [
                        Expanded(
                          child: SizedBox(
                            height: 54,
                            child: FilledButton.icon(
                              style: FilledButton.styleFrom(
                                backgroundColor: isRecording
                                    ? AppColors.darkRed
                                    : AppColors.accent,
                                foregroundColor: AppColors.white,
                                shape: RoundedRectangleBorder(
                                  borderRadius: BorderRadius.circular(16),
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
                        if (isRecording) ...[
                          const SizedBox(width: 10),
                          Expanded(
                            child: SizedBox(
                              height: 54,
                              child: OutlinedButton.icon(
                                onPressed: onPauseToggle,
                                style: OutlinedButton.styleFrom(
                                  foregroundColor: AppColors.white,
                                  side: const BorderSide(
                                    color: AppColors.border,
                                  ),
                                  shape: RoundedRectangleBorder(
                                    borderRadius: BorderRadius.circular(16),
                                  ),
                                ),
                                icon: Icon(
                                  isPaused
                                      ? Icons.play_arrow_rounded
                                      : Icons.pause_rounded,
                                ),
                                label: Text(isPaused ? 'Retomar' : 'Pausar'),
                              ),
                            ),
                          ),
                        ],
                      ],
                    ),
                    const SizedBox(height: 12),
                    Text(
                      statusMessage,
                      textAlign: TextAlign.center,
                      style: theme.textTheme.bodySmall,
                    ),
                  ],
                ),
              ),
              const SizedBox(height: 14),
              Row(
                children: [
                  Expanded(
                    child: _InfoCard(
                      title: 'Qualidade',
                      value: '${settings.preferredResolution} • ${settings.preferredFps} FPS',
                      icon: Icons.high_quality_rounded,
                    ),
                  ),
                  const SizedBox(width: 10),
                  Expanded(
                    child: _InfoCard(
                      title: 'Taxa de bits',
                      value: '${settings.bitrateKbps ~/ 1000} Mbps',
                      icon: Icons.speed_rounded,
                    ),
                  ),
                ],
              ),
              const SizedBox(height: 12),
              Row(
                mainAxisAlignment: MainAxisAlignment.spaceBetween,
                children: [
                  Text('Vídeos salvos', style: theme.textTheme.titleMedium),
                  TextButton(
                    onPressed: onOpenLibrary,
                    child: const Text('Ver todos'),
                  ),
                ],
              ),
              Expanded(
                child: recent.isEmpty
                    ? Center(
                        child: Text(
                          'Suas gravações aparecerão aqui.',
                          style: theme.textTheme.bodyMedium?.copyWith(
                            color: AppColors.muted,
                          ),
                        ),
                      )
                    : ListView.builder(
                        padding: EdgeInsets.zero,
                        itemCount: recent.length,
                        itemBuilder: (context, index) => _RecordingTile(
                          recording: recent[index],
                          onTap: () => onOpenRecording(recent[index]),
                        ),
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
      padding: const EdgeInsets.all(12),
      decoration: BoxDecoration(
        color: Theme.of(context).colorScheme.surface,
        borderRadius: BorderRadius.circular(16),
        border: Border.all(color: Theme.of(context).dividerColor),
      ),
      child: Row(
        children: [
          Icon(icon, color: AppColors.silver, size: 20),
          const SizedBox(width: 8),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(title, style: Theme.of(context).textTheme.bodySmall),
                Text(
                  value,
                  maxLines: 1,
                  overflow: TextOverflow.ellipsis,
                  style: Theme.of(context).textTheme.titleSmall,
                ),
              ],
            ),
          ),
        ],
      ),
    );
  }
}

class _RecordingTile extends StatelessWidget {
  const _RecordingTile({required this.recording, required this.onTap});

  final SavedRecording recording;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    return ListTile(
      contentPadding: const EdgeInsets.symmetric(horizontal: 4),
      leading: const CircleAvatar(
        backgroundColor: AppColors.graphite,
        child: Icon(Icons.play_arrow_rounded, color: AppColors.silver),
      ),
      title: Text(recording.name, maxLines: 1, overflow: TextOverflow.ellipsis),
      subtitle: Text(
        '${Formatters.formatDuration(recording.duration)} • '
        '${Formatters.formatBytes(recording.sizeBytes)}',
      ),
      trailing: const Icon(Icons.chevron_right_rounded),
      onTap: onTap,
    );
  }
}
