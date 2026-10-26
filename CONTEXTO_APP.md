# Contexto do app — B13 FALCON

Documento de referência para continuar o desenvolvimento do B13 FALCON. Descreve o estado observado no código e no histórico do repositório, incluindo funcionalidades, arquitetura, limitações, entregas publicadas e próximos trabalhos. Atualizado após a publicação da versão `v1.1.2`.

## Visão geral

- **Produto:** aplicativo Android de gravação de tela, com interface Flutter e implementação de captura nativa em Kotlin.
- **Identificador Android:** `com.b13.falcon`.
- **Versão configurada:** `1.1.2+4` em `pubspec.yaml`; a release de teste mais recente é a tag `v1.1.2`.
- **Plataforma suportada pelo código nativo:** Android 8 (API 26) ou superior; `minSdk` 26 e `targetSdk` 34.
- **Idioma da interface:** português.
- **Marca:** logo `logo B13 falcon.png`, também usada como ícone do app.
- **Objetivo principal:** gravar a tela em MP4, com opções de qualidade e áudio; permitir comandar a gravação sem voltar ao app usando controles flutuantes e notificação.

## Funcionalidades existentes

### Gravação

- Captura de tela via Android `MediaProjection`, executada num serviço em primeiro plano.
- Codificação de vídeo AVC/H.264 em MP4, com resoluções selecionáveis de 480p, 720p, 1080p e 1440p. A resolução solicitada define o limite da dimensão maior e a proporção da tela é preservada.
- Opções de 24, 30 ou 60 FPS e taxa de bits ajustável de 4 a 30 Mbps.
- A tela inicial apresenta estado, cronômetro, qualidade, taxa de bits e ações de gravar, pausar/retomar e parar.
- A gravação pode ser pausada e retomada. O intervalo pausado é excluído da linha do tempo do vídeo, em vez de deixar um quadro congelado durante a pausa.
- Notificação persistente com ações de pausar/retomar e parar.
- O app recebe atualizações de estado do serviço nativo para sincronizar a interface.

### Áudio

- Modos selecionáveis nas configurações: interno + microfone, som do jogo (interno), microfone ou sem áudio.
- O áudio é codificado como AAC e, quando interno e microfone são usados juntos, é combinado numa única faixa mono.
- Captura de áudio interno usa `AudioPlaybackCapture` e requer Android 10 (API 29) ou superior; cada jogo/app pode impedir a própria captura.
- A captura do microfone exige permissão. A tela explica a limitação do áudio interno e a permissão do microfone.
- Em Android anterior ao 10, o modo combinado pode continuar apenas pelo microfone; o modo exclusivamente interno falha com uma mensagem de requisito.

### Controles flutuantes e desenho

- Há uma navegação flutuante dentro da interface Flutter para acesso rápido ao início da gravação, biblioteca, pausa/retomada e parada.
- Durante gravações, o Android pode exibir uma bolha sobre outros apps. Ela é arrastável e expande controles nativos para pausar/retomar, parar, configurar ou ativar o desenho.
- O pincel tem seletor HSV, traço livre, retângulo e círculo, desfazer/limpar e ajustes de espessura e opacidade.
- As configurações flutuantes permitem ajustar a opacidade da bolha, a opacidade/espessura do pincel e escolher cores predefinidas.
- A permissão Android de sobreposição é solicitada quando o recurso está habilitado. Sem ela, a gravação pode começar sem a bolha.
- A opacidade da logo da bolha pode ser configurada de 20% a 100%; mudanças podem ser aplicadas enquanto a gravação está ativa.

### Biblioteca e arquivos

- Gravações são salvas em `Movies/B13 Falcon`.
- Android 10 ou superior usa `MediaStore` e publica o vídeo após concluir a gravação; versões anteriores usam arquivo público e Media Scanner.
- A biblioteca consulta vídeos nessa pasta, ordenados do mais recente para o mais antigo. Mostra nome, duração e tamanho, oferece atualização e confirmação antes de apagar.
- A tela inicial mostra até quatro gravações recentes.
- Abrir um vídeo delega a reprodução ao app de vídeo disponível no dispositivo (`ACTION_VIEW`); não há reprodução integrada implementada.

### Interface e preferências

- Telas de início, biblioteca e configurações; navegação inferior animada e retorno à tela inicial pelo botão Voltar.
- Temas escuro e claro, animações de entrada, transições de tela e animação do estado de gravação.
- Opção para manter a tela ligada, controles flutuantes, opacidade da logo, modo de áudio, resolução, FPS e taxa de bits.
- Preferências serializadas em JSON com `SharedPreferences`.
- Valores padrão do modelo: tema escuro, 1080p, 30 FPS, 16 Mbps, áudio interno + microfone, tela ligada, controles flutuantes e opacidade de 85%.

## Organização do código

### Flutter/Dart (`lib/`)

- `main.dart`: inicialização do Flutter e criação do app.
- `app.dart`: estado e coordenação geral; navegação, preferências, status da gravação, biblioteca e integração com o bridge Android.
- `features/home/home_page.dart`: painel de gravação, estado/cronômetro, qualidade e vídeos recentes.
- `features/library/library_page.dart`: listagem, atualização, abertura e pedido de remoção de vídeos.
- `features/settings/settings_page.dart`: interface das opções.
- `features/player/video_player_page.dart`: tela demonstrativa/placeholder; a reprodução real atualmente é externa, via Android.
- `data/models/app_settings.dart`: modelo, defaults, serialização e `copyWith` das preferências.
- `data/models/saved_recording.dart`: modelo de metadados retornados pelo Android.
- `services/native_recorder.dart`: `MethodChannel` `com.b13.falcon/recorder` e chamadas para o código nativo.
- `services/settings_service.dart`: persistência local das preferências.
- `services/storage_service.dart`: utilitários de caminho/armazenamento; não é o caminho principal usado pela biblioteca, que consulta o `MediaStore` nativo.
- `core/constants/`: cores e textos comuns.
- `core/theme/app_theme.dart`: temas claro e escuro.
- `core/utils/formatters.dart`: formatação de duração e tamanho de arquivos.
- `core/widgets/entrance_animation.dart`: animação de entrada reutilizável.

### Android/Kotlin (`android/app/src/main/`)

- `MainActivity.kt`: implementa o canal Flutter/Android, solicita permissões e consentimento de captura, inicia serviços, consulta/abre/apaga vídeos e atualiza opções nativas. O estado de gravação reportado à Flutter ainda é mantido localmente na Activity e atualizado por broadcasts.
- `RecordingService.kt`: serviço em primeiro plano; captura `MediaProjection`, codifica vídeo/áudio, controla pausa, sincroniza faixas no muxer e finaliza/publica o MP4. A finalização agora acontece numa thread de trabalho para não bloquear a thread principal do serviço.
- `FloatingControlsService.kt`: bolha e controles de sobreposição, desenho e opacidade.
- `AndroidManifest.xml`: serviços, permissões e declarações de captura.
- Existe também um `MainActivity.kt` sob `com/example/b13_falcon`; o namespace e a atividade usada pelo app são `com.b13.falcon`.

## Fluxos importantes

1. Ao iniciar gravação, Flutter verifica as opções e, se necessário, solicita a permissão de sobreposição.
2. O bridge chama `startRecording`; Android solicita permissão de microfone quando o modo selecionado precisa dela e apresenta o diálogo de consentimento de captura de tela.
3. Com consentimento, `MainActivity` inicia `RecordingService` e, se habilitada e autorizada, `FloatingControlsService`.
4. O serviço grava o vídeo e o áudio selecionados. Mudanças de estado são enviadas por broadcast ao Flutter e ao serviço flutuante.
5. Ao parar, o serviço faz o teardown em segundo plano; após finalizar o muxer/arquivo, publica o estado final e encerra o serviço. O bridge responde que a finalização começou, não que o arquivo já está pronto.
6. A biblioteca usa o URI do MediaStore para abrir o arquivo no reprodutor externo ou apagá-lo após confirmação.

## Dependências e comandos

- Flutter/Dart; Android SDK; Java 17.
- Dependências Flutter principais: `path_provider`, `shared_preferences` e `cupertino_icons`. Testes usam `flutter_test`; ícones Android são configurados com `flutter_launcher_icons`.
- Comandos de desenvolvimento documentados:

```bash
flutter pub get
dart format --set-exit-if-changed lib test
flutter analyze
flutter test
flutter build apk --debug
```

- O workflow `.github/workflows/build.yml` roda no push para `main`, em tags `v*` e manualmente. Usa Flutter 3.47.8 e Java 17; formata, analisa, testa e compila APK debug.
- O workflow publica artefato APK nas execuções. Para tags `v*`, cria também um GitHub pre-release. A APK é assinada com a chave debug do runner: serve para teste, não é assinatura de produção/Play Store e pode não atualizar instalações assinadas por outra chave.

## Testes existentes

`test/widget_test.dart` verifica a serialização da cor do tema, o carregamento do app e a mudança do modo de áudio “Som do jogo”. Na preparação da release `v1.1.0`, o teste de UI foi corrigido para tornar a opção visível antes do toque. A validação Flutter (formatação, análise e testes) passou novamente para as alterações da `v1.1.2`. Não há testes automatizados de integração para captura de tela, permissões Android, áudio, serviços de sobreposição ou reprodução.

## Limitações e pontos de atenção

- Captura de tela e áudio dependem de permissões, consentimento do Android, versão do sistema e suporte do dispositivo.
- Áudio interno pode ser bloqueado pelo jogo ou aplicativo de origem.
- A reprodução não é dentro do app: `VideoPlayerPage` continua sendo apenas uma tela placeholder; `openRecording` abre o player Android externo.
- `StorageService.getAvailableStorageMb()` contém um placeholder: usa o tamanho retornado por `stat` como estimativa de espaço livre. Não tratar esse valor como espaço disponível confiável.
- Há propriedades antigas/auxiliares no modelo de configuração, como `useInternalAudio`; o modo efetivamente exposto e enviado ao gravador é `audioMode`.
- `MainActivity` ainda guarda cópias locais de `isRecording`/`isPaused`. Se o processo da Activity for recriado enquanto o serviço continuar ativo, o estado inicial pode ficar dessincronizado; a fonte da verdade ainda não foi movida para o serviço.
- A notificação usa `POST_NOTIFICATIONS`, mas a solicitação runtime dessa permissão no Android 13+ ainda não foi implementada.
- A orientação e as dimensões de captura são obtidas no início. Mudanças de orientação ou de tamanho do conteúdo durante uma sessão (incluindo compartilhamento de app no Android 14+) ainda precisam ser tratadas.
- Áudio e vídeo ainda usam caminhos de timestamp diferentes. A sincronização por um relógio monotônico único e testes de deriva continuam pendentes.
- Se a captura de áudio falhar antes de produzir o formato, `pendingSamples` pode acumular amostras aguardando o muxer; a política de limite/timeout com fallback para vídeo sem áudio ainda precisa ser implementada.
- A saída precisa de tratamento adicional para apagar entradas órfãs quando a gravação falha antes de o muxer iniciar corretamente.
- O fluxo de permissões de captura ainda usa `startActivityForResult`/`onActivityResult`, APIs deprecated.
- O build automatizado atual produz APK debug com chave efêmera do runner; cada build pode não atualizar por cima de uma instalação anterior. Keystore estável, build release e split por ABI ainda não estão configurados.
- Os testes atuais não exercitam o fluxo nativo de gravação. Validar em dispositivo/emulador Android após alterações em `RecordingService`, permissões, áudio ou overlays.

## Histórico de entregas identificado no Git

O histórico observado no momento da criação deste documento registra, entre outras, estas etapas:

- `faae8cc` — versão inicial: interface Flutter, serviço Android de gravação, modelos, configurações, biblioteca, testes e workflow.
- `4f13b00` — identidade visual B13 FALCON, ícones e ajustes finais de UI.
- `8b86fcb`, `1e1dbce` e `285a50e` — automação para builds Android e releases de APK e correções da configuração de CI.
- `6d4950a` — controles flutuantes, pincel de desenho e integração das ações de gravação.
- `6295cb8` e `77d35e2` — formatação e correções de compilação Kotlin/APK.
- `166d6e6` — refinamento das animações e implementação/aprimoramento da captura de áudio interno e microfone.
- `ae8c17a` — correção do import da captura de áudio de reprodução Android.
- `7e50581` — melhorias na captura do microfone e nos controles flutuantes do pincel.
- `fc1d685` — preparação inicial da versão `v1.1.0`; o workflow falhou na compilação Kotlin.
- `56837ec` — correção das referências de estado HSV no seletor de cores; publicação de `v1.1.1` após build bem-sucedido.
- `9f00de4` — comandos de controle mais seguros e encerramento da gravação em thread separada; publicação de `v1.1.2` após build bem-sucedido.

## Entregas recentes e validação

- A release `v1.1.0` não concluiu o build: `FloatingControlsService.kt` referenciava `selectedHue`, `selectedSaturation` e `selectedBrightness` fora do escopo do seletor. A correção sincronizou esses valores HSV no `DrawingOverlayView`; a versão corrigida foi publicada como `v1.1.1`.
- Na `v1.1.2`, `MainActivity` passou a enviar STOP/PAUSE/RESUME via `startService()` em vez de `startForegroundService()` e evita enviar ações inválidas quando não há gravação ativa/pausada.
- `RecordingService.stopRecording()` agora marca a gravação como encerrando, interrompe e aguarda os workers numa thread dedicada, e só então remove a notificação, envia o estado final e encerra o serviço. Falhas durante o início também passam pelo encerramento assíncrono com estado de erro.
- Para a `v1.1.2`, `dart format --set-exit-if-changed lib test`, `flutter analyze` e `flutter test` passaram. O Android SDK não está instalado neste ambiente; o GitHub Actions compilou o APK nativo e publicou `b13-falcon-debug.apk`.
- Download da versão mais recente: https://github.com/joao00789/b13-falcon/releases/tag/v1.1.2.

## O que estamos fazendo agora / próximos passos

A prioridade atual é continuar a revisão de estabilidade da gravação depois de corrigir os dois primeiros problemas apontados pelo usuário. As correções de STOP/PAUSE/RESUME e teardown não resolvem ainda todos os riscos. Próximas tarefas recomendadas:

1. Tornar o serviço a fonte de verdade do estado e consultar o estado atual ao iniciar/recriar a Activity, sincronizando cronômetro e controles flutuantes.
2. Solicitar `POST_NOTIFICATIONS` em tempo de execução no Android 13+ e tratar a recusa sem esconder erros.
3. Corrigir falhas de inicialização/encerramento da saída: apagar a entrada MediaStore se o muxer nunca iniciar e evitar MP4 vazio órfão.
4. Limitar `pendingSamples` e definir timeout/fallback para continuar somente com vídeo quando áudio não produzir formato.
5. Tratar orientação e redimensionamento do conteúdo, incluindo `onCapturedContentResize` no Android 14+.
6. Unificar a referência de timestamps de áudio/vídeo e adicionar testes de lógica pura para PTS/pausas e seleção de resolução.

Depois dessas tarefas, considerar os demais itens levantados: fluxo moderno de Activity Result, limpeza de permissões/arquivos/modelo de áudio, miniaturas/compartilhamento, aviso de armazenamento, tile e câmera frontal. Preservar o caminho de captura de áudio se não houver necessidade direta de alterá-lo e validar em aparelho Android: testes Flutter não cobrem codecs, `MediaProjection` nem sobreposições.

## Estado do worktree ao atualizar este contexto

O branch `main` está sincronizado com `origin/main` no commit `9f00de4` (tag `v1.1.2`). `CONTEXTO_APP.md` é um arquivo local não rastreado pelo Git; esta atualização não o adiciona nem cria commit automaticamente. Conferir `git status` antes de iniciar outras alterações para preservar arquivos locais.
