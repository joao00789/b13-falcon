import 'package:b13_falcon/core/constants/app_colors.dart';
import 'package:flutter/material.dart';

class LibraryPage extends StatelessWidget {
  const LibraryPage({super.key});

  @override
  Widget build(BuildContext context) {
    final items = [
      _LibraryItem('B13_FALCON_2026_10_24_01.mp4', '42,5 MB', '00:04:12'),
      _LibraryItem('B13_FALCON_2026_10_20_09.mp4', '27,1 MB', '00:02:46'),
      _LibraryItem('B13_FALCON_2026_10_18_05.mp4', '58,3 MB', '00:05:33'),
    ];

    return Scaffold(
      appBar: AppBar(title: const Text('Biblioteca')),
      body: ListView.separated(
        padding: const EdgeInsets.all(18),
        itemCount: items.length,
        separatorBuilder: (_, __) => const SizedBox(height: 12),
        itemBuilder: (context, index) {
          final item = items[index];
          return Container(
            padding: const EdgeInsets.all(12),
            decoration: BoxDecoration(
              color: AppColors.surface,
              borderRadius: BorderRadius.circular(18),
              border: Border.all(color: AppColors.border),
            ),
            child: Row(
              children: [
                Container(
                  width: 64,
                  height: 64,
                  decoration: BoxDecoration(
                    borderRadius: BorderRadius.circular(16),
                    color: AppColors.graphite,
                  ),
                  child: const Icon(
                    Icons.videocam_rounded,
                    color: AppColors.silver,
                  ),
                ),
                const SizedBox(width: 12),
                Expanded(
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text(
                        item.name,
                        style: Theme.of(context).textTheme.titleMedium,
                      ),
                      const SizedBox(height: 4),
                      Text(
                        '${item.duration} • ${item.size}',
                        style: Theme.of(context).textTheme.bodySmall
                            ?.copyWith(color: AppColors.muted),
                      ),
                    ],
                  ),
                ),
                IconButton(
                  onPressed: () {},
                  icon: const Icon(Icons.more_vert_rounded),
                ),
              ],
            ),
          );
        },
      ),
    );
  }
}

class _LibraryItem {
  const _LibraryItem(this.name, this.size, this.duration);

  final String name;
  final String size;
  final String duration;
}
