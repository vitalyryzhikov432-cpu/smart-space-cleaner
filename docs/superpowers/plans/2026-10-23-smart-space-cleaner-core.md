# Smart Space Cleaner — Подпроект 1 (ядро ревизии): план реализации

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Android-приложение, которое только читает память телефона, показывает план уборки по уровням риска 0–2, выполняет одобренное (корзина на 7 дней, отмена) и выдаёт честный отчёт «было → стало».

**Architecture:** Два Gradle-модуля. `:core` — чистый Kotlin/JVM: модель, правила сканирования, планировщик, исполнитель, отмена, корзина, отчёты; общается с миром через порты (`FileSystem`, `ActionLogStore`, `ApkInspector`, `Clock`) и тестируется JUnit 5 на ПК с фейками. `:app` — тонкий Android-слой: адаптеры портов (`java.io.File`, Room, `PackageManager`, `StorageStatsManager`), WorkManager, Compose UI с одним `ViewModel`.

**Tech Stack:** Kotlin 2.1.0, AGP 8.7.3, Gradle 8.11.1, Jetpack Compose (BOM 2024.12.01, Material 3), корутины 1.9.0, Room 2.6.1 + KSP, WorkManager 2.10.0, JUnit 5.11.4 (`:core`), AndroidX Test (`:app`).

**Spec:** [docs/superpowers/specs/2026-10-22-smart-space-cleaner-core-design.md](../specs/2026-10-22-smart-space-cleaner-core-design.md)

## Global Constraints

- `minSdk = 31`, `compileSdk = 35`, `targetSdk = 35`. Версии библиотек закреплены набором из Tech Stack — он заведомо совместим; обновление версий — отдельная задача после подпроекта 1 (уточнение к спеке, где сказано «последняя стабильная»).
- Пакет: `com.smartspacecleaner` (app), `com.smartspacecleaner.core` (core).
- `:core` не зависит ни от Android, ни от корутин. Все вызовы `:core` из `:app` идут на `Dispatchers.IO`.
- Корень памяти: `/storage/emulated/0`. Корзина: `<корень>/.SmartSpaceCleaner/trash`, внутри — пустой `.nomedia`. Срок хранения в корзине: 7 дней.
- Исключены из скана: `<корень>/Android/data`, `<корень>/Android/obb`, `<корень>/.SmartSpaceCleaner`.
- Стартовые пороги: дубли от 1 КБ; частичный хэш — первые и последние 64 КБ; скриншоты старше 180 дней (`DCIM/Screenshots`, `Pictures/Screenshots`); крупные файлы от 100 МБ; медиа мессенджеров старше 90 дней.
- Стратегия дублей по умолчанию: `DuplicateStrategy.TRASH`. Реализуются и тестируются обе стратегии.
- Сравнение папок без учёта регистра (файловая система Android на общей памяти регистронезависима).
- Все тексты интерфейса — на русском. Объёмы: `formatBytes` (`1,8 ГБ`, `5 МБ`, `2 КБ`, `0 Б`), основание 1024.
- Без DI-фреймворка: зависимости собираются вручную в `AppContainer`.
- Команды Gradle запускаются из корня репозитория в Git Bash: `./gradlew ...` (переменные окружения из `~/.bashrc`, см. Task 1).
- Начиная с Task 12, команды `connectedDebugAndroidTest` и `installDebug` требуют запущенного эмулятора `ssc_api31` (как запустить — Task 12, Step 1).
- Каждый коммит заканчивается строкой `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>` (второй `-m`).
- Уровень 0 «временные файлы приложения» отдельной группой не реализуется: собственный кэш приложения очищается той же системной очисткой кэша (группа «Кэш приложений»). Других временных файлов в подпроекте 1 приложение не создаёт.

## Review Focus

1. **Нечитаемый файл или папка во время скана** (нет доступа, файл удалили посреди скана) → скан не падает, файл просто пропускается. Тест: Task 3 `skips unreadable files`.
2. **Оригинал дубля в той же сессии тоже отправлен в корзину** (он же старый скриншот на уровне 2) → оба файла лежат в корзине и оба возвращаются. Тест: Task 9 `restores duplicate copy even if its original was trashed in the same session`.
3. **Имена папок в другом регистре** (`dcim/screenshots`, `download`) → правила срабатывают так же. Тесты: Task 4 `folder checks ignore case`, Task 5 `old screenshot rule ignores folder case`.
4. **Не хватает места для восстановления удалённого дубля** → отмена честно отказывает с причиной, ничего не портит. Тест: Task 9 `fails to restore duplicate when there is not enough space`.
5. **Ничего не найдено** (чистая память) → план содержит только кэш, сводка говорит «ничего заметного не нашлось». Тесты: Task 6 `always offers app cache group first`, Task 11 `says so when nothing was found`.

---

## Карта файлов

```
settings.gradle.kts, build.gradle.kts, gradle.properties, gradle/libs.versions.toml, .gitignore, .gitattributes
core/build.gradle.kts
core/src/main/kotlin/com/smartspacecleaner/core/
  model/FileEntry.kt        — запись о файле
  model/Group.kt            — RiskLevel, GroupKind, PlannedItem, Group, Plan
  model/Selection.kt        — отметки групп/файлов
  model/ActionRecord.kt     — запись журнала действий
  port/Ports.kt             — FileSystem, Clock, WallClock, ActionLogStore, ApkInspector, ApkStatus
  text/Text.kt              — formatBytes, plural
  scan/Hasher.kt            — частичный и полный SHA-256
  scan/DuplicateFinder.kt   — точные дубли
  scan/StorageLayout.kt     — знание о папках Android, Messenger
  scan/KeepCopyPolicy.kt    — какую копию оставить
  scan/Rules.kt             — ApkRule, OldScreenshotRule, LargeFileRule, MessengerMediaRule
  scan/Planner.kt           — группы с текстами
  scan/Scanner.kt           — обход памяти + планировщик
  action/Executor.kt        — исполнение одобренного
  action/PendingReconciler.kt — сверка после сбоя
  action/Restorer.kt        — отмена
  action/TrashPurger.kt     — окончательная очистка корзины
  report/SummaryBuilder.kt  — сводка 🔴/🟡/🟢
  report/ReportBuilder.kt   — итоговый отчёт
core/src/test/kotlin/com/smartspacecleaner/core/
  fakes/Fakes.kt            — FakeFileSystem, FakeClock, InMemoryActionLogStore, FakeApkInspector, тестовые константы
  ...Test.kt                — тесты рядом по пакетам
app/build.gradle.kts, app/src/main/AndroidManifest.xml
app/src/main/java/com/smartspacecleaner/
  SmartSpaceApp.kt, AppContainer.kt, MainActivity.kt
  data/AndroidFileSystem.kt, data/AndroidApkInspector.kt, data/StorageInfo.kt
  data/ActionRecordEntity.kt, data/ActionRecordDao.kt, data/AppDatabase.kt, data/RoomActionLogStore.kt
  work/PurgeWorker.kt
  ui/MainViewModel.kt, ui/SimpleScreens.kt, ui/PlanScreen.kt, ui/ReportScreen.kt, ui/HistoryScreen.kt
app/src/androidTest/java/com/smartspacecleaner/data/
  AndroidFileSystemTest.kt, RoomActionLogStoreTest.kt, AndroidApkInspectorTest.kt
scripts/push-test-data.sh
```

---

### Task 1: Окружение и каркас проекта

**Files:**
- Create: `settings.gradle.kts`, `build.gradle.kts`, `gradle.properties`, `gradle/libs.versions.toml`, `.gitignore`, `.gitattributes`, `local.properties` (не коммитится)
- Create: `core/build.gradle.kts`, `app/build.gradle.kts`, `app/src/main/AndroidManifest.xml`, `app/src/main/java/com/smartspacecleaner/MainActivity.kt`
- Create (генерируется): `gradlew`, `gradlew.bat`, `gradle/wrapper/*`
- Modify: `~/.bashrc`

**Interfaces:**
- Consumes: —
- Produces: собираемый проект с модулями `:core` (JUnit 5) и `:app` (Compose, Room/KSP, WorkManager уже подключены).

- [ ] **Step 1: Задать пути без кириллицы (PowerShell)**

Путь к профилю пользователя содержит кириллицу, а эмулятор и Gradle с такими путями работают ненадёжно. Выполнить в PowerShell:

```powershell
[Environment]::SetEnvironmentVariable('ANDROID_AVD_HOME','C:\Android\avd','User')
[Environment]::SetEnvironmentVariable('GRADLE_USER_HOME','C:\gradle-home','User')
New-Item -ItemType Directory -Force C:\Android\avd, C:\Android\Sdk, C:\gradle-home | Out-Null
```

- [ ] **Step 2: Установить Android Studio (вручную, пользователь)**

Попросить пользователя:
1. Скачать Android Studio с https://developer.android.com/studio и установить с настройками по умолчанию.
2. При первом запуске выбрать **Custom** и указать SDK location `C:\Android\Sdk`.
3. Открыть **Settings → Languages & Frameworks → Android SDK**:
   - вкладка **SDK Platforms**: отметить «Android 15.0 (API 35)», раскрыть «Android 12.0 (S) / API 31» (галка «Show Package Details») и отметить «Google APIs Intel x86_64 Atom System Image»;
   - вкладка **SDK Tools**: Android SDK Build-Tools 35, Android Emulator, Android SDK Platform-Tools, Android SDK Command-line Tools (latest).
4. **Settings → Build, Execution, Deployment → Build Tools → Gradle**: Gradle user home = `C:\gradle-home`.
5. **Device Manager → Create Virtual Device**: Pixel 5 → образ API 31 (Google APIs, x86_64) → имя `ssc_api31`.
6. Перезапустить VS Code, чтобы подхватились новые переменные окружения.

- [ ] **Step 3: Проверить установку**

Run:
```bash
ls "/c/Program Files/Android/Android Studio/jbr/bin/java.exe" && ls /c/Android/Sdk/platforms && /c/Android/Sdk/emulator/emulator -list-avds
```
Expected: путь к `java.exe`, в списке платформ есть `android-35`, в списке AVD есть `ssc_api31`.

- [ ] **Step 4: Прописать окружение для Git Bash**

```bash
cat >> ~/.bashrc <<'EOF'
export JAVA_HOME="/c/Program Files/Android/Android Studio/jbr"
export ANDROID_HOME="/c/Android/Sdk"
export ANDROID_AVD_HOME="/c/Android/avd"
export GRADLE_USER_HOME="/c/gradle-home"
export PATH="$JAVA_HOME/bin:$ANDROID_HOME/platform-tools:$ANDROID_HOME/emulator:$PATH"
EOF
source ~/.bashrc && java -version && adb version
```
Expected: версия Java (21.x от JetBrains) и версия adb.

- [ ] **Step 5: Корневые файлы сборки**

`settings.gradle.kts`:
```kotlin
pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}
rootProject.name = "SmartSpaceCleaner"
include(":core", ":app")
```

`build.gradle.kts`:
```kotlin
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.ksp) apply false
}
```

`gradle.properties`:
```properties
org.gradle.jvmargs=-Xmx2048m -Dfile.encoding=UTF-8
android.useAndroidX=true
android.nonTransitiveRClass=true
# Путь к репозиторию содержит кириллицу; без этого AGP отказывается собирать.
android.overridePathCheck=true
kotlin.code.style=official
```

`gradle/libs.versions.toml`:
```toml
[versions]
agp = "8.7.3"
kotlin = "2.1.0"
ksp = "2.1.0-1.0.29"
coroutines = "1.9.0"
junit5 = "5.11.4"
composeBom = "2024.12.01"
activityCompose = "1.9.3"
lifecycle = "2.8.7"
coreKtx = "1.15.0"
room = "2.6.1"
work = "2.10.0"
androidxJunit = "1.2.1"
androidxTestRunner = "1.6.2"

[libraries]
coroutines-android = { module = "org.jetbrains.kotlinx:kotlinx-coroutines-android", version.ref = "coroutines" }
junit-bom = { module = "org.junit:junit-bom", version.ref = "junit5" }
junit-jupiter = { module = "org.junit.jupiter:junit-jupiter" }
junit-platform-launcher = { module = "org.junit.platform:junit-platform-launcher" }
androidx-core-ktx = { module = "androidx.core:core-ktx", version.ref = "coreKtx" }
activity-compose = { module = "androidx.activity:activity-compose", version.ref = "activityCompose" }
compose-bom = { module = "androidx.compose:compose-bom", version.ref = "composeBom" }
compose-ui = { module = "androidx.compose.ui:ui" }
compose-ui-tooling = { module = "androidx.compose.ui:ui-tooling" }
compose-ui-tooling-preview = { module = "androidx.compose.ui:ui-tooling-preview" }
compose-material3 = { module = "androidx.compose.material3:material3" }
lifecycle-runtime-compose = { module = "androidx.lifecycle:lifecycle-runtime-compose", version.ref = "lifecycle" }
lifecycle-viewmodel-compose = { module = "androidx.lifecycle:lifecycle-viewmodel-compose", version.ref = "lifecycle" }
room-runtime = { module = "androidx.room:room-runtime", version.ref = "room" }
room-compiler = { module = "androidx.room:room-compiler", version.ref = "room" }
work-runtime-ktx = { module = "androidx.work:work-runtime-ktx", version.ref = "work" }
androidx-junit = { module = "androidx.test.ext:junit", version.ref = "androidxJunit" }
androidx-test-runner = { module = "androidx.test:runner", version.ref = "androidxTestRunner" }

[plugins]
android-application = { id = "com.android.application", version.ref = "agp" }
kotlin-android = { id = "org.jetbrains.kotlin.android", version.ref = "kotlin" }
kotlin-jvm = { id = "org.jetbrains.kotlin.jvm", version.ref = "kotlin" }
kotlin-compose = { id = "org.jetbrains.kotlin.plugin.compose", version.ref = "kotlin" }
ksp = { id = "com.google.devtools.ksp", version.ref = "ksp" }
```

`.gitignore`:
```
.gradle/
.kotlin/
build/
local.properties
.idea/
*.iml
captures/
```

`.gitattributes`:
```
gradlew text eol=lf
*.sh text eol=lf
*.bat text eol=crlf
```

`local.properties` (не коммитится):
```bash
cat > local.properties <<'EOF'
sdk.dir=C\:\\Android\\Sdk
EOF
```

- [ ] **Step 6: Модуль `:core`**

`core/build.gradle.kts`:
```kotlin
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.jvm)
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
}

dependencies {
    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}

tasks.test {
    useJUnitPlatform()
}
```

- [ ] **Step 7: Модуль `:app`**

`app/build.gradle.kts`:
```kotlin
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.smartspacecleaner"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.smartspacecleaner"
        minSdk = 31
        targetSdk = 35
        versionCode = 1
        versionName = "0.1"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildFeatures { compose = true }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
}

dependencies {
    implementation(project(":core"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.activity.compose)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    debugImplementation(libs.compose.ui.tooling)
    implementation(libs.lifecycle.runtime.compose)
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.coroutines.android)
    implementation(libs.room.runtime)
    ksp(libs.room.compiler)
    implementation(libs.work.runtime.ktx)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.test.runner)
}
```

`app/src/main/AndroidManifest.xml`:
```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:tools="http://schemas.android.com/tools">

    <uses-permission
        android:name="android.permission.MANAGE_EXTERNAL_STORAGE"
        tools:ignore="ScopedStorage" />
    <!-- Нужно, чтобы видеть установленные приложения и сравнивать их с .apk -->
    <uses-permission
        android:name="android.permission.QUERY_ALL_PACKAGES"
        tools:ignore="QueryAllPackagesPermission" />

    <application
        android:label="Smart Space Cleaner"
        android:theme="@android:style/Theme.Material.Light.NoActionBar">
        <activity
            android:name=".MainActivity"
            android:exported="true">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>
    </application>
</manifest>
```

`app/src/main/java/com/smartspacecleaner/MainActivity.kt` (временная версия, заменяется в Task 17):
```kotlin
package com.smartspacecleaner

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MaterialTheme { Text("Smart Space Cleaner") } }
    }
}
```

- [ ] **Step 8: Сгенерировать Gradle Wrapper**

```bash
TMPG=$(mktemp -d) && curl -fL -o "$TMPG/gradle.zip" https://services.gradle.org/distributions/gradle-8.11.1-bin.zip \
  && unzip -q "$TMPG/gradle.zip" -d "$TMPG" \
  && "$TMPG/gradle-8.11.1/bin/gradle" wrapper --gradle-version 8.11.1 --distribution-type bin \
  && ls gradlew gradlew.bat gradle/wrapper
```
Expected: файлы `gradlew`, `gradlew.bat`, `gradle/wrapper/gradle-wrapper.jar`, `gradle/wrapper/gradle-wrapper.properties`.

- [ ] **Step 9: Собрать проект**

Run: `./gradlew :core:test :app:assembleDebug`
Expected: `BUILD SUCCESSFUL`. У `:core:test` будет `NO-SOURCE`, это нормально.

Если сборка падает с ошибкой, похожей на путь с кириллицей (`aapt2`, `Invalid path`, `non-ASCII`), используйте запасной путь: в PowerShell выполните `subst S: "C:\Users\Виталий\Desktop\ИИ\документы\projects\ma-first-pr"` и запускайте все команды из `/s/` (`cd /s && ./gradlew ...`). Запишите это в `docs/superpowers/plans/NOTES.md`, чтобы следующие задачи знали.

- [ ] **Step 10: Commit**

```bash
git add settings.gradle.kts build.gradle.kts gradle.properties gradle/ gradlew gradlew.bat .gitignore .gitattributes core/build.gradle.kts app/
git commit -m "chore: scaffold Gradle project with core and app modules" -m "Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 2: Модель, порты, тексты и тестовые фейки

**Files:**
- Create: `core/src/main/kotlin/com/smartspacecleaner/core/model/FileEntry.kt`
- Create: `core/src/main/kotlin/com/smartspacecleaner/core/model/Group.kt`
- Create: `core/src/main/kotlin/com/smartspacecleaner/core/model/ActionRecord.kt`
- Create: `core/src/main/kotlin/com/smartspacecleaner/core/port/Ports.kt`
- Create: `core/src/main/kotlin/com/smartspacecleaner/core/text/Text.kt`
- Create: `core/src/test/kotlin/com/smartspacecleaner/core/fakes/Fakes.kt`
- Test: `core/src/test/kotlin/com/smartspacecleaner/core/text/TextTest.kt`, `core/src/test/kotlin/com/smartspacecleaner/core/model/ModelTest.kt`

**Interfaces:**
- Produces:
  - `data class FileEntry(path: String, size: Long, modifiedAt: Instant)` с `name`, `extension` (в нижнем регистре).
  - `enum class RiskLevel(number: Int) { SAFE(0), EXACT(1), PERSONAL(2) }`
  - `enum class GroupKind { APP_CACHE, DUPLICATES, APK_INSTALLED, APK_UNKNOWN, OLD_SCREENSHOTS, LARGE_FILES, MESSENGER_MEDIA }`
  - `data class PlannedItem(path, size, modifiedAt, keptOriginalPath: String? = null, sha256: String? = null)`
  - `data class Group(id, kind, level, title, willRemove, willKeep, why, items: List<PlannedItem>, selectedByDefault: Boolean)` + `totalBytes`
  - `data class Plan(groups, scannedFiles: Int, scannedBytes: Long)` + `defaultSelection(): Set<String>`, `selectedBytes(selected: Set<String>): Long`
  - `enum class ActionKind { TRASH, DELETE_DUPLICATE, DELETE_APK }`, `enum class ActionStatus { PENDING, DONE, FAILED, RESTORED, PURGED }`, `data class ActionRecord(...)` (поля ниже)
  - Порты: `FileSystem`, `Clock`, `WallClock`, `ActionLogStore`, `ApkInspector`, `enum class ApkStatus { INSTALLED_SAME_OR_NEWER, NOT_INSTALLED_OR_OLDER, UNREADABLE }`
  - `formatBytes(bytes: Long): String`, `plural(n: Int, one: String, few: String, many: String): String`
  - Тестовые: `ROOT`, `p(rel)`, `T0`, `bytes(size, seed)`, `FakeFileSystem`, `FakeClock`, `InMemoryActionLogStore`, `FakeApkInspector`

- [ ] **Step 1: Write the failing tests**

`core/src/test/kotlin/com/smartspacecleaner/core/text/TextTest.kt`:
```kotlin
package com.smartspacecleaner.core.text

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class TextTest {
    @Test
    fun `formats bytes in russian units`() {
        assertEquals("0 Б", formatBytes(0))
        assertEquals("512 Б", formatBytes(512))
        assertEquals("2 КБ", formatBytes(1536))
        assertEquals("5 МБ", formatBytes(5L * 1024 * 1024))
        assertEquals("100 МБ", formatBytes(100L * 1024 * 1024))
        assertEquals("1,8 ГБ", formatBytes(1_932_735_283))
        assertEquals("16,5 ГБ", formatBytes(17_716_740_096))
    }

    @Test
    fun `picks russian plural form`() {
        assertEquals("файл", plural(1, "файл", "файла", "файлов"))
        assertEquals("файла", plural(3, "файл", "файла", "файлов"))
        assertEquals("файлов", plural(5, "файл", "файла", "файлов"))
        assertEquals("файлов", plural(11, "файл", "файла", "файлов"))
        assertEquals("файлов", plural(12, "файл", "файла", "файлов"))
        assertEquals("файл", plural(21, "файл", "файла", "файлов"))
        assertEquals("файла", plural(22, "файл", "файла", "файлов"))
        assertEquals("файлов", plural(0, "файл", "файла", "файлов"))
    }
}
```

`core/src/test/kotlin/com/smartspacecleaner/core/model/ModelTest.kt`:
```kotlin
package com.smartspacecleaner.core.model

import com.smartspacecleaner.core.fakes.T0
import com.smartspacecleaner.core.fakes.p
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ModelTest {
    @Test
    fun `file entry exposes name and lowercase extension`() {
        val e = FileEntry(p("DCIM/Camera/IMG_1.JPG"), 10, T0)
        assertEquals("IMG_1.JPG", e.name)
        assertEquals("jpg", e.extension)
        assertEquals("", FileEntry(p("Download/README"), 1, T0).extension)
    }

    @Test
    fun `default selection takes only groups selected by default`() {
        val a = PlannedItem(p("a"), 10, T0)
        val b = PlannedItem(p("b"), 20, T0)
        val plan = Plan(
            groups = listOf(
                Group("g1", GroupKind.DUPLICATES, RiskLevel.EXACT, "", "", "", "", listOf(a), selectedByDefault = true),
                Group("g2", GroupKind.LARGE_FILES, RiskLevel.PERSONAL, "", "", "", "", listOf(b), selectedByDefault = false),
            ),
            scannedFiles = 2,
            scannedBytes = 30,
        )
        assertEquals(setOf(p("a")), plan.defaultSelection())
        assertEquals(30L, plan.selectedBytes(setOf(p("a"), p("b"))))
        assertEquals(10L, plan.groups[0].totalBytes)
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew :core:test`
Expected: FAIL — ошибки компиляции `Unresolved reference: formatBytes`, `FileEntry`, `p`, `T0`.

- [ ] **Step 3: Write the implementation**

`core/src/main/kotlin/com/smartspacecleaner/core/model/FileEntry.kt`:
```kotlin
package com.smartspacecleaner.core.model

import java.time.Instant

data class FileEntry(val path: String, val size: Long, val modifiedAt: Instant) {
    val name: String get() = path.substringAfterLast('/')
    val extension: String get() = name.substringAfterLast('.', "").lowercase()
}
```

`core/src/main/kotlin/com/smartspacecleaner/core/model/Group.kt`:
```kotlin
package com.smartspacecleaner.core.model

import java.time.Instant

enum class RiskLevel(val number: Int) { SAFE(0), EXACT(1), PERSONAL(2) }

enum class GroupKind { APP_CACHE, DUPLICATES, APK_INSTALLED, APK_UNKNOWN, OLD_SCREENSHOTS, LARGE_FILES, MESSENGER_MEDIA }

data class PlannedItem(
    val path: String,
    val size: Long,
    val modifiedAt: Instant,
    /** Для дублей: путь к копии, которая остаётся. */
    val keptOriginalPath: String? = null,
    /** Для дублей: SHA-256 содержимого на момент скана. */
    val sha256: String? = null,
)

data class Group(
    val id: String,
    val kind: GroupKind,
    val level: RiskLevel,
    val title: String,
    val willRemove: String,
    val willKeep: String,
    val why: String,
    val items: List<PlannedItem>,
    val selectedByDefault: Boolean,
) {
    val totalBytes: Long get() = items.sumOf { it.size }
}

data class Plan(val groups: List<Group>, val scannedFiles: Int, val scannedBytes: Long) {
    fun defaultSelection(): Set<String> =
        groups.filter { it.selectedByDefault }.flatMapTo(mutableSetOf()) { g -> g.items.map { it.path } }

    fun selectedBytes(selected: Set<String>): Long =
        groups.flatMap { it.items }.filter { it.path in selected }.sumOf { it.size }
}
```

`core/src/main/kotlin/com/smartspacecleaner/core/model/ActionRecord.kt`:
```kotlin
package com.smartspacecleaner.core.model

import java.time.Instant

enum class ActionKind { TRASH, DELETE_DUPLICATE, DELETE_APK }

enum class ActionStatus { PENDING, DONE, FAILED, RESTORED, PURGED }

data class ActionRecord(
    val id: Long = 0,
    val sessionId: String,
    val groupId: String,
    val groupTitle: String,
    val kind: ActionKind,
    val originalPath: String,
    val size: Long,
    val trashPath: String? = null,
    val keptOriginalPath: String? = null,
    val sha256: String? = null,
    val status: ActionStatus,
    val reason: String? = null,
    val createdAt: Instant,
    val expiresAt: Instant? = null,
)
```

`core/src/main/kotlin/com/smartspacecleaner/core/port/Ports.kt`:
```kotlin
package com.smartspacecleaner.core.port

import com.smartspacecleaner.core.model.ActionRecord
import com.smartspacecleaner.core.model.ActionStatus
import com.smartspacecleaner.core.model.FileEntry
import java.io.InputStream
import java.time.Instant

interface FileSystem {
    /** Все файлы под [root]; пути из [excludedPrefixes] и всё под ними пропускаются. */
    fun walk(root: String, excludedPrefixes: Set<String>): Sequence<FileEntry>
    fun exists(path: String): Boolean
    fun openRead(path: String): InputStream
    /** Переименование; создаёт родительские папки; false, если цель уже существует или не получилось. */
    fun move(from: String, to: String): Boolean
    /** Копирование; false, если цель уже существует или не получилось. */
    fun copy(from: String, to: String): Boolean
    fun delete(path: String): Boolean
    fun mkdirs(path: String): Boolean
    fun createEmptyFile(path: String): Boolean
    fun freeBytes(): Long
}

interface Clock {
    fun now(): Instant
}

object WallClock : Clock {
    override fun now(): Instant = Instant.now()
}

interface ActionLogStore {
    /** Сохраняет запись и возвращает присвоенный id. */
    fun insert(record: ActionRecord): Long
    fun update(record: ActionRecord)
    fun get(id: Long): ActionRecord?
    fun byStatus(status: ActionStatus): List<ActionRecord>
    fun all(): List<ActionRecord>
}

enum class ApkStatus { INSTALLED_SAME_OR_NEWER, NOT_INSTALLED_OR_OLDER, UNREADABLE }

interface ApkInspector {
    fun inspect(path: String): ApkStatus
}
```

`core/src/main/kotlin/com/smartspacecleaner/core/text/Text.kt`:
```kotlin
package com.smartspacecleaner.core.text

import java.util.Locale
import kotlin.math.roundToLong

private const val KB = 1024.0
private const val MB = KB * 1024
private const val GB = MB * 1024

fun formatBytes(bytes: Long): String = when {
    bytes >= GB -> String.format(Locale.ROOT, "%.1f", bytes / GB).replace('.', ',') + " ГБ"
    bytes >= MB -> "${(bytes / MB).roundToLong()} МБ"
    bytes >= KB -> "${(bytes / KB).roundToLong()} КБ"
    else -> "$bytes Б"
}

fun plural(n: Int, one: String, few: String, many: String): String {
    val mod100 = n % 100
    val mod10 = n % 10
    return when {
        mod100 in 11..14 -> many
        mod10 == 1 -> one
        mod10 in 2..4 -> few
        else -> many
    }
}
```

`core/src/test/kotlin/com/smartspacecleaner/core/fakes/Fakes.kt`:
```kotlin
package com.smartspacecleaner.core.fakes

import com.smartspacecleaner.core.model.ActionRecord
import com.smartspacecleaner.core.model.ActionStatus
import com.smartspacecleaner.core.model.FileEntry
import com.smartspacecleaner.core.port.ActionLogStore
import com.smartspacecleaner.core.port.ApkInspector
import com.smartspacecleaner.core.port.ApkStatus
import com.smartspacecleaner.core.port.Clock
import com.smartspacecleaner.core.port.FileSystem
import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.InputStream
import java.time.Instant

const val ROOT = "/storage/emulated/0"
fun p(relative: String) = "$ROOT/$relative"
val T0: Instant = Instant.parse("2026-10-01T00:00:00Z")

/** Детерминированное содержимое: одинаковые (size, seed) дают одинаковые байты. */
fun bytes(size: Int, seed: Int): ByteArray = ByteArray(size) { ((it * 31 + seed) % 251).toByte() }

class FakeFileSystem(var free: Long = 10L * 1024 * 1024 * 1024) : FileSystem {
    private class FakeFile(val content: ByteArray, val modifiedAt: Instant)

    private val files = linkedMapOf<String, FakeFile>()
    /** move/copy/delete с этими путями (как источником) возвращают false. */
    val failingPaths = mutableSetOf<String>()
    /** openRead для этих путей бросает IOException. */
    val unreadablePaths = mutableSetOf<String>()

    fun add(path: String, content: ByteArray, modifiedAt: Instant = T0) {
        files[path] = FakeFile(content, modifiedAt)
    }

    fun write(path: String, content: ByteArray) {
        files[path] = FakeFile(content, files[path]?.modifiedAt ?: T0)
    }

    fun content(path: String): ByteArray = files.getValue(path).content

    override fun walk(root: String, excludedPrefixes: Set<String>): Sequence<FileEntry> =
        files.entries
            .filter { (path, _) -> path.startsWith("$root/") && excludedPrefixes.none { path == it || path.startsWith("$it/") } }
            .map { (path, f) -> FileEntry(path, f.content.size.toLong(), f.modifiedAt) }
            .asSequence()

    override fun exists(path: String) = path in files

    override fun openRead(path: String): InputStream {
        if (path in unreadablePaths) throw IOException("permission denied: $path")
        val f = files[path] ?: throw IOException("no such file: $path")
        return ByteArrayInputStream(f.content)
    }

    override fun move(from: String, to: String): Boolean {
        if (from in failingPaths || from !in files || to in files) return false
        files[to] = files.remove(from)!!
        return true
    }

    override fun copy(from: String, to: String): Boolean {
        if (from in failingPaths || from !in files || to in files) return false
        val f = files.getValue(from)
        files[to] = FakeFile(f.content.copyOf(), f.modifiedAt)
        return true
    }

    override fun delete(path: String): Boolean {
        if (path in failingPaths) return false
        return files.remove(path) != null
    }

    override fun mkdirs(path: String) = true

    override fun createEmptyFile(path: String): Boolean {
        if (path !in files) add(path, ByteArray(0))
        return true
    }

    override fun freeBytes() = free
}

class FakeClock(var current: Instant = T0) : Clock {
    override fun now(): Instant = current
}

class InMemoryActionLogStore : ActionLogStore {
    private val records = linkedMapOf<Long, ActionRecord>()
    private var nextId = 1L

    override fun insert(record: ActionRecord): Long {
        val id = nextId++
        records[id] = record.copy(id = id)
        return id
    }

    override fun update(record: ActionRecord) {
        require(record.id in records) { "unknown record ${record.id}" }
        records[record.id] = record
    }

    override fun get(id: Long) = records[id]
    override fun byStatus(status: ActionStatus) = records.values.filter { it.status == status }
    override fun all() = records.values.toList()
}

class FakeApkInspector(private val statuses: Map<String, ApkStatus> = emptyMap()) : ApkInspector {
    override fun inspect(path: String) = statuses[path] ?: ApkStatus.UNREADABLE
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew :core:test`
Expected: `BUILD SUCCESSFUL`, 4 теста пройдены.

- [ ] **Step 5: Commit**

```bash
git add core/src
git commit -m "feat(core): add model, ports, text helpers and test fakes" -m "Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 3: Хэши и поиск точных дублей

**Files:**
- Create: `core/src/main/kotlin/com/smartspacecleaner/core/scan/Hasher.kt`
- Create: `core/src/main/kotlin/com/smartspacecleaner/core/scan/DuplicateFinder.kt`
- Test: `core/src/test/kotlin/com/smartspacecleaner/core/scan/HasherTest.kt`, `core/src/test/kotlin/com/smartspacecleaner/core/scan/DuplicateFinderTest.kt`

**Interfaces:**
- Consumes: `FileSystem`, `FileEntry` (Task 2)
- Produces:
  - `class Hasher(fs: FileSystem)` с `partial(entry: FileEntry): String` и `full(path: String): String` (hex SHA-256 в нижнем регистре; бросают `IOException`)
  - `data class DuplicateSet(sha256: String, size: Long, files: List<FileEntry>)` (файлы отсортированы по пути)
  - `class DuplicateFinder(hasher: Hasher, minSize: Long = 1024)` с `find(entries: List<FileEntry>): List<DuplicateSet>` (сортировка по «лишнему» объёму, по убыванию)

- [ ] **Step 1: Write the failing tests**

`core/src/test/kotlin/com/smartspacecleaner/core/scan/HasherTest.kt`:
```kotlin
package com.smartspacecleaner.core.scan

import com.smartspacecleaner.core.fakes.FakeFileSystem
import com.smartspacecleaner.core.fakes.T0
import com.smartspacecleaner.core.fakes.bytes
import com.smartspacecleaner.core.fakes.p
import com.smartspacecleaner.core.model.FileEntry
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Test

class HasherTest {
    private val fs = FakeFileSystem()
    private val hasher = Hasher(fs)

    @Test
    fun `full hash is sha256 hex`() {
        fs.add(p("a.txt"), "abc".toByteArray())
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", hasher.full(p("a.txt")))
    }

    @Test
    fun `partial hash ignores the middle of large files but full hash does not`() {
        val a = bytes(200 * 1024, 1)
        val b = a.copyOf().also { it[100 * 1024] = (it[100 * 1024] + 1).toByte() }
        fs.add(p("a.bin"), a)
        fs.add(p("b.bin"), b)
        val ea = FileEntry(p("a.bin"), a.size.toLong(), T0)
        val eb = FileEntry(p("b.bin"), b.size.toLong(), T0)
        assertEquals(hasher.partial(ea), hasher.partial(eb))
        assertNotEquals(hasher.full(p("a.bin")), hasher.full(p("b.bin")))
    }

    @Test
    fun `partial hash of small file covers whole content`() {
        fs.add(p("a.bin"), bytes(5000, 1))
        fs.add(p("b.bin"), bytes(5000, 2))
        assertNotEquals(
            hasher.partial(FileEntry(p("a.bin"), 5000, T0)),
            hasher.partial(FileEntry(p("b.bin"), 5000, T0)),
        )
    }
}
```

`core/src/test/kotlin/com/smartspacecleaner/core/scan/DuplicateFinderTest.kt`:
```kotlin
package com.smartspacecleaner.core.scan

import com.smartspacecleaner.core.fakes.FakeFileSystem
import com.smartspacecleaner.core.fakes.ROOT
import com.smartspacecleaner.core.fakes.bytes
import com.smartspacecleaner.core.fakes.p
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class DuplicateFinderTest {
    private val fs = FakeFileSystem()
    private val finder = DuplicateFinder(Hasher(fs))
    private fun entries() = fs.walk(ROOT, emptySet()).toList()

    @Test
    fun `finds identical files in different folders`() {
        fs.add(p("DCIM/Camera/a.jpg"), bytes(4096, 1))
        fs.add(p("redmi6/a.jpg"), bytes(4096, 1))
        val sets = finder.find(entries())
        assertEquals(1, sets.size)
        assertEquals(listOf(p("DCIM/Camera/a.jpg"), p("redmi6/a.jpg")), sets[0].files.map { it.path })
        assertEquals(4096L, sets[0].size)
    }

    @Test
    fun `same size with different content is not a duplicate`() {
        fs.add(p("a.bin"), bytes(4096, 1))
        fs.add(p("b.bin"), bytes(4096, 2))
        assertTrue(finder.find(entries()).isEmpty())
    }

    @Test
    fun `ignores files smaller than 1 KB`() {
        fs.add(p("a.txt"), bytes(1000, 1))
        fs.add(p("b.txt"), bytes(1000, 1))
        assertTrue(finder.find(entries()).isEmpty())
    }

    @Test
    fun `groups three copies into one set`() {
        listOf("a", "b", "c").forEach { fs.add(p("$it/x.jpg"), bytes(2048, 7)) }
        assertEquals(3, finder.find(entries()).single().files.size)
    }

    @Test
    fun `files that differ only in the middle are not duplicates`() {
        val a = bytes(200 * 1024, 1)
        fs.add(p("a.bin"), a)
        fs.add(p("b.bin"), a.copyOf().also { it[100 * 1024] = (it[100 * 1024] + 1).toByte() })
        assertTrue(finder.find(entries()).isEmpty())
    }

    @Test
    fun `skips unreadable files`() {
        fs.add(p("a.bin"), bytes(4096, 1))
        fs.add(p("b.bin"), bytes(4096, 1))
        fs.add(p("c.bin"), bytes(4096, 1))
        fs.unreadablePaths += p("c.bin")
        assertEquals(listOf(p("a.bin"), p("b.bin")), finder.find(entries()).single().files.map { it.path })
    }

    @Test
    fun `largest waste comes first`() {
        fs.add(p("s1"), bytes(2048, 1)); fs.add(p("s2"), bytes(2048, 1))
        fs.add(p("b1"), bytes(8192, 2)); fs.add(p("b2"), bytes(8192, 2))
        assertEquals(listOf(8192L, 2048L), finder.find(entries()).map { it.size })
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew :core:test --tests "com.smartspacecleaner.core.scan.*"`
Expected: FAIL — `Unresolved reference: Hasher`, `DuplicateFinder`.

- [ ] **Step 3: Write the implementation**

`core/src/main/kotlin/com/smartspacecleaner/core/scan/Hasher.kt`:
```kotlin
package com.smartspacecleaner.core.scan

import com.smartspacecleaner.core.model.FileEntry
import com.smartspacecleaner.core.port.FileSystem
import java.io.IOException
import java.io.InputStream
import java.security.MessageDigest

private const val CHUNK = 64 * 1024

class Hasher(private val fs: FileSystem) {
    /** SHA-256 первых и последних 64 КБ (или всего файла, если он не больше 128 КБ). */
    fun partial(entry: FileEntry): String {
        val md = MessageDigest.getInstance("SHA-256")
        fs.openRead(entry.path).use { input ->
            if (entry.size <= 2L * CHUNK) {
                md.update(input.readBytes())
            } else {
                md.update(input.readExactly(CHUNK))
                input.skipExactly(entry.size - 2L * CHUNK)
                md.update(input.readExactly(CHUNK))
            }
        }
        return md.digest().toHex()
    }

    fun full(path: String): String {
        val md = MessageDigest.getInstance("SHA-256")
        fs.openRead(path).use { input ->
            val buf = ByteArray(CHUNK)
            while (true) {
                val read = input.read(buf)
                if (read < 0) break
                md.update(buf, 0, read)
            }
        }
        return md.digest().toHex()
    }
}

// InputStream.readNBytes/skipNBytes появились в Android только в API 33, поэтому свои версии.
private fun InputStream.readExactly(n: Int): ByteArray {
    val buf = ByteArray(n)
    var offset = 0
    while (offset < n) {
        val read = read(buf, offset, n - offset)
        if (read < 0) throw IOException("unexpected end of file")
        offset += read
    }
    return buf
}

private fun InputStream.skipExactly(n: Long) {
    var left = n
    while (left > 0) {
        val skipped = skip(left)
        if (skipped > 0) {
            left -= skipped
        } else {
            if (read() < 0) throw IOException("unexpected end of file")
            left--
        }
    }
}

private fun ByteArray.toHex(): String = joinToString("") { (it.toInt() and 0xff).toString(16).padStart(2, '0') }
```

`core/src/main/kotlin/com/smartspacecleaner/core/scan/DuplicateFinder.kt`:
```kotlin
package com.smartspacecleaner.core.scan

import com.smartspacecleaner.core.model.FileEntry
import java.io.IOException

data class DuplicateSet(val sha256: String, val size: Long, val files: List<FileEntry>)

class DuplicateFinder(private val hasher: Hasher, private val minSize: Long = 1024) {
    fun find(entries: List<FileEntry>): List<DuplicateSet> {
        val result = mutableListOf<DuplicateSet>()
        entries.filter { it.size >= minSize }
            .groupBy { it.size }.values
            .filter { it.size > 1 }
            .forEach { sameSize ->
                sameSize.groupBy { orNull { hasher.partial(it) } }
                    .filterKeys { it != null }.values
                    .filter { it.size > 1 }
                    .forEach { samePartial ->
                        samePartial.groupBy { orNull { hasher.full(it.path) } }
                            .forEach { (hash, files) ->
                                if (hash != null && files.size > 1) {
                                    result += DuplicateSet(hash, files.first().size, files.sortedBy { it.path })
                                }
                            }
                    }
            }
        return result.sortedByDescending { it.size * (it.files.size - 1) }
    }

    private fun orNull(block: () -> String): String? =
        try { block() } catch (e: IOException) { null } catch (e: SecurityException) { null }
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew :core:test --tests "com.smartspacecleaner.core.scan.*"`
Expected: PASS, 10 тестов.

- [ ] **Step 5: Commit**

```bash
git add core/src
git commit -m "feat(core): find exact duplicates via size, partial and full SHA-256" -m "Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 4: Знание о папках и выбор оставляемой копии

**Files:**
- Create: `core/src/main/kotlin/com/smartspacecleaner/core/scan/StorageLayout.kt`
- Create: `core/src/main/kotlin/com/smartspacecleaner/core/scan/KeepCopyPolicy.kt`
- Test: `core/src/test/kotlin/com/smartspacecleaner/core/scan/StorageLayoutTest.kt`, `core/src/test/kotlin/com/smartspacecleaner/core/scan/KeepCopyPolicyTest.kt`

**Interfaces:**
- Produces:
  - `enum class Messenger(title: String, dirs: List<String>) { TELEGRAM, WHATSAPP }`
  - `class StorageLayout(root: String)` с `relative(path)`, `isUnder(path, dir)`, `isPrimaryMedia(path)`, `isDownload(path)`, `messengerOf(path): Messenger?`, `isWorkFolder(path)`, `parentRelative(path)`, `trashRoot`, `excludedPrefixes`
  - `class KeepCopyPolicy(layout: StorageLayout)` с `chooseKeeper(paths: List<String>): String`; `KeepCopyPolicy.hasCopySuffix(path): Boolean`

- [ ] **Step 1: Write the failing tests**

`core/src/test/kotlin/com/smartspacecleaner/core/scan/StorageLayoutTest.kt`:
```kotlin
package com.smartspacecleaner.core.scan

import com.smartspacecleaner.core.fakes.ROOT
import com.smartspacecleaner.core.fakes.p
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class StorageLayoutTest {
    private val layout = StorageLayout(ROOT)

    @Test
    fun `knows primary media, downloads and messengers`() {
        assertTrue(layout.isPrimaryMedia(p("DCIM/Camera/a.jpg")))
        assertTrue(layout.isPrimaryMedia(p("Pictures/Album/a.jpg")))
        assertFalse(layout.isPrimaryMedia(p("DCIM/Screenshots/a.png")))
        assertTrue(layout.isDownload(p("Download/a.pdf")))
        assertEquals(Messenger.TELEGRAM, layout.messengerOf(p("Telegram/Telegram Images/a.jpg")))
        assertEquals(Messenger.TELEGRAM, layout.messengerOf(p("Android/media/org.telegram.messenger/Telegram/a.jpg")))
        assertEquals(Messenger.WHATSAPP, layout.messengerOf(p("Android/media/com.whatsapp/WhatsApp/Media/a.jpg")))
        assertNull(layout.messengerOf(p("DCIM/Camera/a.jpg")))
    }

    @Test
    fun `folder checks ignore case`() {
        assertTrue(layout.isPrimaryMedia(p("dcim/camera/a.jpg")))
        assertTrue(layout.isDownload(p("download/a.pdf")))
        assertTrue(layout.isUnder(p("dcim/screenshots/a.png"), "DCIM/Screenshots"))
    }

    @Test
    fun `isUnder does not match folder name prefixes`() {
        assertFalse(layout.isUnder(p("Downloads2/a.pdf"), "Download"))
    }

    @Test
    fun `work folder is anything outside camera, pictures, downloads and messengers`() {
        assertTrue(layout.isWorkFolder(p("Documents/project/a.psd")))
        assertTrue(layout.isWorkFolder(p("redmi6/a.jpg")))
        assertFalse(layout.isWorkFolder(p("DCIM/Screenshots/a.png")))
        assertFalse(layout.isWorkFolder(p("Download/a.pdf")))
        assertFalse(layout.isWorkFolder(p("Telegram/a.jpg")))
    }

    @Test
    fun `computes parent folder relative to root and service paths`() {
        assertEquals("DCIM/Camera", layout.parentRelative(p("DCIM/Camera/a.jpg")))
        assertEquals("", layout.parentRelative(p("a.jpg")))
        assertEquals(p(".SmartSpaceCleaner/trash"), layout.trashRoot)
        assertEquals(setOf(p("Android/data"), p("Android/obb"), p(".SmartSpaceCleaner")), layout.excludedPrefixes)
    }
}
```

`core/src/test/kotlin/com/smartspacecleaner/core/scan/KeepCopyPolicyTest.kt`:
```kotlin
package com.smartspacecleaner.core.scan

import com.smartspacecleaner.core.fakes.ROOT
import com.smartspacecleaner.core.fakes.p
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class KeepCopyPolicyTest {
    private val policy = KeepCopyPolicy(StorageLayout(ROOT))

    @Test
    fun `camera copy beats random folder and downloads`() {
        assertEquals(
            p("DCIM/Camera/IMG_1.jpg"),
            policy.chooseKeeper(listOf(p("Download/IMG_1.jpg"), p("redmi6/IMG_1.jpg"), p("DCIM/Camera/IMG_1.jpg"))),
        )
    }

    @Test
    fun `random folder beats downloads and messengers`() {
        assertEquals(
            p("redmi6/IMG_1.jpg"),
            policy.chooseKeeper(listOf(p("Download/IMG_1.jpg"), p("Telegram/IMG_1.jpg"), p("redmi6/IMG_1.jpg"))),
        )
    }

    @Test
    fun `name without copy suffix wins inside the same folder rank`() {
        assertEquals(p("redmi6/IMG_1.jpg"), policy.chooseKeeper(listOf(p("redmi6/IMG_1(1).jpg"), p("redmi6/IMG_1.jpg"))))
    }

    @Test
    fun `shorter path wins, then alphabetical order`() {
        assertEquals(p("a/x.jpg"), policy.chooseKeeper(listOf(p("a/b/c/x.jpg"), p("a/x.jpg"))))
        assertEquals(p("a/x.jpg"), policy.chooseKeeper(listOf(p("b/x.jpg"), p("a/x.jpg"))))
    }

    @Test
    fun `recognises copy suffixes`() {
        assertTrue(KeepCopyPolicy.hasCopySuffix(p("a/IMG_1(1).jpg")))
        assertTrue(KeepCopyPolicy.hasCopySuffix(p("a/IMG_1 (0).jpg")))
        assertTrue(KeepCopyPolicy.hasCopySuffix(p("a/report copy.pdf")))
        assertTrue(KeepCopyPolicy.hasCopySuffix(p("a/report_Copy.pdf")))
        assertTrue(KeepCopyPolicy.hasCopySuffix(p("a/Отчёт - копия.docx")))
        assertFalse(KeepCopyPolicy.hasCopySuffix(p("a/scopy.jpg")))
        assertFalse(KeepCopyPolicy.hasCopySuffix(p("a/IMG_1.jpg")))
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew :core:test --tests "com.smartspacecleaner.core.scan.*"`
Expected: FAIL — `Unresolved reference: StorageLayout`, `KeepCopyPolicy`.

- [ ] **Step 3: Write the implementation**

`core/src/main/kotlin/com/smartspacecleaner/core/scan/StorageLayout.kt`:
```kotlin
package com.smartspacecleaner.core.scan

enum class Messenger(val title: String, val dirs: List<String>) {
    TELEGRAM("Telegram", listOf("Android/media/org.telegram.messenger", "Telegram")),
    WHATSAPP("WhatsApp", listOf("Android/media/com.whatsapp")),
}

class StorageLayout(val root: String) {
    val trashRoot: String get() = "$root/.SmartSpaceCleaner/trash"
    val excludedPrefixes: Set<String> get() = setOf("$root/Android/data", "$root/Android/obb", "$root/.SmartSpaceCleaner")

    fun relative(path: String): String = path.removePrefix("$root/")

    fun isUnder(path: String, dir: String): Boolean {
        val rel = relative(path)
        return rel.equals(dir, ignoreCase = true) || rel.startsWith("$dir/", ignoreCase = true)
    }

    fun isPrimaryMedia(path: String) = isUnder(path, "DCIM/Camera") || isUnder(path, "Pictures")
    fun isDownload(path: String) = isUnder(path, "Download")
    fun messengerOf(path: String): Messenger? = Messenger.entries.firstOrNull { m -> m.dirs.any { isUnder(path, it) } }

    /** Папка, где копии могут лежать намеренно: всё, кроме камеры/картинок, загрузок и мессенджеров. */
    fun isWorkFolder(path: String) =
        !isUnder(path, "DCIM") && !isUnder(path, "Pictures") && !isDownload(path) && messengerOf(path) == null

    fun parentRelative(path: String): String = relative(path).substringBeforeLast('/', "")
}
```

`core/src/main/kotlin/com/smartspacecleaner/core/scan/KeepCopyPolicy.kt`:
```kotlin
package com.smartspacecleaner.core.scan

class KeepCopyPolicy(private val layout: StorageLayout) {
    fun chooseKeeper(paths: List<String>): String = paths.minWith(
        compareBy<String>({ folderRank(it) }, { if (hasCopySuffix(it)) 1 else 0 }, { it.length }, { it }),
    )

    private fun folderRank(path: String): Int = when {
        layout.isPrimaryMedia(path) -> 0
        layout.isDownload(path) || layout.messengerOf(path) != null -> 2
        else -> 1
    }

    companion object {
        private val COPY_SUFFIX = Regex("""(\s?\(\d+\)|[\s_-]copy|\s-\sкопия)$""", RegexOption.IGNORE_CASE)

        fun hasCopySuffix(path: String): Boolean {
            val base = path.substringAfterLast('/').substringBeforeLast('.')
            return COPY_SUFFIX.containsMatchIn(base)
        }
    }
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew :core:test --tests "com.smartspacecleaner.core.scan.*"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add core/src
git commit -m "feat(core): add storage layout knowledge and keep-copy policy" -m "Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 5: Правила: установщики, скриншоты, крупные файлы, мессенджеры

**Files:**
- Create: `core/src/main/kotlin/com/smartspacecleaner/core/scan/Rules.kt`
- Test: `core/src/test/kotlin/com/smartspacecleaner/core/scan/RulesTest.kt`

**Interfaces:**
- Consumes: `StorageLayout`, `Messenger` (Task 4), `ApkInspector`, `ApkStatus`, `Clock` (Task 2)
- Produces:
  - `class ApkRule(inspector: ApkInspector)` → `classify(entries): ApkRule.Result(installed: List<FileEntry>, unknown: List<FileEntry>)`
  - `class OldScreenshotRule(layout, clock, maxAgeDays: Long = 180)` → `find(entries): List<FileEntry>`; свойство `maxAgeDays`
  - `enum class LargeKind(title) { VIDEO, ARCHIVE, OTHER }`; `class LargeFileRule(minSizeBytes: Long = 100 МБ)` → `find(entries): Map<LargeKind, List<FileEntry>>`; свойство `minSizeBytes`
  - `class MessengerMediaRule(layout, clock, maxAgeDays: Long = 90)` → `find(entries): Map<Messenger, List<FileEntry>>`; свойство `maxAgeDays`

- [ ] **Step 1: Write the failing tests**

`core/src/test/kotlin/com/smartspacecleaner/core/scan/RulesTest.kt`:
```kotlin
package com.smartspacecleaner.core.scan

import com.smartspacecleaner.core.fakes.FakeApkInspector
import com.smartspacecleaner.core.fakes.FakeClock
import com.smartspacecleaner.core.fakes.ROOT
import com.smartspacecleaner.core.fakes.T0
import com.smartspacecleaner.core.fakes.p
import com.smartspacecleaner.core.model.FileEntry
import com.smartspacecleaner.core.port.ApkStatus
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Duration

class RulesTest {
    private val layout = StorageLayout(ROOT)
    private val clock = FakeClock(T0)
    private fun daysAgo(days: Long) = T0.minus(Duration.ofDays(days))
    private fun entry(rel: String, size: Long = 2048, days: Long = 0) = FileEntry(p(rel), size, daysAgo(days))

    @Test
    fun `apk of installed app is separated from unknown apk`() {
        val rule = ApkRule(
            FakeApkInspector(
                mapOf(
                    p("Download/app.apk") to ApkStatus.INSTALLED_SAME_OR_NEWER,
                    p("Download/old.apk") to ApkStatus.NOT_INSTALLED_OR_OLDER,
                ),
            ),
        )
        val result = rule.classify(
            listOf(entry("Download/app.apk"), entry("Download/old.apk"), entry("Download/broken.APK"), entry("Download/a.pdf")),
        )
        assertEquals(listOf(p("Download/app.apk")), result.installed.map { it.path })
        assertEquals(listOf(p("Download/old.apk"), p("Download/broken.APK")), result.unknown.map { it.path })
    }

    @Test
    fun `old screenshots are strictly older than 180 days`() {
        val rule = OldScreenshotRule(layout, clock)
        val found = rule.find(
            listOf(
                entry("DCIM/Screenshots/old.png", days = 181),
                entry("Pictures/Screenshots/old2.png", days = 400),
                entry("DCIM/Screenshots/edge.png", days = 180),
                entry("DCIM/Camera/old.jpg", days = 400),
            ),
        )
        assertEquals(listOf(p("DCIM/Screenshots/old.png"), p("Pictures/Screenshots/old2.png")), found.map { it.path })
        assertEquals(180L, rule.maxAgeDays)
    }

    @Test
    fun `old screenshot rule ignores folder case`() {
        val found = OldScreenshotRule(layout, clock).find(listOf(entry("dcim/screenshots/old.png", days = 200)))
        assertEquals(1, found.size)
    }

    @Test
    fun `large files start at exactly 100 MB and are split by kind`() {
        val mb100 = 100L * 1024 * 1024
        val found = LargeFileRule().find(
            listOf(
                entry("Movies/a.MP4", size = mb100),
                entry("Download/b.zip", size = mb100 * 2),
                entry("Download/c.iso", size = mb100 + 1),
                entry("Movies/small.mp4", size = mb100 - 1),
            ),
        )
        assertEquals(listOf(p("Movies/a.MP4")), found[LargeKind.VIDEO]!!.map { it.path })
        assertEquals(listOf(p("Download/b.zip")), found[LargeKind.ARCHIVE]!!.map { it.path })
        assertEquals(listOf(p("Download/c.iso")), found[LargeKind.OTHER]!!.map { it.path })
    }

    @Test
    fun `messenger media older than 90 days is grouped by messenger`() {
        val found = MessengerMediaRule(layout, clock).find(
            listOf(
                entry("Telegram/Telegram Images/a.jpg", days = 100),
                entry("Android/media/com.whatsapp/WhatsApp/Media/b.jpg", days = 91),
                entry("Android/media/com.whatsapp/WhatsApp/Media/fresh.jpg", days = 89),
                entry("DCIM/Camera/c.jpg", days = 500),
            ),
        )
        assertEquals(listOf(p("Telegram/Telegram Images/a.jpg")), found[Messenger.TELEGRAM]!!.map { it.path })
        assertEquals(listOf(p("Android/media/com.whatsapp/WhatsApp/Media/b.jpg")), found[Messenger.WHATSAPP]!!.map { it.path })
        assertTrue(found.values.flatten().none { it.path.endsWith("fresh.jpg") || it.path.endsWith("c.jpg") })
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew :core:test --tests "com.smartspacecleaner.core.scan.RulesTest"`
Expected: FAIL — `Unresolved reference: ApkRule`.

- [ ] **Step 3: Write the implementation**

`core/src/main/kotlin/com/smartspacecleaner/core/scan/Rules.kt`:
```kotlin
package com.smartspacecleaner.core.scan

import com.smartspacecleaner.core.model.FileEntry
import com.smartspacecleaner.core.port.ApkInspector
import com.smartspacecleaner.core.port.ApkStatus
import com.smartspacecleaner.core.port.Clock
import java.time.Duration

class ApkRule(private val inspector: ApkInspector) {
    data class Result(val installed: List<FileEntry>, val unknown: List<FileEntry>)

    fun classify(entries: List<FileEntry>): Result {
        val (installed, unknown) = entries
            .filter { it.extension == "apk" }
            .partition { inspector.inspect(it.path) == ApkStatus.INSTALLED_SAME_OR_NEWER }
        return Result(installed, unknown)
    }
}

class OldScreenshotRule(
    private val layout: StorageLayout,
    private val clock: Clock,
    val maxAgeDays: Long = 180,
) {
    fun find(entries: List<FileEntry>): List<FileEntry> {
        val cutoff = clock.now().minus(Duration.ofDays(maxAgeDays))
        return entries.filter {
            (layout.isUnder(it.path, "DCIM/Screenshots") || layout.isUnder(it.path, "Pictures/Screenshots")) &&
                it.modifiedAt.isBefore(cutoff)
        }
    }
}

enum class LargeKind(val title: String) {
    VIDEO("Крупные видео"),
    ARCHIVE("Крупные архивы"),
    OTHER("Другие крупные файлы"),
}

class LargeFileRule(val minSizeBytes: Long = 100L * 1024 * 1024) {
    fun find(entries: List<FileEntry>): Map<LargeKind, List<FileEntry>> =
        entries.filter { it.size >= minSizeBytes }.groupBy { kindOf(it) }

    private fun kindOf(entry: FileEntry): LargeKind = when (entry.extension) {
        in VIDEO_EXTENSIONS -> LargeKind.VIDEO
        in ARCHIVE_EXTENSIONS -> LargeKind.ARCHIVE
        else -> LargeKind.OTHER
    }

    private companion object {
        val VIDEO_EXTENSIONS = setOf("mp4", "mkv", "mov", "avi", "3gp", "webm")
        val ARCHIVE_EXTENSIONS = setOf("zip", "rar", "7z", "tar", "gz")
    }
}

class MessengerMediaRule(
    private val layout: StorageLayout,
    private val clock: Clock,
    val maxAgeDays: Long = 90,
) {
    fun find(entries: List<FileEntry>): Map<Messenger, List<FileEntry>> {
        val cutoff = clock.now().minus(Duration.ofDays(maxAgeDays))
        return entries
            .filter { it.modifiedAt.isBefore(cutoff) }
            .mapNotNull { e -> layout.messengerOf(e.path)?.let { it to e } }
            .groupBy({ it.first }, { it.second })
    }
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew :core:test --tests "com.smartspacecleaner.core.scan.RulesTest"`
Expected: PASS, 5 тестов.

- [ ] **Step 5: Commit**

```bash
git add core/src
git commit -m "feat(core): add apk, screenshot, large file and messenger rules" -m "Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 6: Планировщик, сканер и отметки

**Files:**
- Create: `core/src/main/kotlin/com/smartspacecleaner/core/scan/Planner.kt`
- Create: `core/src/main/kotlin/com/smartspacecleaner/core/scan/Scanner.kt`
- Create: `core/src/main/kotlin/com/smartspacecleaner/core/model/Selection.kt`
- Test: `core/src/test/kotlin/com/smartspacecleaner/core/scan/PlannerTest.kt`, `core/src/test/kotlin/com/smartspacecleaner/core/scan/ScannerTest.kt`, `core/src/test/kotlin/com/smartspacecleaner/core/model/SelectionTest.kt`

**Interfaces:**
- Consumes: всё из Tasks 2–5
- Produces:
  - `class Planner(layout, keepPolicy, duplicateFinder, apkRule, screenshotRule, largeFileRule, messengerRule)` → `plan(entries: List<FileEntry>): Plan`
  - id групп: `"app-cache"`, `"dup:<папка>"`, `"apk-installed"`, `"apk-unknown"`, `"messenger:telegram"`, `"messenger:whatsapp"`, `"screenshots"`, `"large:video"`, `"large:archive"`, `"large:other"`
  - Каждый файл попадает максимум в одну группу (приоритет: дубли → установщики → мессенджеры → скриншоты → крупные). Группа «Кэш приложений» есть всегда, у неё нет файлов.
  - Группы отсортированы по уровню, внутри уровня — по объёму по убыванию.
  - `class Scanner(fs, layout, planner)` → `scan(onProgress: (filesSeen: Int) -> Unit = {}): Plan`
  - `enum class GroupCheck { ALL, SOME, NONE }`, `Group.checkState(selected: Set<String>)`, `Set<String>.toggleGroup(group)`, `Set<String>.toggleItem(path)`

- [ ] **Step 1: Write the failing tests**

`core/src/test/kotlin/com/smartspacecleaner/core/scan/PlannerTest.kt`:
```kotlin
package com.smartspacecleaner.core.scan

import com.smartspacecleaner.core.fakes.FakeApkInspector
import com.smartspacecleaner.core.fakes.FakeClock
import com.smartspacecleaner.core.fakes.FakeFileSystem
import com.smartspacecleaner.core.fakes.ROOT
import com.smartspacecleaner.core.fakes.T0
import com.smartspacecleaner.core.fakes.bytes
import com.smartspacecleaner.core.fakes.p
import com.smartspacecleaner.core.model.FileEntry
import com.smartspacecleaner.core.model.GroupKind
import com.smartspacecleaner.core.model.RiskLevel
import com.smartspacecleaner.core.port.ApkStatus
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Duration

class PlannerTest {
    private val fs = FakeFileSystem()
    private val clock = FakeClock(T0)
    private val layout = StorageLayout(ROOT)
    private var apkStatuses = mapOf<String, ApkStatus>()
    private fun planner() = Planner(
        layout,
        KeepCopyPolicy(layout),
        DuplicateFinder(Hasher(fs)),
        ApkRule(FakeApkInspector(apkStatuses)),
        OldScreenshotRule(layout, clock),
        LargeFileRule(),
        MessengerMediaRule(layout, clock),
    )
    private fun entries() = fs.walk(ROOT, layout.excludedPrefixes).toList()
    private fun daysAgo(days: Long) = T0.minus(Duration.ofDays(days))

    @Test
    fun `always offers app cache group first`() {
        val plan = planner().plan(emptyList())
        assertEquals(listOf("app-cache"), plan.groups.map { it.id })
        val cache = plan.groups.single()
        assertEquals(GroupKind.APP_CACHE, cache.kind)
        assertEquals(RiskLevel.SAFE, cache.level)
        assertTrue(cache.selectedByDefault)
        assertTrue(cache.items.isEmpty())
    }

    @Test
    fun `groups duplicate copies by folder and keeps camera original`() {
        fs.add(p("DCIM/Camera/IMG_1.jpg"), bytes(4096, 1))
        fs.add(p("redmi6/IMG_1.jpg"), bytes(4096, 1))
        fs.add(p("Download/IMG_1(1).jpg"), bytes(4096, 1))
        val plan = planner().plan(entries())

        val redmi = plan.groups.single { it.id == "dup:redmi6" }
        assertEquals(RiskLevel.EXACT, redmi.level)
        assertTrue(redmi.selectedByDefault)
        assertEquals(listOf(p("redmi6/IMG_1.jpg")), redmi.items.map { it.path })
        assertEquals(p("DCIM/Camera/IMG_1.jpg"), redmi.items[0].keptOriginalPath)
        assertEquals(64, redmi.items[0].sha256!!.length)
        assertEquals("Дубли в папке redmi6", redmi.title)
        assertEquals("1 копия из этой папки", redmi.willRemove)
        assertEquals("оригиналы в: DCIM/Camera", redmi.willKeep)

        assertTrue(plan.groups.any { it.id == "dup:Download" })
        assertTrue(plan.groups.flatMap { it.items }.none { it.path == p("DCIM/Camera/IMG_1.jpg") })
    }

    @Test
    fun `duplicates only inside work folders are not selected by default`() {
        fs.add(p("Documents/project-a/data.bin"), bytes(4096, 3))
        fs.add(p("Documents/project-b/data.bin"), bytes(4096, 3))
        val group = planner().plan(entries()).groups.single { it.kind == GroupKind.DUPLICATES }
        assertFalse(group.selectedByDefault)
        assertTrue(group.why.contains("рабочую"))
    }

    @Test
    fun `a file appears in at most one group`() {
        fs.add(p("DCIM/Camera/a.jpg"), bytes(4096, 2))
        fs.add(p("Telegram/Telegram Images/a.jpg"), bytes(4096, 2), daysAgo(200))
        val plan = planner().plan(entries())
        val all = plan.groups.flatMap { g -> g.items.map { it.path } }
        assertEquals(all.size, all.toSet().size)
        assertTrue(plan.groups.none { it.kind == GroupKind.MESSENGER_MEDIA })
        assertTrue(plan.groups.single { it.kind == GroupKind.DUPLICATES }.items.any { it.path == p("Telegram/Telegram Images/a.jpg") })
    }

    @Test
    fun `personal groups are never selected by default`() {
        fs.add(p("DCIM/Screenshots/old.png"), bytes(3000, 4), daysAgo(365))
        val big = FileEntry(p("Movies/big.mp4"), 200L * 1024 * 1024, T0)
        val plan = planner().plan(entries() + big)
        val personal = plan.groups.filter { it.level == RiskLevel.PERSONAL }
        assertEquals(setOf("screenshots", "large:video"), personal.map { it.id }.toSet())
        assertTrue(personal.none { it.selectedByDefault })
        assertTrue(plan.defaultSelection().none { it == p("DCIM/Screenshots/old.png") || it == big.path })
        assertEquals("Скриншоты старше 180 дней", personal.single { it.id == "screenshots" }.title)
        assertEquals("Крупные видео (от 100 МБ)", personal.single { it.id == "large:video" }.title)
    }

    @Test
    fun `installed apk is level 1 and unknown apk is level 2`() {
        fs.add(p("Download/app.apk"), bytes(3000, 5))
        fs.add(p("Download/other.apk"), bytes(3100, 6))
        apkStatuses = mapOf(p("Download/app.apk") to ApkStatus.INSTALLED_SAME_OR_NEWER)
        val plan = planner().plan(entries())
        val installed = plan.groups.single { it.id == "apk-installed" }
        val unknown = plan.groups.single { it.id == "apk-unknown" }
        assertEquals(RiskLevel.EXACT, installed.level)
        assertTrue(installed.selectedByDefault)
        assertEquals(RiskLevel.PERSONAL, unknown.level)
        assertFalse(unknown.selectedByDefault)
    }

    @Test
    fun `groups are ordered by level then by size`() {
        fs.add(p("DCIM/Camera/a.jpg"), bytes(4096, 1)); fs.add(p("x/a.jpg"), bytes(4096, 1))
        fs.add(p("DCIM/Camera/b.jpg"), bytes(8192, 2)); fs.add(p("y/b.jpg"), bytes(8192, 2))
        fs.add(p("DCIM/Screenshots/old.png"), bytes(3000, 4), daysAgo(365))
        val plan = planner().plan(entries())
        assertEquals(listOf("app-cache", "dup:y", "dup:x", "screenshots"), plan.groups.map { it.id })
        assertEquals(entries().size, plan.scannedFiles)
    }
}
```

`core/src/test/kotlin/com/smartspacecleaner/core/scan/ScannerTest.kt`:
```kotlin
package com.smartspacecleaner.core.scan

import com.smartspacecleaner.core.fakes.FakeApkInspector
import com.smartspacecleaner.core.fakes.FakeClock
import com.smartspacecleaner.core.fakes.FakeFileSystem
import com.smartspacecleaner.core.fakes.ROOT
import com.smartspacecleaner.core.fakes.bytes
import com.smartspacecleaner.core.fakes.p
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ScannerTest {
    @Test
    fun `scans storage except excluded folders and reports progress`() {
        val fs = FakeFileSystem()
        val layout = StorageLayout(ROOT)
        val clock = FakeClock()
        fs.add(p("DCIM/Camera/a.jpg"), bytes(4096, 1))
        fs.add(p("Download/a.jpg"), bytes(4096, 1))
        fs.add(p("Android/data/com.x/cache.bin"), bytes(4096, 1))
        fs.add(p(".SmartSpaceCleaner/trash/t1"), bytes(4096, 1))
        val planner = Planner(
            layout, KeepCopyPolicy(layout), DuplicateFinder(Hasher(fs)), ApkRule(FakeApkInspector()),
            OldScreenshotRule(layout, clock), LargeFileRule(), MessengerMediaRule(layout, clock),
        )
        val progress = mutableListOf<Int>()

        val plan = Scanner(fs, layout, planner).scan { progress += it }

        assertEquals(2, plan.scannedFiles)
        assertEquals(2, progress.last())
        val dup = plan.groups.single { it.id == "dup:Download" }
        assertEquals(1, dup.items.size)
        assertTrue(plan.groups.flatMap { it.items }.none { it.path.contains("Android/data") || it.path.contains(".SmartSpaceCleaner") })
    }
}
```

`core/src/test/kotlin/com/smartspacecleaner/core/model/SelectionTest.kt`:
```kotlin
package com.smartspacecleaner.core.model

import com.smartspacecleaner.core.fakes.T0
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class SelectionTest {
    private val group = Group(
        "g", GroupKind.DUPLICATES, RiskLevel.EXACT, "", "", "", "",
        listOf(PlannedItem("a", 1, T0), PlannedItem("b", 1, T0)), true,
    )

    @Test
    fun `group check state follows its items`() {
        assertEquals(GroupCheck.NONE, group.checkState(emptySet()))
        assertEquals(GroupCheck.SOME, group.checkState(setOf("a")))
        assertEquals(GroupCheck.ALL, group.checkState(setOf("a", "b", "other")))
    }

    @Test
    fun `toggling a fully checked group clears it, otherwise checks all`() {
        assertEquals(setOf("other"), setOf("a", "b", "other").toggleGroup(group))
        assertEquals(setOf("a", "b"), setOf("a").toggleGroup(group))
    }

    @Test
    fun `toggling an item flips it`() {
        assertEquals(setOf("a"), emptySet<String>().toggleItem("a"))
        assertEquals(emptySet<String>(), setOf("a").toggleItem("a"))
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew :core:test`
Expected: FAIL — `Unresolved reference: Planner`, `Scanner`, `GroupCheck`.

- [ ] **Step 3: Write the implementation**

`core/src/main/kotlin/com/smartspacecleaner/core/model/Selection.kt`:
```kotlin
package com.smartspacecleaner.core.model

enum class GroupCheck { ALL, SOME, NONE }

fun Group.checkState(selected: Set<String>): GroupCheck {
    val checked = items.count { it.path in selected }
    return when {
        checked == 0 -> GroupCheck.NONE
        checked == items.size -> GroupCheck.ALL
        else -> GroupCheck.SOME
    }
}

fun Set<String>.toggleGroup(group: Group): Set<String> {
    val paths = group.items.map { it.path }.toSet()
    return if (group.checkState(this) == GroupCheck.ALL) this - paths else this + paths
}

fun Set<String>.toggleItem(path: String): Set<String> = if (path in this) this - path else this + path
```

`core/src/main/kotlin/com/smartspacecleaner/core/scan/Planner.kt`:
```kotlin
package com.smartspacecleaner.core.scan

import com.smartspacecleaner.core.model.FileEntry
import com.smartspacecleaner.core.model.Group
import com.smartspacecleaner.core.model.GroupKind
import com.smartspacecleaner.core.model.Plan
import com.smartspacecleaner.core.model.PlannedItem
import com.smartspacecleaner.core.model.RiskLevel
import com.smartspacecleaner.core.text.formatBytes
import com.smartspacecleaner.core.text.plural

private const val TRASH_NOTE = "7 дней файл лежит в корзине"
private const val ROOT_NAME = "корень памяти"

class Planner(
    private val layout: StorageLayout,
    private val keepPolicy: KeepCopyPolicy,
    private val duplicateFinder: DuplicateFinder,
    private val apkRule: ApkRule,
    private val screenshotRule: OldScreenshotRule,
    private val largeFileRule: LargeFileRule,
    private val messengerRule: MessengerMediaRule,
) {
    fun plan(entries: List<FileEntry>): Plan {
        val claimed = mutableSetOf<String>()
        val groups = mutableListOf(appCacheGroup())
        groups += duplicateGroups(duplicateFinder.find(entries), claimed)

        val apks = apkRule.classify(entries)
        groups += listOfNotNull(
            simpleGroup(
                "apk-installed", GroupKind.APK_INSTALLED, RiskLevel.EXACT,
                "Установщики установленных приложений",
                "сами приложения остаются установленными",
                "приложение уже установлено той же или более новой версии; установщик всегда можно скачать снова",
                apks.installed, claimed, selected = true,
            ),
            simpleGroup(
                "apk-unknown", GroupKind.APK_UNKNOWN, RiskLevel.PERSONAL,
                "Установщики неизвестных приложений",
                "всё, что не отметите",
                "приложение из этого файла не установлено — проверьте, нужен ли он; $TRASH_NOTE",
                apks.unknown, claimed, selected = false,
            ),
        )
        messengerRule.find(entries).forEach { (messenger, files) ->
            simpleGroup(
                "messenger:${messenger.name.lowercase()}", GroupKind.MESSENGER_MEDIA, RiskLevel.PERSONAL,
                "Медиа ${messenger.title} старше ${messengerRule.maxAgeDays} дней",
                "всё, что не отметите",
                "файлы, полученные в мессенджере; обычно их можно снова скачать из чата; $TRASH_NOTE",
                files, claimed, selected = false,
            )?.let { groups += it }
        }
        simpleGroup(
            "screenshots", GroupKind.OLD_SCREENSHOTS, RiskLevel.PERSONAL,
            "Скриншоты старше ${screenshotRule.maxAgeDays} дней",
            "всё, что не отметите",
            "старые скриншоты редко нужны; $TRASH_NOTE",
            screenshotRule.find(entries), claimed, selected = false,
        )?.let { groups += it }
        largeFileRule.find(entries).forEach { (kind, files) ->
            simpleGroup(
                "large:${kind.name.lowercase()}", GroupKind.LARGE_FILES, RiskLevel.PERSONAL,
                "${kind.title} (от ${formatBytes(largeFileRule.minSizeBytes)})",
                "всё, что не отметите",
                "крупные личные файлы — решайте поштучно; $TRASH_NOTE",
                files, claimed, selected = false,
            )?.let { groups += it }
        }

        return Plan(
            groups = groups.sortedWith(compareBy<Group> { it.level.number }.thenByDescending { it.totalBytes }),
            scannedFiles = entries.size,
            scannedBytes = entries.sumOf { it.size },
        )
    }

    private fun appCacheGroup() = Group(
        id = "app-cache",
        kind = GroupKind.APP_CACHE,
        level = RiskLevel.SAFE,
        title = "Кэш приложений",
        willRemove = "временные данные всех приложений — очищает сама система",
        willKeep = "приложения, их настройки и ваши данные",
        why = "кэш пересоздаётся автоматически; точный объём станет известен после очистки",
        items = emptyList(),
        selectedByDefault = true,
    )

    private class DupRemoval(val item: PlannedItem, val dir: String, val fromWorkSet: Boolean)

    private fun duplicateGroups(sets: List<DuplicateSet>, claimed: MutableSet<String>): List<Group> {
        val removals = sets.flatMap { set ->
            val keeper = keepPolicy.chooseKeeper(set.files.map { it.path })
            val workSet = set.files.all { layout.isWorkFolder(it.path) }
            set.files.filter { it.path != keeper }.map { f ->
                DupRemoval(
                    PlannedItem(f.path, f.size, f.modifiedAt, keptOriginalPath = keeper, sha256 = set.sha256),
                    layout.parentRelative(f.path),
                    workSet,
                )
            }
        }
        claimed += removals.map { it.item.path }
        return removals.groupBy { it.dir }.map { (dir, rs) ->
            val n = rs.size
            val keptDirs = rs.map { layout.parentRelative(it.item.keptOriginalPath!!).ifEmpty { ROOT_NAME } }.distinct()
            val keptText = keptDirs.take(2).joinToString(", ") + if (keptDirs.size > 2) " и др." else ""
            val anyWork = rs.any { it.fromWorkSet }
            Group(
                id = "dup:$dir",
                kind = GroupKind.DUPLICATES,
                level = RiskLevel.EXACT,
                title = "Дубли в папке ${dir.ifEmpty { ROOT_NAME }}",
                willRemove = "$n ${plural(n, "копия", "копии", "копий")} из этой папки",
                willKeep = "оригиналы в: $keptText",
                why = "содержимое совпадает побайтово (SHA-256); одна копия каждого файла всегда остаётся" +
                    if (anyWork) ". Папка похожа на рабочую — по умолчанию не отмечено" else "",
                items = rs.map { it.item }.sortedByDescending { it.size },
                selectedByDefault = !anyWork,
            )
        }
    }

    private fun simpleGroup(
        id: String,
        kind: GroupKind,
        level: RiskLevel,
        title: String,
        willKeep: String,
        why: String,
        files: List<FileEntry>,
        claimed: MutableSet<String>,
        selected: Boolean,
    ): Group? {
        val items = files.filter { it.path !in claimed }
            .sortedByDescending { it.size }
            .map { PlannedItem(it.path, it.size, it.modifiedAt) }
        if (items.isEmpty()) return null
        claimed += items.map { it.path }
        val n = items.size
        val count = "$n ${plural(n, "файл", "файла", "файлов")}"
        val willRemove = if (level == RiskLevel.PERSONAL) "$count — отмечаете поштучно" else count
        return Group(id, kind, level, title, willRemove, willKeep, why, items, selected)
    }
}
```

`core/src/main/kotlin/com/smartspacecleaner/core/scan/Scanner.kt`:
```kotlin
package com.smartspacecleaner.core.scan

import com.smartspacecleaner.core.model.FileEntry
import com.smartspacecleaner.core.model.Plan
import com.smartspacecleaner.core.port.FileSystem

class Scanner(
    private val fs: FileSystem,
    private val layout: StorageLayout,
    private val planner: Planner,
) {
    /** Только чтение: обходит память и строит план. */
    fun scan(onProgress: (filesSeen: Int) -> Unit = {}): Plan {
        val entries = ArrayList<FileEntry>()
        fs.walk(layout.root, layout.excludedPrefixes).forEach {
            entries += it
            if (entries.size % 500 == 0) onProgress(entries.size)
        }
        onProgress(entries.size)
        return planner.plan(entries)
    }
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew :core:test`
Expected: PASS, все тесты `:core`.

- [ ] **Step 5: Commit**

```bash
git add core/src
git commit -m "feat(core): build risk-level plan with explained groups and selection helpers" -m "Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 7: Исполнитель (тесты безопасности первыми)

**Files:**
- Create: `core/src/main/kotlin/com/smartspacecleaner/core/action/Executor.kt`
- Test: `core/src/test/kotlin/com/smartspacecleaner/core/action/ExecutorTest.kt`

**Interfaces:**
- Consumes: `Plan`, `Group`, `PlannedItem`, `ActionRecord`, порты (Task 2); `Hasher`, `StorageLayout` (Tasks 3–4)
- Produces:
  - `enum class DuplicateStrategy { TRASH, DELETE_WITH_RESTORE_COPY }`
  - `data class ExecutionResult(sessionId: String, records: List<ActionRecord>)` + `done`, `failed`
  - `class Executor(fs, log, clock, hasher, layout, duplicateStrategy = TRASH, trashRetention: Duration = 7 дней, newTrashName: () -> String = UUID)` → `execute(plan: Plan, selectedPaths: Set<String>, sessionId: String): ExecutionResult`
  - Порядок: уровень 1 раньше уровня 2; группа `APP_CACHE` игнорируется (её выполняет приложение).
  - Каждая запись создаётся в журнале как `PENDING` до действия и обновляется до `DONE` или `FAILED`.

- [ ] **Step 1: Write the failing tests**

`core/src/test/kotlin/com/smartspacecleaner/core/action/ExecutorTest.kt`:
```kotlin
package com.smartspacecleaner.core.action

import com.smartspacecleaner.core.fakes.FakeClock
import com.smartspacecleaner.core.fakes.FakeFileSystem
import com.smartspacecleaner.core.fakes.InMemoryActionLogStore
import com.smartspacecleaner.core.fakes.ROOT
import com.smartspacecleaner.core.fakes.T0
import com.smartspacecleaner.core.fakes.bytes
import com.smartspacecleaner.core.fakes.p
import com.smartspacecleaner.core.model.ActionKind
import com.smartspacecleaner.core.model.ActionStatus
import com.smartspacecleaner.core.model.Group
import com.smartspacecleaner.core.model.GroupKind
import com.smartspacecleaner.core.model.Plan
import com.smartspacecleaner.core.model.PlannedItem
import com.smartspacecleaner.core.model.RiskLevel
import com.smartspacecleaner.core.scan.Hasher
import com.smartspacecleaner.core.scan.StorageLayout
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Duration

class ExecutorTest {
    private val fs = FakeFileSystem()
    private val log = InMemoryActionLogStore()
    private val clock = FakeClock(T0)
    private val layout = StorageLayout(ROOT)
    private val hasher = Hasher(fs)
    private var trashCounter = 0

    private fun executor(strategy: DuplicateStrategy = DuplicateStrategy.TRASH) =
        Executor(fs, log, clock, hasher, layout, strategy) { "t${++trashCounter}" }

    private fun dupGroup(vararg copyToKeeper: Pair<String, String>) = Group(
        "dup:test", GroupKind.DUPLICATES, RiskLevel.EXACT, "Дубли", "", "", "",
        copyToKeeper.map { (copy, keeper) ->
            PlannedItem(copy, fs.content(copy).size.toLong(), T0, keptOriginalPath = keeper, sha256 = hasher.full(copy))
        },
        true,
    )

    private fun group(id: String, kind: GroupKind, level: RiskLevel, vararg paths: String) = Group(
        id, kind, level, id, "", "", "",
        paths.map { PlannedItem(it, fs.content(it).size.toLong(), T0) },
        false,
    )

    private fun plan(vararg groups: Group) = Plan(groups.toList(), 0, 0)

    // --- Красные линии ---

    @Test
    fun `never removes the last copy even if the plan marks copies as duplicates of each other`() {
        fs.add(p("A/x.jpg"), bytes(2048, 1))
        fs.add(p("B/x.jpg"), bytes(2048, 1))
        val g = dupGroup(p("A/x.jpg") to p("B/x.jpg"), p("B/x.jpg") to p("A/x.jpg"))

        val result = executor(DuplicateStrategy.DELETE_WITH_RESTORE_COPY).execute(plan(g), setOf(p("A/x.jpg"), p("B/x.jpg")), "s1")

        assertEquals(1, result.done.size)
        assertEquals(1, result.failed.size)
        assertTrue(fs.exists(p("B/x.jpg")))
        assertTrue(result.failed[0].reason!!.contains("оригинал не найден"))
    }

    @Test
    fun `does not touch a duplicate whose content changed after the scan`() {
        fs.add(p("DCIM/Camera/x.jpg"), bytes(2048, 1))
        fs.add(p("Download/x.jpg"), bytes(2048, 1))
        val g = dupGroup(p("Download/x.jpg") to p("DCIM/Camera/x.jpg"))
        fs.write(p("Download/x.jpg"), bytes(2048, 9))

        val result = executor().execute(plan(g), setOf(p("Download/x.jpg")), "s1")

        assertEquals(ActionStatus.FAILED, result.records.single().status)
        assertTrue(result.records.single().reason!!.contains("изменился"))
        assertTrue(fs.exists(p("Download/x.jpg")))
    }

    @Test
    fun `does not touch a duplicate whose original changed after the scan`() {
        fs.add(p("DCIM/Camera/x.jpg"), bytes(2048, 1))
        fs.add(p("Download/x.jpg"), bytes(2048, 1))
        val g = dupGroup(p("Download/x.jpg") to p("DCIM/Camera/x.jpg"))
        fs.write(p("DCIM/Camera/x.jpg"), bytes(2048, 9))

        val result = executor().execute(plan(g), setOf(p("Download/x.jpg")), "s1")

        assertEquals(ActionStatus.FAILED, result.records.single().status)
        assertTrue(fs.exists(p("Download/x.jpg")))
    }

    @Test
    fun `leaves unselected files alone`() {
        fs.add(p("DCIM/Screenshots/a.png"), bytes(2048, 1))
        fs.add(p("DCIM/Screenshots/b.png"), bytes(2048, 2))
        val g = group("screenshots", GroupKind.OLD_SCREENSHOTS, RiskLevel.PERSONAL, p("DCIM/Screenshots/a.png"), p("DCIM/Screenshots/b.png"))

        val result = executor().execute(plan(g), setOf(p("DCIM/Screenshots/a.png")), "s1")

        assertEquals(listOf(p("DCIM/Screenshots/a.png")), result.records.map { it.originalPath })
        assertTrue(fs.exists(p("DCIM/Screenshots/b.png")))
    }

    // --- Стратегии и виды действий ---

    @Test
    fun `trash strategy moves duplicate to trash for 7 days`() {
        fs.add(p("DCIM/Camera/x.jpg"), bytes(2048, 1))
        fs.add(p("Download/x.jpg"), bytes(2048, 1))
        val g = dupGroup(p("Download/x.jpg") to p("DCIM/Camera/x.jpg"))

        val record = executor().execute(plan(g), setOf(p("Download/x.jpg")), "s1").records.single()

        assertEquals(ActionKind.TRASH, record.kind)
        assertEquals(ActionStatus.DONE, record.status)
        assertEquals(p(".SmartSpaceCleaner/trash/t1"), record.trashPath)
        assertEquals(T0.plus(Duration.ofDays(7)), record.expiresAt)
        assertEquals(p("DCIM/Camera/x.jpg"), record.keptOriginalPath)
        assertFalse(fs.exists(p("Download/x.jpg")))
        assertTrue(fs.exists(p(".SmartSpaceCleaner/trash/t1")))
        assertTrue(fs.exists(p(".SmartSpaceCleaner/trash/.nomedia")))
        assertEquals(record, log.get(record.id))
    }

    @Test
    fun `restore-copy strategy deletes duplicate right away`() {
        fs.add(p("DCIM/Camera/x.jpg"), bytes(2048, 1))
        fs.add(p("Download/x.jpg"), bytes(2048, 1))
        val g = dupGroup(p("Download/x.jpg") to p("DCIM/Camera/x.jpg"))

        val record = executor(DuplicateStrategy.DELETE_WITH_RESTORE_COPY).execute(plan(g), setOf(p("Download/x.jpg")), "s1").records.single()

        assertEquals(ActionKind.DELETE_DUPLICATE, record.kind)
        assertEquals(ActionStatus.DONE, record.status)
        assertEquals(null, record.trashPath)
        assertFalse(fs.exists(p("Download/x.jpg")))
        assertTrue(fs.exists(p("DCIM/Camera/x.jpg")))
    }

    @Test
    fun `installed apk is deleted right away`() {
        fs.add(p("Download/app.apk"), bytes(3000, 1))
        val g = group("apk-installed", GroupKind.APK_INSTALLED, RiskLevel.EXACT, p("Download/app.apk"))

        val record = executor().execute(plan(g), setOf(p("Download/app.apk")), "s1").records.single()

        assertEquals(ActionKind.DELETE_APK, record.kind)
        assertEquals(ActionStatus.DONE, record.status)
        assertFalse(fs.exists(p("Download/app.apk")))
    }

    @Test
    fun `personal file goes to trash`() {
        fs.add(p("Movies/big.mp4"), bytes(5000, 1))
        val g = group("large:video", GroupKind.LARGE_FILES, RiskLevel.PERSONAL, p("Movies/big.mp4"))

        val record = executor().execute(plan(g), setOf(p("Movies/big.mp4")), "s1").records.single()

        assertEquals(ActionKind.TRASH, record.kind)
        assertTrue(fs.exists(record.trashPath!!))
        assertEquals("s1", record.sessionId)
        assertEquals("large:video", record.groupId)
    }

    // --- Ошибки ---

    @Test
    fun `reports failure and keeps the file when move is refused`() {
        fs.add(p("Movies/big.mp4"), bytes(5000, 1))
        fs.failingPaths += p("Movies/big.mp4")
        val g = group("large:video", GroupKind.LARGE_FILES, RiskLevel.PERSONAL, p("Movies/big.mp4"))

        val record = executor().execute(plan(g), setOf(p("Movies/big.mp4")), "s1").records.single()

        assertEquals(ActionStatus.FAILED, record.status)
        assertTrue(record.reason!!.contains("нет доступа"))
        assertTrue(fs.exists(p("Movies/big.mp4")))
    }

    @Test
    fun `reports failure when the file is already gone`() {
        fs.add(p("Movies/big.mp4"), bytes(5000, 1))
        val g = group("large:video", GroupKind.LARGE_FILES, RiskLevel.PERSONAL, p("Movies/big.mp4"))
        fs.delete(p("Movies/big.mp4"))

        val record = executor().execute(plan(g), setOf(p("Movies/big.mp4")), "s1").records.single()

        assertEquals(ActionStatus.FAILED, record.status)
        assertTrue(record.reason!!.contains("отсутствует"))
    }

    // --- Порядок ---

    @Test
    fun `executes level 1 before level 2 and ignores app cache group`() {
        fs.add(p("DCIM/Camera/x.jpg"), bytes(2048, 1))
        fs.add(p("Download/x.jpg"), bytes(2048, 1))
        fs.add(p("Movies/big.mp4"), bytes(5000, 1))
        val personal = group("large:video", GroupKind.LARGE_FILES, RiskLevel.PERSONAL, p("Movies/big.mp4"))
        val dup = dupGroup(p("Download/x.jpg") to p("DCIM/Camera/x.jpg"))
        val cache = Group("app-cache", GroupKind.APP_CACHE, RiskLevel.SAFE, "", "", "", "", emptyList(), true)

        val result = executor().execute(plan(cache, personal, dup), setOf(p("Movies/big.mp4"), p("Download/x.jpg")), "s1")

        assertEquals(listOf("dup:test", "large:video"), result.records.map { it.groupId })
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew :core:test --tests "com.smartspacecleaner.core.action.ExecutorTest"`
Expected: FAIL — `Unresolved reference: Executor`, `DuplicateStrategy`.

- [ ] **Step 3: Write the implementation**

`core/src/main/kotlin/com/smartspacecleaner/core/action/Executor.kt`:
```kotlin
package com.smartspacecleaner.core.action

import com.smartspacecleaner.core.model.ActionKind
import com.smartspacecleaner.core.model.ActionRecord
import com.smartspacecleaner.core.model.ActionStatus
import com.smartspacecleaner.core.model.Group
import com.smartspacecleaner.core.model.GroupKind
import com.smartspacecleaner.core.model.Plan
import com.smartspacecleaner.core.model.PlannedItem
import com.smartspacecleaner.core.port.ActionLogStore
import com.smartspacecleaner.core.port.Clock
import com.smartspacecleaner.core.port.FileSystem
import com.smartspacecleaner.core.scan.Hasher
import com.smartspacecleaner.core.scan.StorageLayout
import java.io.IOException
import java.time.Duration
import java.util.UUID

enum class DuplicateStrategy { TRASH, DELETE_WITH_RESTORE_COPY }

data class ExecutionResult(val sessionId: String, val records: List<ActionRecord>) {
    val done: List<ActionRecord> get() = records.filter { it.status == ActionStatus.DONE }
    val failed: List<ActionRecord> get() = records.filter { it.status == ActionStatus.FAILED }
}

class Executor(
    private val fs: FileSystem,
    private val log: ActionLogStore,
    private val clock: Clock,
    private val hasher: Hasher,
    private val layout: StorageLayout,
    private val duplicateStrategy: DuplicateStrategy = DuplicateStrategy.TRASH,
    private val trashRetention: Duration = Duration.ofDays(7),
    private val newTrashName: () -> String = { UUID.randomUUID().toString() },
) {
    fun execute(plan: Plan, selectedPaths: Set<String>, sessionId: String): ExecutionResult {
        val records = plan.groups
            .filter { it.kind != GroupKind.APP_CACHE }
            .sortedBy { it.level.number }
            .flatMap { g -> g.items.filter { it.path in selectedPaths }.map { g to it } }
            .map { (group, item) -> executeOne(group, item, sessionId) }
        return ExecutionResult(sessionId, records)
    }

    private fun executeOne(group: Group, item: PlannedItem, sessionId: String): ActionRecord {
        val kind = when {
            group.kind == GroupKind.APK_INSTALLED -> ActionKind.DELETE_APK
            group.kind == GroupKind.DUPLICATES && duplicateStrategy == DuplicateStrategy.DELETE_WITH_RESTORE_COPY -> ActionKind.DELETE_DUPLICATE
            else -> ActionKind.TRASH
        }
        val now = clock.now()
        val pending = ActionRecord(
            sessionId = sessionId,
            groupId = group.id,
            groupTitle = group.title,
            kind = kind,
            originalPath = item.path,
            size = item.size,
            trashPath = if (kind == ActionKind.TRASH) "${layout.trashRoot}/${newTrashName()}" else null,
            keptOriginalPath = item.keptOriginalPath,
            sha256 = item.sha256,
            status = ActionStatus.PENDING,
            createdAt = now,
            expiresAt = if (kind == ActionKind.TRASH) now.plus(trashRetention) else null,
        )
        val record = pending.copy(id = log.insert(pending))

        precheck(group, item)?.let { return finish(record, ActionStatus.FAILED, it) }

        val ok = if (kind == ActionKind.TRASH) {
            ensureTrashRoot()
            fs.move(item.path, record.trashPath!!)
        } else {
            fs.delete(item.path)
        }
        return if (ok) {
            finish(record, ActionStatus.DONE, null)
        } else {
            val verb = if (kind == ActionKind.TRASH) "переместить в корзину" else "удалить"
            finish(record, ActionStatus.FAILED, "не удалось $verb: нет доступа или файл занят")
        }
    }

    private fun precheck(group: Group, item: PlannedItem): String? {
        if (!fs.exists(item.path)) return "файл уже отсутствует"
        if (group.kind != GroupKind.DUPLICATES) return null
        val keeper = item.keptOriginalPath ?: return "не указан оригинал — копию не трогаю"
        if (!fs.exists(keeper)) return "оригинал не найден — копию не трогаю"
        val copyHash = hashOrNull(item.path)
        val keeperHash = hashOrNull(keeper)
        if (copyHash == null || keeperHash == null) return "не удалось прочитать файл для проверки"
        if (copyHash != item.sha256 || keeperHash != item.sha256) return "файл изменился после сканирования — не трогаю"
        return null
    }

    private fun hashOrNull(path: String): String? =
        try { hasher.full(path) } catch (e: IOException) { null } catch (e: SecurityException) { null }

    private fun ensureTrashRoot() {
        fs.mkdirs(layout.trashRoot)
        fs.createEmptyFile("${layout.trashRoot}/.nomedia")
    }

    private fun finish(record: ActionRecord, status: ActionStatus, reason: String?): ActionRecord {
        val updated = record.copy(status = status, reason = reason)
        log.update(updated)
        return updated
    }
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew :core:test --tests "com.smartspacecleaner.core.action.ExecutorTest"`
Expected: PASS, 11 тестов.

- [ ] **Step 5: Commit**

```bash
git add core/src
git commit -m "feat(core): execute approved actions with hash re-check and journal-first logging" -m "Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 8: Сверка незавершённых действий после сбоя

**Files:**
- Create: `core/src/main/kotlin/com/smartspacecleaner/core/action/PendingReconciler.kt`
- Test: `core/src/test/kotlin/com/smartspacecleaner/core/action/PendingReconcilerTest.kt`

**Interfaces:**
- Consumes: `FileSystem`, `ActionLogStore`, `ActionRecord` (Task 2)
- Produces: `class PendingReconciler(fs, log)` → `reconcile(): Int` (сколько записей `PENDING` разобрано)

- [ ] **Step 1: Write the failing test**

`core/src/test/kotlin/com/smartspacecleaner/core/action/PendingReconcilerTest.kt`:
```kotlin
package com.smartspacecleaner.core.action

import com.smartspacecleaner.core.fakes.FakeFileSystem
import com.smartspacecleaner.core.fakes.InMemoryActionLogStore
import com.smartspacecleaner.core.fakes.T0
import com.smartspacecleaner.core.fakes.bytes
import com.smartspacecleaner.core.fakes.p
import com.smartspacecleaner.core.model.ActionKind
import com.smartspacecleaner.core.model.ActionRecord
import com.smartspacecleaner.core.model.ActionStatus
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PendingReconcilerTest {
    private val fs = FakeFileSystem()
    private val log = InMemoryActionLogStore()
    private val reconciler = PendingReconciler(fs, log)

    private fun pending(kind: ActionKind, path: String, trash: String? = null) = log.insert(
        ActionRecord(
            sessionId = "s", groupId = "g", groupTitle = "g", kind = kind, originalPath = path, size = 10,
            trashPath = trash, status = ActionStatus.PENDING, createdAt = T0,
        ),
    )

    @Test
    fun `file still in place means the action was interrupted`() {
        fs.add(p("a.jpg"), bytes(10, 1))
        val id = pending(ActionKind.TRASH, p("a.jpg"), p(".SmartSpaceCleaner/trash/t1"))
        assertEquals(1, reconciler.reconcile())
        assertEquals(ActionStatus.FAILED, log.get(id)!!.status)
        assertTrue(log.get(id)!!.reason!!.contains("прервано"))
    }

    @Test
    fun `file found in trash means the move completed`() {
        fs.add(p(".SmartSpaceCleaner/trash/t1"), bytes(10, 1))
        val id = pending(ActionKind.TRASH, p("a.jpg"), p(".SmartSpaceCleaner/trash/t1"))
        reconciler.reconcile()
        assertEquals(ActionStatus.DONE, log.get(id)!!.status)
    }

    @Test
    fun `file missing in both places is reported as lost`() {
        val id = pending(ActionKind.TRASH, p("a.jpg"), p(".SmartSpaceCleaner/trash/t1"))
        reconciler.reconcile()
        assertEquals(ActionStatus.FAILED, log.get(id)!!.status)
        assertTrue(log.get(id)!!.reason!!.contains("потерян"))
    }

    @Test
    fun `deleted file means the delete completed`() {
        val id = pending(ActionKind.DELETE_APK, p("Download/app.apk"))
        reconciler.reconcile()
        assertEquals(ActionStatus.DONE, log.get(id)!!.status)
    }

    @Test
    fun `finished records are not touched`() {
        val id = log.insert(
            ActionRecord(
                sessionId = "s", groupId = "g", groupTitle = "g", kind = ActionKind.TRASH, originalPath = p("a.jpg"),
                size = 10, status = ActionStatus.DONE, createdAt = T0,
            ),
        )
        assertEquals(0, reconciler.reconcile())
        assertEquals(ActionStatus.DONE, log.get(id)!!.status)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :core:test --tests "com.smartspacecleaner.core.action.PendingReconcilerTest"`
Expected: FAIL — `Unresolved reference: PendingReconciler`.

- [ ] **Step 3: Write the implementation**

`core/src/main/kotlin/com/smartspacecleaner/core/action/PendingReconciler.kt`:
```kotlin
package com.smartspacecleaner.core.action

import com.smartspacecleaner.core.model.ActionKind
import com.smartspacecleaner.core.model.ActionRecord
import com.smartspacecleaner.core.model.ActionStatus
import com.smartspacecleaner.core.port.ActionLogStore
import com.smartspacecleaner.core.port.FileSystem

/** Сверяет записи, оставшиеся в PENDING после сбоя, с реальным состоянием файлов. */
class PendingReconciler(private val fs: FileSystem, private val log: ActionLogStore) {
    fun reconcile(): Int {
        val pending = log.byStatus(ActionStatus.PENDING)
        pending.forEach { log.update(resolve(it)) }
        return pending.size
    }

    private fun resolve(r: ActionRecord): ActionRecord = when {
        fs.exists(r.originalPath) ->
            r.copy(status = ActionStatus.FAILED, reason = "прервано: действие не завершилось, файл на месте")
        r.kind == ActionKind.TRASH && r.trashPath != null && fs.exists(r.trashPath) ->
            r.copy(status = ActionStatus.DONE)
        r.kind == ActionKind.TRASH ->
            r.copy(status = ActionStatus.FAILED, reason = "файл потерян при сбое: его нет ни на месте, ни в корзине")
        else ->
            r.copy(status = ActionStatus.DONE)
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :core:test --tests "com.smartspacecleaner.core.action.PendingReconcilerTest"`
Expected: PASS, 5 тестов.

- [ ] **Step 5: Commit**

```bash
git add core/src
git commit -m "feat(core): reconcile pending actions after a crash" -m "Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 9: Отмена действий

**Files:**
- Create: `core/src/main/kotlin/com/smartspacecleaner/core/action/Restorer.kt`
- Test: `core/src/test/kotlin/com/smartspacecleaner/core/action/RestorerTest.kt`

**Interfaces:**
- Consumes: `Executor`, `DuplicateStrategy` (Task 7), `Hasher` (Task 3), порты (Task 2)
- Produces:
  - `sealed interface RestoreResult { data class Restored(path: String); data class Failed(reason: String) }`
  - `class Restorer(fs, log, hasher)` → `restore(recordId: Long): RestoreResult`; при успехе статус записи становится `RESTORED`

- [ ] **Step 1: Write the failing tests**

`core/src/test/kotlin/com/smartspacecleaner/core/action/RestorerTest.kt`:
```kotlin
package com.smartspacecleaner.core.action

import com.smartspacecleaner.core.fakes.FakeClock
import com.smartspacecleaner.core.fakes.FakeFileSystem
import com.smartspacecleaner.core.fakes.InMemoryActionLogStore
import com.smartspacecleaner.core.fakes.ROOT
import com.smartspacecleaner.core.fakes.T0
import com.smartspacecleaner.core.fakes.bytes
import com.smartspacecleaner.core.fakes.p
import com.smartspacecleaner.core.model.ActionRecord
import com.smartspacecleaner.core.model.ActionStatus
import com.smartspacecleaner.core.model.Group
import com.smartspacecleaner.core.model.GroupKind
import com.smartspacecleaner.core.model.Plan
import com.smartspacecleaner.core.model.PlannedItem
import com.smartspacecleaner.core.model.RiskLevel
import com.smartspacecleaner.core.scan.Hasher
import com.smartspacecleaner.core.scan.StorageLayout
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class RestorerTest {
    private val fs = FakeFileSystem()
    private val log = InMemoryActionLogStore()
    private val hasher = Hasher(fs)
    private val layout = StorageLayout(ROOT)
    private val restorer = Restorer(fs, log, hasher)
    private var n = 0

    private fun run(strategy: DuplicateStrategy, plan: Plan, vararg selected: String): List<ActionRecord> =
        Executor(fs, log, FakeClock(T0), hasher, layout, strategy) { "t${++n}" }
            .execute(plan, selected.toSet(), "s1").records

    private fun dupPlan(copy: String, keeper: String) = Plan(
        listOf(
            Group(
                "dup:x", GroupKind.DUPLICATES, RiskLevel.EXACT, "Дубли", "", "", "",
                listOf(PlannedItem(copy, fs.content(copy).size.toLong(), T0, keeper, hasher.full(copy))), true,
            ),
        ),
        0, 0,
    )

    private fun personalPlan(vararg paths: String) = Plan(
        listOf(
            Group(
                "screenshots", GroupKind.OLD_SCREENSHOTS, RiskLevel.PERSONAL, "Скриншоты", "", "", "",
                paths.map { PlannedItem(it, fs.content(it).size.toLong(), T0) }, false,
            ),
        ),
        0, 0,
    )

    @Test
    fun `restores trashed file to its original path`() {
        val content = bytes(3000, 1)
        fs.add(p("DCIM/Screenshots/s.png"), content)
        val record = run(DuplicateStrategy.TRASH, personalPlan(p("DCIM/Screenshots/s.png")), p("DCIM/Screenshots/s.png")).single()

        assertEquals(RestoreResult.Restored(p("DCIM/Screenshots/s.png")), restorer.restore(record.id))
        assertArrayEquals(content, fs.content(p("DCIM/Screenshots/s.png")))
        assertFalse(fs.exists(record.trashPath!!))
        assertEquals(ActionStatus.RESTORED, log.get(record.id)!!.status)
    }

    @Test
    fun `restores under a new name when the original path is taken`() {
        fs.add(p("DCIM/Screenshots/s.png"), bytes(3000, 1))
        val record = run(DuplicateStrategy.TRASH, personalPlan(p("DCIM/Screenshots/s.png")), p("DCIM/Screenshots/s.png")).single()
        fs.add(p("DCIM/Screenshots/s.png"), bytes(10, 2))

        assertEquals(RestoreResult.Restored(p("DCIM/Screenshots/s (восстановлено).png")), restorer.restore(record.id))
    }

    @Test
    fun `restores duplicate deleted with restore-copy strategy by copying the original back`() {
        fs.add(p("DCIM/Camera/x.jpg"), bytes(2048, 1))
        fs.add(p("Download/x.jpg"), bytes(2048, 1))
        val record = run(DuplicateStrategy.DELETE_WITH_RESTORE_COPY, dupPlan(p("Download/x.jpg"), p("DCIM/Camera/x.jpg")), p("Download/x.jpg")).single()
        assertFalse(fs.exists(p("Download/x.jpg")))

        assertEquals(RestoreResult.Restored(p("Download/x.jpg")), restorer.restore(record.id))
        assertEquals(record.sha256, hasher.full(p("Download/x.jpg")))
        assertTrue(fs.exists(p("DCIM/Camera/x.jpg")))
    }

    @Test
    fun `fails to restore duplicate when its original is gone`() {
        fs.add(p("DCIM/Camera/x.jpg"), bytes(2048, 1))
        fs.add(p("Download/x.jpg"), bytes(2048, 1))
        val record = run(DuplicateStrategy.DELETE_WITH_RESTORE_COPY, dupPlan(p("Download/x.jpg"), p("DCIM/Camera/x.jpg")), p("Download/x.jpg")).single()
        fs.delete(p("DCIM/Camera/x.jpg"))

        val result = restorer.restore(record.id)
        assertTrue(result is RestoreResult.Failed && result.reason.contains("оригинал"))
        assertEquals(ActionStatus.DONE, log.get(record.id)!!.status)
    }

    @Test
    fun `fails to restore duplicate when there is not enough space`() {
        fs.add(p("DCIM/Camera/x.jpg"), bytes(2048, 1))
        fs.add(p("Download/x.jpg"), bytes(2048, 1))
        val record = run(DuplicateStrategy.DELETE_WITH_RESTORE_COPY, dupPlan(p("Download/x.jpg"), p("DCIM/Camera/x.jpg")), p("Download/x.jpg")).single()
        fs.free = 100

        val result = restorer.restore(record.id)
        assertTrue(result is RestoreResult.Failed && result.reason.contains("места"))
        assertFalse(fs.exists(p("Download/x.jpg")))
    }

    @Test
    fun `restores duplicate copy even if its original was trashed in the same session`() {
        fs.add(p("DCIM/Screenshots/s.png"), bytes(2048, 1))
        fs.add(p("Download/s.png"), bytes(2048, 1))
        val dup = dupPlan(p("Download/s.png"), p("DCIM/Screenshots/s.png")).groups.single()
        val personal = personalPlan(p("DCIM/Screenshots/s.png")).groups.single()
        val records = run(DuplicateStrategy.TRASH, Plan(listOf(dup, personal), 0, 0), p("Download/s.png"), p("DCIM/Screenshots/s.png"))
        assertEquals(listOf(ActionStatus.DONE, ActionStatus.DONE), records.map { it.status })

        records.forEach { assertTrue(restorer.restore(it.id) is RestoreResult.Restored) }
        assertTrue(fs.exists(p("Download/s.png")))
        assertTrue(fs.exists(p("DCIM/Screenshots/s.png")))
    }

    @Test
    fun `cannot restore a deleted apk`() {
        fs.add(p("Download/app.apk"), bytes(3000, 1))
        val plan = Plan(
            listOf(
                Group(
                    "apk-installed", GroupKind.APK_INSTALLED, RiskLevel.EXACT, "Установщики", "", "", "",
                    listOf(PlannedItem(p("Download/app.apk"), 3000, T0)), true,
                ),
            ),
            0, 0,
        )
        val record = run(DuplicateStrategy.TRASH, plan, p("Download/app.apk")).single()

        val result = restorer.restore(record.id)
        assertTrue(result is RestoreResult.Failed && result.reason.contains("скачайте"))
    }

    @Test
    fun `cannot restore twice or after purge`() {
        fs.add(p("DCIM/Screenshots/s.png"), bytes(3000, 1))
        val record = run(DuplicateStrategy.TRASH, personalPlan(p("DCIM/Screenshots/s.png")), p("DCIM/Screenshots/s.png")).single()
        restorer.restore(record.id)
        assertTrue(restorer.restore(record.id) is RestoreResult.Failed)

        fs.add(p("DCIM/Screenshots/t.png"), bytes(3000, 2))
        val second = run(DuplicateStrategy.TRASH, personalPlan(p("DCIM/Screenshots/t.png")), p("DCIM/Screenshots/t.png")).single()
        fs.delete(second.trashPath!!)
        val result = restorer.restore(second.id)
        assertTrue(result is RestoreResult.Failed && result.reason.contains("окончательно"))
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew :core:test --tests "com.smartspacecleaner.core.action.RestorerTest"`
Expected: FAIL — `Unresolved reference: Restorer`, `RestoreResult`.

- [ ] **Step 3: Write the implementation**

`core/src/main/kotlin/com/smartspacecleaner/core/action/Restorer.kt`:
```kotlin
package com.smartspacecleaner.core.action

import com.smartspacecleaner.core.model.ActionKind
import com.smartspacecleaner.core.model.ActionRecord
import com.smartspacecleaner.core.model.ActionStatus
import com.smartspacecleaner.core.port.ActionLogStore
import com.smartspacecleaner.core.port.FileSystem
import com.smartspacecleaner.core.scan.Hasher
import java.io.IOException

sealed interface RestoreResult {
    data class Restored(val path: String) : RestoreResult
    data class Failed(val reason: String) : RestoreResult
}

class Restorer(
    private val fs: FileSystem,
    private val log: ActionLogStore,
    private val hasher: Hasher,
) {
    fun restore(recordId: Long): RestoreResult {
        val r = log.get(recordId) ?: return RestoreResult.Failed("запись не найдена")
        if (r.status != ActionStatus.DONE) return RestoreResult.Failed("это действие нельзя отменить")
        return when (r.kind) {
            ActionKind.TRASH -> fromTrash(r)
            ActionKind.DELETE_DUPLICATE -> fromOriginal(r)
            ActionKind.DELETE_APK -> RestoreResult.Failed("установщик удалён окончательно — при необходимости скачайте его заново")
        }
    }

    private fun fromTrash(r: ActionRecord): RestoreResult {
        val trash = r.trashPath ?: return RestoreResult.Failed("в записи нет пути корзины")
        if (!fs.exists(trash)) return RestoreResult.Failed("файл уже удалён из корзины окончательно")
        val target = freeTarget(r.originalPath)
        fs.mkdirs(target.substringBeforeLast('/'))
        if (!fs.move(trash, target)) return RestoreResult.Failed("не удалось вернуть файл: нет доступа")
        return markRestored(r, target)
    }

    private fun fromOriginal(r: ActionRecord): RestoreResult {
        val kept = r.keptOriginalPath ?: return RestoreResult.Failed("в записи нет пути к оригиналу")
        if (!fs.exists(kept)) return RestoreResult.Failed("оригинал больше не существует — копию вернуть нельзя")
        if (hashOrNull(kept) != r.sha256) return RestoreResult.Failed("оригинал изменился — точную копию вернуть нельзя")
        if (fs.freeBytes() < r.size) return RestoreResult.Failed("недостаточно места для восстановления")
        val target = freeTarget(r.originalPath)
        fs.mkdirs(target.substringBeforeLast('/'))
        if (!fs.copy(kept, target)) return RestoreResult.Failed("не удалось скопировать оригинал")
        if (hashOrNull(target) != r.sha256) {
            fs.delete(target)
            return RestoreResult.Failed("копия повреждена при восстановлении")
        }
        return markRestored(r, target)
    }

    private fun markRestored(r: ActionRecord, target: String): RestoreResult {
        val note = if (target != r.originalPath) "возвращён как ${target.substringAfterLast('/')}" else null
        log.update(r.copy(status = ActionStatus.RESTORED, reason = note))
        return RestoreResult.Restored(target)
    }

    private fun freeTarget(path: String): String {
        if (!fs.exists(path)) return path
        val dir = path.substringBeforeLast('/')
        val name = path.substringAfterLast('/')
        val base = name.substringBeforeLast('.')
        val ext = if (name.contains('.')) "." + name.substringAfterLast('.') else ""
        var candidate = "$dir/$base (восстановлено)$ext"
        var i = 2
        while (fs.exists(candidate)) {
            candidate = "$dir/$base (восстановлено $i)$ext"
            i++
        }
        return candidate
    }

    private fun hashOrNull(path: String): String? =
        try { hasher.full(path) } catch (e: IOException) { null } catch (e: SecurityException) { null }
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew :core:test --tests "com.smartspacecleaner.core.action.RestorerTest"`
Expected: PASS, 8 тестов.

- [ ] **Step 5: Commit**

```bash
git add core/src
git commit -m "feat(core): restore trashed files and deleted duplicates" -m "Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 10: Окончательная очистка корзины

**Files:**
- Create: `core/src/main/kotlin/com/smartspacecleaner/core/action/TrashPurger.kt`
- Test: `core/src/test/kotlin/com/smartspacecleaner/core/action/TrashPurgerTest.kt`

**Interfaces:**
- Consumes: порты, `ActionRecord` (Task 2)
- Produces: `class TrashPurger(fs, log, clock)` → `purgeExpired(): Int`, `purgeNow(recordIds: Collection<Long>): Int`; очищенные записи получают статус `PURGED`

- [ ] **Step 1: Write the failing tests**

`core/src/test/kotlin/com/smartspacecleaner/core/action/TrashPurgerTest.kt`:
```kotlin
package com.smartspacecleaner.core.action

import com.smartspacecleaner.core.fakes.FakeClock
import com.smartspacecleaner.core.fakes.FakeFileSystem
import com.smartspacecleaner.core.fakes.InMemoryActionLogStore
import com.smartspacecleaner.core.fakes.T0
import com.smartspacecleaner.core.fakes.bytes
import com.smartspacecleaner.core.fakes.p
import com.smartspacecleaner.core.model.ActionKind
import com.smartspacecleaner.core.model.ActionRecord
import com.smartspacecleaner.core.model.ActionStatus
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Duration

class TrashPurgerTest {
    private val fs = FakeFileSystem()
    private val log = InMemoryActionLogStore()
    private val clock = FakeClock(T0)
    private val purger = TrashPurger(fs, log, clock)

    private fun trashed(name: String, kind: ActionKind = ActionKind.TRASH, status: ActionStatus = ActionStatus.DONE): Long {
        val trash = p(".SmartSpaceCleaner/trash/$name")
        fs.add(trash, bytes(100, 1))
        return log.insert(
            ActionRecord(
                sessionId = "s", groupId = "g", groupTitle = "g", kind = kind, originalPath = p("x/$name"), size = 100,
                trashPath = trash, status = status, createdAt = T0, expiresAt = T0.plus(Duration.ofDays(7)),
            ),
        )
    }

    @Test
    fun `keeps files younger than 7 days`() {
        val id = trashed("t1")
        clock.current = T0.plus(Duration.ofDays(7)).minusSeconds(1)
        assertEquals(0, purger.purgeExpired())
        assertEquals(ActionStatus.DONE, log.get(id)!!.status)
        assertTrue(fs.exists(p(".SmartSpaceCleaner/trash/t1")))
    }

    @Test
    fun `purges files once 7 days have passed`() {
        val id = trashed("t1")
        clock.current = T0.plus(Duration.ofDays(7))
        assertEquals(1, purger.purgeExpired())
        assertEquals(ActionStatus.PURGED, log.get(id)!!.status)
        assertFalse(fs.exists(p(".SmartSpaceCleaner/trash/t1")))
    }

    @Test
    fun `purge now deletes only done trash records`() {
        val done = trashed("t1")
        val restored = trashed("t2", status = ActionStatus.RESTORED)
        assertEquals(1, purger.purgeNow(listOf(done, restored, 999)))
        assertEquals(ActionStatus.PURGED, log.get(done)!!.status)
        assertEquals(ActionStatus.RESTORED, log.get(restored)!!.status)
        assertTrue(fs.exists(p(".SmartSpaceCleaner/trash/t2")))
    }

    @Test
    fun `keeps record when the file cannot be deleted`() {
        val id = trashed("t1")
        fs.failingPaths += p(".SmartSpaceCleaner/trash/t1")
        assertEquals(0, purger.purgeNow(listOf(id)))
        assertEquals(ActionStatus.DONE, log.get(id)!!.status)
    }

    @Test
    fun `marks record purged when the trash file is already gone`() {
        val id = trashed("t1")
        fs.delete(p(".SmartSpaceCleaner/trash/t1"))
        assertEquals(1, purger.purgeNow(listOf(id)))
        assertEquals(ActionStatus.PURGED, log.get(id)!!.status)
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew :core:test --tests "com.smartspacecleaner.core.action.TrashPurgerTest"`
Expected: FAIL — `Unresolved reference: TrashPurger`.

- [ ] **Step 3: Write the implementation**

`core/src/main/kotlin/com/smartspacecleaner/core/action/TrashPurger.kt`:
```kotlin
package com.smartspacecleaner.core.action

import com.smartspacecleaner.core.model.ActionKind
import com.smartspacecleaner.core.model.ActionRecord
import com.smartspacecleaner.core.model.ActionStatus
import com.smartspacecleaner.core.port.ActionLogStore
import com.smartspacecleaner.core.port.Clock
import com.smartspacecleaner.core.port.FileSystem

class TrashPurger(
    private val fs: FileSystem,
    private val log: ActionLogStore,
    private val clock: Clock,
) {
    fun purgeExpired(): Int {
        val now = clock.now()
        return purge(
            log.byStatus(ActionStatus.DONE).filter {
                it.kind == ActionKind.TRASH && it.expiresAt != null && !it.expiresAt.isAfter(now)
            },
        )
    }

    fun purgeNow(recordIds: Collection<Long>): Int =
        purge(recordIds.mapNotNull { log.get(it) }.filter { it.status == ActionStatus.DONE && it.kind == ActionKind.TRASH })

    private fun purge(records: List<ActionRecord>): Int {
        var purged = 0
        records.forEach { r ->
            val trash = r.trashPath
            if (trash == null || !fs.exists(trash) || fs.delete(trash)) {
                log.update(r.copy(status = ActionStatus.PURGED))
                purged++
            }
        }
        return purged
    }
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew :core:test --tests "com.smartspacecleaner.core.action.TrashPurgerTest"`
Expected: PASS, 5 тестов.

- [ ] **Step 5: Commit**

```bash
git add core/src
git commit -m "feat(core): purge expired trash and support delete-now" -m "Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 11: Сводка и итоговый отчёт

**Files:**
- Create: `core/src/main/kotlin/com/smartspacecleaner/core/report/SummaryBuilder.kt`
- Create: `core/src/main/kotlin/com/smartspacecleaner/core/report/ReportBuilder.kt`
- Test: `core/src/test/kotlin/com/smartspacecleaner/core/report/SummaryBuilderTest.kt`, `core/src/test/kotlin/com/smartspacecleaner/core/report/ReportBuilderTest.kt`

**Interfaces:**
- Consumes: `Plan`, `Group`, `ActionRecord` (Task 2), `ExecutionResult` (Task 7)
- Produces:
  - `enum class Severity(emoji, label) { CRITICAL, ATTENTION, OK }`; `data class Summary(severity, freeBytes, totalBytes, lines: List<String>)`; `object SummaryBuilder { fun build(freeBytes: Long, totalBytes: Long, plan: Plan): Summary }`
  - `enum class CacheOutcome { NOT_REQUESTED, SYSTEM_CLEARED, DECLINED, FALLBACK_OPENED }`
  - `data class ReportLine(groupTitle, count, bytes)`, `data class FailedLine(path, reason, manualHint)`
  - `data class Report(freeBefore, freeAfter, done, failed, notSelected: List<String>, inTrashBytes, trashFreesOn: Instant?, cacheNote: String?)` + `freedNow`
  - `object ReportBuilder { fun build(freeBefore, freeAfter, plan, selectedPaths, cacheOutcome, result): Report }`

- [ ] **Step 1: Write the failing tests**

`core/src/test/kotlin/com/smartspacecleaner/core/report/SummaryBuilderTest.kt`:
```kotlin
package com.smartspacecleaner.core.report

import com.smartspacecleaner.core.fakes.T0
import com.smartspacecleaner.core.fakes.p
import com.smartspacecleaner.core.model.Group
import com.smartspacecleaner.core.model.GroupKind
import com.smartspacecleaner.core.model.Plan
import com.smartspacecleaner.core.model.PlannedItem
import com.smartspacecleaner.core.model.RiskLevel
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class SummaryBuilderTest {
    private val gb = 1024L * 1024 * 1024
    private val cache = Group("app-cache", GroupKind.APP_CACHE, RiskLevel.SAFE, "Кэш приложений", "", "", "", emptyList(), true)
    private fun group(id: String, title: String, bytes: Long) =
        Group(id, GroupKind.DUPLICATES, RiskLevel.EXACT, title, "", "", "", listOf(PlannedItem(p(id), bytes, T0)), true)

    @Test
    fun `severity follows free space percentage`() {
        val empty = Plan(listOf(cache), 0, 0)
        assertEquals(Severity.CRITICAL, SummaryBuilder.build(4, 100, empty).severity)
        assertEquals(Severity.ATTENTION, SummaryBuilder.build(5, 100, empty).severity)
        assertEquals(Severity.ATTENTION, SummaryBuilder.build(15, 100, empty).severity)
        assertEquals(Severity.OK, SummaryBuilder.build(16, 100, empty).severity)
    }

    @Test
    fun `lists free space and the three biggest groups`() {
        val plan = Plan(
            listOf(
                cache,
                group("a", "Дубли в папке a", 1 * gb),
                group("b", "Дубли в папке b", 3 * gb),
                group("c", "Дубли в папке c", 2 * gb),
                group("d", "Дубли в папке d", gb / 2),
            ),
            0, 0,
        )
        val summary = SummaryBuilder.build(gb, 128 * gb, plan)
        assertEquals(
            listOf(
                "Свободно 1,0 ГБ из 128,0 ГБ",
                "Дубли в папке b — 3,0 ГБ",
                "Дубли в папке c — 2,0 ГБ",
                "Дубли в папке a — 1,0 ГБ",
            ),
            summary.lines,
        )
    }

    @Test
    fun `says so when nothing was found`() {
        val summary = SummaryBuilder.build(50 * gb, 128 * gb, Plan(listOf(cache), 10, 100))
        assertEquals("Ничего заметного для уборки не нашлось — можно только очистить кэш приложений", summary.lines.last())
    }
}
```

`core/src/test/kotlin/com/smartspacecleaner/core/report/ReportBuilderTest.kt`:
```kotlin
package com.smartspacecleaner.core.report

import com.smartspacecleaner.core.action.ExecutionResult
import com.smartspacecleaner.core.fakes.T0
import com.smartspacecleaner.core.fakes.p
import com.smartspacecleaner.core.model.ActionKind
import com.smartspacecleaner.core.model.ActionRecord
import com.smartspacecleaner.core.model.ActionStatus
import com.smartspacecleaner.core.model.Group
import com.smartspacecleaner.core.model.GroupKind
import com.smartspacecleaner.core.model.Plan
import com.smartspacecleaner.core.model.PlannedItem
import com.smartspacecleaner.core.model.RiskLevel
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Duration

class ReportBuilderTest {
    private val cache = Group("app-cache", GroupKind.APP_CACHE, RiskLevel.SAFE, "Кэш приложений", "", "", "", emptyList(), true)
    private val dup = Group("dup:x", GroupKind.DUPLICATES, RiskLevel.EXACT, "Дубли в папке x", "", "", "", listOf(PlannedItem(p("x/a"), 100, T0), PlannedItem(p("x/b"), 50, T0)), true)
    private val apk = Group("apk-installed", GroupKind.APK_INSTALLED, RiskLevel.EXACT, "Установщики", "", "", "", listOf(PlannedItem(p("Download/a.apk"), 30, T0)), true)
    private val shots = Group("screenshots", GroupKind.OLD_SCREENSHOTS, RiskLevel.PERSONAL, "Скриншоты", "", "", "", listOf(PlannedItem(p("s.png"), 10, T0)), false)
    private val plan = Plan(listOf(cache, dup, apk, shots), 0, 0)

    private fun record(path: String, group: Group, kind: ActionKind, status: ActionStatus, size: Long, reason: String? = null) = ActionRecord(
        sessionId = "s", groupId = group.id, groupTitle = group.title, kind = kind, originalPath = path, size = size,
        status = status, reason = reason, createdAt = T0,
        expiresAt = if (kind == ActionKind.TRASH) T0.plus(Duration.ofDays(7)) else null,
    )

    @Test
    fun `summarises done, failed, unselected and trash`() {
        val result = ExecutionResult(
            "s",
            listOf(
                record(p("x/a"), dup, ActionKind.TRASH, ActionStatus.DONE, 100),
                record(p("x/b"), dup, ActionKind.TRASH, ActionStatus.FAILED, 50, "файл изменился после сканирования — не трогаю"),
                record(p("Download/a.apk"), apk, ActionKind.DELETE_APK, ActionStatus.DONE, 30),
            ),
        )

        val report = ReportBuilder.build(1000, 1030, plan, setOf(p("x/a"), p("x/b"), p("Download/a.apk")), CacheOutcome.SYSTEM_CLEARED, result)

        assertEquals(30L, report.freedNow)
        assertEquals(listOf(ReportLine("Дубли в папке x", 1, 100), ReportLine("Установщики", 1, 30)), report.done)
        assertEquals(p("x/b"), report.failed.single().path)
        assertTrue(report.failed.single().manualHint.contains("«Файлы»"))
        assertEquals(listOf("Скриншоты"), report.notSelected)
        assertEquals(100L, report.inTrashBytes)
        assertEquals(T0.plus(Duration.ofDays(7)), report.trashFreesOn)
        assertTrue(report.cacheNote!!.contains("очищен системой"))
    }

    @Test
    fun `mentions cache as not selected and never reports negative freed space`() {
        val report = ReportBuilder.build(1000, 900, plan, emptySet(), CacheOutcome.NOT_REQUESTED, ExecutionResult("s", emptyList()))
        assertEquals(0L, report.freedNow)
        assertEquals(listOf("Дубли в папке x", "Установщики", "Скриншоты", "Кэш приложений"), report.notSelected)
        assertNull(report.cacheNote)
        assertNull(report.trashFreesOn)
    }

    @Test
    fun `explains the manual way when system cache cleaning is unavailable`() {
        val report = ReportBuilder.build(0, 0, plan, emptySet(), CacheOutcome.FALLBACK_OPENED, ExecutionResult("s", emptyList()))
        assertTrue(report.cacheNote!!.contains("Хранилище"))
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew :core:test --tests "com.smartspacecleaner.core.report.*"`
Expected: FAIL — `Unresolved reference: SummaryBuilder`, `ReportBuilder`.

- [ ] **Step 3: Write the implementation**

`core/src/main/kotlin/com/smartspacecleaner/core/report/SummaryBuilder.kt`:
```kotlin
package com.smartspacecleaner.core.report

import com.smartspacecleaner.core.model.Plan
import com.smartspacecleaner.core.text.formatBytes

enum class Severity(val emoji: String, val label: String) {
    CRITICAL("🔴", "критично"),
    ATTENTION("🟡", "стоит заняться"),
    OK("🟢", "в порядке"),
}

data class Summary(val severity: Severity, val freeBytes: Long, val totalBytes: Long, val lines: List<String>)

object SummaryBuilder {
    fun build(freeBytes: Long, totalBytes: Long, plan: Plan): Summary {
        val freePercent = if (totalBytes == 0L) 0.0 else freeBytes * 100.0 / totalBytes
        val severity = when {
            freePercent < 5 -> Severity.CRITICAL
            freePercent <= 15 -> Severity.ATTENTION
            else -> Severity.OK
        }
        val lines = mutableListOf("Свободно ${formatBytes(freeBytes)} из ${formatBytes(totalBytes)}")
        val biggest = plan.groups.filter { it.items.isNotEmpty() }.sortedByDescending { it.totalBytes }.take(3)
        biggest.forEach { lines += "${it.title} — ${formatBytes(it.totalBytes)}" }
        if (biggest.isEmpty()) lines += "Ничего заметного для уборки не нашлось — можно только очистить кэш приложений"
        return Summary(severity, freeBytes, totalBytes, lines)
    }
}
```

`core/src/main/kotlin/com/smartspacecleaner/core/report/ReportBuilder.kt`:
```kotlin
package com.smartspacecleaner.core.report

import com.smartspacecleaner.core.action.ExecutionResult
import com.smartspacecleaner.core.model.ActionKind
import com.smartspacecleaner.core.model.GroupKind
import com.smartspacecleaner.core.model.Plan
import java.time.Instant

enum class CacheOutcome { NOT_REQUESTED, SYSTEM_CLEARED, DECLINED, FALLBACK_OPENED }

data class ReportLine(val groupTitle: String, val count: Int, val bytes: Long)

data class FailedLine(val path: String, val reason: String, val manualHint: String)

data class Report(
    val freeBefore: Long,
    val freeAfter: Long,
    val done: List<ReportLine>,
    val failed: List<FailedLine>,
    val notSelected: List<String>,
    val inTrashBytes: Long,
    val trashFreesOn: Instant?,
    val cacheNote: String?,
) {
    val freedNow: Long get() = (freeAfter - freeBefore).coerceAtLeast(0)
}

object ReportBuilder {
    fun build(
        freeBefore: Long,
        freeAfter: Long,
        plan: Plan,
        selectedPaths: Set<String>,
        cacheOutcome: CacheOutcome,
        result: ExecutionResult,
    ): Report {
        val done = result.done.groupBy { it.groupTitle }.map { (title, rs) -> ReportLine(title, rs.size, rs.sumOf { it.size }) }
        val failed = result.failed.map {
            FailedLine(
                path = it.originalPath,
                reason = it.reason ?: "неизвестная причина",
                manualHint = "Если файл точно не нужен, удалите его вручную в приложении «Файлы»: ${it.originalPath}",
            )
        }
        val notSelected = plan.groups
            .filter { g -> g.kind != GroupKind.APP_CACHE && g.items.none { it.path in selectedPaths } }
            .map { it.title } +
            if (cacheOutcome == CacheOutcome.NOT_REQUESTED && plan.groups.any { it.kind == GroupKind.APP_CACHE }) listOf("Кэш приложений") else emptyList()
        val trashed = result.done.filter { it.kind == ActionKind.TRASH }
        val cacheNote = when (cacheOutcome) {
            CacheOutcome.SYSTEM_CLEARED -> "Кэш приложений очищен системой — освобождённое место уже учтено в «было → стало»"
            CacheOutcome.DECLINED -> "Очистку кэша вы отменили в системном диалоге"
            CacheOutcome.FALLBACK_OPENED -> "Этот телефон не дал очистить кэш автоматически. Откройте «Настройки → О телефоне → Хранилище» и очистите кэш там"
            CacheOutcome.NOT_REQUESTED -> null
        }
        return Report(
            freeBefore = freeBefore,
            freeAfter = freeAfter,
            done = done,
            failed = failed,
            notSelected = notSelected,
            inTrashBytes = trashed.sumOf { it.size },
            trashFreesOn = trashed.mapNotNull { it.expiresAt }.maxOrNull(),
            cacheNote = cacheNote,
        )
    }
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew :core:test`
Expected: PASS, все тесты `:core`.

- [ ] **Step 5: Commit**

```bash
git add core/src
git commit -m "feat(core): build plain-language summary and final report" -m "Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 12: Адаптер файловой системы Android

**Files:**
- Create: `app/src/main/java/com/smartspacecleaner/data/AndroidFileSystem.kt`
- Test: `app/src/androidTest/java/com/smartspacecleaner/data/AndroidFileSystemTest.kt`

**Interfaces:**
- Consumes: порт `FileSystem` (Task 2)
- Produces: `class AndroidFileSystem(storageRoot: File) : FileSystem` (не идёт по символическим ссылкам; `freeBytes()` = `storageRoot.usableSpace`)

- [ ] **Step 1: Запустить эмулятор (в фоне)**

Run (в фоне): `emulator -avd ssc_api31 -no-snapshot-save`
Затем: `adb wait-for-device && adb shell getprop sys.boot_completed`
Expected: `1` (если пусто — повторить через 20–30 секунд).

- [ ] **Step 2: Write the failing test**

`app/src/androidTest/java/com/smartspacecleaner/data/AndroidFileSystemTest.kt`:
```kotlin
package com.smartspacecleaner.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.time.Instant

@RunWith(AndroidJUnit4::class)
class AndroidFileSystemTest {
    private lateinit var root: File
    private lateinit var fs: AndroidFileSystem

    @Before
    fun setUp() {
        root = File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir, "fs-test").apply {
            deleteRecursively()
            mkdirs()
        }
        fs = AndroidFileSystem(root)
    }

    @After
    fun tearDown() {
        root.deleteRecursively()
    }

    private fun write(rel: String, text: String) = File(root, rel).apply {
        parentFile!!.mkdirs()
        writeText(text)
    }

    private fun path(rel: String) = "${root.path}/$rel"

    @Test
    fun walkListsFilesAndSkipsExcludedDirectories() {
        write("DCIM/a.jpg", "a")
        write("Android/data/x/b.bin", "b")
        write(".SmartSpaceCleaner/trash/c", "c")
        val paths = fs.walk(root.path, setOf(path("Android/data"), path(".SmartSpaceCleaner"))).map { it.path }.toList()
        assertEquals(listOf(path("DCIM/a.jpg")), paths)
    }

    @Test
    fun walkReportsSizeAndModificationTime() {
        write("x.txt", "hello").setLastModified(1_700_000_000_000)
        val entry = fs.walk(root.path, emptySet()).single()
        assertEquals(5L, entry.size)
        assertEquals(Instant.ofEpochMilli(1_700_000_000_000), entry.modifiedAt)
    }

    @Test
    fun moveCreatesParentsAndRefusesToOverwrite() {
        write("a.txt", "1")
        write("b.txt", "2")
        assertTrue(fs.move(path("a.txt"), path("t/x/a.txt")))
        assertFalse(File(root, "a.txt").exists())
        assertEquals("1", File(root, "t/x/a.txt").readText())
        assertFalse(fs.move(path("b.txt"), path("t/x/a.txt")))
        assertEquals("2", File(root, "b.txt").readText())
    }

    @Test
    fun copyKeepsSourceAndRefusesToOverwrite() {
        write("a.txt", "1")
        assertTrue(fs.copy(path("a.txt"), path("c/a.txt")))
        assertEquals("1", File(root, "a.txt").readText())
        assertEquals("1", File(root, "c/a.txt").readText())
        assertFalse(fs.copy(path("a.txt"), path("c/a.txt")))
    }

    @Test
    fun deleteExistsAndEmptyFile() {
        write("a.txt", "1")
        assertTrue(fs.exists(path("a.txt")))
        assertTrue(fs.delete(path("a.txt")))
        assertFalse(fs.exists(path("a.txt")))
        assertTrue(fs.mkdirs(path("d/e")))
        assertTrue(fs.createEmptyFile(path("d/e/.nomedia")))
        assertTrue(fs.createEmptyFile(path("d/e/.nomedia")))
        assertEquals("", fs.openRead(path("d/e/.nomedia")).use { it.readBytes().decodeToString() })
    }
}
```

- [ ] **Step 3: Run test to verify it fails**

Run: `./gradlew :app:connectedDebugAndroidTest`
Expected: FAIL — `Unresolved reference: AndroidFileSystem`.

- [ ] **Step 4: Write the implementation**

`app/src/main/java/com/smartspacecleaner/data/AndroidFileSystem.kt`:
```kotlin
package com.smartspacecleaner.data

import com.smartspacecleaner.core.model.FileEntry
import com.smartspacecleaner.core.port.FileSystem
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.io.InputStream
import java.nio.file.Files
import java.time.Instant

class AndroidFileSystem(private val storageRoot: File) : FileSystem {
    override fun walk(root: String, excludedPrefixes: Set<String>): Sequence<FileEntry> =
        File(root).walkTopDown()
            .onEnter { dir -> !isExcluded(dir.path, excludedPrefixes) && !Files.isSymbolicLink(dir.toPath()) }
            .filter { it.isFile && !isExcluded(it.path, excludedPrefixes) }
            .map { FileEntry(it.path, it.length(), Instant.ofEpochMilli(it.lastModified())) }

    private fun isExcluded(path: String, prefixes: Set<String>) =
        prefixes.any { path == it || path.startsWith("$it/") }

    override fun exists(path: String) = File(path).exists()

    override fun openRead(path: String): InputStream = FileInputStream(path)

    override fun move(from: String, to: String): Boolean {
        val target = File(to)
        if (target.exists()) return false
        target.parentFile?.mkdirs()
        return File(from).renameTo(target)
    }

    override fun copy(from: String, to: String): Boolean {
        val target = File(to)
        if (target.exists()) return false
        return try {
            File(from).copyTo(target)
            true
        } catch (e: IOException) {
            target.delete()
            false
        }
    }

    override fun delete(path: String) = File(path).delete()

    override fun mkdirs(path: String): Boolean {
        val dir = File(path)
        return dir.isDirectory || dir.mkdirs()
    }

    override fun createEmptyFile(path: String): Boolean {
        val file = File(path)
        return try { file.exists() || file.createNewFile() } catch (e: IOException) { false }
    }

    override fun freeBytes(): Long = storageRoot.usableSpace
}
```

- [ ] **Step 5: Run test to verify it passes**

Run: `./gradlew :app:connectedDebugAndroidTest`
Expected: `BUILD SUCCESSFUL`, 5 тестов пройдены.

- [ ] **Step 6: Commit**

```bash
git add app/src
git commit -m "feat(app): add java.io file system adapter" -m "Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 13: Журнал действий в Room

**Files:**
- Create: `app/src/main/java/com/smartspacecleaner/data/ActionRecordEntity.kt`
- Create: `app/src/main/java/com/smartspacecleaner/data/ActionRecordDao.kt`
- Create: `app/src/main/java/com/smartspacecleaner/data/AppDatabase.kt`
- Create: `app/src/main/java/com/smartspacecleaner/data/RoomActionLogStore.kt`
- Test: `app/src/androidTest/java/com/smartspacecleaner/data/RoomActionLogStoreTest.kt`

**Interfaces:**
- Consumes: порт `ActionLogStore`, `ActionRecord` (Task 2)
- Produces: `AppDatabase` (`actionRecords(): ActionRecordDao`), `class RoomActionLogStore(dao: ActionRecordDao) : ActionLogStore`. DAO синхронный: вызывать только не на главном потоке.

- [ ] **Step 1: Write the failing test**

`app/src/androidTest/java/com/smartspacecleaner/data/RoomActionLogStoreTest.kt`:
```kotlin
package com.smartspacecleaner.data

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.smartspacecleaner.core.model.ActionKind
import com.smartspacecleaner.core.model.ActionRecord
import com.smartspacecleaner.core.model.ActionStatus
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant

@RunWith(AndroidJUnit4::class)
class RoomActionLogStoreTest {
    private lateinit var db: AppDatabase
    private lateinit var store: RoomActionLogStore

    private val sample = ActionRecord(
        sessionId = "s1", groupId = "dup:redmi6", groupTitle = "Дубли в папке redmi6", kind = ActionKind.TRASH,
        originalPath = "/storage/emulated/0/redmi6/a.jpg", size = 1234, trashPath = "/storage/emulated/0/.SmartSpaceCleaner/trash/t1",
        keptOriginalPath = "/storage/emulated/0/DCIM/Camera/a.jpg", sha256 = "ab", status = ActionStatus.PENDING,
        createdAt = Instant.ofEpochMilli(1_700_000_000_123), expiresAt = Instant.ofEpochMilli(1_700_604_800_123),
    )

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(InstrumentationRegistry.getInstrumentation().targetContext, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        store = RoomActionLogStore(db.actionRecords())
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun insertAssignsIdAndRoundTripsAllFields() {
        val id = store.insert(sample)
        assertEquals(sample.copy(id = id), store.get(id))
    }

    @Test
    fun nullableFieldsRoundTrip() {
        val id = store.insert(sample.copy(trashPath = null, keptOriginalPath = null, sha256 = null, expiresAt = null, kind = ActionKind.DELETE_APK))
        val loaded = store.get(id)!!
        assertNull(loaded.trashPath)
        assertNull(loaded.expiresAt)
        assertEquals(ActionKind.DELETE_APK, loaded.kind)
    }

    @Test
    fun updateAndQueryByStatus() {
        val a = store.insert(sample)
        val b = store.insert(sample.copy(originalPath = "/b"))
        store.update(store.get(a)!!.copy(status = ActionStatus.DONE, reason = "ok"))
        assertEquals(listOf(a), store.byStatus(ActionStatus.DONE).map { it.id })
        assertEquals(listOf(b), store.byStatus(ActionStatus.PENDING).map { it.id })
        assertEquals(listOf(a, b), store.all().map { it.id })
        assertEquals("ok", store.get(a)!!.reason)
    }

    @Test
    fun missingRecordIsNull() {
        assertNull(store.get(42))
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :app:connectedDebugAndroidTest`
Expected: FAIL — `Unresolved reference: AppDatabase`, `RoomActionLogStore`.

- [ ] **Step 3: Write the implementation**

`app/src/main/java/com/smartspacecleaner/data/ActionRecordEntity.kt`:
```kotlin
package com.smartspacecleaner.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.smartspacecleaner.core.model.ActionKind
import com.smartspacecleaner.core.model.ActionRecord
import com.smartspacecleaner.core.model.ActionStatus
import java.time.Instant

@Entity(tableName = "action_records")
data class ActionRecordEntity(
    @PrimaryKey(autoGenerate = true) val id: Long,
    val sessionId: String,
    val groupId: String,
    val groupTitle: String,
    val kind: String,
    val originalPath: String,
    val size: Long,
    val trashPath: String?,
    val keptOriginalPath: String?,
    val sha256: String?,
    val status: String,
    val reason: String?,
    val createdAtMillis: Long,
    val expiresAtMillis: Long?,
) {
    fun toDomain() = ActionRecord(
        id = id,
        sessionId = sessionId,
        groupId = groupId,
        groupTitle = groupTitle,
        kind = ActionKind.valueOf(kind),
        originalPath = originalPath,
        size = size,
        trashPath = trashPath,
        keptOriginalPath = keptOriginalPath,
        sha256 = sha256,
        status = ActionStatus.valueOf(status),
        reason = reason,
        createdAt = Instant.ofEpochMilli(createdAtMillis),
        expiresAt = expiresAtMillis?.let { Instant.ofEpochMilli(it) },
    )

    companion object {
        fun from(r: ActionRecord) = ActionRecordEntity(
            id = r.id,
            sessionId = r.sessionId,
            groupId = r.groupId,
            groupTitle = r.groupTitle,
            kind = r.kind.name,
            originalPath = r.originalPath,
            size = r.size,
            trashPath = r.trashPath,
            keptOriginalPath = r.keptOriginalPath,
            sha256 = r.sha256,
            status = r.status.name,
            reason = r.reason,
            createdAtMillis = r.createdAt.toEpochMilli(),
            expiresAtMillis = r.expiresAt?.toEpochMilli(),
        )
    }
}
```

`app/src/main/java/com/smartspacecleaner/data/ActionRecordDao.kt`:
```kotlin
package com.smartspacecleaner.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update

@Dao
interface ActionRecordDao {
    @Insert
    fun insert(entity: ActionRecordEntity): Long

    @Update
    fun update(entity: ActionRecordEntity)

    @Query("SELECT * FROM action_records WHERE id = :id")
    fun get(id: Long): ActionRecordEntity?

    @Query("SELECT * FROM action_records WHERE status = :status ORDER BY id")
    fun byStatus(status: String): List<ActionRecordEntity>

    @Query("SELECT * FROM action_records ORDER BY id")
    fun all(): List<ActionRecordEntity>
}
```

`app/src/main/java/com/smartspacecleaner/data/AppDatabase.kt`:
```kotlin
package com.smartspacecleaner.data

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(entities = [ActionRecordEntity::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun actionRecords(): ActionRecordDao
}
```

`app/src/main/java/com/smartspacecleaner/data/RoomActionLogStore.kt`:
```kotlin
package com.smartspacecleaner.data

import com.smartspacecleaner.core.model.ActionRecord
import com.smartspacecleaner.core.model.ActionStatus
import com.smartspacecleaner.core.port.ActionLogStore

class RoomActionLogStore(private val dao: ActionRecordDao) : ActionLogStore {
    override fun insert(record: ActionRecord): Long = dao.insert(ActionRecordEntity.from(record.copy(id = 0)))
    override fun update(record: ActionRecord) = dao.update(ActionRecordEntity.from(record))
    override fun get(id: Long): ActionRecord? = dao.get(id)?.toDomain()
    override fun byStatus(status: ActionStatus): List<ActionRecord> = dao.byStatus(status.name).map { it.toDomain() }
    override fun all(): List<ActionRecord> = dao.all().map { it.toDomain() }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :app:connectedDebugAndroidTest`
Expected: `BUILD SUCCESSFUL`, тесты Room и файловой системы пройдены.

- [ ] **Step 5: Commit**

```bash
git add app/src
git commit -m "feat(app): persist action log in Room" -m "Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 14: Установщики, свободное место, сборка зависимостей и фоновая очистка корзины

**Files:**
- Create: `app/src/main/java/com/smartspacecleaner/data/AndroidApkInspector.kt`
- Create: `app/src/main/java/com/smartspacecleaner/data/StorageInfo.kt`
- Create: `app/src/main/java/com/smartspacecleaner/AppContainer.kt`
- Create: `app/src/main/java/com/smartspacecleaner/SmartSpaceApp.kt`
- Create: `app/src/main/java/com/smartspacecleaner/work/PurgeWorker.kt`
- Modify: `app/src/main/AndroidManifest.xml` (атрибут `android:name` у `<application>`)
- Test: `app/src/androidTest/java/com/smartspacecleaner/data/AndroidApkInspectorTest.kt`

**Interfaces:**
- Consumes: всё из `:core`; `AndroidFileSystem` (Task 12); `AppDatabase`, `RoomActionLogStore` (Task 13)
- Produces:
  - `class AndroidApkInspector(pm: PackageManager) : ApkInspector`
  - `class StorageInfo(context)` с `freeBytes(): Long`, `totalBytes(): Long`
  - `class AppContainer(context)` с полями `layout`, `fs`, `log`, `storageInfo`, `scanner`, `executor`, `restorer`, `purger`, `reconciler`
  - `class SmartSpaceApp : Application` с `container: AppContainer`
  - `PurgeWorker.schedule(context)`

- [ ] **Step 1: Write the failing test**

`app/src/androidTest/java/com/smartspacecleaner/data/AndroidApkInspectorTest.kt`:
```kotlin
package com.smartspacecleaner.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.smartspacecleaner.core.port.ApkStatus
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class AndroidApkInspectorTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val inspector = AndroidApkInspector(context.packageManager)

    @Test
    fun ownInstalledApkIsRecognised() {
        assertEquals(ApkStatus.INSTALLED_SAME_OR_NEWER, inspector.inspect(context.applicationInfo.sourceDir))
    }

    @Test
    fun garbageFileIsUnreadable() {
        val fake = File(context.cacheDir, "fake.apk").apply { writeText("not an apk") }
        try {
            assertEquals(ApkStatus.UNREADABLE, inspector.inspect(fake.path))
        } finally {
            fake.delete()
        }
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :app:connectedDebugAndroidTest`
Expected: FAIL — `Unresolved reference: AndroidApkInspector`.

- [ ] **Step 3: Write the implementation**

`app/src/main/java/com/smartspacecleaner/data/AndroidApkInspector.kt`:
```kotlin
package com.smartspacecleaner.data

import android.content.pm.PackageManager
import com.smartspacecleaner.core.port.ApkInspector
import com.smartspacecleaner.core.port.ApkStatus

class AndroidApkInspector(private val pm: PackageManager) : ApkInspector {
    override fun inspect(path: String): ApkStatus {
        @Suppress("DEPRECATION")
        val archive = pm.getPackageArchiveInfo(path, 0) ?: return ApkStatus.UNREADABLE
        val installed = try {
            @Suppress("DEPRECATION")
            pm.getPackageInfo(archive.packageName, 0)
        } catch (e: PackageManager.NameNotFoundException) {
            return ApkStatus.NOT_INSTALLED_OR_OLDER
        }
        return if (installed.longVersionCode >= archive.longVersionCode) {
            ApkStatus.INSTALLED_SAME_OR_NEWER
        } else {
            ApkStatus.NOT_INSTALLED_OR_OLDER
        }
    }
}
```

`app/src/main/java/com/smartspacecleaner/data/StorageInfo.kt`:
```kotlin
package com.smartspacecleaner.data

import android.app.usage.StorageStatsManager
import android.content.Context
import android.os.storage.StorageManager

/** Те же цифры, что показывает системный экран «Хранилище». */
class StorageInfo(context: Context) {
    private val stats = context.getSystemService(StorageStatsManager::class.java)

    fun freeBytes(): Long = stats.getFreeBytes(StorageManager.UUID_DEFAULT)
    fun totalBytes(): Long = stats.getTotalBytes(StorageManager.UUID_DEFAULT)
}
```

`app/src/main/java/com/smartspacecleaner/AppContainer.kt`:
```kotlin
package com.smartspacecleaner

import android.content.Context
import android.os.Environment
import androidx.room.Room
import com.smartspacecleaner.core.action.DuplicateStrategy
import com.smartspacecleaner.core.action.Executor
import com.smartspacecleaner.core.action.PendingReconciler
import com.smartspacecleaner.core.action.Restorer
import com.smartspacecleaner.core.action.TrashPurger
import com.smartspacecleaner.core.port.ActionLogStore
import com.smartspacecleaner.core.port.WallClock
import com.smartspacecleaner.core.scan.ApkRule
import com.smartspacecleaner.core.scan.DuplicateFinder
import com.smartspacecleaner.core.scan.Hasher
import com.smartspacecleaner.core.scan.KeepCopyPolicy
import com.smartspacecleaner.core.scan.LargeFileRule
import com.smartspacecleaner.core.scan.MessengerMediaRule
import com.smartspacecleaner.core.scan.OldScreenshotRule
import com.smartspacecleaner.core.scan.Planner
import com.smartspacecleaner.core.scan.Scanner
import com.smartspacecleaner.core.scan.StorageLayout
import com.smartspacecleaner.data.AndroidApkInspector
import com.smartspacecleaner.data.AndroidFileSystem
import com.smartspacecleaner.data.AppDatabase
import com.smartspacecleaner.data.RoomActionLogStore
import com.smartspacecleaner.data.StorageInfo

/** Ручная сборка зависимостей. Стратегию дублей менять здесь. */
class AppContainer(context: Context) {
    @Suppress("DEPRECATION")
    private val storageRoot = Environment.getExternalStorageDirectory()
    private val clock = WallClock
    private val db = Room.databaseBuilder(context, AppDatabase::class.java, "smart-space.db").build()
    private val hasher: Hasher

    val layout = StorageLayout(storageRoot.path)
    val fs = AndroidFileSystem(storageRoot)
    val log: ActionLogStore = RoomActionLogStore(db.actionRecords())
    val storageInfo = StorageInfo(context)
    val duplicateStrategy = DuplicateStrategy.TRASH

    val scanner: Scanner
    val executor: Executor
    val restorer: Restorer
    val purger: TrashPurger
    val reconciler: PendingReconciler

    init {
        hasher = Hasher(fs)
        val planner = Planner(
            layout,
            KeepCopyPolicy(layout),
            DuplicateFinder(hasher),
            ApkRule(AndroidApkInspector(context.packageManager)),
            OldScreenshotRule(layout, clock),
            LargeFileRule(),
            MessengerMediaRule(layout, clock),
        )
        scanner = Scanner(fs, layout, planner)
        executor = Executor(fs, log, clock, hasher, layout, duplicateStrategy)
        restorer = Restorer(fs, log, hasher)
        purger = TrashPurger(fs, log, clock)
        reconciler = PendingReconciler(fs, log)
    }
}
```

`app/src/main/java/com/smartspacecleaner/SmartSpaceApp.kt`:
```kotlin
package com.smartspacecleaner

import android.app.Application
import com.smartspacecleaner.work.PurgeWorker

class SmartSpaceApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        PurgeWorker.schedule(this)
    }
}
```

`app/src/main/java/com/smartspacecleaner/work/PurgeWorker.kt`:
```kotlin
package com.smartspacecleaner.work

import android.content.Context
import android.os.Environment
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.smartspacecleaner.SmartSpaceApp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

/** Раз в сутки окончательно удаляет из корзины файлы старше 7 дней. */
class PurgeWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        if (!Environment.isExternalStorageManager()) return Result.success()
        val container = (applicationContext as SmartSpaceApp).container
        withContext(Dispatchers.IO) {
            container.reconciler.reconcile()
            container.purger.purgeExpired()
        }
        return Result.success()
    }

    companion object {
        fun schedule(context: Context) {
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                "trash-purge",
                ExistingPeriodicWorkPolicy.KEEP,
                PeriodicWorkRequestBuilder<PurgeWorker>(1, TimeUnit.DAYS).build(),
            )
        }
    }
}
```

В `app/src/main/AndroidManifest.xml` добавить `android:name=".SmartSpaceApp"` в тег `<application>`:
```xml
    <application
        android:name=".SmartSpaceApp"
        android:label="Smart Space Cleaner"
        android:theme="@android:style/Theme.Material.Light.NoActionBar">
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew :core:test :app:connectedDebugAndroidTest`
Expected: `BUILD SUCCESSFUL`, все тесты пройдены.

- [ ] **Step 5: Commit**

```bash
git add app/src
git commit -m "feat(app): wire dependencies, apk inspector, storage info and daily trash purge" -m "Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 15: Экраны «Первый запуск», «Скан», «Сводка», «План»

**Files:**
- Create: `app/src/main/java/com/smartspacecleaner/ui/SimpleScreens.kt`
- Create: `app/src/main/java/com/smartspacecleaner/ui/PlanScreen.kt`

**Interfaces:**
- Consumes: `Summary`, `Severity` (Task 11), `Plan`, `Group`, `PlannedItem`, `GroupCheck`, `checkState` (Tasks 2, 6), `formatBytes`
- Produces (Composable-функции без собственной логики, только данные и колбэки):
  - `PermissionScreen(onGrant: () -> Unit)`
  - `ScanningScreen(filesSeen: Int)`
  - `SummaryScreen(summary: Summary, scanMillis: Long, onShowPlan: () -> Unit, onHistory: () -> Unit)`
  - `ExecutingScreen()`
  - `PlanScreen(plan: Plan, selected: Set<String>, clearCache: Boolean, onToggleGroup: (Group) -> Unit, onToggleItem: (String) -> Unit, onToggleCache: () -> Unit, onExecute: () -> Unit)`

У Compose-экранов нет модульных тестов: они только отображают данные, а вся логика проверена в `:core`. Проверка задачи — сборка, работа экранов проверяется в Task 17–18.

- [ ] **Step 1: Write the simple screens**

`app/src/main/java/com/smartspacecleaner/ui/SimpleScreens.kt`:
```kotlin
package com.smartspacecleaner.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.smartspacecleaner.core.report.Summary

@Composable
fun PermissionScreen(onGrant: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("Нужен доступ ко всем файлам", style = MaterialTheme.typography.headlineSmall)
        Text("Чтобы найти дубли, старые скриншоты, установщики и крупные файлы во всех папках, приложению нужно видеть всю память телефона.")
        Text("Приложение ничего не удаляет само: сначала только смотрит, потом показывает план, и вы решаете по каждой группе. Удалённое 7 дней лежит в корзине, и его можно вернуть.")
        Text("Без этого разрешения работать не получится: ограниченный доступ не видит загрузки и папки мессенджеров.")
        Button(onClick = onGrant) { Text("Открыть настройку доступа") }
    }
}

@Composable
fun ScanningScreen(filesSeen: Int) {
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CircularProgressIndicator()
        Text("Пока только смотрю, ничего не удаляю", style = MaterialTheme.typography.titleMedium)
        Text("Просмотрено файлов: $filesSeen")
    }
}

@Composable
fun SummaryScreen(summary: Summary, scanMillis: Long, onShowPlan: () -> Unit, onHistory: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("${summary.severity.emoji} Память: ${summary.severity.label}", style = MaterialTheme.typography.headlineSmall)
        summary.lines.forEach { Text(it) }
        Text("Скан занял ${scanMillis / 1000} с", style = MaterialTheme.typography.bodySmall)
        Button(onClick = onShowPlan) { Text("Показать план") }
        OutlinedButton(onClick = onHistory) { Text("История") }
    }
}

@Composable
fun ExecutingScreen() {
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CircularProgressIndicator()
        Text("Выполняю только то, что вы отметили…")
    }
}
```

- [ ] **Step 2: Write the plan screen**

`app/src/main/java/com/smartspacecleaner/ui/PlanScreen.kt`:
```kotlin
package com.smartspacecleaner.ui

import android.media.ThumbnailUtils
import android.util.Size
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TriStateCheckbox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.smartspacecleaner.core.model.Group
import com.smartspacecleaner.core.model.GroupCheck
import com.smartspacecleaner.core.model.GroupKind
import com.smartspacecleaner.core.model.Plan
import com.smartspacecleaner.core.model.PlannedItem
import com.smartspacecleaner.core.model.RiskLevel
import com.smartspacecleaner.core.model.checkState
import com.smartspacecleaner.core.text.formatBytes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

@Composable
fun PlanScreen(
    plan: Plan,
    selected: Set<String>,
    clearCache: Boolean,
    onToggleGroup: (Group) -> Unit,
    onToggleItem: (String) -> Unit,
    onToggleCache: () -> Unit,
    onExecute: () -> Unit,
) {
    val expanded = remember { mutableStateMapOf<String, Boolean>() }
    Column(Modifier.fillMaxSize()) {
        LazyColumn(Modifier.weight(1f)) {
            RiskLevel.entries.forEach { level ->
                val groups = plan.groups.filter { it.level == level }
                if (groups.isEmpty()) return@forEach
                item(key = "level-$level") { LevelHeader(level) }
                groups.forEach { group ->
                    val isCache = group.kind == GroupKind.APP_CACHE
                    val check = if (isCache) {
                        if (clearCache) GroupCheck.ALL else GroupCheck.NONE
                    } else {
                        group.checkState(selected)
                    }
                    val isExpanded = expanded[group.id] == true
                    item(key = group.id) {
                        GroupCard(
                            group = group,
                            check = check,
                            expanded = isExpanded,
                            onToggle = { if (isCache) onToggleCache() else onToggleGroup(group) },
                            onExpand = { expanded[group.id] = !isExpanded },
                        )
                    }
                    if (isExpanded) {
                        items(group.items, key = { "${group.id}|${it.path}" }) { item ->
                            ItemRow(item, item.path in selected) { onToggleItem(item.path) }
                        }
                    }
                }
            }
        }
        val bytes = plan.selectedBytes(selected)
        Button(
            onClick = onExecute,
            enabled = bytes > 0 || clearCache,
            modifier = Modifier.fillMaxWidth().padding(16.dp),
        ) {
            Text("Выполнить отмеченное (${formatBytes(bytes)}${if (clearCache) " + кэш" else ""})")
        }
    }
}

@Composable
private fun LevelHeader(level: RiskLevel) {
    val text = when (level) {
        RiskLevel.SAFE -> "Уровень 0 · Безопасное"
        RiskLevel.EXACT -> "Уровень 1 · Точные дубли и установщики"
        RiskLevel.PERSONAL -> "Уровень 2 · Личное — решаете поштучно"
    }
    Text(text, style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 4.dp))
}

@Composable
private fun GroupCard(group: Group, check: GroupCheck, expanded: Boolean, onToggle: () -> Unit, onExpand: () -> Unit) {
    Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)) {
        Row(Modifier.padding(12.dp)) {
            TriStateCheckbox(
                state = when (check) {
                    GroupCheck.ALL -> ToggleableState.On
                    GroupCheck.SOME -> ToggleableState.Indeterminate
                    GroupCheck.NONE -> ToggleableState.Off
                },
                onClick = onToggle,
            )
            Column(Modifier.weight(1f)) {
                val title = if (group.kind == GroupKind.APP_CACHE) group.title else "${group.title} — ${formatBytes(group.totalBytes)}"
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text("Удалится: ${group.willRemove}", style = MaterialTheme.typography.bodySmall)
                Text("Останется: ${group.willKeep}", style = MaterialTheme.typography.bodySmall)
                Text("Почему: ${group.why}", style = MaterialTheme.typography.bodySmall)
                if (group.items.isNotEmpty()) {
                    TextButton(onClick = onExpand) {
                        Text(if (expanded) "Скрыть файлы" else "Показать файлы (${group.items.size})")
                    }
                }
            }
        }
    }
}

@Composable
private fun ItemRow(item: PlannedItem, checked: Boolean, onToggle: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onToggle).padding(start = 32.dp, end = 16.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = checked, onCheckedChange = { onToggle() })
        Thumbnail(item.path)
        Column(Modifier.weight(1f).padding(start = 8.dp)) {
            Text(item.path.substringAfterLast('/'), maxLines = 1, overflow = TextOverflow.Ellipsis)
            val original = item.keptOriginalPath?.let {
                " · оригинал в ${it.substringBeforeLast('/').substringAfterLast('/')}"
            } ?: ""
            Text(formatBytes(item.size) + original, style = MaterialTheme.typography.bodySmall)
        }
    }
}

private val IMAGE_EXTENSIONS = setOf("jpg", "jpeg", "png", "webp", "heic", "gif")
private val VIDEO_EXTENSIONS = setOf("mp4", "mkv", "mov", "3gp", "webm", "avi")

@Composable
private fun Thumbnail(path: String) {
    val bitmap by produceState<ImageBitmap?>(initialValue = null, path) {
        value = withContext(Dispatchers.IO) { loadThumbnail(path) }
    }
    Box(Modifier.size(48.dp)) {
        bitmap?.let { Image(it, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()) }
    }
}

private fun loadThumbnail(path: String): ImageBitmap? = try {
    val file = File(path)
    val size = Size(128, 128)
    when (path.substringAfterLast('.', "").lowercase()) {
        in IMAGE_EXTENSIONS -> ThumbnailUtils.createImageThumbnail(file, size, null)
        in VIDEO_EXTENSIONS -> ThumbnailUtils.createVideoThumbnail(file, size, null)
        else -> null
    }?.asImageBitmap()
} catch (e: Exception) {
    null
}
```

- [ ] **Step 3: Build**

Run: `./gradlew :app:assembleDebug`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 4: Commit**

```bash
git add app/src
git commit -m "feat(app): add permission, scanning, summary and plan screens" -m "Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 16: Экраны «Отчёт» и «История»

**Files:**
- Create: `app/src/main/java/com/smartspacecleaner/ui/ReportScreen.kt`
- Create: `app/src/main/java/com/smartspacecleaner/ui/HistoryScreen.kt`

**Interfaces:**
- Consumes: `Report` (Task 11), `ActionRecord`, `ActionKind`, `ActionStatus` (Task 2), `formatBytes`, `plural`
- Produces:
  - `ReportScreen(report: Report, onHistory: () -> Unit, onDone: () -> Unit)`
  - `HistoryScreen(records: List<ActionRecord>, onRestore: (Long) -> Unit, onPurge: (List<Long>) -> Unit, onBack: () -> Unit)`. Перед окончательным удалением экран показывает диалог подтверждения.

- [ ] **Step 1: Write the report screen**

`app/src/main/java/com/smartspacecleaner/ui/ReportScreen.kt`:
```kotlin
package com.smartspacecleaner.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.smartspacecleaner.core.report.Report
import com.smartspacecleaner.core.text.formatBytes
import com.smartspacecleaner.core.text.plural
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

internal val DAY_FORMAT: DateTimeFormatter =
    DateTimeFormatter.ofPattern("d MMMM", Locale.forLanguageTag("ru")).withZone(ZoneId.systemDefault())

@Composable
fun ReportScreen(report: Report, onHistory: () -> Unit, onDone: () -> Unit) {
    LazyColumn(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item { Text("Готово", style = MaterialTheme.typography.headlineSmall) }
        item {
            Text("Было свободно ${formatBytes(report.freeBefore)} → стало ${formatBytes(report.freeAfter)}")
            Text("Освобождено сейчас: ${formatBytes(report.freedNow)}", style = MaterialTheme.typography.titleMedium)
        }
        val freesOn = report.trashFreesOn
        if (report.inTrashBytes > 0 && freesOn != null) {
            item {
                Text(
                    "Ещё ${formatBytes(report.inTrashBytes)} освободится ${DAY_FORMAT.format(freesOn)}. " +
                        "До этого файлы можно вернуть или удалить сразу в «Истории».",
                )
            }
        }
        if (report.done.isNotEmpty()) {
            item { Text("Сделано", style = MaterialTheme.typography.titleMedium) }
            items(report.done) { line ->
                Text("${line.groupTitle}: ${line.count} ${plural(line.count, "файл", "файла", "файлов")} · ${formatBytes(line.bytes)}")
            }
        }
        if (report.failed.isNotEmpty()) {
            item { Text("Не сделано", style = MaterialTheme.typography.titleMedium) }
            items(report.failed) { line ->
                Text("${line.path.substringAfterLast('/')}: ${line.reason}")
                Text(line.manualHint, style = MaterialTheme.typography.bodySmall)
            }
        }
        if (report.notSelected.isNotEmpty()) {
            item {
                Text("Не отмечено вами", style = MaterialTheme.typography.titleMedium)
                Text(report.notSelected.joinToString(", "))
            }
        }
        report.cacheNote?.let { note -> item { Text(note) } }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onDone) { Text("Просканировать снова") }
                OutlinedButton(onClick = onHistory) { Text("История") }
            }
        }
    }
}
```

- [ ] **Step 2: Write the history screen**

`app/src/main/java/com/smartspacecleaner/ui/HistoryScreen.kt`:
```kotlin
package com.smartspacecleaner.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.smartspacecleaner.core.model.ActionKind
import com.smartspacecleaner.core.model.ActionRecord
import com.smartspacecleaner.core.model.ActionStatus
import com.smartspacecleaner.core.text.formatBytes
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val SESSION_FORMAT: DateTimeFormatter =
    DateTimeFormatter.ofPattern("d MMMM, HH:mm", Locale.forLanguageTag("ru")).withZone(ZoneId.systemDefault())

@Composable
fun HistoryScreen(
    records: List<ActionRecord>,
    onRestore: (Long) -> Unit,
    onPurge: (List<Long>) -> Unit,
    onBack: () -> Unit,
) {
    var confirmPurge by remember { mutableStateOf<List<Long>?>(null) }

    Column(Modifier.fillMaxSize()) {
        TextButton(onClick = onBack, modifier = Modifier.padding(8.dp)) { Text("← Назад") }
        if (records.isEmpty()) {
            Text("История пуста", modifier = Modifier.padding(16.dp))
        }
        LazyColumn(Modifier.weight(1f)) {
            records.groupBy { it.sessionId }.forEach { (session, sessionRecords) ->
                item(key = "s-$session") {
                    Text(
                        "Сессия ${SESSION_FORMAT.format(sessionRecords.first().createdAt)}",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(start = 16.dp, top = 16.dp),
                    )
                }
                sessionRecords.groupBy { it.groupId }.forEach { (groupId, groupRecords) ->
                    val purgeable = groupRecords.filter { it.status == ActionStatus.DONE && it.kind == ActionKind.TRASH }
                    item(key = "g-$session-$groupId") {
                        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "${groupRecords.first().groupTitle} · ${formatBytes(groupRecords.sumOf { it.size })}",
                                style = MaterialTheme.typography.titleSmall,
                                modifier = Modifier.weight(1f),
                            )
                            if (purgeable.size > 1) {
                                TextButton(onClick = { confirmPurge = purgeable.map { it.id } }) { Text("Удалить группу сейчас") }
                            }
                        }
                    }
                    items(groupRecords, key = { it.id }) { record ->
                        HistoryRow(record, onRestore) { confirmPurge = listOf(record.id) }
                    }
                }
            }
        }
    }

    confirmPurge?.let { ids ->
        AlertDialog(
            onDismissRequest = { confirmPurge = null },
            title = { Text("Удалить окончательно?") },
            text = { Text("Файлов: ${ids.size}. После этого вернуть их будет нельзя, зато место освободится сразу.") },
            confirmButton = {
                TextButton(onClick = {
                    onPurge(ids)
                    confirmPurge = null
                }) { Text("Удалить") }
            },
            dismissButton = { TextButton(onClick = { confirmPurge = null }) { Text("Отмена") } },
        )
    }
}

@Composable
private fun HistoryRow(record: ActionRecord, onRestore: (Long) -> Unit, onPurge: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(start = 32.dp, end = 16.dp, top = 4.dp)) {
        Text(record.originalPath.substringAfterLast('/'))
        Text(statusText(record), style = MaterialTheme.typography.bodySmall)
        Row {
            if (record.status == ActionStatus.DONE && record.kind != ActionKind.DELETE_APK) {
                TextButton(onClick = { onRestore(record.id) }) { Text("Вернуть") }
            }
            if (record.status == ActionStatus.DONE && record.kind == ActionKind.TRASH) {
                TextButton(onClick = onPurge) { Text("Удалить сейчас") }
            }
        }
    }
}

private fun statusText(r: ActionRecord): String = when (r.status) {
    ActionStatus.PENDING -> "не завершено"
    ActionStatus.FAILED -> "не выполнено: ${r.reason ?: "причина неизвестна"}"
    ActionStatus.RESTORED -> "возвращён" + (r.reason?.let { " ($it)" } ?: "")
    ActionStatus.PURGED -> "удалён окончательно"
    ActionStatus.DONE -> when (r.kind) {
        ActionKind.TRASH -> "в корзине до ${r.expiresAt?.let { DAY_FORMAT.format(it) } ?: "—"}"
        ActionKind.DELETE_DUPLICATE -> "удалён, оригинал: ${r.keptOriginalPath}"
        ActionKind.DELETE_APK -> "удалён окончательно — при необходимости скачайте заново"
    }
}
```

- [ ] **Step 3: Build**

Run: `./gradlew :app:assembleDebug`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 4: Commit**

```bash
git add app/src
git commit -m "feat(app): add report and history screens" -m "Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 17: ViewModel, Activity и очистка кэша через систему

**Files:**
- Create: `app/src/main/java/com/smartspacecleaner/ui/MainViewModel.kt`
- Modify: `app/src/main/java/com/smartspacecleaner/MainActivity.kt` (заменить целиком)

**Interfaces:**
- Consumes: `AppContainer` (Task 14), все экраны (Tasks 15–16), `SummaryBuilder`, `ReportBuilder`, `CacheOutcome` (Task 11), `toggleGroup`, `toggleItem` (Task 6), `RestoreResult` (Task 9)
- Produces:
  - `enum class Screen { Permission, Scanning, Summary, Plan, Executing, Report, History }`
  - `data class UiState(...)`
  - `class MainViewModel(app: Application) : AndroidViewModel` с методами `onResume(hasAccess)`, `startScan()`, `showPlan()`, `backToSummary()`, `toggleGroup(group)`, `toggleItem(path)`, `toggleCache()`, `execute()`, `consumeCacheRequest()`, `onCacheResult(outcome)`, `openHistory()`, `closeHistory()`, `restore(id)`, `purgeNow(ids)`, `consumeToast()`

- [ ] **Step 1: Write the ViewModel**

`app/src/main/java/com/smartspacecleaner/ui/MainViewModel.kt`:
```kotlin
package com.smartspacecleaner.ui

import android.app.Application
import android.media.MediaScannerConnection
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.smartspacecleaner.SmartSpaceApp
import com.smartspacecleaner.core.action.ExecutionResult
import com.smartspacecleaner.core.action.RestoreResult
import com.smartspacecleaner.core.model.ActionRecord
import com.smartspacecleaner.core.model.Group
import com.smartspacecleaner.core.model.Plan
import com.smartspacecleaner.core.model.toggleGroup
import com.smartspacecleaner.core.model.toggleItem
import com.smartspacecleaner.core.report.CacheOutcome
import com.smartspacecleaner.core.report.Report
import com.smartspacecleaner.core.report.ReportBuilder
import com.smartspacecleaner.core.report.Summary
import com.smartspacecleaner.core.report.SummaryBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

enum class Screen { Permission, Scanning, Summary, Plan, Executing, Report, History }

data class UiState(
    val screen: Screen = Screen.Permission,
    val filesSeen: Int = 0,
    val scanMillis: Long = 0,
    val summary: Summary? = null,
    val plan: Plan? = null,
    val selected: Set<String> = emptySet(),
    val clearCache: Boolean = true,
    val cacheRequestPending: Boolean = false,
    val report: Report? = null,
    val history: List<ActionRecord> = emptyList(),
    val historyReturnTo: Screen = Screen.Summary,
    val toast: String? = null,
)

class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val c = (app as SmartSpaceApp).container
    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    private var freeBefore = 0L
    private var lastResult: ExecutionResult? = null

    fun onResume(hasAllFilesAccess: Boolean) {
        if (!hasAllFilesAccess) {
            _state.update { it.copy(screen = Screen.Permission) }
        } else if (_state.value.screen == Screen.Permission) {
            startScan()
        }
    }

    fun startScan() {
        _state.update { it.copy(screen = Screen.Scanning, filesSeen = 0, report = null) }
        viewModelScope.launch {
            val started = System.currentTimeMillis()
            val (plan, summary) = withContext(Dispatchers.IO) {
                c.reconciler.reconcile()
                c.purger.purgeExpired()
                val plan = c.scanner.scan { n -> _state.update { s -> s.copy(filesSeen = n) } }
                plan to SummaryBuilder.build(c.storageInfo.freeBytes(), c.storageInfo.totalBytes(), plan)
            }
            _state.update {
                it.copy(
                    screen = Screen.Summary,
                    plan = plan,
                    summary = summary,
                    selected = plan.defaultSelection(),
                    clearCache = true,
                    scanMillis = System.currentTimeMillis() - started,
                )
            }
        }
    }

    fun showPlan() = _state.update { it.copy(screen = Screen.Plan) }
    fun backToSummary() = _state.update { it.copy(screen = Screen.Summary) }
    fun toggleGroup(group: Group) = _state.update { it.copy(selected = it.selected.toggleGroup(group)) }
    fun toggleItem(path: String) = _state.update { it.copy(selected = it.selected.toggleItem(path)) }
    fun toggleCache() = _state.update { it.copy(clearCache = !it.clearCache) }

    fun execute() {
        val snapshot = _state.value
        val plan = snapshot.plan ?: return
        _state.update { it.copy(screen = Screen.Executing) }
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                freeBefore = c.storageInfo.freeBytes()
                c.executor.execute(plan, snapshot.selected, UUID.randomUUID().toString())
            }
            lastResult = result
            scanMedia(result.done.map { it.originalPath })
            if (snapshot.clearCache) {
                _state.update { it.copy(cacheRequestPending = true) }
            } else {
                finish(CacheOutcome.NOT_REQUESTED)
            }
        }
    }

    fun consumeCacheRequest() = _state.update { it.copy(cacheRequestPending = false) }

    fun onCacheResult(outcome: CacheOutcome) {
        viewModelScope.launch { finish(outcome) }
    }

    private suspend fun finish(outcome: CacheOutcome) {
        val s = _state.value
        val result = lastResult ?: return
        val plan = s.plan ?: return
        val report = withContext(Dispatchers.IO) {
            ReportBuilder.build(freeBefore, c.storageInfo.freeBytes(), plan, s.selected, outcome, result)
        }
        _state.update { it.copy(screen = Screen.Report, report = report) }
    }

    fun openHistory() {
        val returnTo = _state.value.screen
        viewModelScope.launch {
            val history = loadHistory()
            _state.update { it.copy(screen = Screen.History, history = history, historyReturnTo = returnTo) }
        }
    }

    fun closeHistory() = _state.update { it.copy(screen = it.historyReturnTo) }

    fun restore(id: Long) {
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) { c.restorer.restore(id) }
            val message = when (result) {
                is RestoreResult.Restored -> {
                    scanMedia(listOf(result.path))
                    "Файл возвращён: ${result.path.substringAfterLast('/')}"
                }
                is RestoreResult.Failed -> result.reason
            }
            _state.update { it.copy(history = loadHistory(), toast = message) }
        }
    }

    fun purgeNow(ids: List<Long>) {
        viewModelScope.launch {
            val purged = withContext(Dispatchers.IO) { c.purger.purgeNow(ids) }
            _state.update { it.copy(history = loadHistory(), toast = "Удалено окончательно: $purged") }
        }
    }

    fun consumeToast() = _state.update { it.copy(toast = null) }

    private suspend fun loadHistory(): List<ActionRecord> = withContext(Dispatchers.IO) { c.log.all().reversed() }

    private fun scanMedia(paths: List<String>) {
        if (paths.isNotEmpty()) MediaScannerConnection.scanFile(getApplication(), paths.toTypedArray(), null, null)
    }
}
```

- [ ] **Step 2: Replace MainActivity**

`app/src/main/java/com/smartspacecleaner/MainActivity.kt`:
```kotlin
package com.smartspacecleaner

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.os.storage.StorageManager
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.smartspacecleaner.core.report.CacheOutcome
import com.smartspacecleaner.ui.ExecutingScreen
import com.smartspacecleaner.ui.HistoryScreen
import com.smartspacecleaner.ui.MainViewModel
import com.smartspacecleaner.ui.PermissionScreen
import com.smartspacecleaner.ui.PlanScreen
import com.smartspacecleaner.ui.ReportScreen
import com.smartspacecleaner.ui.ScanningScreen
import com.smartspacecleaner.ui.Screen
import com.smartspacecleaner.ui.SummaryScreen

class MainActivity : ComponentActivity() {
    private val vm: MainViewModel by viewModels()

    private val clearCacheLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        vm.onCacheResult(if (result.resultCode == RESULT_OK) CacheOutcome.SYSTEM_CLEARED else CacheOutcome.DECLINED)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MaterialTheme {
                val state by vm.state.collectAsStateWithLifecycle()

                LaunchedEffect(state.cacheRequestPending) {
                    if (state.cacheRequestPending) {
                        vm.consumeCacheRequest()
                        launchCacheClear()
                    }
                }
                LaunchedEffect(state.toast) {
                    state.toast?.let {
                        Toast.makeText(this@MainActivity, it, Toast.LENGTH_LONG).show()
                        vm.consumeToast()
                    }
                }
                BackHandler(enabled = state.screen == Screen.Plan || state.screen == Screen.History) {
                    if (state.screen == Screen.Plan) vm.backToSummary() else vm.closeHistory()
                }

                Scaffold { padding ->
                    Box(Modifier.padding(padding)) {
                        when (state.screen) {
                            Screen.Permission -> PermissionScreen(onGrant = ::openAllFilesAccessSettings)
                            Screen.Scanning -> ScanningScreen(state.filesSeen)
                            Screen.Summary -> SummaryScreen(state.summary!!, state.scanMillis, vm::showPlan, vm::openHistory)
                            Screen.Plan -> PlanScreen(
                                plan = state.plan!!,
                                selected = state.selected,
                                clearCache = state.clearCache,
                                onToggleGroup = vm::toggleGroup,
                                onToggleItem = vm::toggleItem,
                                onToggleCache = vm::toggleCache,
                                onExecute = vm::execute,
                            )
                            Screen.Executing -> ExecutingScreen()
                            Screen.Report -> ReportScreen(state.report!!, onHistory = vm::openHistory, onDone = vm::startScan)
                            Screen.History -> HistoryScreen(state.history, vm::restore, vm::purgeNow, vm::closeHistory)
                        }
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        vm.onResume(Environment.isExternalStorageManager())
    }

    private fun openAllFilesAccessSettings() {
        val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:$packageName"))
        try {
            startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            startActivity(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION))
        }
    }

    /** Системная очистка кэша всех приложений; на MIUI может быть недоступна — тогда открываем «Хранилище». */
    private fun launchCacheClear() {
        val intent = Intent(StorageManager.ACTION_CLEAR_APP_CACHE)
        if (intent.resolveActivity(packageManager) != null) {
            try {
                clearCacheLauncher.launch(intent)
                return
            } catch (e: ActivityNotFoundException) {
                // переходим к запасному пути
            } catch (e: SecurityException) {
                // переходим к запасному пути
            }
        }
        startActivity(Intent(Settings.ACTION_INTERNAL_STORAGE_SETTINGS))
        vm.onCacheResult(CacheOutcome.FALLBACK_OPENED)
    }
}
```

- [ ] **Step 3: Build and install on the emulator**

Run: `./gradlew :core:test :app:installDebug`
Expected: `BUILD SUCCESSFUL`, `Installed on 1 device`.

- [ ] **Step 4: Smoke-check the launch flow on the emulator**

Run: `adb shell am start -n com.smartspacecleaner/.MainActivity`
Проверить на экране эмулятора:
1. Появился экран «Нужен доступ ко всем файлам». Кнопка открывает системную настройку; включить доступ и вернуться назад.
2. Появился экран скана с надписью «Пока только смотрю, ничего не удаляю», затем сводка.
3. «Показать план» → видна группа «Кэш приложений» на уровне 0.

Run: `adb logcat -d -s AndroidRuntime:E | tail -20`
Expected: нет записей `FATAL EXCEPTION` из `com.smartspacecleaner`.

- [ ] **Step 5: Commit**

```bash
git add app/src
git commit -m "feat(app): wire screens through view model and system cache clearing" -m "Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 18: Тестовые данные и сквозная проверка на эмуляторе

**Files:**
- Create: `scripts/push-test-data.sh`

**Interfaces:**
- Consumes: установленное приложение (Task 17)
- Produces: воспроизводимый набор файлов на эмуляторе и проверенный полный цикл

- [ ] **Step 1: Write the script**

`scripts/push-test-data.sh`:
```bash
#!/usr/bin/env bash
# Раскладывает на подключённом эмуляторе набор файлов для ручной проверки Smart Space Cleaner.
# Запуск из корня репозитория: bash scripts/push-test-data.sh
set -euo pipefail
export MSYS_NO_PATHCONV=1   # иначе Git Bash превращает /sdcard/... в пути Windows

R=/sdcard
TMP=$(mktemp -d)
trap 'rm -rf "$TMP"' EXIT

head -c 300000 /dev/urandom > "$TMP/photo.jpg"
head -c 200000 /dev/urandom > "$TMP/work.bin"
head -c 50000 /dev/urandom > "$TMP/shot.png"
head -c 80000 /dev/urandom > "$TMP/tg.jpg"
head -c 120000000 /dev/urandom > "$TMP/big.mp4"
head -c 4000 /dev/urandom > "$TMP/unknown.apk"

# adb shell склеивает аргументы в одну строку, поэтому пути с пробелами — во внутренних кавычках
adb shell mkdir -p "$R/DCIM/Camera" "$R/redmi6" "$R/Download" "$R/DCIM/Screenshots" \
  "$R/Documents/project-a" "$R/Documents/project-b" "'$R/Telegram/Telegram Images'" "$R/Movies"

# Уровень 1: дубли (оригинал в Camera) и дубли в «рабочих» папках
adb push "$TMP/photo.jpg" "$R/DCIM/Camera/IMG_0001.jpg"
adb push "$TMP/photo.jpg" "$R/redmi6/IMG_0001.jpg"
adb push "$TMP/photo.jpg" "$R/Download/IMG_0001(1).jpg"
adb push "$TMP/work.bin" "$R/Documents/project-a/data.bin"
adb push "$TMP/work.bin" "$R/Documents/project-b/data.bin"

# Уровень 1: установщик уже установленного приложения (копия собственного APK)
APK=$(adb shell pm path com.smartspacecleaner | sed 's/package://' | tr -d '\r')
adb shell cp "$APK" "$R/Download/smart-space-cleaner.apk"

# Уровень 2: неизвестный установщик, старый скриншот, старое медиа Telegram, крупное видео
adb push "$TMP/unknown.apk" "$R/Download/unknown.apk"
adb push "$TMP/shot.png" "$R/DCIM/Screenshots/Screenshot_old.png"
adb shell touch -m -d 2025-01-01T12:00:00 "$R/DCIM/Screenshots/Screenshot_old.png"
adb push "$TMP/tg.jpg" "$R/Telegram/Telegram Images/old.jpg"
adb shell touch -m -d 2025-06-01T12:00:00 "'$R/Telegram/Telegram Images/old.jpg'"
adb push "$TMP/big.mp4" "$R/Movies/big.mp4"

echo "Готово. Ожидаемые группы описаны в docs/superpowers/plans/2026-10-23-smart-space-cleaner-core.md, Task 18."
```

- [ ] **Step 2: Push the data**

Run: `bash scripts/push-test-data.sh && adb shell ls -l /sdcard/DCIM/Screenshots`
Expected: «Готово…», у `Screenshot_old.png` стоит дата 2025-01-01.

- [ ] **Step 3: Walk through the full cycle on the emulator**

Запустить приложение и нажать «Просканировать снова» (или перезапустить приложение). Проверить:

| Где | Ожидание |
|---|---|
| План, уровень 0 | «Кэш приложений», отмечено |
| План, уровень 1 | «Дубли в папке redmi6» (1 копия, оригиналы в DCIM/Camera), «Дубли в папке Download», обе отмечены; «Дубли в папке Documents/project-b» **не отмечена**, с пометкой «похожа на рабочую»; «Установщики установленных приложений», отмечено |
| План, уровень 2 | «Установщики неизвестных приложений», «Медиа Telegram старше 90 дней», «Скриншоты старше 180 дней», «Крупные видео (от 100 МБ)», все **не отмечены** |
| `DCIM/Camera/IMG_0001.jpg` | ни в одной группе |

Отметить дополнительно «Скриншоты» и выполнить. Проверить:
1. Появился системный диалог очистки кэша (или открылось «Хранилище» — тогда в отчёте есть подсказка).
2. Отчёт: «было → стало», раздел «Сделано», строка «Ещё … освободится <дата через 7 дней>», в «Не отмечено вами» перечислены группы уровня 2 без скриншотов.
3. `adb shell ls -a /sdcard/.SmartSpaceCleaner/trash` → файлы с UUID-именами и `.nomedia`.
4. `adb shell ls /sdcard/redmi6` → пусто; `adb shell ls /sdcard/DCIM/Camera` → `IMG_0001.jpg` на месте.
5. «История» → у `IMG_0001.jpg` из redmi6 нажать «Вернуть» → `adb shell ls /sdcard/redmi6` показывает файл.
6. «История» → у скриншота нажать «Удалить сейчас», подтвердить → статус «удалён окончательно».
7. `adb logcat -d -s AndroidRuntime:E | tail -20` → нет падений `com.smartspacecleaner`.

Все расхождения записать и исправить до перехода к Task 19. Если правка затрагивает логику, начинать с падающего теста в `:core`.

- [ ] **Step 4: Commit**

```bash
git add scripts/push-test-data.sh
git commit -m "test: add emulator test data script" -m "Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 19: Приёмка на телефоне автора и подстройка порогов

**Files:**
- Modify (только если пороги меняются): `core/src/main/kotlin/com/smartspacecleaner/core/scan/Rules.kt` (значения по умолчанию) и соответствующие тесты в `RulesTest.kt`, `PlannerTest.kt`
- Create: `docs/superpowers/plans/2026-10-23-acceptance-notes.md`

**Interfaces:**
- Consumes: всё приложение
- Produces: заполненный чек-лист приёмки из спецификации

- [ ] **Step 1: Подготовить телефон (вручную, пользователь)**

На Redmi Note 9 Pro (MIUI 14):
1. «Настройки → О телефоне» → 7 раз нажать на «Версия MIUI» → включится режим разработчика.
2. «Настройки → Расширенные настройки → Для разработчиков»: включить «Отладка по USB» и «Установка через USB» (MIUI просит войти в Mi-аккаунт и иногда вставленную SIM-карту).
3. Подключить кабель, на телефоне разрешить отладку для этого компьютера.

Run: `adb devices`
Expected: два устройства — эмулятор и телефон (серийный номер телефона без `emulator-`).

- [ ] **Step 2: Установить приложение на телефон**

Run: `ANDROID_SERIAL=<серийный номер телефона> ./gradlew :app:installDebug`
Expected: `Installed on 1 device`.

- [ ] **Step 3: Первый проход: только посмотреть**

На телефоне: выдать доступ ко всем файлам, дождаться сводки. Записать в `docs/superpowers/plans/2026-10-23-acceptance-notes.md`:
- время скана (с экрана сводки);
- свободное место по приложению и по «Настройки → О телефоне → Хранилище» (расхождение должно быть не больше 100 МБ);
- список групп с объёмами;
- что в группах выглядит лишним или, наоборот, не нашлось (это материал для порогов).

На этом шаге **ничего не выполнять**.

- [ ] **Step 4: Первое исполнение: одна группа дублей**

Отметить только одну группу дублей (снять всё остальное, включая кэш), выполнить. Открыть «Историю», выбрать один файл и «Вернуть». Убедиться в «Галерее», что файл вернулся. Записать результат.

- [ ] **Step 5: Основное исполнение**

Отметить то, что пользователь одобряет (включая кэш), выполнить. Записать: «было → стало», что сработало с кэшем (системный диалог или запасной путь MIUI), какие пункты не выполнены и почему. Если пользователь проверил группы дублей в корзине, он может нажать «Удалить группу сейчас» — записать освобождённый объём.

- [ ] **Step 6: Подстроить пороги**

Обсудить с пользователем пороги (180 дней для скриншотов, 100 МБ, 90 дней для мессенджеров). Если значение меняется:
1. Сначала поправить ожидания в `RulesTest.kt` и `PlannerTest.kt` (граничные значения и заголовки групп), запустить `./gradlew :core:test` → FAIL.
2. Поменять значение по умолчанию в `Rules.kt` → `./gradlew :core:test` → PASS.
3. Переустановить приложение на телефон.

- [ ] **Step 7: Пройти чек-лист приёмки из спецификации**

В заметках отметить каждый пункт:
1. Полный скан без сбоев, время выведено на экран.
2. Цикл «сводка → план → выполнение → отчёт» освободил реальное место; «было/стало» совпадает с системой ±100 МБ.
3. Отмена дубля и файла из корзины реально вернула файлы.
4. Кэш очищен через систему или через запасной путь.
5. Пороги подтверждены или поправлены.
6. Время скана: цель меньше 3 минут (ориентир, не блокер) — записать факт и причину, если цель не достигнута.

- [ ] **Step 8: Commit**

```bash
git add docs/superpowers/plans/2026-10-23-acceptance-notes.md core/src
git commit -m "docs: record on-device acceptance results" -m "Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```
