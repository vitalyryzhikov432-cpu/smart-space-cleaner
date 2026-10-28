package com.smartspacecleaner.core.report

import com.smartspacecleaner.core.fakes.T0
import com.smartspacecleaner.core.fakes.p
import com.smartspacecleaner.core.model.PlannedItem
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class DuplicateComparisonTest {
    @Test
    fun `duplicate shows the copy to be removed first, then the original that stays`() {
        val item = PlannedItem(p("Download/IMG_1 (1).jpg"), 10, T0, keptOriginalPath = p("DCIM/Camera/IMG_1.jpg"))

        val sides = DuplicateComparison.of(item)!!

        assertEquals(listOf(CompareRole.REMOVED_COPY, CompareRole.KEPT_ORIGINAL), sides.map { it.role })
        assertEquals(listOf(p("Download/IMG_1 (1).jpg"), p("DCIM/Camera/IMG_1.jpg")), sides.map { it.path })
        assertEquals("Удалится (копия)", CompareRole.REMOVED_COPY.title)
        assertEquals("Останется (оригинал)", CompareRole.KEPT_ORIGINAL.title)
    }

    @Test
    fun `file without an original has nothing to compare`() {
        assertNull(DuplicateComparison.of(PlannedItem(p("Movies/big.mp4"), 10, T0)))
    }
}
