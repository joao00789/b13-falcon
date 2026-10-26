# CONTEXTO — B13 FALCON

## Visão geral e arquitetura

- Aplicativo Android de gravação de tela, Flutter/Dart na interface e Kotlin nos serviços nativos. Identificador: `com.b13.falcon`.
- Versão atual: `1.2.2+8`; branch `main`; a versão `v1.2.1` é a última release publicada.
- Flutter coordena navegação, preferências e estado visual em `lib/app.dart`; `RecordingNativeBridge` usa o canal `com.b13.falcon/recorder` para chamar o Android.
- `MainActivity` atende o MethodChannel, permissões solicitadas dentro do app, biblioteca de vídeos e configuração do overlay.
- `CaptureConsentActivity` é uma Activity transparente sem interface própria para solicitar consentimento MediaProjection quando a gravação começa pelo overlay. O resultado inicia `RecordingService` diretamente sem abrir ou reordenar `MainActivity`.
- `FloatingControlsService` mantém o overlay e envia ações de gravar/pausar/retomar/parar e desenho.
- `RecordingService` é o serviço em primeiro plano que controla MediaProjection, VirtualDisplay, MediaCodec, captura/mixagem de áudio, MediaMuxer e publicação MP4 via MediaStore em `Movies/B13 Falcon`.
- Broadcasts do `RecordingService` comunicam `recording`, `paused`, `stopped` e `error` à interface e ao overlay.

### Fluxos

1. Flutter persiste configurações e chama `MainActivity` através do MethodChannel.
2. Ao gravar pela tela inicial, a Activity pede permissões necessárias e inicia o consentimento de captura.
3. Ao gravar pelo flutuante, o serviço lança `CaptureConsentActivity`; ela solicita MediaProjection e inicia diretamente o serviço gravador.
4. `RecordingService` inicia encoder, VirtualDisplay e áudio configurado.
5. Ao finalizar, o serviço fecha e publica o arquivo; a biblioteca atualiza pela consulta do MediaStore.

## Configurações e valores válidos

- `isDarkMode`: `true` / `false`.
- `themeAccent`: `falcon | ocean | emerald | violet`.
- `showFloatingControls`, `keepScreenOn`: `true` / `false`.
- `floatingLogoOpacity`: `0.0` a `1.0`; em 0%, a bolha continua tocável.
- `floatingLogoSizeDp`: 32 a 96 dp.
- `preferredResolution`: `360p | 480p | 540p | 720p | 1080p | 1440p | native`, limitada por suporte do codificador e tamanho do display.
- `preferredFps`: 15, 24, 30, 45, 60 ou 90, conforme suporte.
- `bitratePreset`: `economy | balanced | high | maximum | custom`; bitrate manual/automático limitado a 4–80 Mbps.
- `codec`: `h264 | hevc`; HEVC só é selecionado quando suportado pela combinação de formato.
- `orientation`: `auto | portrait | landscape`.
- `audioMode`: `both | internal | microphone | none`; valores legados em português são normalizados.
- A captura de áudio interno exige Android 10+ e pode ser bloqueada pela aplicação capturada. Microfone exige `RECORD_AUDIO`.
- `themeAccent` seleciona os temas animados Falcon, Ocean, Emerald ou Violet; animações respeitam a preferência de reduzir movimento do sistema.

## Funcionalidades atuais

- Gravação de tela MP4, pausa/retomada, controles de notificação e overlay persistente.
- Ajustes de resolução, FPS, bitrate, HEVC/H.264, orientação e modo de áudio.
- Ferramentas flutuantes de desenho: traço livre, caixa, círculo, cores, espessura/opacidade, desfazer e limpar.
- Biblioteca MediaStore com listar, abrir via reprodutor externo e excluir gravações.
- Preferências persistidas localmente em `SharedPreferences`.
- Lucide SVGs e licença ISC em `assets/icons/lucide/`; drawables vetoriais Android equivalentes em `android/app/src/main/res/drawable/`.

## Changelog (cronológico)

### 2026-10-26 — Base funcional e entregas anteriores

- Implementados os fluxos de gravação Flutter/Kotlin, áudio interno e microfone, biblioteca, permissões, desenho flutuante, temas animados, qualidade e workflow GitHub Actions.
- A finalização do gravador foi movida para thread de trabalho para evitar bloqueio da thread principal; comandos STOP/PAUSE/RESUME foram corrigidos para ações de serviço existentes.
- Atualizado o fluxo de build para a versão `v1.2.1` e publicados os ícones Lucide, limite de bitrate até 80 Mbps e seletor de cor menor.
- A tag `v1.2.1` e sua pré-release foram publicadas; o APK está anexado à release do GitHub.
- Arquivos históricos complementares de arquitetura, limitações e entregas de `v1.1.x` foram incorporados de `CONTEXTO_APP.md`, que foi removido após esta consolidação.

### 2026-10-26 — Commit 1: consentimento de captura pelo overlay

- Criada `CaptureConsentActivity`, transparente, sem layout, excluída de recents, com task affinity vazia e orientação `behind`.
- O overlay agora chama essa Activity para mostrar o diálogo MediaProjection e iniciar `RecordingService` sem abrir/reordenar a `MainActivity`.
- Removidos o fluxo antigo `startRequestedFromFloating`, a ação de início encaminhada à `MainActivity` e chamadas `moveTaskToBack`.
- Versão elevada para `1.2.2+8`.

## Decisões e limitações conhecidas

- MediaProjection exige consentimento do usuário em cada nova sessão; o overlay não pode reutilizar consentimento anterior.
- Suporte a resolução/FPS/codec depende do aparelho e do encoder; o formato efetivo deve ser informado na interface quando limitado.
- Mudança de orientação durante uma gravação não redimensiona o encoder; a orientação é escolhida no início.
- A captura de áudio interno continua condicionada ao Android e às políticas do jogo/aplicativo de origem.
- Testes Flutter não exercitam MediaProjection, Android services, codecs nem sobreposições. O ambiente de desenvolvimento pode não ter Android SDK; o CI é responsável pelo build nativo.
- O signing release deve usar keystore estável guardada em GitHub Secrets; nunca versionar o arquivo ou senhas.
- O estado da gravação ainda tem cópia local na Activity; o serviço/broadcast é a fonte operacional durante a sessão.

## Pendências

- Validar em aparelho o início pelo overlay sem trazer o app à frente e a orientação em jogo horizontal.
- Verificar timestamps, mixagem/ganhos de áudio, limite de amostras pendentes e limpeza de saída em falha.
- Configurar e validar keystore fixa no GitHub Secrets antes de distribuir APK release; verificar uma atualização sobre instalação já existente.
- Validar POST_NOTIFICATIONS no Android 13+, tamanho/qualidade e compatibilidade de codecs em aparelhos reais.
- Concluir limpeza de permissões antigas e componentes não usados, após confirmar a consulta da biblioteca em cada versão Android suportada.

## Comandos de validação

```bash
dart format --set-exit-if-changed lib test
flutter analyze
flutter test
flutter build apk --release --split-per-abi
```

Atualizar esta página com as configurações atuais, decisões, pendências e um único item cronológico no changelog em cada alteração. Incrementar o build number em `pubspec.yaml` em cada commit de mudança.
