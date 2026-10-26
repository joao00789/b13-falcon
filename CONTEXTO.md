# CONTEXTO — B13 FALCON

## Visão geral

Projeto: B13 FALCON v1.2.0+

- Plataforma: Android, app de gravação de tela com Flutter + Kotlin.
- Arquitetura: UI em Flutter, captura/serviços nativos em Kotlin e comunicação por `MethodChannel` com canal `com.b13.falcon/recorder`.
- Objetivo: gravar a tela e o áudio interno/microfone em MP4, com controles flutuantes, desenho, temas animados e ajustes de qualidade.
- Repositório atual: `/home/joaodacruzxxx/b13-falcon-publicar`
- Branch de trabalho: `main`
- Status: em desenvolvimento para a versão de entrega com melhorias de tema, flutuante e qualidade de gravação.

## Arquitetura e fluxo

### 1) Flutter -> Android

- `lib/app.dart`: coordena a navegação, estado da gravação e integração com os serviços nativos.
- `lib/services/native_recorder.dart`: expõe chamadas para o canal nativo e consulta capacidades de codec/encoder.
- `MainActivity.kt`: recebe os métodos do Flutter, valida permissões (microfone, sobreposição, MediaProjection), dispara a gravação e consulta a biblioteca.

### 2) Serviços nativos

- `RecordingService.kt`: serviço em primeiro plano que ativa `MediaProjection`, cria o `VirtualDisplay`, monta o `MediaCodec` e grava o vídeo/áudio selecionado.
- `FloatingControlsService.kt`: serviço flutuante em primeiro plano; controla a bolha, estado de gravação, desenho e ajustes de opacidade/tamanho.
- `MainActivity.kt` também coordena a criação da captura de tela a partir do flutuante e reinicia as ações quando a app retorna ao foreground.

### 3) Fluxo principal de gravação

1. Flutter valida opções como resolução, FPS, codec, áudio, tema e flutuante.
2. `MainActivity` solicita permissões necessárias.
3. `MediaProjection` abre o diálogo de consentimento de captura.
4. `RecordingService` inicia a gravação com `VirtualDisplay` + `MediaCodec`.
5. Broadcasts enviam estado (`recording`, `paused`, `stopped`, `error`) para UI e para o serviço flutuante.
6. Ao encerrar, o arquivo é publicado em `Movies/B13 Falcon` e a biblioteca atualiza.

## Configurações existentes e valores válidos

### Gerais

- `isDarkMode`: `true`/`false`
- `themeAccent`: `'falcon' | 'ocean' | 'emerald' | 'violet'`
- `showFloatingControls`: `true`/`false`
- `keepScreenOn`: `true`/`false`
- `floatingLogoOpacity`: `0.0` a `1.0` (aceita `0%` para manter o botão tocável)
- `floatingLogoSizeDp`: `32` a `96`
- `preferredResolution`: `'360p' | '480p' | '540p' | '720p' | '1080p' | '1440p' | 'native'`
- `preferredFps`: `15 | 24 | 30 | 45 | 60 | 90` conforme suporte do aparelho
- `codec`: `'h264' | 'hevc'`
- `orientation`: `'auto' | 'portrait' | 'landscape'`
- `bitratePreset`: `'economy' | 'balanced' | 'high' | 'maximum' | 'custom'`
- `bitrateKbps`: inteiro em kbps

### Áudio

- `audioMode`: `'both' | 'internal' | 'microphone' | 'none'`
- Aceita o histórico de valores legados e valores em português quando necessário (`'Microfone'`, `'both'`, `'internal'`, etc.).
- A captura interna exige Android 10+ (`AudioPlaybackCapture`), e o microfone exige permissão de `RECORD_AUDIO`.

### Tema

- `AppThemeData` contém tema, gradiente, partículas e fundo animado para cada card.
- Temas disponíveis: `Falcon`, `Ocean`, `Emerald` e `Violet`.
- A animação do fundo respeita `MediaQuery.disableAnimations` e pausa quando o app vai para segundo plano.

## Changelog

### 2026-10-26 — Atualização da base de tema, flutuante e qualidade

Arquivos principais alterados:

- `lib/core/theme/app_theme_data.dart`
- `lib/core/theme/app_theme.dart`
- `lib/features/settings/settings_page.dart`
- `lib/features/home/home_page.dart`
- `lib/data/models/app_settings.dart`
- `lib/app.dart`
- `lib/services/native_recorder.dart`
- `android/app/src/main/kotlin/com/b13/falcon/MainActivity.kt`
- `android/app/src/main/kotlin/com/b13/falcon/FloatingControlsService.kt`
- `android/app/src/main/kotlin/com/b13/falcon/RecordingService.kt`
- `android/app/src/main/AndroidManifest.xml`

Mudanças principais:

- Cards de tema animados com fundo vivo e seleção visual.
- Ajustes de opacidade do flutuante de 0% a 100% e tamanho ajustável de 32 a 96 dp.
- Suporte a mais presets de qualidade, codec e orientação.
- Estado do flutuante persistente após parar a gravação.
- Melhorias de estabilidade em `MainActivity` e `RecordingService`.
- Reset do flag de flutuante para manter o overlay ativo após a gravação.

### 2026-10-26 — Ajustes de validação

- Correção de erro de classe aninhada em `app_theme_data.dart`.
- Correção de estrutura do `ScaleOnPress` e ajuste de botões em `home_page.dart`.
- Validação com `flutter analyze` e `flutter test` concluída com sucesso no ambiente atual.

## Decisões e limitações conhecidas

- O envio de ações STOP/PAUSE/RESUME para o serviço deve usar `startService()` quando a aplicação estiver em primeiro plano, evitando `ForegroundServiceDidNotStartInTimeException` em Android 8+.
- O estado de gravação deve ser considerado uma fonte de verdade no serviço, não só na Activity, para evitar inconsistências quando o app entra em segundo plano.
- A captura de qualidade/codec depende do suporte do aparelho; o app filtra opções quando `MediaCodecInfo.VideoCapabilities.areSizeAndRateSupported()` não aceita a combinação.
- A orientação do vídeo é tratada no início da gravação; mudança dinâmica de rotação durante a gravação ainda não é implementada.
- O flutuante permanece ativo enquanto a opção estiver ligada, mas a experiência final precisa ser validada em dispositivo Android real.
- O ambiente atual não possui Android SDK instalado, então a compilação local do APK não foi concluída aqui; a geração do APK deve ser validada no GitHub Actions ou em um ambiente Android completo.

## Pendências

- Finalizar revisão e ajuste do fluxo do overlay em dispositivos reais, especialmente a abertura do diálogo de MediaProjection a partir do botão flutuante.
- Validar o comportamento do flutuante em segundo plano e dos estados `idle / recording / paused / stopped` na prática.
- Confirmar se a sequência de permissão de microfone e `MediaProjection` funciona sem duplicidade em Android 13/14.
- Executar o push para o GitHub e verificar o workflow de build do APK.
- Criar e publicar tag de release quando a versão estiver pronta para distribuição.

## Observações finais

- `CONTEXTO.md` deve ser atualizada em toda mudança relevante do projeto.
- A versão em `pubspec.yaml` deve ter o número após o `+` incrementado a cada atualização de build.
- O trabalho em andamento procura manter estabilidade, qualidade e compatibilidade com Android moderno, enquanto prioriza a experiência de gravação em jogos e apps externos.

### 2026-10-26 — Correção da publicação v1.2.0

- O workflow da tag `v1.2.0` falhou no `flutter analyze` porque o commit inicial não incluiu os arquivos de implementação que acompanham o novo teste de configurações.
- As alterações restantes de Flutter e Android foram incluídas na correção; o teste permanece isolado em `SettingsPage`.
- Validação local repetida: `dart format --set-exit-if-changed lib test`, `flutter analyze` e `flutter test` passaram.
- A primeira repetição passou por análise e testes, mas encontrou duas falhas Kotlin no build: a função de suporte do codec estava aninhada dentro do encoder e não podia ser chamada.
- A função `supportsVideoFormat` foi movida para o escopo da classe. A compilação do APK e a publicação da release dependem da nova execução do GitHub Actions.
