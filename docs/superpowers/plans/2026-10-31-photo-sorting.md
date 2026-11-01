# Сортировка фото и видео по папкам — план реализации

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** отдельная функция «Разложить фото по папкам»: пользователь выбирает папки, приложение на телефоне (ML Kit) распознаёт фото, показывает план, переносит фото и видео в `Pictures/Сортировка/<Категория>` и умеет вернуть всё на место из Истории.

**Architecture:**
- Чистая логика в `:core`, новый пакет `com.smartspacecleaner.core.sort`, по TDD:
  - `PhotoClassifier` — правила категорий по «сигналам» фото;
  - `PhotoSources` — список папок с фото и видео;
  - `SortPlan` / `SortAnalysis` — план раскладки;
  - `SortExecutor` / `SortRestorer` — перенос и возврат через журнал `ActionRecord` (новый вид `SORT_MOVE`);
  - `SortSessions`, `SortTexts` — История и тексты.
- `:app` — тонкий слой: `MlKitPhotoAnalyzer` (реализация порта `PhotoAnalyzer`), миграция Room 1 → 2, экраны Compose, методы в `MainViewModel`.
- Навигация прежняя (`Screen` + `UiState` в `MainViewModel`). Добавляются экраны `SortSources`, `SortAnalyzing`, `SortPlan`, `SortMoving`, `SortResult`.

**Tech Stack:**
- Kotlin 2.1, Jetpack Compose (BOM 2024.12.01, Material 3, `LazyVerticalGrid`);
- Room 2.6.1 (миграция), `MediaScannerConnection`;
- Google ML Kit, встроенные модели: `image-labeling`, `text-recognition`, `face-detection`;
- JUnit 5 в `:core`.

**Spec:** [docs/superpowers/specs/2026-10-29-photo-sorting-design.md](../specs/2026-10-29-photo-sorting-design.md)

**Отступления от спецификации (осознанные):**
- Медиасканер уведомляется не через новый порт `MediaIndex`, а существующей функцией `MainViewModel.scanMedia(...)`: так уже делают очистка и возврат из корзины. Ядро возвращает списки затронутых путей (`touchedPaths`).
- В причинах сбоя пишется «файл», а не «фото», потому что переносятся и видео: «прервано: файл остался на месте», «файл не найден после сбоя».

## Global Constraints

- `minSdk = 31`. Нельзя использовать API новее 31.
- Новые библиотеки — только три модуля ML Kit из задачи 9. Больше ничего не добавлять.
- Все модели ML Kit встроенные (bundled), без загрузки через Google Play Services. Фото и их имена не покидают телефон.
- Фото: `jpg`, `jpeg`, `png`, `webp`, `heic`, `heif`. Видео: всё, что `previewKind(path) == PreviewKind.VIDEO` (`mp4`, `mkv`, `mov`, `3gp`, `webm`, `avi`). Регистр расширения не важен.
- Корень раскладки: `Pictures/Сортировка`. Папки категорий: `Документы`, `Скриншоты`, `Люди`, `Еда`, `Природа`, `Животные`, `Мессенджеры`, `Разное`, `Видео`.
- Стартовые пороги: текст ≥ 25 % площади и ≥ 80 символов; слабый текст 30–79 символов; лицо ≥ 3 % кадра; метка ≥ 75 % уверенно, 50–75 % слабо; «почти равны» — разница меньше 10 пунктов.
- Ничего не перезаписывается: при совпадении имени добавляется суффикс ` (1)`, ` (2)`…
- Разложенные файлы никогда не удаляются автоматически. `TrashPurger` трогает только `TRASH`.
- Тексты интерфейса на русском, дословно из спецификации и этого плана.
- Версия: `versionName = "1.3"`, `versionCode = 4`.
- Gradle запускается так: `cd /s && GRADLE_USER_HOME=C:/gradle-home ./gradlew <задача>`. `S:` — `subst` корня репозитория, песочница выключена. Если `S:` нет, создать его из корня репозитория в PowerShell: `subst S: "$PWD"`.
- Тесты ядра: `cd /s && GRADLE_USER_HOME=C:/gradle-home ./gradlew :core:test`. Сборка приложения: `... ./gradlew :app:assembleDebug`.
- Коммиты — в ветку `feat/photo-sorting`, никогда в `master`. Каждое сообщение коммита заканчивается строкой `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`.

## Review Focus

1. **Одинаковые имена из разных папок в одной пачке.** `DCIM/Camera/IMG_1.jpg` и `Download/IMG_1.jpg` попадают в одну категорию: второй файл должен стать `IMG_1 (1).jpg`, а не потеряться и не перезаписать первый. Тест — в задаче 5.
2. **Файл исчез между анализом и переносом** (пользователь удалил его в галерее, пока смотрел план). Такой файл становится `FAILED` «файл исчез», остальные переносятся. Тест — в задаче 5.
3. **Повторный запуск сортировки.** Уже разложенные файлы из `Pictures/Сортировка` не должны снова попадать в список папок, иначе их можно «разложить» по второму кругу. Тест — в задаче 3.
4. **Возврат, когда на старом месте уже лежит файл с тем же именем, или исходную папку удалили.** Файл возвращается с суффиксом ` (1)`; папка создаётся заново. Тесты — в задаче 6.
5. **«Очистить историю» сразу после сортировки** не должна лишать возможности вернуть фото. Тест — в задаче 7.

---

### Task 1: Журнал, общие части и миграция базы

Добавляет вид действия `SORT_MOVE`, поле `movedToPath`, папку сортировки, категории, удаление пустых папок, общую функцию свободного имени и миграцию Room 1 → 2. После задачи приложение собирается, старая история сохраняется.

**Files:**
- Modify: `core/src/main/kotlin/com/smartspacecleaner/core/model/ActionRecord.kt`
- Modify: `core/src/main/kotlin/com/smartspacecleaner/core/scan/StorageLayout.kt`
- Create: `core/src/main/kotlin/com/smartspacecleaner/core/sort/SortCategory.kt`
- Create: `core/src/main/kotlin/com/smartspacecleaner/core/action/FreeName.kt`
- Modify: `core/src/main/kotlin/com/smartspacecleaner/core/action/Restorer.kt`
- Modify: `core/src/main/kotlin/com/smartspacecleaner/core/port/Ports.kt`
- Modify: `core/src/main/kotlin/com/smartspacecleaner/core/report/HistoryPreview.kt`
- Modify: `core/src/test/kotlin/com/smartspacecleaner/core/fakes/Fakes.kt`
- Modify: `app/src/main/java/com/smartspacecleaner/data/AndroidFileSystem.kt`
- Modify: `app/src/main/java/com/smartspacecleaner/data/ActionRecordEntity.kt`
- Modify: `app/src/main/java/com/smartspacecleaner/data/AppDatabase.kt`
- Modify: `app/src/main/java/com/smartspacecleaner/AppContainer.kt`
- Modify: `app/src/main/java/com/smartspacecleaner/ui/HistoryScreen.kt` (функции `statusColor`, `statusText` в конце файла)
- Test: `core/src/test/kotlin/com/smartspacecleaner/core/action/FreeNameTest.kt`
- Test: `core/src/test/kotlin/com/smartspacecleaner/core/sort/SortJournalTest.kt`

**Interfaces:**
- Produces:
  - `enum class ActionKind { TRASH, DELETE_DUPLICATE, DELETE_APK, SORT_MOVE }`
  - `ActionRecord.movedToPath: String? = null` (последний параметр конструктора)
  - `StorageLayout.SORT_DIR = "Pictures/Сортировка"` (const в companion), `StorageLayout.sortRoot: String`
  - `enum class SortCategory(val title: String)` со значениями `DOCUMENTS, SCREENSHOTS, PEOPLE, FOOD, NATURE, ANIMALS, MESSENGERS, OTHER, VIDEO`; `val relativeFolder: String`; `fun folder(layout: StorageLayout): String`
  - `fun freeTarget(fs: FileSystem, path: String, label: (Int) -> String): String` (пакет `core.action`)
  - `FileSystem.deleteEmptyDir(path: String): Boolean`
  - `FakeFileSystem.deletedDirs: MutableList<String>`

- [ ] **Step 1: Write the failing tests**

`core/src/test/kotlin/com/smartspacecleaner/core/action/FreeNameTest.kt`:

```kotlin
package com.smartspacecleaner.core.action

import com.smartspacecleaner.core.fakes.FakeFileSystem
import com.smartspacecleaner.core.fakes.bytes
import com.smartspacecleaner.core.fakes.p
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class FreeNameTest {
    private val fs = FakeFileSystem()

    @Test
    fun `free path is returned as is`() {
        assertEquals(p("a/x.jpg"), freeTarget(fs, p("a/x.jpg")) { "$it" })
    }

    @Test
    fun `taken path gets the first free numbered label`() {
        fs.add(p("a/x.jpg"), bytes(1, 1))
        fs.add(p("a/x (1).jpg"), bytes(1, 2))
        assertEquals(p("a/x (2).jpg"), freeTarget(fs, p("a/x.jpg")) { "$it" })
    }

    @Test
    fun `name without extension gets no dot`() {
        fs.add(p("a/x"), bytes(1, 1))
        assertEquals(p("a/x (1)"), freeTarget(fs, p("a/x")) { "$it" })
    }

    @Test
    fun `label decides the wording`() {
        fs.add(p("a/x.jpg"), bytes(1, 1))
        assertEquals(p("a/x (восстановлено).jpg"), freeTarget(fs, p("a/x.jpg")) { if (it == 1) "восстановлено" else "восстановлено $it" })
    }
}
```

`core/src/test/kotlin/com/smartspacecleaner/core/sort/SortJournalTest.kt`:

```kotlin
package com.smartspacecleaner.core.sort

import com.smartspacecleaner.core.action.RestoreResult
import com.smartspacecleaner.core.action.Restorer
import com.smartspacecleaner.core.fakes.FakeFileSystem
import com.smartspacecleaner.core.fakes.InMemoryActionLogStore
import com.smartspacecleaner.core.fakes.ROOT
import com.smartspacecleaner.core.fakes.T0
import com.smartspacecleaner.core.fakes.bytes
import com.smartspacecleaner.core.fakes.p
import com.smartspacecleaner.core.model.ActionKind
import com.smartspacecleaner.core.model.ActionRecord
import com.smartspacecleaner.core.model.ActionStatus
import com.smartspacecleaner.core.report.HistoryPreview
import com.smartspacecleaner.core.scan.Hasher
import com.smartspacecleaner.core.scan.StorageLayout
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SortJournalTest {
    private val layout = StorageLayout(ROOT)

    private fun sorted(status: ActionStatus = ActionStatus.DONE) = ActionRecord(
        sessionId = "s", groupId = "sort:NATURE", groupTitle = "Природа", kind = ActionKind.SORT_MOVE,
        originalPath = p("DCIM/Camera/a.jpg"), size = 10, status = status, createdAt = T0,
        movedToPath = p("Pictures/Сортировка/Природа/a.jpg"),
    )

    @Test
    fun `sort root lives in Pictures`() {
        assertEquals(p("Pictures/Сортировка"), layout.sortRoot)
    }

    @Test
    fun `every category has its own folder under the sort root`() {
        assertEquals(p("Pictures/Сортировка/Документы"), SortCategory.DOCUMENTS.folder(layout))
        assertEquals("Pictures/Сортировка/Видео", SortCategory.VIDEO.relativeFolder)
        assertEquals(
            listOf("Документы", "Скриншоты", "Люди", "Еда", "Природа", "Животные", "Мессенджеры", "Разное", "Видео"),
            SortCategory.entries.map { it.title },
        )
    }

    @Test
    fun `sorted file is shown from its new place in history`() {
        assertEquals(p("Pictures/Сортировка/Природа/a.jpg"), HistoryPreview.contentPath(sorted()))
        assertEquals(p("DCIM/Camera/a.jpg"), HistoryPreview.contentPath(sorted(ActionStatus.RESTORED)))
    }

    @Test
    fun `single-file restore refuses sorted files and points to the whole-session button`() {
        val fs = FakeFileSystem()
        val log = InMemoryActionLogStore()
        fs.add(p("Pictures/Сортировка/Природа/a.jpg"), bytes(10, 1))
        val id = log.insert(sorted())

        val result = Restorer(fs, log, Hasher(fs)).restore(id)

        assertTrue(result is RestoreResult.Failed && "Вернуть всё на место" in result.reason)
        assertEquals(ActionStatus.DONE, log.get(id)!!.status)
    }

    @Test
    fun `fake deletes only empty folders and remembers them`() {
        val fs = FakeFileSystem()
        fs.add(p("Pictures/Сортировка/Еда/x.jpg"), bytes(1, 1))

        assertFalse(fs.deleteEmptyDir(p("Pictures/Сортировка/Еда")))
        assertTrue(fs.deleteEmptyDir(p("Pictures/Сортировка/Природа")))
        assertEquals(listOf(p("Pictures/Сортировка/Природа")), fs.deletedDirs)
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `cd /s && GRADLE_USER_HOME=C:/gradle-home ./gradlew :core:test`
Expected: FAIL — компиляция: `Unresolved reference: freeTarget`, `SORT_MOVE`, `movedToPath`, `sortRoot`, `SortCategory`, `deleteEmptyDir`.

- [ ] **Step 3: Implement**

`ActionRecord.kt` — новый вид и поле в конце конструктора:

```kotlin
enum class ActionKind { TRASH, DELETE_DUPLICATE, DELETE_APK, SORT_MOVE }
```

```kotlin
    val createdAt: Instant,
    val expiresAt: Instant? = null,
    /** Куда перенесён файл при сортировке по папкам. */
    val movedToPath: String? = null,
)
```

`StorageLayout.kt` — внутри `class StorageLayout(val root: String) {` после `excludedPrefixes`:

```kotlin
    /** Куда раскладываются фото и видео по категориям. */
    val sortRoot: String get() = "$root/$SORT_DIR"
```

и в конце класса:

```kotlin
    companion object {
        const val SORT_DIR = "Pictures/Сортировка"
    }
```

`core/src/main/kotlin/com/smartspacecleaner/core/sort/SortCategory.kt`:

```kotlin
package com.smartspacecleaner.core.sort

import com.smartspacecleaner.core.scan.StorageLayout

/** Категории раскладки. Название — это же имя папки внутри `Pictures/Сортировка`. */
enum class SortCategory(val title: String) {
    DOCUMENTS("Документы"),
    SCREENSHOTS("Скриншоты"),
    PEOPLE("Люди"),
    FOOD("Еда"),
    NATURE("Природа"),
    ANIMALS("Животные"),
    MESSENGERS("Мессенджеры"),
    OTHER("Разное"),
    VIDEO("Видео"),
    ;

    val relativeFolder: String get() = "${StorageLayout.SORT_DIR}/$title"

    fun folder(layout: StorageLayout): String = "${layout.sortRoot}/$title"
}
```

`core/src/main/kotlin/com/smartspacecleaner/core/action/FreeName.kt`:

```kotlin
package com.smartspacecleaner.core.action

import com.smartspacecleaner.core.port.FileSystem

/** Свободный путь рядом с [path]: «x.jpg» → «x (метка).jpg»; [label] получает номер попытки, начиная с 1. Ничего не перезаписываем. */
fun freeTarget(fs: FileSystem, path: String, label: (Int) -> String): String {
    if (!fs.exists(path)) return path
    val dir = path.substringBeforeLast('/')
    val name = path.substringAfterLast('/')
    val base = name.substringBeforeLast('.')
    val ext = if (name.contains('.')) "." + name.substringAfterLast('.') else ""
    var i = 1
    while (true) {
        val candidate = "$dir/$base (${label(i)})$ext"
        if (!fs.exists(candidate)) return candidate
        i++
    }
}
```

`Restorer.kt`:
- в `when (r.kind)` добавить ветку:

```kotlin
            ActionKind.SORT_MOVE -> RestoreResult.Failed("разложенные файлы возвращаются все сразу: «Вернуть всё на место» в Истории")
```

- удалить приватную функцию `freeTarget(path: String)` целиком и заменить её на:

```kotlin
    private fun restoreTarget(path: String): String =
        freeTarget(fs, path) { if (it == 1) "восстановлено" else "восстановлено $it" }
```

- оба вызова `val target = freeTarget(r.originalPath)` заменить на `val target = restoreTarget(r.originalPath)`.

`Ports.kt` — в `interface FileSystem` после `fun delete(path: String): Boolean`:

```kotlin
    /** Удаляет папку, только если она пуста; false — не пуста, не папка или не получилось. */
    fun deleteEmptyDir(path: String): Boolean
```

`Fakes.kt` — в `FakeFileSystem` после `unreadablePaths`:

```kotlin
    /** Папки, удалённые через deleteEmptyDir, по порядку. */
    val deletedDirs = mutableListOf<String>()
```

и после `override fun delete(...)`:

```kotlin
    override fun deleteEmptyDir(path: String): Boolean {
        if (files.keys.any { it.startsWith("$path/") }) return false
        deletedDirs += path
        return true
    }
```

`HistoryPreview.kt` — ветка `DONE`:

```kotlin
        ActionStatus.DONE -> when (r.kind) {
            ActionKind.TRASH -> r.trashPath
            ActionKind.SORT_MOVE -> r.movedToPath
            ActionKind.DELETE_DUPLICATE, ActionKind.DELETE_APK -> r.keptOriginalPath
        }
```

`AndroidFileSystem.kt` — после `override fun delete(...)`:

```kotlin
    override fun deleteEmptyDir(path: String): Boolean {
        val dir = File(path)
        return dir.isDirectory && dir.list()?.isEmpty() == true && dir.delete()
    }
```

`ActionRecordEntity.kt`:
- в конструктор после `val expiresAtMillis: Long?,` добавить `val movedToPath: String?,`;
- в `toDomain()` после `expiresAt = ...` добавить `movedToPath = movedToPath,`;
- в `from(r)` после `expiresAtMillis = ...` добавить `movedToPath = r.movedToPath,`.

`AppDatabase.kt` целиком:

```kotlin
package com.smartspacecleaner.data

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [ActionRecordEntity::class], version = 2, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun actionRecords(): ActionRecordDao

    companion object {
        /** 1.3: куда перенесён файл при сортировке. Старые записи получают NULL. */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE action_records ADD COLUMN movedToPath TEXT")
            }
        }
    }
}
```

`AppContainer.kt` — строка с базой:

```kotlin
    private val db = Room.databaseBuilder(context, AppDatabase::class.java, "smart-space.db")
        .addMigrations(AppDatabase.MIGRATION_1_2)
        .build()
```

`HistoryScreen.kt` — в конце файла:

```kotlin
private fun statusColor(r: ActionRecord): Color = when (r.status) {
    ActionStatus.RESTORED -> RESTORE_GREEN
    ActionStatus.DONE -> when (r.kind) {
        ActionKind.TRASH -> TRASH_ORANGE
        ActionKind.SORT_MOVE -> RESTORE_GREEN
        ActionKind.DELETE_DUPLICATE, ActionKind.DELETE_APK -> DELETE_RED
    }
    ActionStatus.PURGED, ActionStatus.FAILED -> DELETE_RED
    ActionStatus.PENDING -> TRASH_ORANGE
}
```

и в `statusText` в `when (r.kind)` добавить ветку:

```kotlin
        ActionKind.SORT_MOVE -> "разложен в «${r.groupTitle}»"
```

- [ ] **Step 4: Run tests and build**

Run: `cd /s && GRADLE_USER_HOME=C:/gradle-home ./gradlew :core:test :app:assembleDebug`
Expected: BUILD SUCCESSFUL, все тесты ядра проходят (в том числе старые `RestorerTest` с «(восстановлено)»).

- [ ] **Step 5: Commit**

```bash
git add core app
git commit -m "feat(core): SORT_MOVE journal kind, sort folders, Room migration 1→2

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 2: Распознавание — `SortMedia`, сигналы и `PhotoClassifier`

**Files:**
- Create: `core/src/main/kotlin/com/smartspacecleaner/core/sort/SortMedia.kt`
- Create: `core/src/main/kotlin/com/smartspacecleaner/core/sort/PhotoSignals.kt`
- Create: `core/src/main/kotlin/com/smartspacecleaner/core/sort/LabelTable.kt`
- Create: `core/src/main/kotlin/com/smartspacecleaner/core/sort/PhotoClassifier.kt`
- Modify: `core/src/test/kotlin/com/smartspacecleaner/core/fakes/Fakes.kt`
- Test: `core/src/test/kotlin/com/smartspacecleaner/core/sort/SortMediaTest.kt`
- Test: `core/src/test/kotlin/com/smartspacecleaner/core/sort/PhotoClassifierTest.kt`

**Interfaces:**
- Consumes: `SortCategory`, `StorageLayout.messengerOf`, `StorageLayout.relative` (задача 1), `previewKind` (`core.text`), `plural` (`core.text`).
- Produces:
  - `object SortMedia { fun isPhoto(path: String): Boolean; fun isVideo(path: String): Boolean; fun isSortable(path: String): Boolean }`
  - `data class Label(val name: String, val confidence: Float)`
  - `data class PhotoSignals(val textAreaRatio: Float = 0f, val textChars: Int = 0, val largestFaceRatio: Float = 0f, val labels: List<Label> = emptyList())`
  - `sealed interface Analysis { data class Ok(val signals: PhotoSignals); data object Unreadable }`
  - `interface PhotoAnalyzer { fun analyze(path: String): Analysis }`
  - `data class SortDecision(val category: SortCategory?, val reason: String)` — `category == null` значит «Проверьте»
  - `object LabelTable { fun categoryOf(label: String): SortCategory?; fun best(labels: List<Label>): List<Pair<SortCategory, Label>> }`
  - `class PhotoClassifier(layout: StorageLayout, analyzer: PhotoAnalyzer) { fun classify(path: String): SortDecision }`
  - `FakePhotoAnalyzer(results: Map<String, Analysis> = emptyMap())` с полем `asked: MutableList<String>` (в `Fakes.kt`)

- [ ] **Step 1: Add the fake analyzer**

В конец `Fakes.kt` (и импорты `com.smartspacecleaner.core.sort.Analysis`, `com.smartspacecleaner.core.sort.PhotoAnalyzer`, `com.smartspacecleaner.core.sort.PhotoSignals`):

```kotlin
/** Возвращает заданные сигналы; для остальных путей — «ничего не найдено». Запоминает, какие пути спрашивали. */
class FakePhotoAnalyzer(private val results: Map<String, Analysis> = emptyMap()) : PhotoAnalyzer {
    val asked = mutableListOf<String>()

    override fun analyze(path: String): Analysis {
        asked += path
        return results[path] ?: Analysis.Ok(PhotoSignals())
    }
}
```

- [ ] **Step 2: Write the failing tests**

`core/src/test/kotlin/com/smartspacecleaner/core/sort/SortMediaTest.kt`:

```kotlin
package com.smartspacecleaner.core.sort

import com.smartspacecleaner.core.fakes.p
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SortMediaTest {
    @Test
    fun `photo extensions from the spec are photos, case does not matter`() {
        listOf("a.jpg", "a.JPEG", "a.png", "a.webp", "a.HEIC", "a.heif").forEach { assertTrue(SortMedia.isPhoto(p("DCIM/$it")), it) }
    }

    @Test
    fun `videos are recognised by extension`() {
        listOf("v.mp4", "v.MKV", "v.mov", "v.3gp", "v.webm", "v.avi").forEach { assertTrue(SortMedia.isVideo(p("DCIM/$it")), it) }
    }

    @Test
    fun `gif, raw and documents are not sorted`() {
        listOf("a.gif", "a.dng", "a.bmp", "a.pdf", "a.apk", "noext").forEach { assertFalse(SortMedia.isSortable(p("Download/$it")), it) }
    }
}
```

`core/src/test/kotlin/com/smartspacecleaner/core/sort/PhotoClassifierTest.kt`:

```kotlin
package com.smartspacecleaner.core.sort

import com.smartspacecleaner.core.fakes.FakePhotoAnalyzer
import com.smartspacecleaner.core.fakes.ROOT
import com.smartspacecleaner.core.fakes.p
import com.smartspacecleaner.core.scan.StorageLayout
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PhotoClassifierTest {
    private val layout = StorageLayout(ROOT)
    private val camera = p("DCIM/Camera/IMG_1.jpg")
    private val telegram = p("Android/media/org.telegram.messenger/Telegram/Telegram Images/t.jpg")

    private fun classify(path: String, signals: PhotoSignals): SortDecision =
        PhotoClassifier(layout, FakePhotoAnalyzer(mapOf(path to Analysis.Ok(signals)))).classify(path)

    private fun labels(vararg pairs: Pair<String, Float>) = PhotoSignals(labels = pairs.map { Label(it.first, it.second) })

    @Test
    fun `videos go to Video without analysis, even screen recordings and messenger videos`() {
        val analyzer = FakePhotoAnalyzer()
        val classifier = PhotoClassifier(layout, analyzer)
        listOf(p("DCIM/Camera/v.mp4"), p("DCIM/ScreenRecorder/Screenrecorder-1.mp4"), p("Telegram/Telegram Video/x.MOV")).forEach {
            assertEquals(SortDecision(SortCategory.VIDEO, "видео, без анализа"), classifier.classify(it), it)
        }
        assertTrue(analyzer.asked.isEmpty())
    }

    @Test
    fun `screenshots are found by name or folder without analysis`() {
        val analyzer = FakePhotoAnalyzer()
        val classifier = PhotoClassifier(layout, analyzer)
        listOf(p("Download/screenshot_2024.png"), p("DCIM/Screenshots/a.jpg"), p("Pictures/screenshots/b.png")).forEach {
            assertEquals(SortDecision(SortCategory.SCREENSHOTS, "имя или папка скриншота"), classifier.classify(it), it)
        }
        assertTrue(analyzer.asked.isEmpty())
    }

    @Test
    fun `a lot of text over a large area is a document`() {
        assertEquals(
            SortDecision(SortCategory.DOCUMENTS, "найден текст: 120 символов"),
            classify(camera, PhotoSignals(textAreaRatio = 0.3f, textChars = 120)),
        )
    }

    @Test
    fun `document thresholds are inclusive at 25 percent and 80 characters`() {
        assertEquals(SortCategory.DOCUMENTS, classify(camera, PhotoSignals(textAreaRatio = 0.25f, textChars = 80)).category)
        assertEquals(SortCategory.OTHER, classify(camera, PhotoSignals(textAreaRatio = 0.24f, textChars = 80)).category)
    }

    @Test
    fun `some text but not enough goes to review`() {
        val d = classify(camera, PhotoSignals(textAreaRatio = 0.5f, textChars = 79))
        assertNull(d.category)
        assertEquals("похоже на документ: 79 символов текста", d.reason)
    }

    @Test
    fun `a face of at least 3 percent of the frame means people`() {
        assertEquals(SortDecision(SortCategory.PEOPLE, "найдено лицо"), classify(camera, PhotoSignals(largestFaceRatio = 0.03f)))
        assertEquals(SortCategory.OTHER, classify(camera, PhotoSignals(largestFaceRatio = 0.029f)).category)
    }

    @Test
    fun `a sure label picks its category and explains why`() {
        assertEquals(SortDecision(SortCategory.ANIMALS, "метка Dog, 92 %"), classify(camera, labels("Dog" to 0.92f)))
    }

    @Test
    fun `labels are matched without regard to case`() {
        assertEquals(SortCategory.ANIMALS, classify(camera, labels("dog" to 0.92f)).category)
    }

    @Test
    fun `content wins over the messenger folder`() {
        assertEquals(SortCategory.ANIMALS, classify(telegram, labels("Dog" to 0.92f)).category)
    }

    @Test
    fun `label confidence is sure from 75 percent and weak below`() {
        assertEquals(SortCategory.NATURE, classify(camera, labels("Sky" to 0.75f)).category)
        val weak = classify(camera, labels("Sky" to 0.74f))
        assertNull(weak.category)
        assertEquals("похоже на «Природа», 74 %", weak.reason)
    }

    @Test
    fun `two categories almost equal go to review`() {
        val d = classify(camera, labels("Food" to 0.9f, "Sky" to 0.85f))
        assertNull(d.category)
        assertEquals("«Еда» или «Природа» — не уверен", d.reason)
    }

    @Test
    fun `a clear leader wins over a weaker category`() {
        assertEquals(SortCategory.FOOD, classify(camera, labels("Food" to 0.9f, "Sky" to 0.6f)).category)
    }

    @Test
    fun `two labels of the same category do not compete`() {
        assertEquals(SortCategory.NATURE, classify(camera, labels("Sky" to 0.9f, "Tree" to 0.88f)).category)
    }

    @Test
    fun `a weak label in a messenger photo still goes to review`() {
        assertNull(classify(telegram, labels("Sky" to 0.6f)).category)
    }

    @Test
    fun `nothing recognised in a messenger photo goes to Messengers`() {
        assertEquals(SortDecision(SortCategory.MESSENGERS, "из Telegram"), classify(telegram, labels("Car" to 0.95f)))
    }

    @Test
    fun `nothing recognised elsewhere goes to Other`() {
        assertEquals(SortDecision(SortCategory.OTHER, "ничего не распознано"), classify(camera, labels("Car" to 0.95f)))
    }

    @Test
    fun `unreadable photo goes to review`() {
        val d = PhotoClassifier(layout, FakePhotoAnalyzer(mapOf(camera to Analysis.Unreadable))).classify(camera)
        assertEquals(SortDecision(null, "не удалось прочитать фото"), d)
    }

    @Test
    fun `rules apply top down - text beats a face, a face beats labels`() {
        assertEquals(SortCategory.DOCUMENTS, classify(camera, PhotoSignals(textAreaRatio = 0.4f, textChars = 200, largestFaceRatio = 0.2f)).category)
        assertEquals(SortCategory.PEOPLE, classify(camera, PhotoSignals(largestFaceRatio = 0.2f, labels = listOf(Label("Food", 0.95f)))).category)
    }
}
```

- [ ] **Step 3: Run tests to verify they fail**

Run: `cd /s && GRADLE_USER_HOME=C:/gradle-home ./gradlew :core:test`
Expected: FAIL — `Unresolved reference: SortMedia`, `PhotoClassifier`, `Analysis` и т. д.

- [ ] **Step 4: Implement**

`SortMedia.kt`:

```kotlin
package com.smartspacecleaner.core.sort

import com.smartspacecleaner.core.text.PreviewKind
import com.smartspacecleaner.core.text.previewKind

/** Какие файлы участвуют в раскладке: фото анализируются, видео идут в «Видео» без анализа. */
object SortMedia {
    private val PHOTO_EXTENSIONS = setOf("jpg", "jpeg", "png", "webp", "heic", "heif")

    private fun extension(path: String) = path.substringAfterLast('/').substringAfterLast('.', "").lowercase()

    fun isPhoto(path: String): Boolean = extension(path) in PHOTO_EXTENSIONS
    fun isVideo(path: String): Boolean = previewKind(path) == PreviewKind.VIDEO
    fun isSortable(path: String): Boolean = isPhoto(path) || isVideo(path)
}
```

`PhotoSignals.kt`:

```kotlin
package com.smartspacecleaner.core.sort

data class Label(val name: String, val confidence: Float)

/** Что анализатор нашёл на фото. Доли — от площади кадра, 0..1. */
data class PhotoSignals(
    val textAreaRatio: Float = 0f,
    val textChars: Int = 0,
    val largestFaceRatio: Float = 0f,
    val labels: List<Label> = emptyList(),
)

sealed interface Analysis {
    data class Ok(val signals: PhotoSignals) : Analysis
    data object Unreadable : Analysis
}

/** Смотрит фото на телефоне. Сейчас это ML Kit; позже сюда можно подключить облачного агента. */
interface PhotoAnalyzer {
    fun analyze(path: String): Analysis
}

/** [category] = null — раздел «Проверьте»: файл остаётся на месте, пока ему не выберут категорию. */
data class SortDecision(val category: SortCategory?, val reason: String)
```

`LabelTable.kt`:

```kotlin
package com.smartspacecleaner.core.sort

/** Какие метки ML Kit к какой категории относятся. Названия — из списка меток базовой модели ML Kit. */
object LabelTable {
    private val table: Map<String, SortCategory> = buildMap {
        fun put(category: SortCategory, vararg names: String) = names.forEach { put(it.lowercase(), category) }
        put(SortCategory.FOOD, "Food", "Dessert", "Cake", "Pizza", "Fast food", "Cuisine", "Bread", "Fruit")
        put(SortCategory.NATURE, "Sky", "Plant", "Flower", "Tree", "Mountain", "Beach", "Sea", "Lake", "River", "Sunset", "Forest")
        put(SortCategory.ANIMALS, "Dog", "Cat", "Bird", "Pet", "Horse", "Fish", "Insect", "Butterfly")
    }

    fun categoryOf(label: String): SortCategory? = table[label.lowercase()]

    /** Самая уверенная метка каждой категории, по убыванию уверенности. */
    fun best(labels: List<Label>): List<Pair<SortCategory, Label>> =
        labels.mapNotNull { label -> categoryOf(label.name)?.let { it to label } }
            .groupBy({ it.first }, { it.second })
            .map { (category, ls) -> category to ls.maxBy { it.confidence } }
            .sortedByDescending { it.second.confidence }
}
```

`PhotoClassifier.kt`:

```kotlin
package com.smartspacecleaner.core.sort

import com.smartspacecleaner.core.scan.StorageLayout
import com.smartspacecleaner.core.text.plural
import kotlin.math.roundToInt

/** Решает, в какую папку положить файл. Правила идут сверху вниз, побеждает первое сработавшее. */
class PhotoClassifier(private val layout: StorageLayout, private val analyzer: PhotoAnalyzer) {
    fun classify(path: String): SortDecision {
        if (SortMedia.isVideo(path)) return SortDecision(SortCategory.VIDEO, "видео, без анализа")
        if (isScreenshot(path)) return SortDecision(SortCategory.SCREENSHOTS, "имя или папка скриншота")
        val signals = when (val analysis = analyzer.analyze(path)) {
            is Analysis.Ok -> analysis.signals
            Analysis.Unreadable -> return SortDecision(null, "не удалось прочитать фото")
        }
        return decide(path, signals)
    }

    private fun isScreenshot(path: String): Boolean =
        path.substringAfterLast('/').startsWith("screenshot", ignoreCase = true) ||
            layout.relative(path).split('/').dropLast(1).any { it.equals("Screenshots", ignoreCase = true) }

    private fun decide(path: String, s: PhotoSignals): SortDecision {
        if (s.textAreaRatio >= TEXT_AREA && s.textChars >= TEXT_CHARS) {
            return SortDecision(SortCategory.DOCUMENTS, "найден текст: ${s.textChars} ${plural(s.textChars, "символ", "символа", "символов")}")
        }
        if (s.largestFaceRatio >= FACE) return SortDecision(SortCategory.PEOPLE, "найдено лицо")

        val ranked = LabelTable.best(s.labels).filter { it.second.confidence >= WEAK }
        val top = ranked.getOrNull(0)
        if (top != null) {
            val (category, label) = top
            val rival = ranked.getOrNull(1)?.takeIf { label.confidence - it.second.confidence < GAP }
            return when {
                rival != null -> SortDecision(null, "«${category.title}» или «${rival.first.title}» — не уверен")
                label.confidence >= SURE -> SortDecision(category, "метка ${label.name}, ${percent(label.confidence)}")
                else -> SortDecision(null, "похоже на «${category.title}», ${percent(label.confidence)}")
            }
        }
        if (s.textChars in WEAK_TEXT_CHARS until TEXT_CHARS) {
            return SortDecision(null, "похоже на документ: ${s.textChars} ${plural(s.textChars, "символ", "символа", "символов")} текста")
        }
        layout.messengerOf(path)?.let { return SortDecision(SortCategory.MESSENGERS, "из ${it.title}") }
        return SortDecision(SortCategory.OTHER, "ничего не распознано")
    }

    private fun percent(confidence: Float) = "${(confidence * 100).roundToInt()} %"

    companion object {
        // Стартовые пороги из спецификации; подстраиваются по реальным фото после первой установки.
        const val TEXT_AREA = 0.25f
        const val TEXT_CHARS = 80
        const val WEAK_TEXT_CHARS = 30
        const val FACE = 0.03f
        const val SURE = 0.75f
        const val WEAK = 0.5f
        const val GAP = 0.10f
    }
}
```

- [ ] **Step 5: Run tests to verify they pass**

Run: `cd /s && GRADLE_USER_HOME=C:/gradle-home ./gradlew :core:test`
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add core
git commit -m "feat(core): photo classifier with rule order, label table and thresholds

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 3: Список папок — `PhotoSources`

**Files:**
- Create: `core/src/main/kotlin/com/smartspacecleaner/core/sort/PhotoSources.kt`
- Test: `core/src/test/kotlin/com/smartspacecleaner/core/sort/PhotoSourcesTest.kt`

**Interfaces:**
- Consumes: `SortMedia` (задача 2), `StorageLayout.sortRoot` (задача 1), `FileSystem.walk`, `StorageLayout.excludedPrefixes`, `StorageLayout.isHidden`.
- Produces:
  - `data class SourceFolder(val path: String, val files: List<FileEntry>)` со свойствами `photos: Int`, `videos: Int`, `bytes: Long`
  - `class PhotoSources(fs: FileSystem, layout: StorageLayout) { fun list(): List<SourceFolder> }`

- [ ] **Step 1: Write the failing test**

```kotlin
package com.smartspacecleaner.core.sort

import com.smartspacecleaner.core.fakes.FakeFileSystem
import com.smartspacecleaner.core.fakes.ROOT
import com.smartspacecleaner.core.fakes.bytes
import com.smartspacecleaner.core.fakes.p
import com.smartspacecleaner.core.scan.StorageLayout
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class PhotoSourcesTest {
    private val fs = FakeFileSystem()
    private val sources = PhotoSources(fs, StorageLayout(ROOT))

    @Test
    fun `groups photos and videos by the folder they lie in directly`() {
        fs.add(p("DCIM/Camera/a.jpg"), bytes(100, 1))
        fs.add(p("DCIM/Camera/v.mp4"), bytes(300, 2))
        fs.add(p("DCIM/Camera/Sub/b.jpg"), bytes(50, 3))

        val list = sources.list()

        assertEquals(listOf(p("DCIM/Camera"), p("DCIM/Camera/Sub")), list.map { it.path })
        val camera = list.first()
        assertEquals(1, camera.photos)
        assertEquals(1, camera.videos)
        assertEquals(400L, camera.bytes)
    }

    @Test
    fun `heaviest folder comes first`() {
        fs.add(p("Download/a.jpg"), bytes(10, 1))
        fs.add(p("Telegram/Telegram Images/b.jpg"), bytes(500, 2))

        assertEquals(listOf(p("Telegram/Telegram Images"), p("Download")), sources.list().map { it.path })
    }

    @Test
    fun `already sorted, hidden, trash and app data are not offered`() {
        fs.add(p("Pictures/Сортировка/Природа/a.jpg"), bytes(10, 1))
        fs.add(p("DCIM/.thumbnails/t.jpg"), bytes(10, 2))
        fs.add(p(".SmartSpaceCleaner/trash/x.jpg"), bytes(10, 3))
        fs.add(p("Android/data/com.app/cache/c.jpg"), bytes(10, 4))
        fs.add(p("Pictures/Screenshots/s.png"), bytes(10, 5))

        assertEquals(listOf(p("Pictures/Screenshots")), sources.list().map { it.path })
    }

    @Test
    fun `folders with only documents or gifs are not listed`() {
        fs.add(p("Download/doc.pdf"), bytes(10, 1))
        fs.add(p("Download/anim.gif"), bytes(10, 2))

        assertEquals(emptyList<SourceFolder>(), sources.list())
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd /s && GRADLE_USER_HOME=C:/gradle-home ./gradlew :core:test`
Expected: FAIL — `Unresolved reference: PhotoSources`.

- [ ] **Step 3: Implement**

```kotlin
package com.smartspacecleaner.core.sort

import com.smartspacecleaner.core.model.FileEntry
import com.smartspacecleaner.core.port.FileSystem
import com.smartspacecleaner.core.scan.StorageLayout

/** Папка-источник для шага «Откуда разложить»; [files] — только файлы, лежащие прямо в ней. */
data class SourceFolder(val path: String, val files: List<FileEntry>) {
    val photos: Int get() = files.count { SortMedia.isPhoto(it.path) }
    val videos: Int get() = files.count { SortMedia.isVideo(it.path) }
    val bytes: Long get() = files.sumOf { it.size }
}

class PhotoSources(private val fs: FileSystem, private val layout: StorageLayout) {
    /** Папки с фото и видео, самые тяжёлые сверху. Уже разложенное и служебное не показываем. */
    fun list(): List<SourceFolder> =
        fs.walk(layout.root, layout.excludedPrefixes + layout.sortRoot)
            .filter { SortMedia.isSortable(it.path) && !layout.isHidden(it.path) }
            .groupBy { it.path.substringBeforeLast('/') }
            .map { (dir, files) -> SourceFolder(dir, files) }
            .sortedByDescending { it.bytes }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `cd /s && GRADLE_USER_HOME=C:/gradle-home ./gradlew :core:test`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add core
git commit -m "feat(core): list source folders with photos and videos for sorting

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 4: План раскладки — `SortPlan` и `SortAnalysis`

**Files:**
- Create: `core/src/main/kotlin/com/smartspacecleaner/core/sort/SortPlan.kt`
- Test: `core/src/test/kotlin/com/smartspacecleaner/core/sort/SortPlanTest.kt`

**Interfaces:**
- Consumes: `PhotoClassifier`, `SortDecision`, `SortCategory`, `FakePhotoAnalyzer` (задачи 1–2), `FileEntry`.
- Produces:
  - `data class SortItem(val path: String, val size: Long, val decision: SortDecision)`
  - `data class SortSection(val category: SortCategory?, val items: List<SortItem>)` со свойством `bytes: Long`
  - `data class SortMove(val path: String, val size: Long, val category: SortCategory)`
  - `data class SortPlan(val items: List<SortItem>)` с методами `sections(): List<SortSection>`, `defaultSelection(): Set<String>`, `reassign(path: String, category: SortCategory): SortPlan`, `moves(selected: Set<String>): List<SortMove>`, `reviewOnly(): SortPlan`, `without(paths: Set<String>): SortPlan` и свойством `videos: Int`
  - `class SortAnalysis(classifier: PhotoClassifier) { fun run(files: List<FileEntry>, onProgress: (Int) -> Unit = {}, shouldStop: () -> Boolean = { false }): SortPlan? }`

- [ ] **Step 1: Write the failing test**

```kotlin
package com.smartspacecleaner.core.sort

import com.smartspacecleaner.core.fakes.FakePhotoAnalyzer
import com.smartspacecleaner.core.fakes.ROOT
import com.smartspacecleaner.core.fakes.T0
import com.smartspacecleaner.core.fakes.p
import com.smartspacecleaner.core.model.FileEntry
import com.smartspacecleaner.core.scan.StorageLayout
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class SortPlanTest {
    private fun item(name: String, category: SortCategory?, size: Long = 10) = SortItem(p("DCIM/Camera/$name"), size, SortDecision(category, "r"))

    private val plan = SortPlan(
        listOf(
            item("cat.jpg", SortCategory.ANIMALS),
            item("doubt.jpg", null),
            item("doc.jpg", SortCategory.DOCUMENTS, 30),
            item("clip.mp4", SortCategory.VIDEO),
            item("doc2.jpg", SortCategory.DOCUMENTS, 20),
        ),
    )

    @Test
    fun `review comes first, then categories in their fixed order, empty ones hidden`() {
        val sections = plan.sections()
        assertEquals(listOf(null, SortCategory.DOCUMENTS, SortCategory.ANIMALS, SortCategory.VIDEO), sections.map { it.category })
        assertEquals(50L, sections[1].bytes)
    }

    @Test
    fun `everything with a category is selected by default, review is not`() {
        assertEquals(setOf("cat.jpg", "doc.jpg", "clip.mp4", "doc2.jpg").map { p("DCIM/Camera/$it") }.toSet(), plan.defaultSelection())
    }

    @Test
    fun `moves include only selected files that have a category`() {
        val moves = plan.moves(setOf(p("DCIM/Camera/cat.jpg"), p("DCIM/Camera/doubt.jpg")))
        assertEquals(listOf(SortMove(p("DCIM/Camera/cat.jpg"), 10, SortCategory.ANIMALS)), moves)
    }

    @Test
    fun `reassigning moves a file into the chosen section`() {
        val changed = plan.reassign(p("DCIM/Camera/doubt.jpg"), SortCategory.NATURE)
        val decision = changed.items.first { it.path == p("DCIM/Camera/doubt.jpg") }.decision
        assertEquals(SortDecision(SortCategory.NATURE, "вы выбрали «Природа»"), decision)
        assertNull(changed.sections().firstOrNull { it.category == null })
    }

    @Test
    fun `review-only plan and plan without moved files`() {
        assertEquals(listOf(p("DCIM/Camera/doubt.jpg")), plan.reviewOnly().items.map { it.path })
        assertEquals(4, plan.without(setOf(p("DCIM/Camera/cat.jpg"))).items.size)
        assertEquals(1, plan.videos)
    }

    @Test
    fun `analysis classifies every file and reports progress`() {
        val files = listOf(FileEntry(p("DCIM/Camera/a.jpg"), 5, T0), FileEntry(p("DCIM/Camera/v.mp4"), 7, T0))
        val progress = mutableListOf<Int>()

        val result = SortAnalysis(PhotoClassifier(StorageLayout(ROOT), FakePhotoAnalyzer())).run(files, onProgress = { progress += it })!!

        assertEquals(listOf(SortCategory.OTHER, SortCategory.VIDEO), result.items.map { it.decision.category })
        assertEquals(listOf(5L, 7L), result.items.map { it.size })
        assertEquals(listOf(1, 2), progress)
    }

    @Test
    fun `stopped analysis returns nothing and looks at no more files`() {
        val analyzer = FakePhotoAnalyzer()
        val files = (1..5).map { FileEntry(p("DCIM/Camera/$it.jpg"), 1, T0) }
        var seen = 0

        val result = SortAnalysis(PhotoClassifier(StorageLayout(ROOT), analyzer)).run(files, onProgress = { seen = it }, shouldStop = { seen >= 2 })

        assertNull(result)
        assertEquals(2, analyzer.asked.size)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd /s && GRADLE_USER_HOME=C:/gradle-home ./gradlew :core:test`
Expected: FAIL — `Unresolved reference: SortPlan`.

- [ ] **Step 3: Implement**

```kotlin
package com.smartspacecleaner.core.sort

import com.smartspacecleaner.core.model.FileEntry

data class SortItem(val path: String, val size: Long, val decision: SortDecision)

/** [category] = null — раздел «Проверьте». */
data class SortSection(val category: SortCategory?, val items: List<SortItem>) {
    val bytes: Long get() = items.sumOf { it.size }
}

data class SortMove(val path: String, val size: Long, val category: SortCategory)

data class SortPlan(val items: List<SortItem>) {
    val videos: Int get() = items.count { it.decision.category == SortCategory.VIDEO }

    /** «Проверьте» первым, дальше категории в порядке [SortCategory]; пустые разделы не показываем. */
    fun sections(): List<SortSection> {
        val byCategory = items.groupBy { it.decision.category }
        return (listOf<SortCategory?>(null) + SortCategory.entries).mapNotNull { c -> byCategory[c]?.let { SortSection(c, it) } }
    }

    /** Заранее отмечено всё, чему выбрана категория; «Проверьте» — нет. */
    fun defaultSelection(): Set<String> = items.filter { it.decision.category != null }.map { it.path }.toSet()

    fun reassign(path: String, category: SortCategory): SortPlan = copy(
        items = items.map { if (it.path == path) it.copy(decision = SortDecision(category, "вы выбрали «${category.title}»")) else it },
    )

    fun moves(selected: Set<String>): List<SortMove> = items.mapNotNull { item ->
        item.decision.category?.takeIf { item.path in selected }?.let { SortMove(item.path, item.size, it) }
    }

    fun reviewOnly(): SortPlan = SortPlan(items.filter { it.decision.category == null })

    fun without(paths: Set<String>): SortPlan = SortPlan(items.filter { it.path !in paths })
}

class SortAnalysis(private val classifier: PhotoClassifier) {
    /** null — анализ остановили. Ничего ещё не перенесено, поэтому остановка безопасна. */
    fun run(files: List<FileEntry>, onProgress: (Int) -> Unit = {}, shouldStop: () -> Boolean = { false }): SortPlan? {
        val items = ArrayList<SortItem>(files.size)
        files.forEachIndexed { i, file ->
            if (shouldStop()) return null
            items += SortItem(file.path, file.size, classifier.classify(file.path))
            onProgress(i + 1)
        }
        return SortPlan(items)
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `cd /s && GRADLE_USER_HOME=C:/gradle-home ./gradlew :core:test`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add core
git commit -m "feat(core): sort plan with sections, selection, reassign and cancellable analysis

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 5: Перенос — `SortExecutor` и разбор после сбоя

**Files:**
- Create: `core/src/main/kotlin/com/smartspacecleaner/core/sort/SortExecutor.kt`
- Modify: `core/src/main/kotlin/com/smartspacecleaner/core/action/PendingReconciler.kt`
- Test: `core/src/test/kotlin/com/smartspacecleaner/core/sort/SortExecutorTest.kt`
- Test: `core/src/test/kotlin/com/smartspacecleaner/core/action/PendingReconcilerTest.kt` (добавить тесты)

**Interfaces:**
- Consumes: `SortMove`, `SortCategory.folder`, `freeTarget`, `ActionKind.SORT_MOVE`, `ActionRecord.movedToPath` (задачи 1, 4).
- Produces:
  - `data class SortResult(val records: List<ActionRecord>)` со свойствами `moved: List<ActionRecord>`, `failed: List<ActionRecord>`, `touchedPaths: List<String>`
  - `class SortExecutor(fs: FileSystem, log: ActionLogStore, clock: Clock, layout: StorageLayout) { fun execute(moves: List<SortMove>, sessionId: String, onProgress: (Int) -> Unit = {}): SortResult }`
  - запись журнала: `groupId = "sort:<CATEGORY.name>"`, `groupTitle = <category.title>`

- [ ] **Step 1: Write the failing tests**

`core/src/test/kotlin/com/smartspacecleaner/core/sort/SortExecutorTest.kt`:

```kotlin
package com.smartspacecleaner.core.sort

import com.smartspacecleaner.core.fakes.FakeClock
import com.smartspacecleaner.core.fakes.FakeFileSystem
import com.smartspacecleaner.core.fakes.InMemoryActionLogStore
import com.smartspacecleaner.core.fakes.ROOT
import com.smartspacecleaner.core.fakes.T0
import com.smartspacecleaner.core.fakes.bytes
import com.smartspacecleaner.core.fakes.p
import com.smartspacecleaner.core.model.ActionKind
import com.smartspacecleaner.core.model.ActionStatus
import com.smartspacecleaner.core.scan.StorageLayout
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SortExecutorTest {
    private val fs = FakeFileSystem()
    private val log = InMemoryActionLogStore()
    private val executor = SortExecutor(fs, log, FakeClock(T0), StorageLayout(ROOT))

    @Test
    fun `moves a file into its category folder and journals it`() {
        val content = bytes(100, 1)
        fs.add(p("DCIM/Camera/a.jpg"), content)

        val result = executor.execute(listOf(SortMove(p("DCIM/Camera/a.jpg"), 100, SortCategory.NATURE)), "s1")

        val target = p("Pictures/Сортировка/Природа/a.jpg")
        assertFalse(fs.exists(p("DCIM/Camera/a.jpg")))
        assertArrayEquals(content, fs.content(target))
        val r = log.all().single()
        assertEquals(ActionKind.SORT_MOVE, r.kind)
        assertEquals(ActionStatus.DONE, r.status)
        assertEquals("s1", r.sessionId)
        assertEquals("sort:NATURE", r.groupId)
        assertEquals("Природа", r.groupTitle)
        assertEquals(target, r.movedToPath)
        assertEquals(listOf(p("DCIM/Camera/a.jpg"), target), result.touchedPaths)
    }

    @Test
    fun `same name from two folders in one batch gets a numbered suffix`() {
        fs.add(p("DCIM/Camera/IMG_1.jpg"), bytes(10, 1))
        fs.add(p("Download/IMG_1.jpg"), bytes(10, 2))

        executor.execute(
            listOf(
                SortMove(p("DCIM/Camera/IMG_1.jpg"), 10, SortCategory.FOOD),
                SortMove(p("Download/IMG_1.jpg"), 10, SortCategory.FOOD),
            ),
            "s1",
        )

        assertArrayEquals(bytes(10, 1), fs.content(p("Pictures/Сортировка/Еда/IMG_1.jpg")))
        assertArrayEquals(bytes(10, 2), fs.content(p("Pictures/Сортировка/Еда/IMG_1 (1).jpg")))
    }

    @Test
    fun `existing file in the category folder is never overwritten`() {
        fs.add(p("Pictures/Сортировка/Еда/a.jpg"), bytes(10, 9))
        fs.add(p("DCIM/Camera/a.jpg"), bytes(10, 1))

        executor.execute(listOf(SortMove(p("DCIM/Camera/a.jpg"), 10, SortCategory.FOOD)), "s1")

        assertArrayEquals(bytes(10, 9), fs.content(p("Pictures/Сортировка/Еда/a.jpg")))
        assertArrayEquals(bytes(10, 1), fs.content(p("Pictures/Сортировка/Еда/a (1).jpg")))
    }

    @Test
    fun `a file that vanished after analysis fails, the rest still move`() {
        fs.add(p("DCIM/Camera/b.jpg"), bytes(10, 2))

        val result = executor.execute(
            listOf(
                SortMove(p("DCIM/Camera/gone.jpg"), 10, SortCategory.OTHER),
                SortMove(p("DCIM/Camera/b.jpg"), 10, SortCategory.OTHER),
            ),
            "s1",
        )

        assertEquals(listOf("файл исчез"), result.failed.map { it.reason })
        assertEquals(1, result.moved.size)
        assertTrue(fs.exists(p("Pictures/Сортировка/Разное/b.jpg")))
    }

    @Test
    fun `a move the system refuses is recorded as failed`() {
        fs.add(p("DCIM/Camera/a.jpg"), bytes(10, 1))
        fs.failingPaths += p("DCIM/Camera/a.jpg")

        val result = executor.execute(listOf(SortMove(p("DCIM/Camera/a.jpg"), 10, SortCategory.OTHER)), "s1")

        assertEquals(ActionStatus.FAILED, result.records.single().status)
        assertEquals("не удалось перенести", result.records.single().reason)
        assertTrue(fs.exists(p("DCIM/Camera/a.jpg")))
        assertTrue(result.touchedPaths.isEmpty())
    }

    @Test
    fun `progress is reported after every file`() {
        fs.add(p("a/1.jpg"), bytes(1, 1))
        fs.add(p("a/2.jpg"), bytes(1, 2))
        val progress = mutableListOf<Int>()

        executor.execute(listOf(SortMove(p("a/1.jpg"), 1, SortCategory.OTHER), SortMove(p("a/2.jpg"), 1, SortCategory.OTHER)), "s1") { progress += it }

        assertEquals(listOf(1, 2), progress)
    }
}
```

В `PendingReconcilerTest.kt` добавить в класс (импорт `com.smartspacecleaner.core.fakes.p` уже есть):

```kotlin
    private fun pendingSort(): Long = log.insert(
        ActionRecord(
            sessionId = "s", groupId = "sort:OTHER", groupTitle = "Разное", kind = ActionKind.SORT_MOVE,
            originalPath = p("DCIM/a.jpg"), size = 10, status = ActionStatus.PENDING, createdAt = T0,
            movedToPath = p("Pictures/Сортировка/Разное/a.jpg"),
        ),
    )

    @Test
    fun `interrupted sort with the file still in place is marked failed`() {
        fs.add(p("DCIM/a.jpg"), bytes(10, 1))
        val id = pendingSort()
        reconciler.reconcile()
        assertEquals(ActionStatus.FAILED, log.get(id)!!.status)
        assertEquals("прервано: файл остался на месте", log.get(id)!!.reason)
    }

    @Test
    fun `interrupted sort with the file already moved is done`() {
        fs.add(p("Pictures/Сортировка/Разное/a.jpg"), bytes(10, 1))
        val id = pendingSort()
        reconciler.reconcile()
        assertEquals(ActionStatus.DONE, log.get(id)!!.status)
    }

    @Test
    fun `interrupted sort with the file nowhere is failed, not done`() {
        val id = pendingSort()
        reconciler.reconcile()
        assertEquals(ActionStatus.FAILED, log.get(id)!!.status)
        assertEquals("файл не найден после сбоя", log.get(id)!!.reason)
    }
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `cd /s && GRADLE_USER_HOME=C:/gradle-home ./gradlew :core:test`
Expected: FAIL — `Unresolved reference: SortExecutor`.

- [ ] **Step 3: Implement**

`SortExecutor.kt`:

```kotlin
package com.smartspacecleaner.core.sort

import com.smartspacecleaner.core.action.freeTarget
import com.smartspacecleaner.core.model.ActionKind
import com.smartspacecleaner.core.model.ActionRecord
import com.smartspacecleaner.core.model.ActionStatus
import com.smartspacecleaner.core.port.ActionLogStore
import com.smartspacecleaner.core.port.Clock
import com.smartspacecleaner.core.port.FileSystem
import com.smartspacecleaner.core.scan.StorageLayout
import java.time.Instant

data class SortResult(val records: List<ActionRecord>) {
    val moved: List<ActionRecord> get() = records.filter { it.status == ActionStatus.DONE }
    val failed: List<ActionRecord> get() = records.filter { it.status == ActionStatus.FAILED }

    /** Старые и новые пути перенесённых файлов — их показываем медиасканеру, чтобы галерея обновилась. */
    val touchedPaths: List<String> get() = moved.flatMap { listOfNotNull(it.originalPath, it.movedToPath) }
}

/** Переносит файлы по папкам категорий. Как и корзина: сначала запись в журнал, потом действие. */
class SortExecutor(
    private val fs: FileSystem,
    private val log: ActionLogStore,
    private val clock: Clock,
    private val layout: StorageLayout,
) {
    fun execute(moves: List<SortMove>, sessionId: String, onProgress: (Int) -> Unit = {}): SortResult {
        val now = clock.now()
        val records = moves.mapIndexed { i, move -> move(move, sessionId, now).also { onProgress(i + 1) } }
        return SortResult(records)
    }

    private fun move(m: SortMove, sessionId: String, now: Instant): ActionRecord {
        val target = freeTarget(fs, "${m.category.folder(layout)}/${m.path.substringAfterLast('/')}") { "$it" }
        val pending = ActionRecord(
            sessionId = sessionId,
            groupId = "sort:${m.category.name}",
            groupTitle = m.category.title,
            kind = ActionKind.SORT_MOVE,
            originalPath = m.path,
            size = m.size,
            movedToPath = target,
            status = ActionStatus.PENDING,
            createdAt = now,
        )
        val record = pending.copy(id = log.insert(pending))
        val finished = when {
            !fs.exists(m.path) -> record.copy(status = ActionStatus.FAILED, reason = "файл исчез")
            fs.mkdirs(target.substringBeforeLast('/')) && fs.move(m.path, target) -> record.copy(status = ActionStatus.DONE)
            else -> record.copy(status = ActionStatus.FAILED, reason = "не удалось перенести")
        }
        log.update(finished)
        return finished
    }
}
```

`PendingReconciler.kt` — `resolve` целиком:

```kotlin
    private fun resolve(r: ActionRecord): ActionRecord = when {
        r.kind == ActionKind.SORT_MOVE -> resolveSort(r)
        fs.exists(r.originalPath) ->
            r.copy(status = ActionStatus.FAILED, reason = "прервано: действие не завершилось, файл на месте")
        r.kind == ActionKind.TRASH && r.trashPath != null && fs.exists(r.trashPath) ->
            r.copy(status = ActionStatus.DONE)
        r.kind == ActionKind.TRASH ->
            r.copy(status = ActionStatus.FAILED, reason = "файл потерян при сбое: его нет ни на месте, ни в корзине")
        else ->
            r.copy(status = ActionStatus.DONE)
    }

    private fun resolveSort(r: ActionRecord): ActionRecord = when {
        fs.exists(r.originalPath) -> r.copy(status = ActionStatus.FAILED, reason = "прервано: файл остался на месте")
        r.movedToPath != null && fs.exists(r.movedToPath) -> r.copy(status = ActionStatus.DONE)
        else -> r.copy(status = ActionStatus.FAILED, reason = "файл не найден после сбоя")
    }
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `cd /s && GRADLE_USER_HOME=C:/gradle-home ./gradlew :core:test`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add core
git commit -m "feat(core): journaled sort executor and crash reconciliation for SORT_MOVE

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 6: «Вернуть всё на место» — `SortRestorer`

**Files:**
- Create: `core/src/main/kotlin/com/smartspacecleaner/core/sort/SortRestorer.kt`
- Test: `core/src/test/kotlin/com/smartspacecleaner/core/sort/SortRestorerTest.kt`

**Interfaces:**
- Consumes: `SortExecutor`, `SortMove`, `SortCategory`, `freeTarget`, `FileSystem.deleteEmptyDir`, `FakeFileSystem.deletedDirs` (задачи 1, 5).
- Produces:
  - `data class SortRestore(val restored: Int, val skipped: Int, val touchedPaths: List<String>)`
  - `class SortRestorer(fs: FileSystem, log: ActionLogStore, layout: StorageLayout) { fun restoreSession(sessionId: String): SortRestore }`

- [ ] **Step 1: Write the failing test**

```kotlin
package com.smartspacecleaner.core.sort

import com.smartspacecleaner.core.fakes.FakeClock
import com.smartspacecleaner.core.fakes.FakeFileSystem
import com.smartspacecleaner.core.fakes.InMemoryActionLogStore
import com.smartspacecleaner.core.fakes.ROOT
import com.smartspacecleaner.core.fakes.T0
import com.smartspacecleaner.core.fakes.bytes
import com.smartspacecleaner.core.fakes.p
import com.smartspacecleaner.core.model.ActionStatus
import com.smartspacecleaner.core.scan.StorageLayout
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SortRestorerTest {
    private val fs = FakeFileSystem()
    private val log = InMemoryActionLogStore()
    private val layout = StorageLayout(ROOT)
    private val restorer = SortRestorer(fs, log, layout)

    private fun sort(session: String, vararg moves: Pair<String, SortCategory>) {
        moves.forEachIndexed { i, (rel, _) -> if (!fs.exists(p(rel))) fs.add(p(rel), bytes(10, i + 1)) }
        SortExecutor(fs, log, FakeClock(T0), layout).execute(moves.map { (rel, c) -> SortMove(p(rel), 10, c) }, session)
    }

    @Test
    fun `puts every file of the session back where it was`() {
        sort("s1", "DCIM/Camera/a.jpg" to SortCategory.NATURE, "Download/b.jpg" to SortCategory.FOOD)

        val result = restorer.restoreSession("s1")

        assertEquals(SortRestore(2, 0, result.touchedPaths), result)
        assertTrue(fs.exists(p("DCIM/Camera/a.jpg")))
        assertTrue(fs.exists(p("Download/b.jpg")))
        assertFalse(fs.exists(p("Pictures/Сортировка/Природа/a.jpg")))
        assertTrue(log.all().all { it.status == ActionStatus.RESTORED })
        assertTrue(p("Pictures/Сортировка/Природа/a.jpg") in result.touchedPaths && p("DCIM/Camera/a.jpg") in result.touchedPaths)
    }

    @Test
    fun `occupied original place gets a numbered suffix, nothing is overwritten`() {
        sort("s1", "DCIM/Camera/a.jpg" to SortCategory.NATURE)
        fs.add(p("DCIM/Camera/a.jpg"), bytes(10, 99))

        restorer.restoreSession("s1")

        assertArrayEquals(bytes(10, 99), fs.content(p("DCIM/Camera/a.jpg")))
        assertArrayEquals(bytes(10, 1), fs.content(p("DCIM/Camera/a (1).jpg")))
        assertEquals("возвращён как a (1).jpg", log.all().single().reason)
    }

    @Test
    fun `file already back from an interrupted restore counts as restored`() {
        sort("s1", "DCIM/Camera/a.jpg" to SortCategory.NATURE)
        fs.move(p("Pictures/Сортировка/Природа/a.jpg"), p("DCIM/Camera/a.jpg"))

        assertEquals(1, restorer.restoreSession("s1").restored)
        assertEquals(ActionStatus.RESTORED, log.all().single().status)
    }

    @Test
    fun `file deleted from the sort folder is skipped, others come back`() {
        sort("s1", "DCIM/Camera/a.jpg" to SortCategory.NATURE, "DCIM/Camera/b.jpg" to SortCategory.NATURE)
        fs.delete(p("Pictures/Сортировка/Природа/a.jpg"))

        val result = restorer.restoreSession("s1")

        assertEquals(1, result.restored)
        assertEquals(1, result.skipped)
        assertEquals("уже не в папке сортировки", log.all().first().reason)
        assertTrue(fs.exists(p("DCIM/Camera/b.jpg")))
    }

    @Test
    fun `only the chosen session is restored`() {
        sort("s1", "DCIM/Camera/a.jpg" to SortCategory.NATURE)
        sort("s2", "DCIM/Camera/b.jpg" to SortCategory.FOOD)

        restorer.restoreSession("s1")

        assertTrue(fs.exists(p("Pictures/Сортировка/Еда/b.jpg")))
    }

    @Test
    fun `empty category folders and the empty root are removed, busy ones stay`() {
        sort("s1", "DCIM/Camera/a.jpg" to SortCategory.NATURE)
        sort("s2", "DCIM/Camera/b.jpg" to SortCategory.FOOD)

        restorer.restoreSession("s1")
        assertTrue(p("Pictures/Сортировка/Природа") in fs.deletedDirs)
        assertFalse(p("Pictures/Сортировка/Еда") in fs.deletedDirs)
        assertFalse(p("Pictures/Сортировка") in fs.deletedDirs)

        restorer.restoreSession("s2")
        assertTrue(p("Pictures/Сортировка") in fs.deletedDirs)
    }

    @Test
    fun `restoring twice does nothing the second time`() {
        sort("s1", "DCIM/Camera/a.jpg" to SortCategory.NATURE)
        restorer.restoreSession("s1")

        assertEquals(SortRestore(0, 0, emptyList()), restorer.restoreSession("s1"))
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd /s && GRADLE_USER_HOME=C:/gradle-home ./gradlew :core:test`
Expected: FAIL — `Unresolved reference: SortRestorer`.

- [ ] **Step 3: Implement**

```kotlin
package com.smartspacecleaner.core.sort

import com.smartspacecleaner.core.action.freeTarget
import com.smartspacecleaner.core.model.ActionKind
import com.smartspacecleaner.core.model.ActionRecord
import com.smartspacecleaner.core.model.ActionStatus
import com.smartspacecleaner.core.port.ActionLogStore
import com.smartspacecleaner.core.port.FileSystem
import com.smartspacecleaner.core.scan.StorageLayout

data class SortRestore(val restored: Int, val skipped: Int, val touchedPaths: List<String>)

/** «Вернуть всё на место»: переносит файлы одной сортировки обратно; один пропавший файл не мешает остальным. */
class SortRestorer(
    private val fs: FileSystem,
    private val log: ActionLogStore,
    private val layout: StorageLayout,
) {
    fun restoreSession(sessionId: String): SortRestore {
        val records = log.all().filter { it.sessionId == sessionId && it.kind == ActionKind.SORT_MOVE && it.status == ActionStatus.DONE }
        var restored = 0
        var skipped = 0
        val touched = mutableListOf<String>()
        records.forEach { r ->
            val moved = r.movedToPath
            when {
                moved != null && fs.exists(moved) -> {
                    val target = freeTarget(fs, r.originalPath) { "$it" }
                    if (fs.mkdirs(target.substringBeforeLast('/')) && fs.move(moved, target)) {
                        val note = if (target != r.originalPath) "возвращён как ${target.substringAfterLast('/')}" else null
                        log.update(r.copy(status = ActionStatus.RESTORED, reason = note))
                        touched += moved
                        touched += target
                        restored++
                    } else {
                        fail(r, "не удалось вернуть")
                        skipped++
                    }
                }
                // Возврат прервался в прошлый раз уже после переноса.
                fs.exists(r.originalPath) -> {
                    log.update(r.copy(status = ActionStatus.RESTORED))
                    restored++
                }
                else -> {
                    fail(r, "уже не в папке сортировки")
                    skipped++
                }
            }
        }
        SortCategory.entries.forEach { fs.deleteEmptyDir(it.folder(layout)) }
        fs.deleteEmptyDir(layout.sortRoot)
        return SortRestore(restored, skipped, touched)
    }

    private fun fail(r: ActionRecord, reason: String) = log.update(r.copy(status = ActionStatus.FAILED, reason = reason))
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `cd /s && GRADLE_USER_HOME=C:/gradle-home ./gradlew :core:test`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add core
git commit -m "feat(core): restore a whole sort session back to the original folders

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 7: История и очистка знают о сортировке

**Files:**
- Modify: `core/src/main/kotlin/com/smartspacecleaner/core/report/HistorySections.kt`
- Create: `core/src/main/kotlin/com/smartspacecleaner/core/sort/SortSessions.kt`
- Modify: `core/src/main/kotlin/com/smartspacecleaner/core/action/HistoryCleaner.kt`
- Modify: `core/src/main/kotlin/com/smartspacecleaner/core/scan/Rules.kt` (`OldScreenshotRule`)
- Test: `core/src/test/kotlin/com/smartspacecleaner/core/sort/SortHistoryTest.kt`

**Interfaces:**
- Consumes: `ActionKind.SORT_MOVE`, `SortCategory.SCREENSHOTS.relativeFolder` (задача 1).
- Produces:
  - `data class SortSession(val sessionId: String, val createdAt: Instant, val moved: Int, val restored: Int, val failed: Int)` со свойством `canRestore: Boolean`
  - `object SortSessions { fun of(records: List<ActionRecord>): List<SortSession> }` — новые сверху
  - `HistorySections.of(...)` больше не содержит записей `SORT_MOVE`

- [ ] **Step 1: Write the failing test**

```kotlin
package com.smartspacecleaner.core.sort

import com.smartspacecleaner.core.action.HistoryCleaner
import com.smartspacecleaner.core.action.TrashPurger
import com.smartspacecleaner.core.fakes.FakeClock
import com.smartspacecleaner.core.fakes.FakeFileSystem
import com.smartspacecleaner.core.fakes.InMemoryActionLogStore
import com.smartspacecleaner.core.fakes.ROOT
import com.smartspacecleaner.core.fakes.T0
import com.smartspacecleaner.core.fakes.bytes
import com.smartspacecleaner.core.fakes.p
import com.smartspacecleaner.core.model.ActionKind
import com.smartspacecleaner.core.model.ActionRecord
import com.smartspacecleaner.core.model.ActionStatus
import com.smartspacecleaner.core.model.FileEntry
import com.smartspacecleaner.core.report.HistorySections
import com.smartspacecleaner.core.scan.OldScreenshotRule
import com.smartspacecleaner.core.scan.StorageLayout
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Duration
import java.time.Instant

class SortHistoryTest {
    private val log = InMemoryActionLogStore()

    private fun sorted(session: String, status: ActionStatus = ActionStatus.DONE, at: Instant = T0, name: String = "a.jpg") = log.insert(
        ActionRecord(
            sessionId = session, groupId = "sort:NATURE", groupTitle = "Природа", kind = ActionKind.SORT_MOVE,
            originalPath = p("DCIM/Camera/$name"), size = 10, status = status, createdAt = at,
            movedToPath = p("Pictures/Сортировка/Природа/$name"),
        ),
    )

    @Test
    fun `sorted files are not listed one by one in the trash history`() {
        sorted("s1")
        sorted("s1", ActionStatus.RESTORED, name = "b.jpg")

        val sections = HistorySections.of(log.all())

        assertTrue(sections.inTrash.isEmpty() && sections.earlier.isEmpty())
    }

    @Test
    fun `one sort is one session with counts, newest first`() {
        sorted("old", at = T0)
        sorted("new", at = T0.plusSeconds(60))
        sorted("new", ActionStatus.RESTORED, at = T0.plusSeconds(60), name = "b.jpg")
        sorted("new", ActionStatus.FAILED, at = T0.plusSeconds(60), name = "c.jpg")

        val sessions = SortSessions.of(log.all())

        assertEquals(listOf("new", "old"), sessions.map { it.sessionId })
        assertEquals(SortSession("new", T0.plusSeconds(60), moved = 1, restored = 1, failed = 1), sessions.first())
        assertTrue(sessions.first().canRestore)
    }

    @Test
    fun `fully restored session can not be restored again`() {
        sorted("s1", ActionStatus.RESTORED)
        assertEquals(false, SortSessions.of(log.all()).single().canRestore)
    }

    @Test
    fun `clearing history right after sorting keeps the way back`() {
        val kept = sorted("s1")
        sorted("s1", ActionStatus.RESTORED, name = "b.jpg")

        HistoryCleaner(log, FakeClock(T0)).clearEarlier()

        assertEquals(listOf(kept), log.all().map { it.id })
    }

    @Test
    fun `sorted records are forgotten after 15 days, files stay where they are`() {
        val young = sorted("young", at = T0.minus(Duration.ofDays(14)))
        sorted("old", at = T0.minus(Duration.ofDays(16)))

        HistoryCleaner(log, FakeClock(T0)).forgetOld()

        assertEquals(listOf(young), log.all().map { it.id })
    }

    @Test
    fun `trash purge never deletes sorted files`() {
        val fs = FakeFileSystem()
        fs.add(p("Pictures/Сортировка/Природа/a.jpg"), bytes(10, 1))
        val id = sorted("s1", at = T0.minus(Duration.ofDays(30)))

        val purger = TrashPurger(fs, log, FakeClock(T0))
        purger.purgeExpired()
        purger.purgeNow(listOf(id))

        assertTrue(fs.exists(p("Pictures/Сортировка/Природа/a.jpg")))
        assertEquals(ActionStatus.DONE, log.get(id)!!.status)
    }

    @Test
    fun `cleanup still finds old screenshots after they were sorted`() {
        val old = FileEntry(p("Pictures/Сортировка/Скриншоты/s.png"), 10, T0.minus(Duration.ofDays(200)))

        val found = OldScreenshotRule(StorageLayout(ROOT), FakeClock(T0)).find(listOf(old))

        assertEquals(listOf(old), found)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd /s && GRADLE_USER_HOME=C:/gradle-home ./gradlew :core:test`
Expected: FAIL — `Unresolved reference: SortSessions`.

- [ ] **Step 3: Implement**

`HistorySections.kt` — тело `of`:

```kotlin
        fun of(records: List<ActionRecord>): HistorySections {
            // Сортировка показывается одной записью на сессию (SortSessions), а не по файлу.
            val (inTrash, earlier) = records.filter { it.kind != ActionKind.SORT_MOVE }
                .partition { it.status == ActionStatus.DONE && it.kind == ActionKind.TRASH }
            return HistorySections(inTrash, earlier)
        }
```

`core/src/main/kotlin/com/smartspacecleaner/core/sort/SortSessions.kt`:

```kotlin
package com.smartspacecleaner.core.sort

import com.smartspacecleaner.core.model.ActionKind
import com.smartspacecleaner.core.model.ActionRecord
import com.smartspacecleaner.core.model.ActionStatus
import java.time.Instant

/** Одна сортировка в Истории: [moved] лежат в папках сортировки, [restored] возвращены, [failed] не получились. */
data class SortSession(val sessionId: String, val createdAt: Instant, val moved: Int, val restored: Int, val failed: Int) {
    val canRestore: Boolean get() = moved > 0
}

object SortSessions {
    fun of(records: List<ActionRecord>): List<SortSession> =
        records.filter { it.kind == ActionKind.SORT_MOVE }
            .groupBy { it.sessionId }
            .map { (id, rs) ->
                SortSession(
                    sessionId = id,
                    createdAt = rs.minOf { it.createdAt },
                    moved = rs.count { it.status == ActionStatus.DONE },
                    restored = rs.count { it.status == ActionStatus.RESTORED },
                    failed = rs.count { it.status == ActionStatus.FAILED },
                )
            }
            .sortedByDescending { it.createdAt }
}
```

`HistoryCleaner.kt` — `forgetOld` и новая функция:

```kotlin
    fun forgetOld(): Int {
        val cutoff = clock.now().minus(keep)
        return forget(log.all().filter { (isFinished(it) || isSorted(it)) && it.createdAt.isBefore(cutoff) })
    }
```

```kotlin
    /** Разложенный файл: через 15 дней запись забывается, сам файл остаётся в папке. «Очистить историю» его не трогает — иначе пропадёт «Вернуть всё на место». */
    private fun isSorted(r: ActionRecord): Boolean = r.kind == ActionKind.SORT_MOVE && r.status == ActionStatus.DONE
```

`Rules.kt` — `OldScreenshotRule.find`, условие пути (и импорт `com.smartspacecleaner.core.sort.SortCategory`):

```kotlin
            (
                layout.isUnder(it.path, "DCIM/Screenshots") || layout.isUnder(it.path, "Pictures/Screenshots") ||
                    layout.isUnder(it.path, SortCategory.SCREENSHOTS.relativeFolder)
                ) &&
                it.modifiedAt.isBefore(cutoff)
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `cd /s && GRADLE_USER_HOME=C:/gradle-home ./gradlew :core:test`
Expected: PASS, включая старые `HistorySectionsTest` и `RulesTest`.

- [ ] **Step 5: Commit**

```bash
git add core
git commit -m "feat(core): sort sessions in history, keep undo on clear, purge and screenshot rule aware of sorting

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 8: Тексты — `SortTexts` и подсказки «?»

**Files:**
- Create: `core/src/main/kotlin/com/smartspacecleaner/core/sort/SortTexts.kt`
- Modify: `core/src/main/kotlin/com/smartspacecleaner/core/help/HelpTexts.kt`
- Test: `core/src/test/kotlin/com/smartspacecleaner/core/sort/SortTextsTest.kt`
- Test: `core/src/test/kotlin/com/smartspacecleaner/core/help/HelpTextsTest.kt` (добавить тест)

**Interfaces:**
- Consumes: `SourceFolder`, `SortSection`, `SortPlan`, `SortRestore`, `SortSession`, `SortCategory` (задачи 1–7); `formatBytes`, `displayPath`, `plural`.
- Produces (`object SortTexts`):
  - `fun folderLine(f: SourceFolder): String`
  - `fun sectionTitle(s: SortSection): String`
  - `fun confirmMove(count: Int): String`
  - `fun quickConfirm(plan: SortPlan): String`
  - `const val NOTHING_SURE: String`
  - `fun result(moved: Int, folders: Int, left: Int, failed: Int): String`
  - `fun restored(r: SortRestore): String`
  - `fun sessionTitle(s: SortSession): String`
  - `fun sessionStatus(s: SortSession): String`
  - `InfoTopic.SORT_SOURCES`, `InfoTopic.SORT_PLAN`

- [ ] **Step 1: Write the failing tests**

`SortTextsTest.kt`:

```kotlin
package com.smartspacecleaner.core.sort

import com.smartspacecleaner.core.fakes.T0
import com.smartspacecleaner.core.fakes.p
import com.smartspacecleaner.core.model.FileEntry
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class SortTextsTest {
    private fun entry(name: String, size: Long) = FileEntry(p("DCIM/Camera/$name"), size, T0)
    private fun item(name: String, c: SortCategory?, size: Long = 1) = SortItem(p("DCIM/Camera/$name"), size, SortDecision(c, "r"))

    @Test
    fun `folder line shows photos, videos and size`() {
        val f = SourceFolder(p("DCIM/Camera"), listOf(entry("a.jpg", 2_000_000_000), entry("v.mp4", 1_000_000_000)))
        assertEquals("DCIM/Camera — 1 фото, 1 видео · 3,0 ГБ", SortTexts.folderLine(f))
        assertEquals("DCIM/Camera — 1 фото · 2,0 ГБ", SortTexts.folderLine(SourceFolder(p("DCIM/Camera"), listOf(entry("a.jpg", 2_000_000_000)))))
    }

    @Test
    fun `section titles name the target folder`() {
        assertEquals(
            "Природа — 2 фото · 3 МБ → Pictures/Сортировка/Природа",
            SortTexts.sectionTitle(SortSection(SortCategory.NATURE, listOf(item("a.jpg", SortCategory.NATURE, 1_000_000), item("b.jpg", SortCategory.NATURE, 2_000_000)))),
        )
        assertEquals("Видео — 1 видео · 5 МБ → Pictures/Сортировка/Видео", SortTexts.sectionTitle(SortSection(SortCategory.VIDEO, listOf(item("v.mp4", SortCategory.VIDEO, 5_000_000)))))
        assertEquals("Проверьте — 1 фото", SortTexts.sectionTitle(SortSection(null, listOf(item("x.jpg", null)))))
    }

    @Test
    fun `confirm dialog texts`() {
        assertEquals("Перенести 21 файл в Pictures/Сортировка? Вернуть можно в Истории.", SortTexts.confirmMove(21))
        val plan = SortPlan(listOf(item("a.jpg", SortCategory.FOOD), item("v.mp4", SortCategory.VIDEO), item("x.jpg", null)))
        assertEquals(
            "Готово к раскладке 2 из 3 файлов (в том числе 1 видео). Разложить их? 1 сомнительное фото останется на месте. Вернуть всё можно в Истории.",
            SortTexts.quickConfirm(plan),
        )
        assertEquals(
            "Готово к раскладке 1 из 1 файла. Разложить их? Вернуть всё можно в Истории.",
            SortTexts.quickConfirm(SortPlan(listOf(item("a.jpg", SortCategory.FOOD)))),
        )
    }

    @Test
    fun `result line`() {
        assertEquals("Разложено 3070 файлов по 9 папкам, 450 файлов осталось на месте", SortTexts.result(3070, 9, 450, 0))
        assertEquals("Разложен 1 файл по 1 папке", SortTexts.result(1, 1, 0, 0))
        assertEquals("Разложено 2 файла по 1 папке. Не удалось перенести: 1", SortTexts.result(2, 1, 0, 1))
    }

    @Test
    fun `restore line`() {
        assertEquals("Возвращено 2940, пропущено 10", SortTexts.restored(SortRestore(2940, 10, emptyList())))
        assertEquals("Возвращено на место: 5 файлов", SortTexts.restored(SortRestore(5, 0, emptyList())))
    }

    @Test
    fun `history session texts`() {
        val s = SortSession("s", T0, moved = 3068, restored = 2, failed = 1)
        assertEquals("Сортировка фото и видео — 3070 файлов", SortTexts.sessionTitle(s))
        assertEquals("в папках сортировки: 3068 · возвращено на место: 2 · не выполнено: 1", SortTexts.sessionStatus(s))
    }
}
```

В `HelpTextsTest.kt` добавить:

```kotlin
    @Test
    fun `sort plan help says doubtful photos stay in place`() {
        assertTrue("останутся на месте" in HelpTexts.info(InfoTopic.SORT_PLAN).body)
        assertTrue("без интернета" in HelpTexts.info(InfoTopic.SORT_SOURCES).body)
    }
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `cd /s && GRADLE_USER_HOME=C:/gradle-home ./gradlew :core:test`
Expected: FAIL — `Unresolved reference: SortTexts`, `SORT_PLAN`.

- [ ] **Step 3: Implement**

`SortTexts.kt`:

```kotlin
package com.smartspacecleaner.core.sort

import com.smartspacecleaner.core.scan.StorageLayout
import com.smartspacecleaner.core.text.displayPath
import com.smartspacecleaner.core.text.formatBytes
import com.smartspacecleaner.core.text.plural

/** Все тексты сортировки в одном месте. */
object SortTexts {
    const val NOTHING_SURE = "Уверенно распознать ничего не удалось — выберите категории сами"

    private fun files(n: Int) = "$n ${plural(n, "файл", "файла", "файлов")}"

    fun folderLine(f: SourceFolder): String {
        val counts = buildList {
            if (f.photos > 0) add("${f.photos} фото")
            if (f.videos > 0) add("${f.videos} видео")
        }
        return "${displayPath(f.path)} — ${counts.joinToString(", ")} · ${formatBytes(f.bytes)}"
    }

    fun sectionTitle(s: SortSection): String {
        val category = s.category ?: return "Проверьте — ${s.items.size} фото"
        val noun = if (category == SortCategory.VIDEO) "видео" else "фото"
        return "${category.title} — ${s.items.size} $noun · ${formatBytes(s.bytes)} → ${category.relativeFolder}"
    }

    fun confirmMove(count: Int): String = "Перенести ${files(count)} в ${StorageLayout.SORT_DIR}? Вернуть можно в Истории."

    fun quickConfirm(plan: SortPlan): String {
        val ready = plan.defaultSelection().size
        val total = plan.items.size
        val left = total - ready
        return buildString {
            // После «из» — родительный падеж: из 1 файла, из 3 файлов, из 21 файла.
            append("Готово к раскладке $ready из $total ${plural(total, "файла", "файлов", "файлов")}")
            if (plan.videos > 0) append(" (в том числе ${plan.videos} видео)")
            append(". Разложить их?")
            if (left > 0) append(" $left ${plural(left, "сомнительное фото останется", "сомнительных фото останутся", "сомнительных фото останутся")} на месте.")
            append(" Вернуть всё можно в Истории.")
        }
    }

    fun result(moved: Int, folders: Int, left: Int, failed: Int): String = buildString {
        append("${plural(moved, "Разложен", "Разложено", "Разложено")} ${files(moved)} по $folders ${plural(folders, "папке", "папкам", "папкам")}")
        if (left > 0) append(", ${files(left)} ${plural(left, "остался", "осталось", "осталось")} на месте")
        if (failed > 0) append(". Не удалось перенести: $failed")
    }

    fun restored(r: SortRestore): String =
        if (r.skipped == 0) "Возвращено на место: ${files(r.restored)}" else "Возвращено ${r.restored}, пропущено ${r.skipped}"

    fun sessionTitle(s: SortSession): String = "Сортировка фото и видео — ${files(s.moved + s.restored)}"

    fun sessionStatus(s: SortSession): String = buildList {
        if (s.moved > 0) add("в папках сортировки: ${s.moved}")
        if (s.restored > 0) add("возвращено на место: ${s.restored}")
        if (s.failed > 0) add("не выполнено: ${s.failed}")
    }.joinToString(" · ")
}
```

`HelpTexts.kt`:
- `enum class InfoTopic { RISK_LEVELS, DUPLICATES, APP_CACHE, SORT_SOURCES, SORT_PLAN }`
- в `info(topic)` добавить ветки:

```kotlin
        InfoTopic.SORT_SOURCES -> HelpPage(
            "Раскладка по папкам",
            "Отметьте папки, фото и видео из которых нужно разложить. Подпапки выбираются отдельно. " +
                "Приложение само посмотрит фото на телефоне, без интернета, и предложит категорию. Видео идут в одну папку «Видео».",
        )
        InfoTopic.SORT_PLAN -> HelpPage(
            "Как проверить план",
            "Отмеченные файлы переедут в Pictures/Сортировка, каждый в папку своей категории. " +
                "В «Проверьте» — фото, в которых приложение не уверено: они останутся на месте, пока вы не выберете им категорию. " +
                "Нажмите на фото, чтобы увидеть причину и перенести его в другую категорию. Вернуть всё можно в «Истории».",
        )
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `cd /s && GRADLE_USER_HOME=C:/gradle-home ./gradlew :core:test`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add core
git commit -m "feat(core): sorting texts and help topics

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 9: ML Kit — `MlKitPhotoAnalyzer`, сборка зависимостей, версия 1.3

**Files:**
- Modify: `gradle/libs.versions.toml`
- Modify: `app/build.gradle.kts`
- Create: `app/src/main/java/com/smartspacecleaner/data/MlKitPhotoAnalyzer.kt`
- Modify: `app/src/main/java/com/smartspacecleaner/AppContainer.kt`
- Modify (если нужно после сверки): `core/src/main/kotlin/com/smartspacecleaner/core/sort/LabelTable.kt`

**Interfaces:**
- Consumes: `PhotoAnalyzer`, `Analysis`, `PhotoSignals`, `Label`, `PhotoClassifier`, `PhotoSources`, `SortAnalysis`, `SortExecutor`, `SortRestorer` (задачи 2–6).
- Produces (в `AppContainer`): `val photoSources: PhotoSources`, `val sortAnalysis: SortAnalysis` (лениво), `val sortExecutor: SortExecutor`, `val sortRestorer: SortRestorer`.

- [ ] **Step 1: Add dependencies and bump the version**

`gradle/libs.versions.toml`, секция `[versions]`:

```toml
mlkitLabeling = "17.0.9"
mlkitText = "16.0.1"
mlkitFace = "16.1.7"
```

секция `[libraries]`:

```toml
mlkit-image-labeling = { module = "com.google.mlkit:image-labeling", version.ref = "mlkitLabeling" }
mlkit-text-recognition = { module = "com.google.mlkit:text-recognition", version.ref = "mlkitText" }
mlkit-face-detection = { module = "com.google.mlkit:face-detection", version.ref = "mlkitFace" }
```

`app/build.gradle.kts`:
- `versionCode = 4`, `versionName = "1.3"`;
- в `dependencies` после `implementation(libs.work.runtime.ktx)`:

```kotlin
    // Модели встроены в APK: анализ работает без интернета и без Google Play Services.
    implementation(libs.mlkit.image.labeling)
    implementation(libs.mlkit.text.recognition)
    implementation(libs.mlkit.face.detection)
```

Run: `cd /s && GRADLE_USER_HOME=C:/gradle-home ./gradlew :app:dependencies --configuration releaseRuntimeClasspath | grep mlkit`
Expected: три строки `com.google.mlkit:...` с указанными версиями. Если Gradle пишет «Could not get resource», скачать этот URL через `curl` в тот же относительный путь под `C:/gradle-home/local-repo` и повторить (см. Global Constraints). Если версии нет в Google Maven, взять последнюю из `https://maven.google.com/web/index.html#com.google.mlkit` и записать в отчёт.

- [ ] **Step 2: Write the analyzer**

`app/src/main/java/com/smartspacecleaner/data/MlKitPhotoAnalyzer.kt`:

```kotlin
package com.smartspacecleaner.data

import android.graphics.Bitmap
import android.graphics.ImageDecoder
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import com.google.mlkit.vision.label.ImageLabeling
import com.google.mlkit.vision.label.defaults.ImageLabelerOptions
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.smartspacecleaner.core.sort.Analysis
import com.smartspacecleaner.core.sort.Label
import com.smartspacecleaner.core.sort.PhotoAnalyzer
import com.smartspacecleaner.core.sort.PhotoSignals
import java.io.File

/** Смотрит фото тремя встроенными моделями ML Kit. Вызывать не из главного потока: ждёт результатов синхронно. */
class MlKitPhotoAnalyzer : PhotoAnalyzer {
    private val labeler = ImageLabeling.getClient(ImageLabelerOptions.Builder().setConfidenceThreshold(0.5f).build())
    private val texts = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    private val faces = FaceDetection.getClient(
        FaceDetectorOptions.Builder().setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST).build(),
    )

    override fun analyze(path: String): Analysis {
        val bitmap = decode(path) ?: return Analysis.Unreadable
        return try {
            val image = InputImage.fromBitmap(bitmap, 0)
            val area = (bitmap.width.toFloat() * bitmap.height).coerceAtLeast(1f)
            val text = Tasks.await(texts.process(image))
            val textArea = text.textBlocks.sumOf { b -> b.boundingBox?.let { it.width().toLong() * it.height() } ?: 0L }
            val textChars = text.textBlocks.sumOf { b -> b.text.count { !it.isWhitespace() } }
            val largestFace = Tasks.await(faces.process(image)).maxOfOrNull { it.boundingBox.width().toLong() * it.boundingBox.height() } ?: 0L
            val labels = Tasks.await(labeler.process(image)).map { Label(it.text, it.confidence) }
            Analysis.Ok(
                PhotoSignals(
                    textAreaRatio = (textArea / area).coerceAtMost(1f),
                    textChars = textChars,
                    largestFaceRatio = (largestFace / area).coerceAtMost(1f),
                    labels = labels,
                ),
            )
        } catch (e: Exception) {
            Analysis.Unreadable
        } finally {
            bitmap.recycle()
        }
    }

    /** Уменьшенная копия до ~640 px по длинной стороне; поворот из EXIF ImageDecoder применяет сам. */
    private fun decode(path: String): Bitmap? = try {
        ImageDecoder.decodeBitmap(ImageDecoder.createSource(File(path))) { decoder, info, _ ->
            val longest = maxOf(info.size.width, info.size.height)
            if (longest > MAX_SIDE) {
                val k = MAX_SIDE.toFloat() / longest
                decoder.setTargetSize(maxOf(1, (info.size.width * k).toInt()), maxOf(1, (info.size.height * k).toInt()))
            }
            // ML Kit читает пиксели напрямую; аппаратный bitmap для него не годится.
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
        }
    } catch (e: Exception) {
        null
    }

    private companion object {
        const val MAX_SIDE = 640
    }
}
```

- [ ] **Step 3: Wire it into `AppContainer`**

Импорты: `com.smartspacecleaner.core.sort.PhotoClassifier`, `PhotoSources`, `SortAnalysis`, `SortExecutor`, `SortRestorer`, `com.smartspacecleaner.data.MlKitPhotoAnalyzer`.

После `val duplicateStrategy = DuplicateStrategy.TRASH`:

```kotlin
    val photoSources = PhotoSources(fs, layout)
    // Модели ML Kit грузятся в память только при первой сортировке.
    val sortAnalysis: SortAnalysis by lazy { SortAnalysis(PhotoClassifier(layout, MlKitPhotoAnalyzer())) }
    val sortExecutor = SortExecutor(fs, log, clock, layout)
    val sortRestorer = SortRestorer(fs, log, layout)
```

- [ ] **Step 4: Check the label table against the official ML Kit label list**

Открыть `https://developers.google.com/ml-kit/vision/image-labeling/label-map` (список меток базовой модели). Для каждого названия из `LabelTable` проверить, что оно есть в списке (без учёта регистра). Отсутствующие названия удалить из `LabelTable.kt`. Если удалённое название встречается в `PhotoClassifierTest` (`Dog`, `Sky`, `Tree`, `Food`, `Car` — проверить в первую очередь), заменить его в тесте на существующую метку той же категории. `Car` в тестах — пример метки вне таблицы, его оставить, если он есть в списке, иначе заменить на любую метку из списка вне таблицы. В отчёт задачи записать удалённые названия.

- [ ] **Step 5: Build and run all tests**

Run: `cd /s && GRADLE_USER_HOME=C:/gradle-home ./gradlew :core:test :app:assembleDebug :app:assembleRelease`
Expected: BUILD SUCCESSFUL. В отчёт записать размер `app/build/outputs/apk/release/app-release.apk` до (1.2) и после (ожидается примерно +10–15 МБ).

- [ ] **Step 6: Commit**

```bash
git add gradle app core
git commit -m "feat(app): on-device ML Kit photo analyzer, sort wiring, version 1.3

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 10: Экраны сортировки и `MainViewModel`

**Files:**
- Modify: `app/src/main/java/com/smartspacecleaner/ui/MainViewModel.kt`
- Create: `app/src/main/java/com/smartspacecleaner/ui/SortScreens.kt`
- Modify: `app/src/main/java/com/smartspacecleaner/ui/SimpleScreens.kt` (`Radar`, `SummaryScreen`)
- Modify: `app/src/main/java/com/smartspacecleaner/ui/PlanScreen.kt` (`Thumbnail`, `loadThumbnail`)
- Modify: `app/src/main/java/com/smartspacecleaner/MainActivity.kt`

**Interfaces:**
- Consumes: `AppContainer.photoSources/sortAnalysis/sortExecutor`, `SourceFolder`, `SortPlan`, `SortSection`, `SortItem`, `SortCategory`, `SortTexts`, `InfoTopic.SORT_SOURCES/SORT_PLAN`, `InfoIcon`, `scanMedia` (существующий в VM).
- Produces:
  - `enum class Screen { …, SortSources, SortAnalyzing, SortPlan, SortMoving, SortResult }`
  - в `UiState`: `sortSources: List<SourceFolder>?`, `sortPicked: Set<String>`, `sortDone: Int`, `sortTotal: Int`, `sortPlan: SortPlan?`, `sortSelected: Set<String>`, `sortQuickConfirm: Boolean`, `sortResult: String?`
  - методы VM: `openSortSources()`, `toggleSortSource(path)`, `startSortAnalysis(quick: Boolean)`, `cancelSortAnalysis()`, `reviewSortPlan()`, `toggleSortItem(path)`, `toggleSortSection(section)`, `reassignSort(path, category)`, `moveSorted()`, `backToSortSources()`, `reviewSortLeftovers()`, `closeSort()`
  - `internal fun Thumbnail(path: String?, name: String = path.orEmpty(), size: Dp = 48.dp, px: Int = THUMB_PX)`

- [ ] **Step 1: Make `Thumbnail` sizeable and `Radar` reusable**

`PlanScreen.kt`:

```kotlin
/** [path] — откуда читать содержимое, [name] — имя, по расширению которого выбирается вид превью. */
@Composable
internal fun Thumbnail(path: String?, name: String = path.orEmpty(), size: Dp = 48.dp, px: Int = THUMB_PX) {
    val bitmap by produceState<ImageBitmap?>(initialValue = null, path, name, px) {
        value = path?.let { withContext(Dispatchers.IO) { loadThumbnail(it, name, px) } }
    }
    Box(Modifier.size(size), contentAlignment = Alignment.Center) {
```

(остальное тело без изменений), и:

```kotlin
internal const val THUMB_PX = 128

private fun loadThumbnail(path: String, name: String, px: Int): ImageBitmap? = try {
    val file = File(path)
    val size = Size(px, px)
```

Импорт `androidx.compose.ui.unit.Dp`.

`SimpleScreens.kt` — `Radar` становится общим:

```kotlin
/** Радар: бегущий луч, расходящиеся круги и счётчик в центре. */
@Composable
internal fun Radar(value: String, caption: String) {
```

и в конце его тела:

```kotlin
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(value, style = MaterialTheme.typography.headlineMedium)
            Text(caption, style = MaterialTheme.typography.bodySmall)
        }
```

В `ScanningScreen`: `Radar("$filesSeen", "файлов просмотрено")`.

`SummaryScreen` — новый параметр и кнопка:

```kotlin
@Composable
fun SummaryScreen(summary: Summary, scanMillis: Long, onShowPlan: () -> Unit, onHistory: () -> Unit, onSort: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("${summary.severity.emoji} Память: ${summary.severity.label}", style = MaterialTheme.typography.headlineSmall)
        summary.lines.forEach { Text(it) }
        Text("Скан занял ${scanMillis / 1000} с", style = MaterialTheme.typography.bodySmall)
        Button(onClick = onShowPlan) { Text("Показать план") }
        OutlinedButton(onClick = onHistory) { Text("История") }
        OutlinedButton(onClick = onSort) { Text("Разложить фото по папкам") }
    }
}
```

- [ ] **Step 2: Add sort state and methods to `MainViewModel`**

`Screen`:

```kotlin
enum class Screen { Onboarding, Permission, Scanning, Summary, Plan, Executing, Report, History, SortSources, SortAnalyzing, SortPlan, SortMoving, SortResult }
```

В `UiState` после `feedbackName`:

```kotlin
    /** null — список папок ещё собирается. */
    val sortSources: List<SourceFolder>? = null,
    val sortPicked: Set<String> = emptySet(),
    val sortDone: Int = 0,
    val sortTotal: Int = 0,
    val sortPlan: SortPlan? = null,
    val sortSelected: Set<String> = emptySet(),
    /** Путь «Разложить сразу»: анализ закончен, ждём ответа в диалоге. */
    val sortQuickConfirm: Boolean = false,
    val sortResult: String? = null,
```

В классе VM (импорты `kotlinx.coroutines.Job`, `kotlinx.coroutines.isActive`, `com.smartspacecleaner.core.sort.*`):

```kotlin
    private var sortJob: Job? = null

    fun openSortSources() {
        _state.update { it.copy(screen = Screen.SortSources, sortSources = null, sortPicked = emptySet(), sortPlan = null, sortResult = null) }
        viewModelScope.launch {
            val sources = withContext(Dispatchers.IO) { c.photoSources.list() }
            _state.update { it.copy(sortSources = sources) }
        }
    }

    fun toggleSortSource(path: String) =
        _state.update { it.copy(sortPicked = if (path in it.sortPicked) it.sortPicked - path else it.sortPicked + path) }

    fun startSortAnalysis(quick: Boolean) {
        val s = _state.value
        val files = s.sortSources.orEmpty().filter { it.path in s.sortPicked }.flatMap { it.files }
        if (files.isEmpty()) return
        _state.update { it.copy(screen = Screen.SortAnalyzing, sortDone = 0, sortTotal = files.size, sortQuickConfirm = false) }
        sortJob = viewModelScope.launch {
            val plan = withContext(Dispatchers.IO) {
                c.sortAnalysis.run(files, onProgress = { n -> _state.update { it.copy(sortDone = n) } }, shouldStop = { !isActive })
            } ?: return@launch
            val selected = plan.defaultSelection()
            _state.update {
                when {
                    !quick -> it.copy(screen = Screen.SortPlan, sortPlan = plan, sortSelected = selected)
                    selected.isEmpty() -> it.copy(screen = Screen.SortPlan, sortPlan = plan, sortSelected = selected, toast = SortTexts.NOTHING_SURE)
                    else -> it.copy(sortPlan = plan, sortSelected = selected, sortQuickConfirm = true)
                }
            }
        }
    }

    fun cancelSortAnalysis() {
        sortJob?.cancel()
        _state.update { it.copy(screen = Screen.SortSources, sortQuickConfirm = false) }
    }

    /** «Посмотреть план» в диалоге «Разложить сразу». */
    fun reviewSortPlan() = _state.update { it.copy(screen = Screen.SortPlan, sortQuickConfirm = false) }

    fun toggleSortItem(path: String) =
        _state.update { it.copy(sortSelected = if (path in it.sortSelected) it.sortSelected - path else it.sortSelected + path) }

    fun toggleSortSection(section: SortSection) = _state.update {
        val paths = section.items.map { i -> i.path }.toSet()
        it.copy(sortSelected = if (it.sortSelected.containsAll(paths)) it.sortSelected - paths else it.sortSelected + paths)
    }

    fun reassignSort(path: String, category: SortCategory) = _state.update {
        it.copy(sortPlan = it.sortPlan?.reassign(path, category), sortSelected = it.sortSelected + path)
    }

    fun moveSorted() {
        val s = _state.value
        val plan = s.sortPlan ?: return
        val moves = plan.moves(s.sortSelected)
        if (moves.isEmpty()) return
        _state.update { it.copy(screen = Screen.SortMoving, sortQuickConfirm = false, sortDone = 0, sortTotal = moves.size) }
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                c.sortExecutor.execute(moves, UUID.randomUUID().toString()) { n -> _state.update { it.copy(sortDone = n) } }
            }
            scanMedia(result.touchedPaths)
            val rest = plan.without(result.moved.map { it.originalPath }.toSet())
            val folders = result.moved.map { it.groupTitle }.distinct().size
            val text = SortTexts.result(result.moved.size, folders, rest.items.size, result.failed.size)
            _state.update { it.copy(screen = Screen.SortResult, sortPlan = rest, sortResult = text) }
        }
    }

    /** «Назад» из плана: к списку папок с прежними галочками, без нового поиска. */
    fun backToSortSources() = _state.update { it.copy(screen = Screen.SortSources) }

    /** «Разобрать оставшиеся»: план только из «Проверьте». */
    fun reviewSortLeftovers() = _state.update {
        it.copy(screen = Screen.SortPlan, sortPlan = it.sortPlan?.reviewOnly(), sortSelected = emptySet())
    }

    /** Файлы переехали — старый план очистки устарел, поэтому сканируем заново. */
    fun closeSort() = startScan()
```

- [ ] **Step 3: Write the sort screens**

`app/src/main/java/com/smartspacecleaner/ui/SortScreens.kt`:

```kotlin
package com.smartspacecleaner.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import com.smartspacecleaner.core.help.InfoTopic
import com.smartspacecleaner.core.sort.SortCategory
import com.smartspacecleaner.core.sort.SortItem
import com.smartspacecleaner.core.sort.SortPlan
import com.smartspacecleaner.core.sort.SortSection
import com.smartspacecleaner.core.sort.SortTexts
import com.smartspacecleaner.core.sort.SourceFolder

private val REVIEW_YELLOW = Color(0xFFFFF59D)

@Composable
fun SortSourcesScreen(
    sources: List<SourceFolder>?,
    picked: Set<String>,
    onToggle: (String) -> Unit,
    onAnalyze: () -> Unit,
    onQuick: () -> Unit,
    onBack: () -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        TextButton(onClick = onBack, modifier = Modifier.padding(8.dp)) { Text("← Назад") }
        Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Откуда разложить", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
            InfoIcon(InfoTopic.SORT_SOURCES)
        }
        when {
            sources == null -> Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            sources.isEmpty() -> Box(Modifier.weight(1f).fillMaxWidth().padding(16.dp)) { Text("Фото и видео для раскладки не найдено") }
            else -> LazyColumn(Modifier.weight(1f)) {
                items(sources, key = { it.path }) { folder ->
                    Row(
                        Modifier.fillMaxWidth().clickable { onToggle(folder.path) }.padding(horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(checked = folder.path in picked, onCheckedChange = { onToggle(folder.path) })
                        Text(SortTexts.folderLine(folder))
                    }
                }
            }
        }
        val count = sources.orEmpty().filter { it.path in picked }.sumOf { it.files.size }
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onAnalyze, enabled = count > 0, modifier = Modifier.fillMaxWidth()) {
                Text("Анализировать и показать план ($count)")
            }
            OutlinedButton(onClick = onQuick, enabled = count > 0, modifier = Modifier.fillMaxWidth()) {
                Text("Разложить сразу, без проверки ($count)")
            }
        }
    }
}

@Composable
fun SortAnalyzingScreen(
    done: Int,
    total: Int,
    quickText: String?,
    onCancel: () -> Unit,
    onQuickMove: () -> Unit,
    onQuickReview: () -> Unit,
) {
    KeepScreenOn()
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Radar("$done из $total", "просмотрено")
        Text("Смотрю фото, ничего не переношу", style = MaterialTheme.typography.titleMedium)
        if (quickText == null) TextButton(onClick = onCancel) { Text("Отмена") }
    }
    if (quickText != null) {
        AlertDialog(
            onDismissRequest = {},
            text = { Text(quickText) },
            confirmButton = { TextButton(onClick = onQuickMove) { Text("Разложить") } },
            dismissButton = { TextButton(onClick = onQuickReview) { Text("Посмотреть план") } },
        )
    }
}

@Composable
fun SortPlanScreen(
    plan: SortPlan,
    selected: Set<String>,
    onToggle: (String) -> Unit,
    onToggleSection: (SortSection) -> Unit,
    onReassign: (String, SortCategory) -> Unit,
    onOpenFile: (path: String, name: String) -> Unit,
    onMove: () -> Unit,
    onBack: () -> Unit,
) {
    var opened by remember { mutableStateOf<String?>(null) }
    var confirm by remember { mutableStateOf(false) }
    val count = plan.moves(selected).size
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text("← Назад") }
            Text("План раскладки", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            InfoIcon(InfoTopic.SORT_PLAN)
        }
        LazyVerticalGrid(columns = GridCells.Fixed(4), modifier = Modifier.weight(1f).padding(horizontal = 8.dp)) {
            plan.sections().forEach { section ->
                item(key = "h-${section.category}", span = { GridItemSpan(maxLineSpan) }) {
                    SectionHeader(section, selected, onToggleSection)
                }
                items(section.items, key = { it.path }) { item ->
                    SortTile(item, checked = item.path in selected, onToggle = { onToggle(item.path) }, onOpen = { opened = item.path })
                }
            }
        }
        Button(onClick = { confirm = true }, enabled = count > 0, modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Text("Разложить ($count)")
        }
    }
    opened?.let { path ->
        plan.items.firstOrNull { it.path == path }?.let { item ->
            SortItemDialog(item, onReassign = { c -> onReassign(path, c); opened = null }, onOpenFile = onOpenFile, onClose = { opened = null })
        }
    }
    if (confirm) {
        AlertDialog(
            onDismissRequest = { confirm = false },
            text = { Text(SortTexts.confirmMove(count)) },
            confirmButton = { TextButton(onClick = { confirm = false; onMove() }) { Text("Перенести") } },
            dismissButton = { TextButton(onClick = { confirm = false }) { Text("Отмена") } },
        )
    }
}

@Composable
private fun SectionHeader(section: SortSection, selected: Set<String>, onToggleSection: (SortSection) -> Unit) {
    val review = section.category == null
    Surface(
        color = if (review) REVIEW_YELLOW else MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 4.dp),
    ) {
        Row(Modifier.padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                SortTexts.sectionTitle(section),
                style = MaterialTheme.typography.titleSmall,
                color = if (review) Color.Black else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            // В «Проверьте» отмечать нечего: без категории файл всё равно не переедет.
            if (!review) {
                val all = section.items.all { it.path in selected }
                TextButton(onClick = { onToggleSection(section) }) { Text(if (all) "Снять все" else "Выбрать все") }
            }
        }
    }
}

@Composable
private fun SortTile(item: SortItem, checked: Boolean, onToggle: () -> Unit, onOpen: () -> Unit) {
    Box(Modifier.padding(2.dp).clickable(onClick = onOpen)) {
        Thumbnail(item.path, size = 84.dp, px = 192)
        if (item.decision.category != null) {
            Checkbox(checked = checked, onCheckedChange = { onToggle() }, modifier = Modifier.align(Alignment.TopEnd))
        }
        if (item.decision.category == SortCategory.VIDEO) {
            Text("▶", color = Color.White, modifier = Modifier.align(Alignment.Center))
        }
    }
}

@Composable
private fun SortItemDialog(item: SortItem, onReassign: (SortCategory) -> Unit, onOpenFile: (String, String) -> Unit, onClose: () -> Unit) {
    var choosing by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text(item.path.substringAfterLast('/')) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.fillMaxWidth().clickable { onOpenFile(item.path, item.path) }, contentAlignment = Alignment.Center) {
                    Thumbnail(item.path, size = 260.dp, px = 1024)
                }
                Text("Почему здесь: ${item.decision.reason}", style = MaterialTheme.typography.bodySmall)
                if (choosing) {
                    SortCategory.entries.forEach { c ->
                        TextButton(onClick = { onReassign(c) }) { Text(c.title) }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { choosing = !choosing }) { Text("Перенести в…") } },
        dismissButton = { TextButton(onClick = onClose) { Text("Закрыть") } },
    )
}

@Composable
fun SortMovingScreen(done: Int, total: Int) {
    KeepScreenOn()
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CircularProgressIndicator()
        Text("Переношу: $done из $total")
    }
}

@Composable
fun SortResultScreen(text: String, canReview: Boolean, onReview: () -> Unit, onDone: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Готово", style = MaterialTheme.typography.headlineSmall)
        Text(text)
        Text("Вернуть всё на место можно в «Истории».", style = MaterialTheme.typography.bodySmall)
        if (canReview) OutlinedButton(onClick = onReview) { Text("Разобрать оставшиеся") }
        Button(onClick = onDone) { Text("Готово") }
    }
}

/** Пока идёт долгая работа, экран не гаснет: иначе система может остановить приложение. */
@Composable
private fun KeepScreenOn() {
    val view = LocalView.current
    DisposableEffect(view) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }
}
```

- [ ] **Step 4: Wire the screens into `MainActivity`**

`SummaryScreen(...)` → `SummaryScreen(state.summary!!, state.scanMillis, vm::showPlan, vm::openHistory, vm::openSortSources)`.

В `when (state.screen)` добавить:

```kotlin
                                Screen.SortSources -> SortSourcesScreen(
                                    sources = state.sortSources,
                                    picked = state.sortPicked,
                                    onToggle = vm::toggleSortSource,
                                    onAnalyze = { vm.startSortAnalysis(quick = false) },
                                    onQuick = { vm.startSortAnalysis(quick = true) },
                                    onBack = vm::backToSummary,
                                )
                                Screen.SortAnalyzing -> SortAnalyzingScreen(
                                    done = state.sortDone,
                                    total = state.sortTotal,
                                    quickText = state.sortPlan?.takeIf { state.sortQuickConfirm }?.let { SortTexts.quickConfirm(it) },
                                    onCancel = vm::cancelSortAnalysis,
                                    onQuickMove = vm::moveSorted,
                                    onQuickReview = vm::reviewSortPlan,
                                )
                                Screen.SortPlan -> SortPlanScreen(
                                    plan = state.sortPlan!!,
                                    selected = state.sortSelected,
                                    onToggle = vm::toggleSortItem,
                                    onToggleSection = vm::toggleSortSection,
                                    onReassign = vm::reassignSort,
                                    onOpenFile = ::openFile,
                                    onMove = vm::moveSorted,
                                    onBack = vm::backToSortSources,
                                )
                                Screen.SortMoving -> SortMovingScreen(state.sortDone, state.sortTotal)
                                Screen.SortResult -> SortResultScreen(
                                    text = state.sortResult.orEmpty(),
                                    canReview = state.sortPlan?.reviewOnly()?.items?.isNotEmpty() == true,
                                    onReview = vm::reviewSortLeftovers,
                                    onDone = vm::closeSort,
                                )
```

`BackHandler` — условие и ветки:

```kotlin
                BackHandler(
                    enabled = state.screen in setOf(Screen.Plan, Screen.History, Screen.SortSources, Screen.SortAnalyzing, Screen.SortPlan, Screen.SortResult) ||
                        (state.screen == Screen.Onboarding && state.onboardingReturnTo != null),
                ) {
                    when (state.screen) {
                        Screen.Plan, Screen.SortSources -> vm.backToSummary()
                        Screen.History -> vm.closeHistory()
                        Screen.SortAnalyzing -> vm.cancelSortAnalysis()
                        Screen.SortPlan -> vm.backToSortSources()
                        Screen.SortResult -> vm.closeSort()
                        else -> vm.finishOnboarding(Environment.isExternalStorageManager())
                    }
                }
```

Импорт `com.smartspacecleaner.core.sort.SortTexts`. `Screen.SortMoving` намеренно без «Назад»: перенос нельзя бросить на середине.

- [ ] **Step 5: Build**

Run: `cd /s && GRADLE_USER_HOME=C:/gradle-home ./gradlew :core:test :app:assembleDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 6: Commit**

```bash
git add app
git commit -m "feat(app): sort screens - sources, analysis, plan grid, quick path, result

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 11: Сортировка в Истории — «Вернуть всё на место»

**Files:**
- Modify: `app/src/main/java/com/smartspacecleaner/ui/MainViewModel.kt`
- Modify: `app/src/main/java/com/smartspacecleaner/ui/HistoryScreen.kt`
- Modify: `app/src/main/java/com/smartspacecleaner/MainActivity.kt`

**Interfaces:**
- Consumes: `SortSessions.of`, `SortSession`, `SortTexts.sessionTitle/sessionStatus/restored`, `AppContainer.sortRestorer` (задачи 6–9).
- Produces: `MainViewModel.restoreSort(sessionId: String)`; новый параметр `HistoryScreen(onRestoreSort: (String) -> Unit)`.

- [ ] **Step 1: Add the restore method to the view model**

```kotlin
    fun restoreSort(sessionId: String) {
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) { c.sortRestorer.restoreSession(sessionId) }
            scanMedia(result.touchedPaths)
            _state.update { it.copy(history = loadHistory(), toast = SortTexts.restored(result)) }
        }
    }
```

- [ ] **Step 2: Show sort sessions in `HistoryScreen`**

Новый параметр после `onRestoreMany`: `onRestoreSort: (String) -> Unit,`. Импорты: `com.smartspacecleaner.core.sort.SortSession`, `com.smartspacecleaner.core.sort.SortSessions`, `com.smartspacecleaner.core.sort.SortTexts`.

В начале функции рядом с `sections`:

```kotlin
    val sortSessions = SortSessions.of(records)
    var confirmSort by remember { mutableStateOf<SortSession?>(null) }
```

В `LazyColumn` перед `item(key = "earlier-title")`:

```kotlin
            if (sortSessions.isNotEmpty()) {
                item(key = "sort-title") {
                    HorizontalDivider(Modifier.padding(top = 16.dp))
                    SectionTitle("Сортировка") {}
                }
                items(sortSessions, key = { "sort-${it.sessionId}" }) { session ->
                    SortSessionRow(session, onRestore = { confirmSort = session })
                }
            }
```

После диалога `confirmClear`:

```kotlin
    confirmSort?.let { session ->
        AlertDialog(
            onDismissRequest = { confirmSort = null },
            title = { Text("Вернуть всё на место?") },
            text = { Text("Файлов: ${session.moved}. Они вернутся в папки, где лежали до сортировки.") },
            confirmButton = {
                TextButton(onClick = {
                    onRestoreSort(session.sessionId)
                    confirmSort = null
                }) { Text("Вернуть") }
            },
            dismissButton = { TextButton(onClick = { confirmSort = null }) { Text("Отмена") } },
        )
    }
```

Новый компонент рядом с `HistoryRow`:

```kotlin
@Composable
private fun SortSessionRow(session: SortSession, onRestore: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)) {
        Text(SortTexts.sessionTitle(session), fontWeight = FontWeight.Bold)
        Text(SESSION_FORMAT.format(session.createdAt), style = MaterialTheme.typography.bodySmall)
        Text(
            SortTexts.sessionStatus(session),
            style = MaterialTheme.typography.bodyMedium,
            color = if (session.canRestore) TRASH_ORANGE else RESTORE_GREEN,
        )
        if (session.canRestore) {
            Button(
                onClick = onRestore,
                colors = ButtonDefaults.buttonColors(containerColor = RESTORE_GREEN),
                modifier = Modifier.padding(top = 4.dp),
            ) { Text("Вернуть всё на место") }
        }
    }
}
```

- [ ] **Step 3: Pass the callback from `MainActivity`**

В вызове `HistoryScreen(...)` после `onRestoreMany = vm::restoreAll,` добавить `onRestoreSort = vm::restoreSort,`.

- [ ] **Step 4: Build**

Run: `cd /s && GRADLE_USER_HOME=C:/gradle-home ./gradlew :core:test :app:assembleDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```bash
git add app
git commit -m "feat(app): sort sessions in history with restore-all

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 12: Приёмка на телефоне

Делается вместе с пользователем: он смотрит телефон и присылает скриншоты. Код в этой задаче меняется только по результатам проверки (пороги, таблица меток, тексты), каждое изменение — с тестом в `:core`.

**Files:**
- Create: `docs/superpowers/plans/2026-10-31-photo-sorting-acceptance.md`
- Modify (по результатам): `PhotoClassifier.kt` (пороги), `LabelTable.kt`, соответствующие тесты

- [ ] **Step 1: Build the release and install over 1.2**

Run: `cd /s && GRADLE_USER_HOME=C:/gradle-home ./gradlew :app:assembleRelease`
Затем: `MSYS_NO_PATHCONV=1 adb -s ee5591f0 install -r S:/app/build/outputs/apk/release/app-release.apk`
Expected: `Success`. Если MIUI показывает подтверждение и первая попытка падает с пустой ошибкой — повторить один раз. Play Protect может предложить проверку: пользователь нажимает «Проверить».

- [ ] **Step 2: Walk through the checklist with the user**

Попросить скриншоты и записать результаты в `2026-10-31-photo-sorting-acceptance.md`:
1. История из 1.2 на месте после обновления (миграция базы).
2. «Разложить фото по папкам» → список папок: числа фото и видео похожи на правду, `Pictures/Сортировка` в списке нет.
3. Анализ одной папки на ~1 000 фото: время анализа (секундомер), экран не гаснет, «Отмена» возвращает к списку.
4. План: разделы, «Проверьте» жёлтый и сверху, превью видео со значком ▶, причина в диалоге фото, «Перенести в…» работает.
5. Русские документы, чеки, справки: попадают ли в «Документы». Если нет — снизить `TEXT_CHARS` (риск из спецификации, кириллица).
6. Перенос: в галерее MIUI появились альбомы категорий, видео воспроизводятся.
7. «Разложить сразу» на другой папке, затем «Разобрать оставшиеся».
8. История → «Сортировка» → «Вернуть всё на место»: фото снова в «Камере».
9. Повторный скан после «Готово»: очистка находит старые скриншоты в `Pictures/Сортировка/Скриншоты`.

- [ ] **Step 3: Tune by results**

Для каждой правки: сначала тест в `PhotoClassifierTest` (новый пример с реальными значениями, например `PhotoSignals(textAreaRatio = 0.31f, textChars = 52)` для русского чека), затем изменение порога или таблицы, затем `:core:test`, сборка, установка, скриншот пользователя.

- [ ] **Step 4: Commit and open the PR**

```bash
git add docs core app
git commit -m "docs: photo sorting acceptance on the phone; tuned thresholds

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
git push -u origin feat/photo-sorting
gh pr create --title "Сортировка фото и видео по папкам (1.3)" --body "<описание: что сделано, итоги приёмки, отложенные мелочи>

🤖 Generated with [Claude Code](https://claude.com/claude-code)"
```

PR сливает пользователь сам на GitHub.
