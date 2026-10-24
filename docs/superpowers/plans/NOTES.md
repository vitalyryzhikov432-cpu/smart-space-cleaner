# Заметки по окружению сборки

- Путь репозитория содержит кириллицу. Gradle запускается с виртуального диска: `subst S: "<путь к репозиторию>"`, затем `cd /s && ./gradlew ...` в Git Bash. Диск `S:` пропадает после перезагрузки — выполнить `subst` заново.
- JAVA_HOME = `C:/Android/jdk-21` (Temurin 21). JBR из Android Studio — Java 25, Gradle 8.11.1 на ней не работает.
- Android SDK: `C:\Android\AndroidSdk` (сама Android Studio установлена в `C:\Android\Sdk`). В `local.properties`: `sdk.dir=C\:/Android/AndroidSdk`.
- AVD: `C:\Android\avd` (`ANDROID_AVD_HOME`), кэш Gradle: `C:\gradle-home` (`GRADLE_USER_HOME`).
- Сеть до dl.google.com нестабильна: пакеты SDK надёжнее качать curl с `--retry` и распаковывать вручную.
