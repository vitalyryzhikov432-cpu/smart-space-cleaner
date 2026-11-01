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
    fun `document thresholds are inclusive at 2 percent and 80 characters`() {
        assertEquals(SortCategory.DOCUMENTS, classify(camera, PhotoSignals(textAreaRatio = 0.02f, textChars = 80)).category)
        assertEquals(SortCategory.OTHER, classify(camera, PhotoSignals(textAreaRatio = 0.019f, textChars = 80)).category)
    }

    // Числа ниже — с телефона автора: снимки листов, паспортов и чеков, людей на пляже и пейзажей.
    @Test
    fun `a photographed sheet with small text is a document`() {
        assertEquals(
            SortDecision(SortCategory.DOCUMENTS, "найден текст: 100 символов"),
            classify(camera, PhotoSignals(textAreaRatio = 0.0269f, textChars = 100, labels = listOf(Label("Paper", 0.51f)))),
        )
    }

    @Test
    fun `a passport with a face photo is a document, not people`() {
        val passport = PhotoSignals(textAreaRatio = 0.1324f, textChars = 243, largestFaceRatio = 0.0342f, labels = listOf(Label("Paper", 0.75f)))
        assertEquals(SortCategory.DOCUMENTS, classify(camera, passport).category)
    }

    @Test
    fun `sure paper is a document even with little or no text`() {
        assertEquals(SortDecision(SortCategory.DOCUMENTS, "метка Paper, 78 %"), classify(camera, labels("Paper" to 0.78f, "Food" to 0.63f)))
        val weakText = PhotoSignals(textAreaRatio = 0.0064f, textChars = 41, labels = listOf(Label("Paper", 0.77f), Label("Food", 0.56f)))
        assertEquals(SortCategory.DOCUMENTS, classify(camera, weakText).category)
    }

    @Test
    fun `unsure paper goes to review`() {
        val d = classify(camera, PhotoSignals(textAreaRatio = 0.0247f, textChars = 71, labels = listOf(Label("Paper", 0.59f))))
        assertNull(d.category)
        assertEquals("похоже на «Документы», 59 %", d.reason)
    }

    @Test
    fun `a tiny face of a person in full height means people`() {
        val beach = PhotoSignals(largestFaceRatio = 0.0012f, labels = listOf(Label("Beach", 0.80f), Label("Rock", 0.81f)))
        assertEquals(SortDecision(SortCategory.PEOPLE, "найдено лицо"), classify(camera, beach))
    }

    @Test
    fun `a person label means people even without a face and beats nature`() {
        assertEquals(
            SortDecision(SortCategory.PEOPLE, "метка Fun, 80 %"),
            classify(camera, labels("Beach" to 0.97f, "Fun" to 0.80f, "Sitting" to 0.61f)),
        )
        assertEquals(SortCategory.PEOPLE, classify(camera, labels("Beach" to 0.95f, "Shorts" to 0.50f)).category)
        assertEquals(SortCategory.PEOPLE, classify(camera, labels("Selfie" to 0.72f, "Person" to 0.99f)).category)
    }

    @Test
    fun `a person label under 50 percent does not count`() {
        assertEquals(SortCategory.NATURE, classify(camera, labels("Beach" to 0.95f, "Shorts" to 0.49f)).category)
    }

    @Test
    fun `a landscape with holiday labels stays nature`() {
        val cliffs = labels("Beach" to 0.95f, "Rock" to 0.83f, "Vacation" to 0.70f, "Leisure" to 0.60f, "Event" to 0.54f)
        assertEquals(SortCategory.NATURE, classify(camera, cliffs).category)
    }

    @Test
    fun `some text but not enough goes to review`() {
        val d = classify(camera, PhotoSignals(textAreaRatio = 0.5f, textChars = 79))
        assertNull(d.category)
        assertEquals("похоже на документ: 79 символов текста", d.reason)
    }

    @Test
    fun `a face of at least 0,1 percent of the frame means people`() {
        assertEquals(SortDecision(SortCategory.PEOPLE, "найдено лицо"), classify(camera, PhotoSignals(largestFaceRatio = 0.001f)))
        assertEquals(SortCategory.OTHER, classify(camera, PhotoSignals(largestFaceRatio = 0.0009f)).category)
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
    fun `percent is truncated so a value under the threshold never shows as 75`() {
        val weak = classify(camera, labels("Sky" to 0.7496f))
        assertNull(weak.category)
        assertEquals("похоже на «Природа», 74 %", weak.reason)
    }

    @Test
    fun `a sure animal beats nature when they are close`() {
        val decision = classify(camera, labels("Cat" to 0.80f, "Sky" to 0.85f))
        assertEquals(SortCategory.ANIMALS, decision.category)
        assertEquals("метка Cat, 80 %", decision.reason)
        assertEquals(SortCategory.ANIMALS, classify(camera, labels("Cat" to 0.85f, "Sky" to 0.80f)).category)
    }

    @Test
    fun `a weak animal close to nature still goes to review`() {
        assertNull(classify(camera, labels("Cat" to 0.70f, "Sky" to 0.72f)).category)
    }

    @Test
    fun `an animal beats nature from exactly 75 percent`() {
        assertEquals(SortCategory.ANIMALS, classify(camera, labels("Cat" to 0.75f, "Sky" to 0.80f)).category)
        assertNull(classify(camera, labels("Cat" to 0.74f, "Sky" to 0.80f)).category)
    }

    @Test
    fun `a sure animal close to food still goes to review`() {
        assertNull(classify(camera, labels("Cat" to 0.80f, "Food" to 0.85f)).category)
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
        assertEquals(SortCategory.NATURE, classify(camera, labels("Sky" to 0.9f, "Mountain" to 0.88f)).category)
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
