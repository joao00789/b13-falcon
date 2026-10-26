# B13 FALCON

B13 FALCON é um aplicativo Android de gravação de tela desenvolvido em Flutter com integração nativa em Kotlin.

## Requisitos

- Flutter 3.47.8+
- Android SDK com `platforms;android-34`, `build-tools;34.0.0` e `cmdline-tools`
- Java 17
- GitHub Actions para geração de APK

## Estrutura principal

- `lib/` – interface, estado, serviços e configurações
- `android/` – configuração Android e integração nativa
- `.github/workflows/build.yml` – build automatizado do APK
- `test/` – testes unitários e de widget

## Recursos

- Gravação de tela com resoluções de 480p a 1440p, 24/30/60 FPS e taxa de bits ajustável.
- Menu de navegação flutuante, acesso rápido para gravar/pausar/parar e retorno ao início pelo botão Voltar.
- Durante a gravação, uma pequena bolha arrastável com a logo abre os controles de pausar/retomar, parar e desenhar sobre a tela.
- A pausa remove o intervalo pausado da linha do tempo do vídeo, evitando manter um quadro congelado; a opacidade da logo flutuante pode ser ajustada nas configurações.
- Captura de áudio do jogo e do microfone em uma única faixa, com seleção do modo nas configurações. O áudio interno exige Android 10 ou superior e pode ser bloqueado pelo próprio jogo.
- Transições animadas entre as telas e animações nos estados de gravação.
- Biblioteca real dos vídeos em `Movies/B13 Falcon`, com abertura no reprodutor do dispositivo e opção de apagar.
- Os vídeos recentes também aparecem na tela inicial.
- Tema claro/escuro e opção para manter a tela ligada durante o uso.

Os jogos precisam permitir a captura de áudio interno do Android. A reprodução é aberta no aplicativo de vídeo instalado no dispositivo.

## Executar localmente

```bash
export PATH="$PATH:/home/joaodacruzxxx/flutter/bin"
flutter pub get
flutter analyze
flutter test
flutter build apk --debug
```

## Gerar APK

O workflow do GitHub Actions gera o APK em debug nos pushes para `main` e quando executado manualmente. O arquivo fica disponível como artefato da execução. Também é possível compilar localmente com:

```bash
flutter build apk --debug
```

Para controlar versões, use versionamento semântico (`MAJOR.MINOR.PATCH`) nas tags.
Atualize também o campo `version` em `pubspec.yaml` antes de cada release (o número
após `+` é o `versionCode` Android, que deve sempre aumentar):

```bash
git tag v1.1.2
git push origin v1.1.2
```

Cada tag `v*` aciona o GitHub Actions, compila o APK e cria um pré-lançamento com
o arquivo para download. Os builds em `main` ficam disponíveis como artefatos da
execução. Os APKs usam a chave de debug do runner e são destinados a testes; não
são adequados para publicação na Play Store e podem não atualizar uma instalação
assinada por outra execução.

## Permissões

- `RECORD_AUDIO`
- `FOREGROUND_SERVICE`
- `POST_NOTIFICATIONS`
- `WRITE_EXTERNAL_STORAGE` (Android 8 e anteriores)
- `READ_MEDIA_VIDEO` / `READ_MEDIA_AUDIO`
- `SYSTEM_ALERT_WINDOW` para controles flutuantes opcionais

## Limitações

- Gravação interna de áudio e captura de tela dependem das permissões e da versão do Android.
- Em dispositivos com restrições de sistema, a aquisição de áudio interno pode ser limitada.
- O projeto está preparado para Android 8+ (API 26+).
