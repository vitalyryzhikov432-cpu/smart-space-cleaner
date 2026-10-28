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
    fun `downloads beat messenger folders that clean themselves`() {
        assertEquals(
            p("Download/x (1).jpg"),
            policy.chooseKeeper(
                listOf(p("Download/x (1).jpg"), p("Android/media/org.telegram.messenger/Telegram/Telegram Images/x.jpg")),
            ),
        )
    }

    @Test
    fun `hidden files and hidden folders are never kept`() {
        assertEquals(
            p("Download/IMG_1.jpg"),
            policy.chooseKeeper(listOf(p("DCIM/Camera/.trashed-1700000000-IMG_1.jpg"), p("Download/IMG_1.jpg"))),
        )
        assertEquals(
            p("Telegram/IMG_1.jpg"),
            policy.chooseKeeper(listOf(p("MIUI/Gallery/cloud/.trashBin/IMG_1.jpg"), p("Telegram/IMG_1.jpg"))),
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
