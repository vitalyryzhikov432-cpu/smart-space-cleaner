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
