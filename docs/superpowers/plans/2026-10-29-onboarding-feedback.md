# Онбординг, подсказки и обратная связь — план реализации

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** первый запуск объясняет приложение, на экранах есть одноразовые подсказки и «?», после очистки приложение просит отзыв и отправляет его в Google Форму.

**Architecture:**
- Чистая логика в `:core`, по TDD:
  - когда спрашивать отзыв;
  - проверка отзыва;
  - кодирование тела формы;
  - все тексты подсказок.
- `:app` — тонкий слой:
  - `SharedPreferences` для флагов;
  - Compose-компоненты;
  - `FeedbackWorker` (WorkManager), который делает POST в `formResponse`.
- Навигация остаётся прежней (`Screen` + `UiState` в `MainViewModel`), добавляется `Screen.Onboarding`.

**Tech Stack:**
- Kotlin 2.1, Jetpack Compose (BOM 2024.12.01, Material 3, `HorizontalPager`);
- WorkManager 2.10, `HttpURLConnection`;
- JUnit 5 в `:core`.

**Spec:** [docs/superpowers/specs/2026-10-29-onboarding-feedback-design.md](../specs/2026-10-29-onboarding-feedback-design.md)

## Global Constraints

- Никаких новых библиотек. Только то, что уже есть в `gradle/libs.versions.toml`.
- `minSdk = 31`. Нельзя использовать API новее 31. Например, `URLEncoder.encode(String, Charset)` появился только в API 33 — используем `URLEncoder.encode(s, "UTF-8")`.
- Тексты интерфейса на русском, дословно из спецификации и этого плана.
- Отзыв: оценка 1–5 обязательна; текст ≤ 2000 символов и обязателен при оценке ≤ 3; имя ≤ 50 символов.
- В отзыв не попадают имена и пути файлов. Отправляются только: оценка, текст, имя, версия приложения, телефон, Android, источник.
- В манифест добавляются только `INTERNET` и `ACCESS_NETWORK_STATE`.
- Версия: `versionName = "1.2"`, `versionCode = 3`.
- Gradle запускается так: `cd /s && GRADLE_USER_HOME=C:/gradle-home ./gradlew <задача>`. `S:` — это `subst` корня репозитория, песочница выключена.

## Review Focus

1. **Длинный отзыв кириллицей.** 2000 русских символов должны помещаться во входные данные WorkManager (лимит 10 КБ). Поэтому в `Data` кладутся сырые поля, а кодирование в URL происходит в воркере. Закодированное тело было бы примерно 12 000 байт и не поместилось бы. Тест на размер — в задаче 2.
2. **Текст из одних пробелов при оценке ≤ 3** должен считаться пустым. Имя из пробелов — пустым. Тест — в задаче 1.
3. **Очистка, в которой ничего не удалено** (сняли все галочки, только кэш, всё упало с ошибкой), окно отзыва не вызывает. Тест — в задаче 1.
4. **Онбординг при первом запуске без разрешения.** `onResume` не должен выбрасывать человека с онбординга на экран разрешения. Это ручная проверка в задаче 4.
5. **Поворот экрана или сворачивание с открытым окном отзыва** — введённый текст и оценка сохраняются. Ручная проверка — в задаче 6.

---

## Карта файлов

| Файл | Ответственность |
|---|---|
| `core/src/main/kotlin/com/smartspacecleaner/core/feedback/Feedback.kt` | `FeedbackDraft`, `Feedback`, `FeedbackCheck`, `FeedbackValidator` |
| `core/src/main/kotlin/com/smartspacecleaner/core/feedback/FeedbackPromptPolicy.kt` | Когда показывать окно само |
| `core/src/main/kotlin/com/smartspacecleaner/core/feedback/FeedbackFormEncoder.kt` | `FeedbackSource`, `DeviceInfo`, `FormFields`, кодирование тела |
| `core/src/main/kotlin/com/smartspacecleaner/core/help/HelpTexts.kt` | `HintId`, `InfoTopic`, `HelpPage`, все тексты |
| `core/src/test/kotlin/com/smartspacecleaner/core/feedback/*Test.kt` | Тесты обратной связи |
| `core/src/test/kotlin/com/smartspacecleaner/core/help/HelpTextsTest.kt` | Тесты текстов |
| `app/src/main/java/com/smartspacecleaner/data/UiPrefs.kt` | Флаги в `SharedPreferences` |
| `app/src/main/java/com/smartspacecleaner/ui/OnboardingScreen.kt` | 4 экрана онбординга |
| `app/src/main/java/com/smartspacecleaner/ui/Help.kt` | `HintBanner`, `InfoIcon`, `AppMenu`, `Screen.hint()` |
| `app/src/main/java/com/smartspacecleaner/ui/FeedbackDialog.kt` | Окно отзыва |
| `app/src/main/java/com/smartspacecleaner/feedback/FeedbackFormConfig.kt` | `FORM_ID` и номера полей |
| `app/src/main/java/com/smartspacecleaner/feedback/FeedbackWorker.kt` | Отправка в форму с повторами |
| `app/src/main/java/com/smartspacecleaner/feedback/FeedbackSender.kt` | Постановка в очередь, проверка сети, сведения об устройстве |
| `app/src/main/java/com/smartspacecleaner/ui/MainViewModel.kt` | Состояние онбординга, подсказок, окна отзыва |
| `app/src/main/java/com/smartspacecleaner/MainActivity.kt` | Меню, плашки, окно отзыва вокруг экранов |
| `app/src/main/java/com/smartspacecleaner/ui/PlanScreen.kt` | «?» у уровней, дублей, кэша |
| `app/src/main/java/com/smartspacecleaner/AppContainer.kt` | `prefs`, `feedback` |
| `app/src/main/AndroidManifest.xml`, `app/build.gradle.kts` | Разрешения, `buildConfig`, версия |
| `.claude/skills/razbor-otzyvov/SKILL.md` | Навык разбора отзывов |

---

### Task 1: Проверка отзыва и правило «когда спрашивать» (`:core`)

**Files:**
- Create: `core/src/main/kotlin/com/smartspacecleaner/core/feedback/Feedback.kt`
- Create: `core/src/main/kotlin/com/smartspacecleaner/core/feedback/FeedbackPromptPolicy.kt`
- Test: `core/src/test/kotlin/com/smartspacecleaner/core/feedback/FeedbackValidatorTest.kt`
- Test: `core/src/test/kotlin/com/smartspacecleaner/core/feedback/FeedbackPromptPolicyTest.kt`

**Interfaces:**
- Consumes: `com.smartspacecleaner.core.report.Report` (поле `done: List<ReportLine>`).
- Produces:
  - `data class FeedbackDraft(val rating: Int, val text: String, val name: String)`
  - `data class Feedback(val rating: Int, val text: String, val name: String)`
  - `sealed interface FeedbackCheck { data class Ok(val feedback: Feedback); data class Invalid(val reason: String) }`
  - `object FeedbackValidator { const val MAX_TEXT = 2000; const val MAX_NAME = 50; fun check(draft: FeedbackDraft): FeedbackCheck }`
  - `object FeedbackPromptPolicy { fun cleanedSomething(report: Report): Boolean; fun shouldPrompt(cleanedSomething: Boolean, alreadySent: Boolean, optedOut: Boolean): Boolean }`

- [ ] **Step 1: Write the failing tests**

`FeedbackValidatorTest.kt`:

```kotlin
package com.smartspacecleaner.core.feedback

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class FeedbackValidatorTest {
    private fun check(rating: Int, text: String = "", name: String = "") =
        FeedbackValidator.check(FeedbackDraft(rating, text, name))

    @Test
    fun `rating outside 1-5 is rejected`() {
        listOf(0, 6, -1).forEach { assertEquals(FeedbackCheck.Invalid("Поставьте оценку"), check(it, "текст")) }
    }

    @Test
    fun `4 and 5 stars may come without text`() {
        assertEquals(FeedbackCheck.Ok(Feedback(4, "", "")), check(4))
        assertEquals(FeedbackCheck.Ok(Feedback(5, "", "")), check(5))
    }

    @Test
    fun `3 stars and below need text, and spaces do not count as text`() {
        listOf(1, 3).forEach { assertTrue(check(it) is FeedbackCheck.Invalid) }
        assertTrue(check(2, "   \n ") is FeedbackCheck.Invalid)
        assertEquals(FeedbackCheck.Ok(Feedback(3, "мешает реклама", "")), check(3, "  мешает реклама "))
    }

    @Test
    fun `text is limited to 2000 characters`() {
        assertTrue(check(5, "а".repeat(2000)) is FeedbackCheck.Ok)
        assertEquals(FeedbackCheck.Invalid("Текст длиннее 2000 символов"), check(5, "а".repeat(2001)))
    }

    @Test
    fun `name is trimmed and cut to 50 characters, blank name becomes empty`() {
        assertEquals("Аня", (check(5, name = "  Аня ") as FeedbackCheck.Ok).feedback.name)
        assertEquals("", (check(5, name = "   ") as FeedbackCheck.Ok).feedback.name)
        assertEquals(50, (check(5, name = "я".repeat(80)) as FeedbackCheck.Ok).feedback.name.length)
    }
}
```

`FeedbackPromptPolicyTest.kt`:

```kotlin
package com.smartspacecleaner.core.feedback

import com.smartspacecleaner.core.report.FailedLine
import com.smartspacecleaner.core.report.Report
import com.smartspacecleaner.core.report.ReportLine
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class FeedbackPromptPolicyTest {
    private fun report(done: List<ReportLine>, failed: List<FailedLine> = emptyList()) = Report(
        freeBefore = 0, freeAfter = 0, done = done, failed = failed, notSelected = emptyList(),
        inTrashBytes = 0, trashFreesOn = null, cacheNote = "Кэш очищен системой",
    )

    @Test
    fun `something was cleaned only when the report has done lines`() {
        assertTrue(FeedbackPromptPolicy.cleanedSomething(report(listOf(ReportLine("Дубли", 2, 100)))))
        assertFalse(FeedbackPromptPolicy.cleanedSomething(report(emptyList())))
        assertFalse(FeedbackPromptPolicy.cleanedSomething(report(emptyList(), listOf(FailedLine("/a", "нет доступа", "удалите вручную")))))
    }

    @Test
    fun `asks only after a cleanup, until sent or opted out`() {
        val cases = mapOf(
            Triple(true, false, false) to true,
            Triple(false, false, false) to false,
            Triple(true, true, false) to false,
            Triple(true, false, true) to false,
            Triple(true, true, true) to false,
        )
        cases.forEach { (input, expected) ->
            assertEquals(expected, FeedbackPromptPolicy.shouldPrompt(input.first, input.second, input.third), "$input")
        }
    }
}
```

- [ ] **Step 2: Run the tests and make sure they fail**

Run: `cd /s && GRADLE_USER_HOME=C:/gradle-home ./gradlew :core:test --tests "com.smartspacecleaner.core.feedback.*"`

Expected: FAIL при компиляции, `Unresolved reference: FeedbackValidator`.

- [ ] **Step 3: Implement**

`Feedback.kt`:

```kotlin
package com.smartspacecleaner.core.feedback

/** То, что человек ввёл в окне отзыва, как есть. */
data class FeedbackDraft(val rating: Int, val text: String, val name: String)

/** Проверенный отзыв: пробелы по краям убраны, имя не длиннее [FeedbackValidator.MAX_NAME]. */
data class Feedback(val rating: Int, val text: String, val name: String)

sealed interface FeedbackCheck {
    data class Ok(val feedback: Feedback) : FeedbackCheck
    data class Invalid(val reason: String) : FeedbackCheck
}

object FeedbackValidator {
    const val MAX_TEXT = 2000
    const val MAX_NAME = 50

    fun check(draft: FeedbackDraft): FeedbackCheck {
        val text = draft.text.trim()
        val name = draft.name.trim().take(MAX_NAME)
        return when {
            draft.rating !in 1..5 -> FeedbackCheck.Invalid("Поставьте оценку")
            text.length > MAX_TEXT -> FeedbackCheck.Invalid("Текст длиннее $MAX_TEXT символов")
            draft.rating <= 3 && text.isEmpty() -> FeedbackCheck.Invalid("Напишите, что мешало — так будет понятно, что исправить")
            else -> FeedbackCheck.Ok(Feedback(draft.rating, text, name))
        }
    }
}
```

`FeedbackPromptPolicy.kt`:

```kotlin
package com.smartspacecleaner.core.feedback

import com.smartspacecleaner.core.report.Report

/** Окно отзыва появляется само только после очистки, пока отзыв не отправлен и человек не отказался. */
object FeedbackPromptPolicy {
    fun cleanedSomething(report: Report): Boolean = report.done.isNotEmpty()

    fun shouldPrompt(cleanedSomething: Boolean, alreadySent: Boolean, optedOut: Boolean): Boolean =
        cleanedSomething && !alreadySent && !optedOut
}
```

- [ ] **Step 4: Run the tests and make sure they pass**

Run: `cd /s && GRADLE_USER_HOME=C:/gradle-home ./gradlew :core:test --tests "com.smartspacecleaner.core.feedback.*"`

Expected: PASS, 7 тестов.

- [ ] **Step 5: Commit**

```bash
git add core/src/main/kotlin/com/smartspacecleaner/core/feedback core/src/test/kotlin/com/smartspacecleaner/core/feedback
git commit -m "feat(core): feedback validation and when-to-ask policy"
```

---

### Task 2: Кодирование отзыва для Google Формы (`:core`)

**Files:**
- Create: `core/src/main/kotlin/com/smartspacecleaner/core/feedback/FeedbackFormEncoder.kt`
- Test: `core/src/test/kotlin/com/smartspacecleaner/core/feedback/FeedbackFormEncoderTest.kt`

**Interfaces:**
- Consumes: `Feedback` из задачи 1.
- Produces:
  - `enum class FeedbackSource(val code: String) { AFTER_CLEANUP("after_cleanup"), MENU("menu") }`
  - `data class DeviceInfo(val appVersion: String, val phone: String, val android: String)`
  - `data class FormFields(val rating: String, val text: String, val name: String, val appVersion: String, val phone: String, val android: String, val source: String)` — значения вида `"entry.123456"`
  - `object FeedbackFormEncoder { fun encode(feedback: Feedback, source: FeedbackSource, device: DeviceInfo, fields: FormFields): String }`

- [ ] **Step 1: Write the failing test**

```kotlin
package com.smartspacecleaner.core.feedback

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.net.URLDecoder

class FeedbackFormEncoderTest {
    private val fields = FormFields("entry.1", "entry.2", "entry.3", "entry.4", "entry.5", "entry.6", "entry.7")
    private val device = DeviceInfo("1.2 (3)", "Xiaomi Redmi Note 9 Pro", "12 (API 31)")

    private fun encode(text: String, name: String = "") =
        FeedbackFormEncoder.encode(Feedback(4, text, name), FeedbackSource.AFTER_CLEANUP, device, fields)

    private fun decode(body: String): Map<String, String> = body.split("&").associate {
        val (k, v) = it.split("=", limit = 2)
        URLDecoder.decode(k, "UTF-8") to URLDecoder.decode(v, "UTF-8")
    }

    @Test
    fun `every field goes to its entry`() {
        assertEquals(
            mapOf(
                "entry.1" to "4", "entry.2" to "Всё понятно", "entry.3" to "Аня", "entry.4" to "1.2 (3)",
                "entry.5" to "Xiaomi Redmi Note 9 Pro", "entry.6" to "12 (API 31)", "entry.7" to "after_cleanup",
            ),
            decode(encode("Всё понятно", "Аня")),
        )
    }

    @Test
    fun `special characters and line breaks survive the round trip`() {
        val tricky = "a&b=c+d %20\nвторая строка 😀"
        assertEquals(tricky, decode(encode(tricky))["entry.2"])
        assertTrue(encode(tricky).contains("entry.2=a%26b%3Dc%2Bd+%2520%0A"))
    }

    @Test
    fun `empty name is still sent as an empty field`() {
        assertTrue(encode("ок").contains("entry.3=&"))
    }

    @Test
    fun `2000 cyrillic characters fit into WorkManager input data as raw text`() {
        // Data хранит строки через writeUTF: 2 байта на кириллический символ. Лимит Data — 10 240 байт.
        val bytes = ByteArrayOutputStream().also { DataOutputStream(it).writeUTF("я".repeat(2000)) }.size()
        assertTrue(bytes < 10_240 - 1024, "сырые поля: $bytes байт")
        // А закодированное тело в лимит не помещается — поэтому кодируем в воркере, а не заранее.
        assertTrue(encode("я".repeat(2000)).length > 10_240)
    }
}
```

- [ ] **Step 2: Run the test and make sure it fails**

Run: `cd /s && GRADLE_USER_HOME=C:/gradle-home ./gradlew :core:test --tests "com.smartspacecleaner.core.feedback.FeedbackFormEncoderTest"`

Expected: FAIL, `Unresolved reference: FormFields`.

- [ ] **Step 3: Implement**

```kotlin
package com.smartspacecleaner.core.feedback

import java.net.URLEncoder

/** Где человек оставил отзыв; значение уходит в таблицу как есть. */
enum class FeedbackSource(val code: String) { AFTER_CLEANUP("after_cleanup"), MENU("menu") }

data class DeviceInfo(val appVersion: String, val phone: String, val android: String)

/** Имена полей Google Формы, вида `entry.123456`. */
data class FormFields(
    val rating: String,
    val text: String,
    val name: String,
    val appVersion: String,
    val phone: String,
    val android: String,
    val source: String,
)

/** Тело POST-запроса `application/x-www-form-urlencoded` для `…/formResponse`. */
object FeedbackFormEncoder {
    fun encode(feedback: Feedback, source: FeedbackSource, device: DeviceInfo, fields: FormFields): String =
        listOf(
            fields.rating to feedback.rating.toString(),
            fields.text to feedback.text,
            fields.name to feedback.name,
            fields.appVersion to device.appVersion,
            fields.phone to device.phone,
            fields.android to device.android,
            fields.source to source.code,
        ).joinToString("&") { (key, value) -> "${enc(key)}=${enc(value)}" }

    // Вариант с Charset появился только в Android API 33, а minSdk у нас 31.
    private fun enc(s: String): String = URLEncoder.encode(s, "UTF-8")
}
```

- [ ] **Step 4: Run the test and make sure it passes**

Run: `cd /s && GRADLE_USER_HOME=C:/gradle-home ./gradlew :core:test --tests "com.smartspacecleaner.core.feedback.*"`

Expected: PASS, 11 тестов.

- [ ] **Step 5: Commit**

```bash
git add core/src/main/kotlin/com/smartspacecleaner/core/feedback/FeedbackFormEncoder.kt core/src/test/kotlin/com/smartspacecleaner/core/feedback/FeedbackFormEncoderTest.kt
git commit -m "feat(core): encode feedback as a Google Form response body"
```

---

### Task 3: Тексты онбординга, плашек и «?» (`:core`)

**Files:**
- Create: `core/src/main/kotlin/com/smartspacecleaner/core/help/HelpTexts.kt`
- Test: `core/src/test/kotlin/com/smartspacecleaner/core/help/HelpTextsTest.kt`

**Interfaces:**
- Produces:
  - `enum class HintId { SUMMARY, PLAN, REPORT, HISTORY }`
  - `enum class InfoTopic { RISK_LEVELS, DUPLICATES, APP_CACHE }`
  - `data class HelpPage(val title: String, val body: String)`
  - `object HelpTexts { val onboarding: List<HelpPage>; fun banner(id: HintId): String; fun info(topic: InfoTopic): HelpPage }`

- [ ] **Step 1: Write the failing test**

```kotlin
package com.smartspacecleaner.core.help

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class HelpTextsTest {
    @Test
    fun `onboarding has the four pages from the spec`() {
        assertEquals(
            listOf("Освободим место — аккуратно", "Сначала только смотрю", "Всё можно вернуть", "Ваши файлы остаются у вас"),
            HelpTexts.onboarding.map { it.title },
        )
        assertTrue(HelpTexts.onboarding.all { it.body.isNotBlank() })
    }

    @Test
    fun `privacy page promises that file names never leave the phone`() {
        val body = HelpTexts.onboarding.last().body
        assertTrue("интернет" in body.lowercase() && "названия не отправляются" in body)
    }

    @Test
    fun `every screen hint and every info topic has text`() {
        HintId.entries.forEach { assertTrue(HelpTexts.banner(it).isNotBlank(), "$it") }
        InfoTopic.entries.forEach { assertTrue(HelpTexts.info(it).title.isNotBlank() && HelpTexts.info(it).body.isNotBlank(), "$it") }
    }

    @Test
    fun `plan hint explains all three risk levels`() {
        val text = HelpTexts.banner(HintId.PLAN)
        listOf("0 —", "1 —", "2 —").forEach { assertTrue(it in text, it) }
    }
}
```

- [ ] **Step 2: Run the test and make sure it fails**

Run: `cd /s && GRADLE_USER_HOME=C:/gradle-home ./gradlew :core:test --tests "com.smartspacecleaner.core.help.*"`

Expected: FAIL, `Unresolved reference: HelpTexts`.

- [ ] **Step 3: Implement**

```kotlin
package com.smartspacecleaner.core.help

/** Плашки, которые показываются один раз при первом посещении экрана. */
enum class HintId { SUMMARY, PLAN, REPORT, HISTORY }

/** Темы для значка «?». */
enum class InfoTopic { RISK_LEVELS, DUPLICATES, APP_CACHE }

data class HelpPage(val title: String, val body: String)

/** Все тексты онбординга и подсказок в одном месте, чтобы править их не ища по экранам. */
object HelpTexts {
    val onboarding: List<HelpPage> = listOf(
        HelpPage(
            "Освободим место — аккуратно",
            "Приложение находит дубли, старые скриншоты, ненужные установщики (APK), крупные файлы, " +
                "медиа из мессенджеров и кэш приложений.",
        ),
        HelpPage(
            "Сначала только смотрю",
            "Три шага: скан → план → вы отмечаете, что убрать. Само приложение ничего не удаляет.",
        ),
        HelpPage(
            "Всё можно вернуть",
            "Удалённое 7 дней лежит в корзине. В «Истории» видно, что, откуда и когда убрано. " +
                "Исключение — установщики уже установленных приложений: они удаляются сразу, при необходимости их скачивают заново.",
        ),
        HelpPage(
            "Ваши файлы остаются у вас",
            "Доступ ко всем файлам нужен, иначе приложение не увидит загрузки и папки мессенджеров. " +
                "Интернет используется только для отправки отзыва. Файлы и их названия не отправляются.",
        ),
    )

    fun banner(id: HintId): String = when (id) {
        HintId.SUMMARY -> "Это оценка памяти. Нажмите «Показать план» — там список того, что можно убрать."
        HintId.PLAN -> "Отметьте группы галочками. Нажмите на файл, чтобы его посмотреть. " +
            "Риск: 0 — безопасно, 1 — точные копии (оригинал остаётся), 2 — личные файлы, проверьте."
        HintId.REPORT -> "Готово. Передумали? Всё вернуть можно в «Истории»."
        HintId.HISTORY -> "Здесь корзина: вернуть или удалить навсегда, по одному или все сразу."
    }

    fun info(topic: InfoTopic): HelpPage = when (topic) {
        InfoTopic.RISK_LEVELS -> HelpPage(
            "Уровни риска",
            "0 — временные файлы и кэш, их можно убирать смело. " +
                "1 — точные копии и установщики уже установленных приложений: оригинал или приложение остаются. " +
                "2 — ваши личные файлы. Они не отмечены заранее: посмотрите и решите сами.",
        )
        InfoTopic.DUPLICATES -> HelpPage(
            "Дубли",
            "Дублями считаются файлы с одинаковым содержимым, а не только с одинаковым именем. " +
                "Оставляется копия в «рабочей» папке или самая старая. Нажмите на файл, чтобы сравнить копию с оригиналом.",
        )
        InfoTopic.APP_CACHE -> HelpPage(
            "Кэш приложений",
            "Это временные данные приложений: они сами появятся снова, когда понадобятся. " +
                "Очищает их система по запросу, личные файлы не затрагиваются.",
        )
    }
}
```

- [ ] **Step 4: Run the tests and make sure they pass**

Run: `cd /s && GRADLE_USER_HOME=C:/gradle-home ./gradlew :core:test`

Expected: PASS. Все тесты `:core`, включая старые.

- [ ] **Step 5: Commit**

```bash
git add core/src/main/kotlin/com/smartspacecleaner/core/help core/src/test/kotlin/com/smartspacecleaner/core/help
git commit -m "feat(core): onboarding, hint and info texts"
```

---

### Task 4: Флаги и онбординг (`:app`)

**Files:**
- Create: `app/src/main/java/com/smartspacecleaner/data/UiPrefs.kt`
- Create: `app/src/main/java/com/smartspacecleaner/ui/OnboardingScreen.kt`
- Modify: `app/src/main/java/com/smartspacecleaner/AppContainer.kt` (добавить `val prefs`)
- Modify: `app/src/main/java/com/smartspacecleaner/ui/MainViewModel.kt` (`Screen.Onboarding`, `onResume`, `finishOnboarding`, `openOnboarding`)
- Modify: `app/src/main/java/com/smartspacecleaner/MainActivity.kt` (ветка `Screen.Onboarding`, `BackHandler`)

**Interfaces:**
- Consumes: `HelpTexts.onboarding`, `HintId` из задачи 3.
- Produces:
  - `class UiPrefs(context: Context)` со свойствами `onboardingDone: Boolean`, `dismissedHints: Set<HintId>`, `feedbackSent: Boolean`, `feedbackOptOut: Boolean`, `feedbackName: String`.
  - `AppContainer.prefs: UiPrefs`.
  - В `UiState`: `onboardingReturnTo: Screen?`. В `MainViewModel`: `finishOnboarding(hasAllFilesAccess: Boolean)`, `openOnboarding()`.
  - `@Composable fun OnboardingScreen(finishLabel: String, onFinish: () -> Unit)`.

- [ ] **Step 1: Create `UiPrefs`**

```kotlin
package com.smartspacecleaner.data

import android.content.Context
import com.smartspacecleaner.core.help.HintId

/** Мелкие флаги интерфейса: что уже показано и что человек выбрал в окне отзыва. */
class UiPrefs(context: Context) {
    private val prefs = context.getSharedPreferences("ui", Context.MODE_PRIVATE)

    var onboardingDone: Boolean
        get() = prefs.getBoolean(ONBOARDING_DONE, false)
        set(value) = prefs.edit().putBoolean(ONBOARDING_DONE, value).apply()

    var dismissedHints: Set<HintId>
        get() = prefs.getStringSet(DISMISSED_HINTS, emptySet()).orEmpty()
            .mapNotNull { name -> HintId.entries.firstOrNull { it.name == name } }
            .toSet()
        set(value) = prefs.edit().putStringSet(DISMISSED_HINTS, value.map { it.name }.toSet()).apply()

    var feedbackSent: Boolean
        get() = prefs.getBoolean(FEEDBACK_SENT, false)
        set(value) = prefs.edit().putBoolean(FEEDBACK_SENT, value).apply()

    var feedbackOptOut: Boolean
        get() = prefs.getBoolean(FEEDBACK_OPT_OUT, false)
        set(value) = prefs.edit().putBoolean(FEEDBACK_OPT_OUT, value).apply()

    var feedbackName: String
        get() = prefs.getString(FEEDBACK_NAME, "").orEmpty()
        set(value) = prefs.edit().putString(FEEDBACK_NAME, value).apply()

    private companion object {
        const val ONBOARDING_DONE = "onboarding_done"
        const val DISMISSED_HINTS = "dismissed_hints"
        const val FEEDBACK_SENT = "feedback_sent"
        const val FEEDBACK_OPT_OUT = "feedback_opt_out"
        const val FEEDBACK_NAME = "feedback_name"
    }
}
```

- [ ] **Step 2: Add `prefs` to `AppContainer`**

В `AppContainer.kt` после строки `val storageInfo = StorageInfo(context)` добавить:

```kotlin
    val prefs = UiPrefs(context)
```

И импорт `import com.smartspacecleaner.data.UiPrefs`.

- [ ] **Step 3: Create `OnboardingScreen`**

```kotlin
package com.smartspacecleaner.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.smartspacecleaner.core.help.HelpTexts
import kotlinx.coroutines.launch

/** Четыре экрана при первом запуске; [finishLabel] — «Начать» или «Готово», если открыт из меню. */
@Composable
fun OnboardingScreen(finishLabel: String, onFinish: () -> Unit) {
    val pages = HelpTexts.onboarding
    val pager = rememberPagerState { pages.size }
    val scope = rememberCoroutineScope()
    val last = pager.currentPage == pages.lastIndex
    Column(Modifier.fillMaxSize().padding(24.dp)) {
        HorizontalPager(pager, Modifier.weight(1f)) { i ->
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
            ) {
                Text(pages[i].title, style = MaterialTheme.typography.headlineSmall)
                Text(pages[i].body, style = MaterialTheme.typography.bodyLarge)
            }
        }
        Row(Modifier.fillMaxWidth().padding(vertical = 16.dp), horizontalArrangement = Arrangement.Center) {
            repeat(pages.size) { i ->
                val color = if (i == pager.currentPage) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
                Box(Modifier.padding(4.dp).size(8.dp).clip(CircleShape).background(color))
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            if (last) Spacer(Modifier) else TextButton(onClick = onFinish) { Text("Пропустить") }
            Button(onClick = { if (last) onFinish() else scope.launch { pager.animateScrollToPage(pager.currentPage + 1) } }) {
                Text(if (last) finishLabel else "Далее")
            }
        }
    }
}
```

- [ ] **Step 4: Teach `MainViewModel` about onboarding**

В `MainViewModel.kt`:

1. Заменить `enum class Screen { Permission, … }` на:

```kotlin
enum class Screen { Onboarding, Permission, Scanning, Summary, Plan, Executing, Report, History }
```

2. В `UiState` после `val toast: String? = null,` добавить:

```kotlin
    val onboardingReturnTo: Screen? = null,
```

3. Заменить `private val _state = MutableStateFlow(UiState())` на:

```kotlin
    private val _state = MutableStateFlow(
        UiState(screen = if (c.prefs.onboardingDone) Screen.Permission else Screen.Onboarding),
    )
```

4. В начало `onResume` добавить первой строкой:

```kotlin
        // Пока человек читает онбординг, экран разрешения не перебивает его.
        if (_state.value.screen == Screen.Onboarding) return
```

5. После `onResume` добавить:

```kotlin
    fun openOnboarding() = _state.update { it.copy(onboardingReturnTo = it.screen, screen = Screen.Onboarding) }

    fun finishOnboarding(hasAllFilesAccess: Boolean) {
        c.prefs.onboardingDone = true
        val back = _state.value.onboardingReturnTo
        when {
            back != null -> _state.update { it.copy(screen = back, onboardingReturnTo = null) }
            hasAllFilesAccess -> startScan()
            else -> _state.update { it.copy(screen = Screen.Permission) }
        }
    }
```

- [ ] **Step 5: Show the screen in `MainActivity`**

1. В `when (state.screen)` первой веткой добавить:

```kotlin
                            Screen.Onboarding -> OnboardingScreen(
                                finishLabel = if (state.onboardingReturnTo != null) "Готово" else "Начать",
                                onFinish = { vm.finishOnboarding(Environment.isExternalStorageManager()) },
                            )
```

2. Заменить текущий `BackHandler(...) { ... }` на:

```kotlin
                BackHandler(
                    enabled = state.screen == Screen.Plan || state.screen == Screen.History ||
                        (state.screen == Screen.Onboarding && state.onboardingReturnTo != null),
                ) {
                    when (state.screen) {
                        Screen.Plan -> vm.backToSummary()
                        Screen.History -> vm.closeHistory()
                        else -> vm.finishOnboarding(Environment.isExternalStorageManager())
                    }
                }
```

3. Импорт: `import com.smartspacecleaner.ui.OnboardingScreen`.

- [ ] **Step 6: Build**

Run: `cd /s && GRADLE_USER_HOME=C:/gradle-home ./gradlew :app:assembleDebug`

Expected: BUILD SUCCESSFUL.

- [ ] **Step 7: Check on the phone (Review Focus 4)**

Версия на телефоне подписана ключом автора, поэтому отладочная сборка поверх неё не встанет. Проверяем релизную:

```bash
cd /s && GRADLE_USER_HOME=C:/gradle-home ./gradlew :app:assembleRelease
MSYS_NO_PATHCONV=1 adb -s ee5591f0 shell pm clear com.smartspacecleaner
MSYS_NO_PATHCONV=1 adb -s ee5591f0 install -r S:/app/build/outputs/apk/release/app-release.apk
```

`pm clear` сбрасывает флаги, и онбординг покажется заново. Корзина на карте памяти не трогается, но история в приложении стирается — предупредить автора заранее. Если MIUI отменит установку (`INSTALL_FAILED_USER_RESTRICTED`), попросить автора подтвердить на телефоне и повторить.

Ожидается:
- 4 экрана листаются;
- «Пропустить» и «Начать» ведут на экран разрешения;
- после выдачи разрешения запускается скан;
- если свернуть приложение на экране онбординга и вернуться, онбординг остаётся на месте.

Попросить у автора скриншоты.

- [ ] **Step 8: Commit**

```bash
git add app/src/main/java/com/smartspacecleaner
git commit -m "feat(app): first-run onboarding"
```

---

### Task 5: Плашки, «?» и меню (`:app`)

**Files:**
- Create: `app/src/main/java/com/smartspacecleaner/ui/Help.kt`
- Modify: `app/src/main/java/com/smartspacecleaner/ui/MainViewModel.kt` (`dismissedHints`, `dismissHint`, `resetHints`)
- Modify: `app/src/main/java/com/smartspacecleaner/MainActivity.kt` (меню и плашка над экраном)
- Modify: `app/src/main/java/com/smartspacecleaner/ui/PlanScreen.kt:203-239` (`LevelHeader`, `GroupCard`)

**Interfaces:**
- Consumes: `HintId`, `InfoTopic`, `HelpTexts` (задача 3); `UiPrefs.dismissedHints`, `MainViewModel.openOnboarding()` (задача 4).
- Produces:
  - `fun Screen.hint(): HintId?`
  - `@Composable fun HintBanner(text: String, onClose: () -> Unit)`
  - `@Composable fun InfoIcon(topic: InfoTopic)`
  - `@Composable fun AppMenu(feedbackEnabled: Boolean, onHowTo: () -> Unit, onFeedback: () -> Unit, onResetHints: () -> Unit)`
  - В `UiState`: `dismissedHints: Set<HintId>`. В VM: `dismissHint(id: HintId)`, `resetHints()`.

- [ ] **Step 1: Create `Help.kt`**

```kotlin
package com.smartspacecleaner.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.smartspacecleaner.core.help.HelpTexts
import com.smartspacecleaner.core.help.HintId
import com.smartspacecleaner.core.help.InfoTopic

fun Screen.hint(): HintId? = when (this) {
    Screen.Summary -> HintId.SUMMARY
    Screen.Plan -> HintId.PLAN
    Screen.Report -> HintId.REPORT
    Screen.History -> HintId.HISTORY
    else -> null
}

/** Плашка-подсказка; после крестика больше не показывается. */
@Composable
fun HintBanner(text: String, onClose: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 12.dp)) {
            Text("💡 $text", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f).padding(vertical = 8.dp))
            TextButton(onClick = onClose) { Text("✕") }
        }
    }
}

/** Значок «?»: по нажатию — короткое пояснение. */
@Composable
fun InfoIcon(topic: InfoTopic) {
    var open by remember { mutableStateOf(false) }
    TextButton(onClick = { open = true }, contentPadding = PaddingValues(0.dp), modifier = Modifier.size(32.dp)) {
        Text("?", fontWeight = FontWeight.Bold, fontSize = 18.sp)
    }
    if (open) {
        val page = HelpTexts.info(topic)
        AlertDialog(
            onDismissRequest = { open = false },
            title = { Text(page.title) },
            text = { Text(page.body) },
            confirmButton = { TextButton(onClick = { open = false }) { Text("Понятно") } },
        )
    }
}

@Composable
fun AppMenu(feedbackEnabled: Boolean, onHowTo: () -> Unit, onFeedback: () -> Unit, onResetHints: () -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        TextButton(onClick = { open = true }) { Text("⋮", fontSize = 22.sp) }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(text = { Text("Как пользоваться") }, onClick = { open = false; onHowTo() })
            if (feedbackEnabled) {
                DropdownMenuItem(text = { Text("Оставить отзыв") }, onClick = { open = false; onFeedback() })
            }
            DropdownMenuItem(text = { Text("Сбросить подсказки") }, onClick = { open = false; onResetHints() })
        }
    }
}
```

- [ ] **Step 2: Hints in `MainViewModel`**

1. В `UiState` добавить `val dismissedHints: Set<HintId> = emptySet(),`.
2. Начальное состояние дополнить:

```kotlin
        UiState(
            screen = if (c.prefs.onboardingDone) Screen.Permission else Screen.Onboarding,
            dismissedHints = c.prefs.dismissedHints,
        ),
```

3. Добавить методы:

```kotlin
    fun dismissHint(id: HintId) {
        val hints = _state.value.dismissedHints + id
        c.prefs.dismissedHints = hints
        _state.update { it.copy(dismissedHints = hints) }
    }

    fun resetHints() {
        c.prefs.dismissedHints = emptySet()
        _state.update { it.copy(dismissedHints = emptySet(), toast = "Подсказки снова включены") }
    }
```

4. Импорт `com.smartspacecleaner.core.help.HintId`.

- [ ] **Step 3: Menu and banner around the screens in `MainActivity`**

Заменить содержимое `Scaffold { padding -> … }`. Вместо `Box(Modifier.padding(padding)) { when … }` будет:

```kotlin
                Scaffold { padding ->
                    Column(Modifier.padding(padding)) {
                        if (state.screen == Screen.Summary || state.screen == Screen.History) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                                AppMenu(
                                    feedbackEnabled = vm.feedbackEnabled,
                                    onHowTo = vm::openOnboarding,
                                    onFeedback = vm::openFeedback,
                                    onResetHints = vm::resetHints,
                                )
                            }
                        }
                        state.screen.hint()?.takeIf { it !in state.dismissedHints }?.let { id ->
                            HintBanner(HelpTexts.banner(id)) { vm.dismissHint(id) }
                        }
                        Box(Modifier.weight(1f)) {
                            when (state.screen) {
                                // … все ветки без изменений …
                            }
                        }
                    }
                }
```

`vm.feedbackEnabled` и `vm.openFeedback` появятся в задаче 6. Чтобы эта задача собиралась сама по себе, добавить в `MainViewModel` временно:

```kotlin
    val feedbackEnabled: Boolean get() = false
    fun openFeedback() = Unit
```

Задача 6 заменит обе строки на настоящие.

Импорты: `androidx.compose.foundation.layout.Arrangement`, `Column`, `Row`, `fillMaxWidth`, `com.smartspacecleaner.core.help.HelpTexts`, `com.smartspacecleaner.ui.AppMenu`, `com.smartspacecleaner.ui.HintBanner`, `com.smartspacecleaner.ui.hint`.

- [ ] **Step 4: «?» in the plan**

В `PlanScreen.kt` заменить `LevelHeader`:

```kotlin
@Composable
private fun LevelHeader(level: RiskLevel) {
    val text = when (level) {
        RiskLevel.SAFE -> "Уровень 0 · Безопасное"
        RiskLevel.EXACT -> "Уровень 1 · Точные дубли и установщики"
        RiskLevel.PERSONAL -> "Уровень 2 · Личное — решаете поштучно"
    }
    Row(Modifier.padding(start = 16.dp, top = 16.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(text, style = MaterialTheme.typography.titleSmall)
        InfoIcon(InfoTopic.RISK_LEVELS)
    }
}
```

В `GroupCard` заменить строку `Text(title, style = MaterialTheme.typography.titleMedium)` на:

```kotlin
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f, fill = false))
                    when (group.kind) {
                        GroupKind.DUPLICATES -> InfoIcon(InfoTopic.DUPLICATES)
                        GroupKind.APP_CACHE -> InfoIcon(InfoTopic.APP_CACHE)
                        else -> Unit
                    }
                }
```

Импорт `com.smartspacecleaner.core.help.InfoTopic`.

- [ ] **Step 5: Build, install, check**

Run: `cd /s && GRADLE_USER_HOME=C:/gradle-home ./gradlew :app:assembleRelease`, затем `MSYS_NO_PATHCONV=1 adb -s ee5591f0 install -r S:/app/build/outputs/apk/release/app-release.apk`.

Ожидается:
- на «Итоге», «Плане», «Отчёте» и «Истории» по одной плашке;
- после ✕ плашка исчезает и после перезапуска не возвращается;
- «?» у уровней, у дублей и у кэша открывают пояснения;
- меню ⋮ на «Итоге» и «Истории»: «Как пользоваться» открывает онбординг, «Готово» возвращает на тот же экран; «Сбросить подсказки» возвращает плашки.

Попросить у автора скриншоты.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/smartspacecleaner
git commit -m "feat(app): one-time screen hints, info icons and menu"
```

---

### Task 6: Окно отзыва и отправка (`:app`)

**Files:**
- Create: `app/src/main/java/com/smartspacecleaner/feedback/FeedbackFormConfig.kt`
- Create: `app/src/main/java/com/smartspacecleaner/feedback/FeedbackWorker.kt`
- Create: `app/src/main/java/com/smartspacecleaner/feedback/FeedbackSender.kt`
- Create: `app/src/main/java/com/smartspacecleaner/ui/FeedbackDialog.kt`
- Modify: `app/src/main/java/com/smartspacecleaner/AppContainer.kt` (`val feedback`)
- Modify: `app/src/main/java/com/smartspacecleaner/ui/MainViewModel.kt`
- Modify: `app/src/main/java/com/smartspacecleaner/MainActivity.kt`
- Modify: `app/src/main/AndroidManifest.xml`
- Modify: `app/build.gradle.kts`

**Interfaces:**
- Consumes:
  - `FeedbackDraft`, `Feedback`, `FeedbackCheck`, `FeedbackValidator`, `FeedbackPromptPolicy` (задача 1);
  - `FeedbackSource`, `DeviceInfo`, `FormFields`, `FeedbackFormEncoder` (задача 2);
  - `UiPrefs` (задача 4).
- Produces:
  - `object FeedbackFormConfig { const val FORM_ID: String; val fields: FormFields; val isConfigured: Boolean; val submitUrl: String }`
  - `class FeedbackSender(context: Context) { val enabled: Boolean; fun send(feedback: Feedback, source: FeedbackSource): Boolean }` — возвращает `true`, если сеть есть прямо сейчас.
  - В `UiState`: `feedback: FeedbackSource?`, `feedbackName: String`. В VM: `feedbackEnabled`, `openFeedback()`, `feedbackLater()`, `feedbackOptOut()`, `sendFeedback(draft: FeedbackDraft)`.

- [ ] **Step 1: Manifest, BuildConfig, version**

В `AndroidManifest.xml` после разрешения `QUERY_ALL_PACKAGES` добавить:

```xml
    <!-- Только для отправки отзыва в Google Форму; файлы и их названия не отправляются -->
    <uses-permission android:name="android.permission.INTERNET" />
    <uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
```

В `app/build.gradle.kts`:
- `versionCode = 2` → `versionCode = 3`;
- `versionName = "1.1"` → `versionName = "1.2"`;
- `buildFeatures { compose = true }` → `buildFeatures { compose = true; buildConfig = true }`.

- [ ] **Step 2: `FeedbackFormConfig`**

```kotlin
package com.smartspacecleaner.feedback

import com.smartspacecleaner.core.feedback.FormFields

/**
 * Куда отправлять отзывы. Значения берутся из «предварительно заполненной ссылки» Google Формы:
 * …/forms/d/e/<FORM_ID>/viewform?entry.N=… Пока FORM_ID пустой, окно отзыва и пункт меню скрыты.
 */
object FeedbackFormConfig {
    const val FORM_ID = ""

    val fields = FormFields(
        rating = "entry.0",
        text = "entry.0",
        name = "entry.0",
        appVersion = "entry.0",
        phone = "entry.0",
        android = "entry.0",
        source = "entry.0",
    )

    val isConfigured: Boolean get() = FORM_ID.isNotEmpty()
    val submitUrl: String get() = "https://docs.google.com/forms/d/e/$FORM_ID/formResponse"
}
```

Пустой `FORM_ID` здесь — осознанное значение «форма ещё не настроена», а не заглушка. Задача 7 заполнит его настоящими данными.

- [ ] **Step 3: `FeedbackWorker`**

```kotlin
package com.smartspacecleaner.feedback

import android.content.Context
import android.os.Build
import android.util.Log
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.smartspacecleaner.BuildConfig
import com.smartspacecleaner.core.feedback.DeviceInfo
import com.smartspacecleaner.core.feedback.Feedback
import com.smartspacecleaner.core.feedback.FeedbackFormEncoder
import com.smartspacecleaner.core.feedback.FeedbackSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.TimeUnit

/**
 * Отправляет один отзыв в Google Форму. Ждёт сеть, при сбое повторяет с растущей паузой, до 5 попыток.
 * Во входных данных лежат сырые поля: закодированный кириллический текст не влез бы в лимит Data (10 КБ).
 */
class FeedbackWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val feedback = Feedback(
            rating = inputData.getInt(KEY_RATING, 0),
            text = inputData.getString(KEY_TEXT).orEmpty(),
            name = inputData.getString(KEY_NAME).orEmpty(),
        )
        val source = FeedbackSource.entries.firstOrNull { it.code == inputData.getString(KEY_SOURCE) } ?: FeedbackSource.MENU
        val body = FeedbackFormEncoder.encode(feedback, source, deviceInfo(), FeedbackFormConfig.fields)
        val code = withContext(Dispatchers.IO) { post(body) }
        return when {
            code in 200..399 -> Result.success()
            code in 400..499 -> {
                Log.w(TAG, "Форма отклонила отзыв: HTTP $code — проверьте FORM_ID и номера полей")
                Result.failure()
            }
            runAttemptCount + 1 >= MAX_ATTEMPTS -> {
                Log.w(TAG, "Отзыв не отправлен за $MAX_ATTEMPTS попыток, последний ответ: $code")
                Result.failure()
            }
            else -> Result.retry()
        }
    }

    /** Код ответа сервера или -1, если до сервера не достучались. */
    private fun post(body: String): Int = try {
        val connection = URL(FeedbackFormConfig.submitUrl).openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "POST"
            connection.doOutput = true
            connection.connectTimeout = TIMEOUT_MS
            connection.readTimeout = TIMEOUT_MS
            connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")
            connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            connection.responseCode
        } finally {
            connection.disconnect()
        }
    } catch (e: IOException) {
        -1
    }

    companion object {
        private const val TAG = "FeedbackWorker"
        private const val MAX_ATTEMPTS = 5
        private const val TIMEOUT_MS = 15_000
        private const val KEY_RATING = "rating"
        private const val KEY_TEXT = "text"
        private const val KEY_NAME = "name"
        private const val KEY_SOURCE = "source"

        fun enqueue(context: Context, feedback: Feedback, source: FeedbackSource) {
            val request = OneTimeWorkRequestBuilder<FeedbackWorker>()
                .setInputData(
                    workDataOf(
                        KEY_RATING to feedback.rating,
                        KEY_TEXT to feedback.text,
                        KEY_NAME to feedback.name,
                        KEY_SOURCE to source.code,
                    ),
                )
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .build()
            WorkManager.getInstance(context).enqueue(request)
        }

        fun deviceInfo() = DeviceInfo(
            appVersion = "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
            phone = "${Build.MANUFACTURER} ${Build.MODEL}",
            android = "${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})",
        )
    }
}
```

- [ ] **Step 4: `FeedbackSender`**

```kotlin
package com.smartspacecleaner.feedback

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.smartspacecleaner.core.feedback.Feedback
import com.smartspacecleaner.core.feedback.FeedbackSource

class FeedbackSender(private val context: Context) {
    val enabled: Boolean get() = FeedbackFormConfig.isConfigured

    /** Ставит отзыв в очередь; `true` — интернет есть прямо сейчас, и отзыв уйдёт сразу. */
    fun send(feedback: Feedback, source: FeedbackSource): Boolean {
        FeedbackWorker.enqueue(context, feedback, source)
        val cm = context.getSystemService(ConnectivityManager::class.java)
        val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }
}
```

В `AppContainer.kt` рядом с `val prefs` добавить `val feedback = FeedbackSender(context)` и импорт `com.smartspacecleaner.feedback.FeedbackSender`.

- [ ] **Step 5: `FeedbackDialog`**

```kotlin
package com.smartspacecleaner.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.smartspacecleaner.core.feedback.FeedbackCheck
import com.smartspacecleaner.core.feedback.FeedbackDraft
import com.smartspacecleaner.core.feedback.FeedbackSource
import com.smartspacecleaner.core.feedback.FeedbackValidator

private val STAR_GOLD = Color(0xFFF9A825)

@Composable
fun FeedbackDialog(
    source: FeedbackSource,
    initialName: String,
    onSend: (FeedbackDraft) -> Unit,
    onLater: () -> Unit,
    onOptOut: () -> Unit,
) {
    // rememberSaveable: оценка и текст переживают поворот экрана и сворачивание.
    var rating by rememberSaveable { mutableIntStateOf(0) }
    var text by rememberSaveable { mutableStateOf("") }
    var name by rememberSaveable { mutableStateOf(initialName) }
    val draft = FeedbackDraft(rating, text, name)
    val check = FeedbackValidator.check(draft)

    AlertDialog(
        onDismissRequest = onLater,
        title = { Text("Помогите улучшить приложение") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Как вам очистка?")
                Row {
                    (1..5).forEach { star ->
                        TextButton(onClick = { rating = star }, contentPadding = PaddingValues(0.dp), modifier = Modifier.size(44.dp)) {
                            Text(if (star <= rating) "★" else "☆", fontSize = 30.sp, color = STAR_GOLD)
                        }
                    }
                }
                OutlinedTextField(
                    value = text,
                    onValueChange = { if (it.length <= FeedbackValidator.MAX_TEXT) text = it },
                    label = { Text("Что понравилось, что мешало, что непонятно?") },
                    minLines = 3,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = name,
                    onValueChange = { if (it.length <= FeedbackValidator.MAX_NAME) name = it },
                    label = { Text("Имя (необязательно)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (rating > 0 && check is FeedbackCheck.Invalid) {
                    Text(check.reason, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
                Text(
                    "Отправятся: ваш текст, оценка, имя, версия приложения, модель телефона и версия Android. " +
                        "Файлы и их названия не отправляются.",
                    style = MaterialTheme.typography.bodySmall,
                )
                if (source == FeedbackSource.AFTER_CLEANUP) {
                    TextButton(onClick = onOptOut) { Text("Больше не спрашивать") }
                }
            }
        },
        confirmButton = { Button(onClick = { onSend(draft) }, enabled = check is FeedbackCheck.Ok) { Text("Отправить") } },
        dismissButton = { TextButton(onClick = onLater) { Text("Позже") } },
    )
}
```

- [ ] **Step 6: Feedback in `MainViewModel`**

1. В `UiState` добавить:

```kotlin
    val feedback: FeedbackSource? = null,
    val feedbackName: String = "",
```

2. Начальное состояние дополнить `feedbackName = c.prefs.feedbackName,`.
3. Удалить временные `feedbackEnabled` и `openFeedback` из задачи 5 и добавить:

```kotlin
    val feedbackEnabled: Boolean get() = c.feedback.enabled

    fun openFeedback() = _state.update { it.copy(feedback = FeedbackSource.MENU) }
    fun feedbackLater() = _state.update { it.copy(feedback = null) }

    fun feedbackOptOut() {
        c.prefs.feedbackOptOut = true
        _state.update { it.copy(feedback = null) }
    }

    fun sendFeedback(draft: FeedbackDraft) {
        val feedback = (FeedbackValidator.check(draft) as? FeedbackCheck.Ok)?.feedback ?: return
        val online = c.feedback.send(feedback, _state.value.feedback ?: FeedbackSource.MENU)
        c.prefs.feedbackSent = true
        c.prefs.feedbackName = feedback.name
        val message = if (online) "Спасибо! Отзыв отправлен" else "Спасибо! Отправим, когда появится интернет"
        _state.update { it.copy(feedback = null, feedbackName = feedback.name, toast = message) }
    }
```

4. В `finish()` заменить строку `_state.update { it.copy(screen = Screen.Report, report = report) }` на:

```kotlin
        val ask = c.feedback.enabled && FeedbackPromptPolicy.shouldPrompt(
            cleanedSomething = FeedbackPromptPolicy.cleanedSomething(report),
            alreadySent = c.prefs.feedbackSent,
            optedOut = c.prefs.feedbackOptOut,
        )
        _state.update { it.copy(screen = Screen.Report, report = report, feedback = if (ask) FeedbackSource.AFTER_CLEANUP else null) }
```

5. Импорты: `com.smartspacecleaner.core.feedback.FeedbackCheck`, `FeedbackDraft`, `FeedbackPromptPolicy`, `FeedbackSource`, `FeedbackValidator`.

- [ ] **Step 7: Show the dialog in `MainActivity`**

Внутри `Scaffold { padding -> … }`, после `Column(...) { … }`, добавить:

```kotlin
                    state.feedback?.let { source ->
                        FeedbackDialog(
                            source = source,
                            initialName = state.feedbackName,
                            onSend = vm::sendFeedback,
                            onLater = vm::feedbackLater,
                            onOptOut = vm::feedbackOptOut,
                        )
                    }
```

Импорт `com.smartspacecleaner.ui.FeedbackDialog`.

- [ ] **Step 8: Build**

Run: `cd /s && GRADLE_USER_HOME=C:/gradle-home ./gradlew :core:test :app:assembleRelease`

Expected: BUILD SUCCESSFUL. Пока `FORM_ID` пустой, пункта «Оставить отзыв» и окна нет. Так и должно быть.

- [ ] **Step 9: Check the dialog without a form (Review Focus 5)**

Временно, не коммитить: поставить `FORM_ID = "test"`, собрать, установить.

Ожидается:
- после очистки появляется окно;
- «Позже» — окно закрывается и после следующей очистки появляется снова;
- оценка 2 без текста — кнопка «Отправить» неактивна, видна причина;
- если ввести текст и повернуть экран или свернуть и вернуть приложение, текст и оценка на месте;
- «Отправить» показывает тост. В `adb logcat -s FeedbackWorker` будет `HTTP 404` или `HTTP 400`, потому что такой формы нет. Это ожидаемо.

Вернуть `FORM_ID = ""`.

- [ ] **Step 10: Commit**

```bash
git add app/src/main app/build.gradle.kts
git commit -m "feat(app): feedback dialog after cleanup, sent to a Google Form via WorkManager"
```

---

### Task 7: Подключение формы, навык разбора, приёмка

**Files:**
- Modify: `app/src/main/java/com/smartspacecleaner/feedback/FeedbackFormConfig.kt`
- Create: `.claude/skills/razbor-otzyvov/SKILL.md`

**Interfaces:**
- Consumes: всё из задач 1–6.
- Produces: рабочая отправка и навык `razbor-otzyvov`.

- [ ] **Step 1: Get the form from the author**

Попросить автора выполнить раздел «Настройка формы» из спецификации и прислать предзаполненную ссылку. В ней поле оценки = `5`, остальные поля = своё название: `ТЕКСТ`, `ИМЯ`, `ВЕРСИЯ`, `ТЕЛЕФОН`, `ANDROID`, `ОТКУДА`. Из ссылки вида `https://docs.google.com/forms/d/e/<FORM_ID>/viewform?usp=pp_url&entry.111=5&entry.222=ТЕКСТ…` взять `FORM_ID` и сопоставить `entry.N` с полями по подставленным значениям.

- [ ] **Step 2: Fill `FeedbackFormConfig`**

Подставить настоящие `FORM_ID` и `entry.N` вместо `""` и `"entry.0"`.

- [ ] **Step 3: Write the skill**

`.claude/skills/razbor-otzyvov/SKILL.md`:

```markdown
---
name: razbor-otzyvov
description: Разобрать отзывы однокурсников о Smart Space Cleaner из Google Таблицы и выдать автору сводку и список решений. Используй по фразам «разбери отзывы», «что пишут в отзывах», «обратная связь от группы».
---

# Разбор отзывов

Отвечай автору по-русски. Код не меняй: решения автор принимает сам (план → «да» → реализация).

1. Скачай CSV: `curl -sL "<CSV_URL>" -o "<scratchpad>/feedback.csv"`. Если ссылки ниже нет — попроси автора опубликовать лист ответов: Файл → Поделиться → Опубликовать в интернете → «Ответы на форму» → CSV.
2. Прочитай CSV через Python (`csv`, кодировка UTF-8). Колонки: отметка времени, оценка, текст, имя, версия, телефон, Android, откуда.
3. Склей дубли: одинаковый текст и имя в пределах часа — один отзыв (повторная отправка после сбоя сети).
4. Сводка: сколько отзывов, средняя оценка — всего и по версиям; сколько после очистки и сколько из меню.
5. Сгруппируй тексты по темам: **ошибки**, **непонятно**, **идеи**, **похвала**. В каждой теме — короткие цитаты с именем (или «аноним») и телефоном, если это может быть баг конкретной модели.
6. «Что сделать в первую очередь»: до 5 пунктов. Для каждого — что исправить, почему (сколько людей, какая оценка), и где в коде (`app/src/main/java/...`, `core/src/main/kotlin/...`), если понятно.
7. В конце спроси, какой пункт берём в работу.

CSV_URL: <автор пришлёт ссылку>
```

Когда автор пришлёт CSV-ссылку, заменить `<автор пришлёт ссылку>` на неё.

- [ ] **Step 4: Build, install, end-to-end check**

Run: `cd /s && GRADLE_USER_HOME=C:/gradle-home ./gradlew :core:test :app:assembleRelease`, установить на телефон.

Проверить:
1. Очистка → окно → оценка 5, «Тестовый отзыв» → «Отправить» → «Спасибо! Отзыв отправлен» → через минуту строка в таблице. Версия `1.2 (3)`, телефон Xiaomi, источник `after_cleanup`.
2. Режим полёта → меню → «Оставить отзыв» → отправить → «Отправим, когда появится интернет». Выключить режим полёта — строка появляется в таблице, источник `menu`.
3. После отправки окно после очистки больше не появляется само.

Попросить у автора скриншоты и подтверждение, что строки видны в таблице.

- [ ] **Step 5: Run the skill on the test rows**

Выполнить навык `razbor-otzyvov` на тестовых строках и убедиться, что сводка строится.

- [ ] **Step 6: Commit and PR**

```bash
git add app/src/main/java/com/smartspacecleaner/feedback/FeedbackFormConfig.kt .claude/skills/razbor-otzyvov/SKILL.md
git commit -m "feat: connect the feedback form and add the feedback review skill"
git push -u origin feat/onboarding-feedback
```

Открыть PR в `master` с описанием на русском. Положить APK для однокурсников в `/sdcard/Download/SmartSpaceCleaner-1.2.apk`.
