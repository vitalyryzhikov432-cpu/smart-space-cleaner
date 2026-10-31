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
