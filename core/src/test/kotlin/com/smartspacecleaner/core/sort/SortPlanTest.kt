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
