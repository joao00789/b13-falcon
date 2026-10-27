# CONTEXTO — B13 FALCON

## Visão geral e arquitetura

- Aplicativo Android de gravação de tela, Flutter/Dart na interface e Kotlin nos serviços nativos. Identificador: `com.b13.falcon`.
- Versão atual em desenvolvimento: `1.2.6+18`; branch `main`; `v1.2.5` é a última release publicada.
- Flutter coordena navegação, preferências e estado visual em `lib/app.dart`; `RecordingNativeBridge` usa o canal `com.b13.falcon/recorder` para chamar o Android.
- `MainActivity` atende o MethodChannel, permissões solicitadas dentro do app, biblioteca de vídeos, configuração do overlay e consentimento MediaProjection quando a gravação começa no app.
- `CaptureConsentActivity` é uma Activity transparente sem interface própria para solicitar consentimento MediaProjection quando a gravação começa pelo overlay. O resultado inicia `RecordingService` diretamente sem abrir ou reordenar `MainActivity`.
- `FloatingControlsService` mantém o overlay e envia ações de gravar/pausar/retomar/parar e desenho.
- `RecordingService` é o serviço em primeiro plano que controla MediaProjection, VirtualDisplay, MediaCodec, captura/mixagem de áudio, MediaMuxer e publicação MP4 via MediaStore em `Movies/B13 Falcon`.
- Quando a câmera frontal está habilitada, `ScreenCameraCompositor` combina a imagem do VirtualDisplay e a textura da câmera frontal via Camera2/EGL/GLES antes de enviar os quadros ao encoder; a sobreposição fica incorporada no MP4.
- Broadcasts do `RecordingService` comunicam `recording`, `paused`, `stopped` e `error` à interface e ao overlay.

### Fluxos

1. Flutter persiste configurações e chama `MainActivity` através do MethodChannel.
2. Ao gravar pela tela inicial, a Activity pede permissões necessárias e inicia o consentimento de captura.
3. Ao gravar pelo flutuante, o serviço lança `CaptureConsentActivity`; ela solicita MediaProjection e inicia diretamente o serviço gravador.
4. `RecordingService` inicia encoder, VirtualDisplay e áudio configurado.
5. Ao finalizar, o serviço fecha e publica o arquivo; a biblioteca atualiza pela consulta do MediaStore.

## Configurações e valores válidos

- `isDarkMode`: `true` / `false`.
- `themeAccent`: `falcon | ocean | emerald | violet | sunset | rose | arctic | black`.
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
- `frontCameraEnabled`: `true | false`; requer permissão `CAMERA`.
- `cameraShape`: `circle | rounded`.
- `cameraBorderColor`: branco (`#FFFFFFFF`), Falcon (`#FFB81C2B`), Ocean (`#FF168AAD`), Emerald (`#FF16865A`), Violet (`#FF7950B8`) ou Black (`#FFC9A85A`).
- `cameraBorderWidthDp`: 0 a 12 dp.

## Funcionalidades atuais

- Gravação de tela MP4, pausa/retomada, controles de notificação e overlay persistente.
- Ajustes de resolução, FPS, bitrate, HEVC/H.264, orientação e modo de áudio.
- Ferramentas flutuantes de desenho: traço livre, caixa, círculo, cores, espessura/opacidade, desfazer e limpar.
- Biblioteca MediaStore com listar, abrir via reprodutor externo e excluir gravações.
- Preferências persistidas localmente em `SharedPreferences`.
- Câmera frontal composta no vídeo, com formato circular/cantos arredondados e borda colorida ajustável.
- Abas inferiores Vídeos, Capturas, Ajustes e Ajuda; as notificações oferecem Iniciar quando ocioso e Pausar/Retomar e Pincel durante a gravação.
- Lucide SVGs e licença ISC em `assets/icons/lucide/`; drawables vetoriais Android equivalentes em `android/app/src/main/res/drawable/`.

## Changelog (cronológico)

### 2026-10-26 — Câmera frontal, controles de notificação e navegação

- Adicionadas preferências e controles para incluir a câmera frontal no MP4, escolher formato e personalizar cor/espessura da borda.
- Criado `ScreenCameraCompositor` para compor a projeção de tela e a câmera frontal na superfície do encoder; a retomada reconecta a superfície do compositor.
- Corrigido o recorte da textura da câmera para preservar a proporção dentro da janela quadrada e liberados os recursos EGL/texturas no encerramento do compositor.
- Adicionadas ações de notificação para iniciar, pausar/retomar e usar o pincel; o overlay tem canal visível em prioridade baixa e mantém a opacidade zero sem remover a área tocável.
- Reorganizada a navegação em Vídeos, Capturas, Ajustes e Ajuda; persistidos os ajustes da câmera no caminho Flutter/MethodChannel/Android.
- O estado e o tempo ativo da sessão agora são consultados no serviço quando a interface reabre, e o overlay recupera o estado atual ao ser criado.
- Corrigida a posição do handler de permissão de câmera em `MainActivity`; removidos `READ_MEDIA_AUDIO`, `READ_MEDIA_VIDEO` e `requestLegacyExternalStorage`, e corrigida a posição da declaração `uses-feature`.
- Atualizada a versão para `1.2.6+18`.
- Arquivos: `CONTEXTO.md`, `pubspec.yaml`, `android/app/src/main/AndroidManifest.xml`, `android/app/src/main/kotlin/com/b13/falcon/{CaptureConsentActivity,FloatingControlsService,MainActivity,RecordingService,ScreenCameraCompositor}.kt`, `lib/app.dart`, `lib/data/models/app_settings.dart`, `lib/features/{home/home_page,library/library_page,settings/settings_page,help/help_page}.dart`, `lib/services/native_recorder.dart` e `test/widget_test.dart`.

### 2026-10-26 — Ajustes finais de estabilidade e refinamento do tema

- Ajustado o texto de validação do app e os testes para refletirem a realidade do fluxo atual com múltiplos textos `B13 FALCON` e seleção de áudio via `ChoiceChip`.
- Refinado o visual dos cards de tema para deixar a seleção mais compacta, com maior contraste e melhor destaque do estado ativo.
- Atualizada a versão do app para `1.2.5+17` e mantido o `CONTEXTO.md` sincronizado com a situação atual do projeto.

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

### 2026-10-26 — Commit 2: orientação e dimensões da captura

- No toque de gravar, o overlay consulta métricas reais, rotação e orientação da configuração atual e envia `sourceWidth`/`sourceHeight` à Activity transparente.
- `RecordingService` usa esse snapshot para `auto`; `landscape` mantém o lado maior na largura e a proporção original para o VirtualDisplay/encoder.
- Versão elevada para `1.2.2+9`.

### 2026-10-26 — Commit 3: parâmetros do encoder e resolução efetiva

- AVC usa perfil High e HEVC usa Main apenas quando o perfil é anunciado pelo encoder; CBR é escolhido quando suportado, com VBR como fallback.
- Intervalo entre keyframes reduzido para um segundo e `KEY_REPEAT_PREVIOUS_FRAME_AFTER` configurado para manter vídeo em telas estáticas quando a API permite.
- O tamanho de saída já é limitado à dimensão de origem (`minOf(1f, ...)`), sem upscale. A dimensão efetiva do VirtualDisplay é enviada no evento de gravação para a tela inicial.
- Versão elevada para `1.2.2+10`.

### 2026-10-26 — Commit 4: bitrate por bits por pixel

- Os presets agora calculam bitrate por `largura × altura × FPS × bpp`: Economia 0,08; Equilibrada 0,14; Alta 0,20; Máxima 0,30.
- Dimensões físicas do display são consultadas pelo Android e usadas no preset `native`; as resoluções selecionadas são reduzidas ao tamanho real, sem upscale.
- Mantido o limite de 4–80 Mbps e adicionados testes para 1440p/90 e resolução nativa.
- Versão elevada para `1.2.2+11`.

### 2026-10-26 — Commit 5: buffer, falha de saída e notificações

- `pendingSamples` agora fica limitado a 600 amostras e 20 MiB; se o formato de áudio não chega dentro desse limite, o muxer segue sem faixa de áudio e mantém vídeo.
- Entradas MediaStore/arquivos locais são apagados quando o muxer nunca inicia; falhas ao publicar são reportadas.
- Android 13+ solicita `POST_NOTIFICATIONS` em runtime nos fluxos iniciados pelo app ou pelo overlay.
- Versão elevada para `1.2.2+12`.

### 2026-10-26 — Assinatura release e APKs por ABI

- Configurado o build Android release com keystore PKCS12 fixa, R8/minificação e redução de recursos; a chave e os arquivos locais de assinatura são ignorados pelo Git.
- O workflow exige os secrets `B13_RELEASE_KEYSTORE_BASE64`, `B13_RELEASE_STORE_PASSWORD`, `B13_RELEASE_KEY_ALIAS` e `B13_RELEASE_KEY_PASSWORD`, gera APKs release assinados para cada ABI e anexa os APKs à pré-release criada por tag.
- Removido o passo KVM sem efeito.
- Versão elevada para `1.2.2+13`; tag planejada: `v1.2.2`.
- A tag `v1.2.2` foi enviada, mas o APK não foi publicado: a compilação Kotlin falhou por falta da constante `REQUEST_MEDIA_PROJECTION` em `MainActivity`.

### 2026-10-26 — Corrige compilação e prepara nova tag de release

- Definido o código de requisição MediaProjection ausente em `MainActivity`; essa falha impedia o workflow de compilar qualquer APK da tag `v1.2.2`.
- Elevada a versão para `1.2.3+14`; criada a tag `v1.2.3`, sem mover nem substituir a tag anterior.
- `dart format`, `flutter analyze` e `flutter test` passaram; a compilação release local não foi possível porque este ambiente não tem Android SDK.
- O workflow da tag `v1.2.3` concluiu com sucesso e publicou APKs assinados para arm64-v8a, armeabi-v7a e x86_64.

### 2026-10-26 — Atualização do número de build após publicação

- Incrementado o build number para `1.2.3+15` após atualizar o estado da publicação neste contexto.

### 2026-10-26 — Recuperação de configurações e renovação dos temas

- `AppSettings.fromJson` agora valida tipos, valores permitidos e limites numéricos ao recuperar preferências. JSON inválido é reportado ao FlutterError em vez de ser silenciosamente ignorado.
- Ao abrir, se o serviço de controles flutuantes não puder iniciar (por exemplo, permissão de sobreposição revogada), a opção é desativada e salva para o app continuar abrindo.
- Os cards de tema foram redesenhados como prévias maiores com estado ativo visível, visual do gravador e botão demonstrativo.
- Adicionados os temas Sunset, Rose, Arctic e Black, com suporte a persistência e compatibilidade com os quatro IDs antigos.
- Incrementada a versão para `1.2.4+16`.

## Decisões e limitações conhecidas

- A câmera frontal usa uma janela fixa no canto inferior direito, com aproximadamente 27% da menor dimensão do vídeo; posição/tamanho da janela ainda não são configuráveis. A rotação/aspecto e o desempenho dependem do sensor, encoder e GPU do aparelho.
- A composição EGL/Camera2 ainda não foi compilada nem validada em aparelho neste ambiente; o Android SDK não está instalado localmente.
- A publicação ainda não foi disparada: a tag só deve ser criada depois do build Android e do teste físico, com os secrets `B13_RELEASE_KEYSTORE_BASE64`, `B13_RELEASE_STORE_PASSWORD`, `B13_RELEASE_KEY_ALIAS` e `B13_RELEASE_KEY_PASSWORD` disponíveis no GitHub.
- A aba Capturas é a tela de controles de gravação; ainda não há fluxo separado para salvar capturas estáticas em PNG/JPEG.
- MediaProjection exige consentimento do usuário em cada nova sessão; o overlay não pode reutilizar consentimento anterior.
- Suporte a resolução/FPS/codec depende do aparelho e do encoder; o formato efetivo deve ser informado na interface quando limitado.
- Mudança de orientação durante uma gravação não redimensiona o encoder; a orientação é escolhida no início.
- A captura de áudio interno continua condicionada ao Android e às políticas do jogo/aplicativo de origem.
- Testes Flutter não exercitam MediaProjection, Android services, codecs nem sobreposições. O ambiente de desenvolvimento pode não ter Android SDK; o CI é responsável pelo build nativo.
- A assinatura release depende da keystore PKCS12 e dos quatro secrets do repositório; a keystore local não deve ser versionada nem publicada.
- A Activity e Flutter mantêm cópias visuais do estado; `RecordingService` é a fonte da verdade no processo e os broadcasts sincronizam as telas.

## Pendências

- Validar em aparelho o início pelo overlay sem trazer o app à frente, a orientação em jogo horizontal e a câmera frontal incorporada no MP4.
- Testar bordas, pausa/retomada e uso simultâneo da notificação e overlay em aparelhos Android 10+.
- Decidir/implementar a captura estática de tela; por enquanto a aba Capturas controla a gravação de vídeo.
- Verificar timestamps, mixagem/ganhos de áudio, limite de amostras pendentes e limpeza de saída em falha.
- Confirmar que o workflow publica APKs assinados; validar uma atualização sobre instalação anterior no aparelho.
- Antes da release: compilar `flutter build apk --debug`, confirmar build/testes, adicionar os quatro secrets de assinatura no repositório, validar o workflow e então criar/enviar uma tag de versão.
- Validar POST_NOTIFICATIONS no Android 13+, tamanho/qualidade e compatibilidade de codecs em aparelhos reais.
- Se o crash persistir, recolher o stack trace do Android/logcat para confirmar a causa específica; as duas causas acima são cenários plausíveis, não diagnóstico confirmado sem log do aparelho.
- Confirmar a consulta da biblioteca em cada versão Android suportada após remover permissões amplas de mídia.

## Comandos de validação

```bash
dart format --set-exit-if-changed lib test
flutter analyze
flutter test
flutter build apk --release --split-per-abi
```

Atualizar esta página com as configurações atuais, decisões, pendências e um único item cronológico no changelog em cada alteração. Incrementar o build number em `pubspec.yaml` em cada commit de mudança.
