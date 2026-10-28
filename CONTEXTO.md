# CONTEXTO — B13 FALCON

## Visão geral e arquitetura

- Aplicativo Android de gravação de tela, Flutter/Dart na interface e Kotlin nos serviços nativos. Identificador: `com.b13.falcon`.
- Versão atual em desenvolvimento: `1.2.14+26`; branch `main`; `v1.2.13` é a última tag enviada ao GitHub.
- Flutter coordena navegação, preferências e estado visual em `lib/app.dart`; `RecordingNativeBridge` usa o canal `com.b13.falcon/recorder` para chamar o Android.
- `MainActivity` atende o MethodChannel, permissões solicitadas dentro do app, biblioteca de vídeos, configuração do overlay e consentimento MediaProjection quando a gravação começa no app.
- `CaptureConsentActivity` é uma Activity transparente sem interface própria para solicitar consentimento MediaProjection quando a gravação começa pelo overlay. O resultado inicia `RecordingService` diretamente sem abrir ou reordenar `MainActivity`.
- `FloatingControlsService` mantém o overlay e envia ações de gravar/pausar/retomar/parar e desenho.
- `RecordingService` é o serviço em primeiro plano que controla MediaProjection, VirtualDisplay, MediaCodec, captura/mixagem de áudio, MediaMuxer e publicação MP4 via MediaStore em `Movies/B13 Falcon`.
- `CameraOverlayService` usa CameraX em modo `PreviewView.ImplementationMode.COMPATIBLE` para exibir uma janela flutuante frontal antes/durante a gravação; o MediaProjection deve capturá-la no MP4, mas isso depende de confirmação física. O modo `baked` mantém o `ScreenCameraCompositor` como fallback.
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
- `microphoneGain` e `gameAudioGain`: ganho independente de 0 a 2; o limitador suave reduz clipping na soma dos canais.
- `microphoneNoiseSuppression`: tenta ativar o cancelamento de ruído nativo quando o aparelho oferece o efeito.
- A captura de áudio interno exige Android 10+ e pode ser bloqueada pela aplicação capturada. Microfone exige `RECORD_AUDIO`.
- `themeAccent` seleciona os temas animados Falcon, Ocean, Emerald ou Violet; animações respeitam a preferência de reduzir movimento do sistema.
- `cameraOverlay` (`cameraConfigJson` no MethodChannel/SharedPreferences): configuração única com câmera ao vivo ou imagem fixa selecionada no aparelho, formato `circle | rounded | square`, tamanho 80–260 dp, espelho, cor ARGB livre, estilo e largura da borda, faixa/nome e posição normalizada/trava. A permissão `CAMERA` é necessária apenas para usar a câmera ao vivo.

## Funcionalidades atuais

- Gravação de tela MP4, pausa/retomada, controles de notificação e overlay persistente.
- Ajustes de resolução, FPS, bitrate, HEVC/H.264, orientação e modo de áudio.
- Ganho independente de microfone e áudio do jogo, limitador suave para mistura e redução de ruído opcional quando suportada pelo hardware.
- Ferramentas flutuantes de desenho: traço livre, caixa, círculo, cores, espessura/opacidade, desfazer e limpar.
- Biblioteca MediaStore com listar, abrir via reprodutor externo e excluir gravações.
- Preferências persistidas localmente em `SharedPreferences`.
- Câmera frontal com prévia CameraX em janela flutuante e compositor antigo preservado como alternativa `baked`; a inclusão da janela no vídeo requer validação em aparelho.
- Opção de substituir a imagem ao vivo por uma imagem local na facecam, sem solicitar permissão de câmera; a câmera continua disponível como alternativa.
- O fluxo iniciado pelo flutuante inicia a prévia da câmera após a solicitação de notificações; gravações iniciadas na tela inicial enviam dimensões reais do display ao serviço nativo.
- Bordas da facecam personalizáveis entre estilos sólido, tracejado, duplo, gradiente e brilho; a cor aceita ajuste HSV, alfa e hexadecimal ARGB.
- Abas inferiores Vídeos, Capturas, Ajustes e Ajuda; as notificações oferecem Iniciar quando ocioso e Pausar/Retomar e Pincel durante a gravação.
- Lucide SVGs e licença ISC em `assets/icons/lucide/`; drawables vetoriais Android equivalentes em `android/app/src/main/res/drawable/`.

## Changelog (cronológico)

### 2026-10-27 — Corrige importação da imagem da facecam

- A imagem escolhida agora é copiada em segundo plano para o armazenamento privado do app, validada e usada pela janela CameraX; isso evita depender de permissões temporárias de provedores de galeria para reabrir o arquivo.
- Mantido suporte a URIs `content://` já salvos; os novos arquivos `file://` são restritos ao diretório privado da facecam.
- Atualizada a versão para `1.2.14+26`; formatação, análise e testes Flutter aguardam execução no CI porque o SDK Flutter não está disponível neste ambiente. Build Android e confirmação em aparelho também dependem do CI/dispositivo.

### 2026-10-27 — Imagem na facecam, controles de áudio e temas refinados

- Adicionada a escolha entre câmera ao vivo e imagem do aparelho; a seleção original usa o seletor de documentos do Android, e o app agora copia a imagem para armazenamento privado para uso confiável; o recorte, a borda e a posição continuam compartilhados.
- Adicionados ganho independente de microfone e áudio interno, limitador suave contra clipping e opção de redução de ruído por hardware, quando disponível.
- Refeito o card de temas com melhor hierarquia visual, indicação de seleção e amostras de paleta.
- Atualizada a versão para `1.2.13+25`; `dart format`, `flutter analyze` e `flutter test` aprovados. A compilação nativa e o teste em aparelho ainda precisam ser feitos, pois não há Android SDK nem Gradle Wrapper neste ambiente.

### 2026-10-27 — Corrige inicialização da facecam pelo flutuante

- Corrigido o fluxo de captura iniciado pelo controle flutuante: após a resposta à permissão de notificações, a prévia CameraX também é iniciada antes do consentimento de captura.
- Corrigido o fluxo iniciado pela tela inicial para enviar ao `RecordingService` as dimensões físicas do display alinhadas à orientação solicitada, evitando dimensões padrão que podiam inverter a gravação.
- Atualizada a versão para `1.2.12+24`; `dart format`, `flutter analyze` e `flutter test` aprovados. Build Android e validação visual/captura em aparelho permanecem pendentes por ausência de Android SDK, Gradle Wrapper e dispositivo Android neste ambiente.

### 2026-10-27 — Personalização de cor e borda da facecam

- Adicionados controles HSV e campo hexadecimal ARGB para personalizar a cor da borda, incluindo matiz, saturação, brilho e opacidade.
- Implementados os estilos sólido, tracejado, duplo, gradiente e brilho na moldura nativa da prévia CameraX.
- Atualizada a versão para `1.2.11+23`; `dart format`, `flutter analyze` e `flutter test` aprovados. A compilação Kotlin e a validação em aparelho seguem pendentes por falta do Gradle Wrapper/SDK local.

### 2026-10-27 — Serviço de prévia CameraX

- Adicionado `CameraOverlayService` (`LifecycleService`) com CameraX 1.6.2 estável e preview compatível via `TextureView`, janela flutuante arrastável e notificação foreground de câmera.
- Ao habilitar a câmera nos Ajustes, o serviço exibe a prévia sem gravar; inicia novamente antes do consentimento quando necessário. No modo `overlay`, `RecordingService` entrega a tela diretamente ao encoder e deixa a câmera como janela; `baked` preserva o compositor EGL existente.
- Falhas de acesso/abertura da câmera removem a janela e exibem aviso no serviço de câmera, sem chamar a finalização do gravador. A câmera ainda precisa ser confirmada no MP4 via MediaProjection em aparelho.
- Atualizada a versão para `1.2.10+22`; validação nativa pendente por falta de Gradle Wrapper/SDK local.

### 2026-10-27 — Configuração única da câmera e migração

- Criada a estrutura `CameraOverlaySettings` com JSON único, validação/clamp para formato, cores ARGB livres, tamanho, borda, faixa/nome e posição.
- `AppSettings` migra automaticamente os campos antigos sem perder as escolhas existentes; o Android consolida a configuração em `cameraConfigJson` e a repassa aos fluxos iniciados no app e pelo overlay.
- Adicionados os métodos de canal `saveCameraConfig` e `updateCameraLive` e testes de serialização, validação e migração do formato legado.
- Versão elevada para `1.2.9+21`; análise e testes Flutter aprovados. A compilação nativa e o teste de captura do overlay no MP4 ainda requerem CI/aparelho.

### 2026-10-27 — Refinamento visual da prévia flutuante

- Reduzido o tamanho padrão da janela da câmera para 136 dp e adicionado controle entre 100 e 200 dp nos Ajustes, persistido para gravações iniciadas pelo app ou pelo flutuante.
- Aplicada moldura escura translúcida, contorno sutil na cor da borda selecionada, sombra e recorte correspondente ao formato escolhido.
- Ao terminar de arrastar, a prévia se encaixa na lateral mais próxima e sua posição vertical é limitada à tela.
- Atualizada a versão para `1.2.8+20`; a exibição final precisa ser conferida em aparelho Android.

### 2026-10-27 — Correção de orientação e prévia flutuante da câmera

- Corrigida a orientação vertical das coordenadas de textura do compositor para que a gravação não fique de cabeça para baixo.
- Adicionada prévia da câmera em uma janela Android flutuante, arrastável e com o formato/borda escolhidos; o compositor entrega o mesmo fluxo da câmera à prévia e ao MP4 sem abrir a câmera duas vezes.
- A ativação da câmera agora solicita permissão de sobreposição; a gravação também verifica essa permissão antes de iniciar e a janela é removida ao parar.
- Atualizada a versão para `1.2.7+19`. Build e teste em aparelho são necessários para confirmar orientação e exibição em cada fabricante.
- Arquivos: `CONTEXTO.md`, `pubspec.yaml`, `lib/app.dart`, `lib/features/settings/settings_page.dart`, `android/app/src/main/kotlin/com/b13/falcon/RecordingService.kt` e `ScreenCameraCompositor.kt`.

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

- O compositor `baked` permanece no canto inferior direito, com aproximadamente 27% da menor dimensão do vídeo. O modo CameraX overlay é arrastável e usa posição fracionária; sincronização total da posição da janela no MP4 ainda requer validação.
- O compositor da versão `v1.2.6` compilou no GitHub Actions, mas a correção de orientação e a prévia introduzidas em `1.2.7+19` ainda precisam passar pelo workflow e por teste em aparelho; não há Android SDK/dispositivo local neste ambiente.
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
