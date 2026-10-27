import 'package:b13_falcon/core/constants/app_colors.dart';
import 'package:b13_falcon/core/utils/formatters.dart';
import 'package:b13_falcon/core/widgets/entrance_animation.dart';
import 'package:b13_falcon/data/models/saved_recording.dart';
import 'package:flutter/material.dart';

class LibraryPage extends StatelessWidget {
  const LibraryPage({
    super.key,
    required this.recordings,
    required this.errorMessage,
    required this.onRefresh,
    required this.onOpenRecording,
    required this.onDeleteRecording,
    required this.onBack,
  });

  final List<SavedRecording> recordings;
  final String? errorMessage;
  final Future<void> Function() onRefresh;
  final ValueChanged<SavedRecording> onOpenRecording;
  final ValueChanged<SavedRecording> onDeleteRecording;
  final VoidCallback onBack;

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        automaticallyImplyLeading: false,
        title: const Align(
          alignment: Alignment.centerLeft,
          child: Text('Vídeos'),
        ),
        actions: [
          IconButton(
            onPressed: onRefresh,
            tooltip: 'Atualizar',
            icon: const Icon(Icons.refresh_rounded),
          ),
        ],
      ),
      body: RefreshIndicator(
        onRefresh: onRefresh,
        child: errorMessage != null
            ? ListView(
                physics: const AlwaysScrollableScrollPhysics(),
                padding: const EdgeInsets.all(24),
                children: [
                  SizedBox(height: MediaQuery.sizeOf(context).height * 0.22),
                  EntranceAnimation(
                    child: Column(
                      children: [
                        const Icon(Icons.error_outline_rounded, size: 42),
                        const SizedBox(height: 12),
                        Text(errorMessage!, textAlign: TextAlign.center),
                      ],
                    ),
                  ),
                ],
              )
            : recordings.isEmpty
            ? ListView(
                physics: const AlwaysScrollableScrollPhysics(),
                padding: const EdgeInsets.all(24),
                children: [
                  const SizedBox(height: 80),
                  EntranceAnimation(
                    child: Column(
                      children: [
                        Container(
                          width: 92,
                          height: 92,
                          decoration: BoxDecoration(
                            color: AppColors.accent.withValues(alpha: 0.12),
                            borderRadius: BorderRadius.circular(28),
                          ),
                          child: const Icon(
                            Icons.video_library_outlined,
                            size: 48,
                            color: AppColors.accent,
                          ),
                        ),
                        const SizedBox(height: 18),
                        Text(
                          'Nenhum vídeo',
                          textAlign: TextAlign.center,
                          style: Theme.of(context).textTheme.titleMedium,
                        ),
                        const SizedBox(height: 6),
                        const Text(
                          'Toque no botão para abrir os controles.',
                          textAlign: TextAlign.center,
                        ),
                      ],
                    ),
                  ),
                ],
              )
            : ListView.separated(
                physics: const AlwaysScrollableScrollPhysics(),
                padding: const EdgeInsets.all(18),
                itemCount: recordings.length,
                separatorBuilder: (_, _) => const SizedBox(height: 10),
                itemBuilder: (context, index) {
                  final recording = recordings[index];
                  return EntranceAnimation(
                    delay: Duration(milliseconds: index * 45),
                    child: Container(
                      decoration: BoxDecoration(
                        color: Theme.of(context).colorScheme.surface,
                        borderRadius: BorderRadius.circular(18),
                        border: Border.all(
                          color: Theme.of(context).dividerColor,
                        ),
                      ),
                      child: ListTile(
                        contentPadding: const EdgeInsets.fromLTRB(14, 8, 4, 8),
                        leading: Container(
                          width: 54,
                          height: 54,
                          decoration: BoxDecoration(
                            borderRadius: BorderRadius.circular(14),
                            color: AppColors.accent.withValues(alpha: 0.14),
                          ),
                          child: const Icon(
                            Icons.play_arrow_rounded,
                            color: AppColors.accent,
                          ),
                        ),
                        title: Text(
                          recording.name,
                          maxLines: 1,
                          overflow: TextOverflow.ellipsis,
                        ),
                        subtitle: Text(
                          '${Formatters.formatDuration(recording.duration)} • '
                          '${Formatters.formatBytes(recording.sizeBytes)}',
                        ),
                        onTap: () => onOpenRecording(recording),
                        trailing: PopupMenuButton<String>(
                          tooltip: 'Ações do vídeo',
                          onSelected: (action) {
                            if (action == 'delete') {
                              onDeleteRecording(recording);
                            }
                          },
                          itemBuilder: (context) => const [
                            PopupMenuItem(
                              value: 'delete',
                              child: Text('Apagar vídeo'),
                            ),
                          ],
                        ),
                      ),
                    ),
                  );
                },
              ),
      ),
    );
  }
}
