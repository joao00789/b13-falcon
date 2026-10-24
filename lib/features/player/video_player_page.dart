import 'package:flutter/material.dart';

class VideoPlayerPage extends StatelessWidget {
  const VideoPlayerPage({super.key, required this.videoName});

  final String videoName;

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: Text(videoName)),
      body: const Center(
        child: Column(
          mainAxisAlignment: MainAxisAlignment.center,
          children: [
            Icon(Icons.play_circle_fill_rounded, size: 72),
            SizedBox(height: 12),
            Text('Reprodutor integrado inicializado'),
            SizedBox(height: 8),
            Text('A reprodução real depende do backend de vídeo do Android e do arquivo gravado.'),
          ],
        ),
      ),
    );
  }
}
