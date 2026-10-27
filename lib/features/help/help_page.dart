import 'package:flutter/material.dart';

class HelpPage extends StatelessWidget {
  const HelpPage({super.key});

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final entries = [
      (
        Icons.fiber_manual_record_rounded,
        'Iniciar uma gravação',
        'Abra Capturas e toque no botão de gravar. O Android pedirá autorização '
            'para compartilhar a tela a cada nova gravação.',
      ),
      (
        Icons.videocam_rounded,
        'Câmera frontal',
        'Ative a câmera em Ajustes antes de gravar. A imagem é incorporada ao '
            'vídeo; posição e recorte podem variar conforme a orientação.',
      ),
      (
        Icons.brush_rounded,
        'Pincel e controles',
        'Use o menu flutuante ou a notificação da gravação para abrir o pincel, '
            'pausar, retomar ou parar.',
      ),
      (
        Icons.high_quality_rounded,
        'Qualidade e espaço',
        'Resolução, FPS, codec e bitrate maiores preservam detalhes, mas usam '
            'mais bateria, processamento e armazenamento.',
      ),
    ];

    return Scaffold(
      appBar: AppBar(
        automaticallyImplyLeading: false,
        title: const Align(
          alignment: Alignment.centerLeft,
          child: Text('Ajuda'),
        ),
      ),
      body: ListView(
        padding: const EdgeInsets.fromLTRB(16, 8, 16, 24),
        children: [
          Text(
            'Dicas para gravar melhor',
            style: theme.textTheme.titleMedium?.copyWith(
              color: theme.colorScheme.primary,
              fontWeight: FontWeight.w700,
            ),
          ),
          const SizedBox(height: 12),
          for (final (icon, title, description) in entries) ...[
            Card(
              margin: const EdgeInsets.only(bottom: 10),
              child: ListTile(
                leading: CircleAvatar(
                  backgroundColor: theme.colorScheme.primary.withValues(
                    alpha: 0.12,
                  ),
                  foregroundColor: theme.colorScheme.primary,
                  child: Icon(icon),
                ),
                title: Text(title),
                subtitle: Padding(
                  padding: const EdgeInsets.only(top: 5),
                  child: Text(description),
                ),
                isThreeLine: true,
              ),
            ),
          ],
        ],
      ),
    );
  }
}
