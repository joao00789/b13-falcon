import 'package:b13_falcon/core/constants/app_colors.dart';
import 'package:b13_falcon/core/utils/formatters.dart';
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
        leading: IconButton(
          onPressed: onBack,
          tooltip: 'Voltar para início',
          icon: const Icon(Icons.arrow_back_rounded),
        ),
        title: const Text('Biblioteca'),
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
                  const SizedBox(height: 80),
                  const Icon(Icons.error_outline_rounded, size: 42),
                  const SizedBox(height: 12),
                  Text(errorMessage!, textAlign: TextAlign.center),
                ],
              )
            : recordings.isEmpty
            ? ListView(
                physics: const AlwaysScrollableScrollPhysics(),
                padding: const EdgeInsets.all(24),
                children: [
                  const SizedBox(height: 80),
                  const Icon(
                    Icons.video_library_outlined,
                    size: 48,
                    color: AppColors.muted,
                  ),
                  const SizedBox(height: 12),
                  Text(
                    'Nenhum vídeo salvo ainda.',
                    textAlign: TextAlign.center,
                    style: Theme.of(context).textTheme.titleMedium,
                  ),
                  const SizedBox(height: 6),
                  const Text(
                    'As gravações concluídas aparecerão nesta biblioteca.',
                    textAlign: TextAlign.center,
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
                  return Container(
                    decoration: BoxDecoration(
                      color: Theme.of(context).colorScheme.surface,
                      borderRadius: BorderRadius.circular(18),
                      border: Border.all(color: Theme.of(context).dividerColor),
                    ),
                    child: ListTile(
                      contentPadding: const EdgeInsets.fromLTRB(14, 8, 4, 8),
                      leading: Container(
                        width: 54,
                        height: 54,
                        decoration: BoxDecoration(
                          borderRadius: BorderRadius.circular(14),
                          color: AppColors.graphite,
                        ),
                        child: const Icon(
                          Icons.play_arrow_rounded,
                          color: AppColors.silver,
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
                          if (action == 'delete') onDeleteRecording(recording);
                        },
                        itemBuilder: (context) => const [
                          PopupMenuItem(
                            value: 'delete',
                            child: Text('Apagar vídeo'),
                          ),
                        ],
                      ),
                    ),
                  );
                },
              ),
      ),
    );
  }
}
