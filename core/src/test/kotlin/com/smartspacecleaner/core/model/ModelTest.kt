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
