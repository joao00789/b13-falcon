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

## Executar localmente

```bash
export PATH="$PATH:/home/joaodacruzxxx/flutter/bin"
flutter pub get
flutter analyze
flutter test
flutter build apk --debug
```

## Gerar APK

O workflow do GitHub Actions gera o APK em debug e publica como artefato. Também é possível compilar localmente com:

```bash
flutter build apk --debug
```

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
