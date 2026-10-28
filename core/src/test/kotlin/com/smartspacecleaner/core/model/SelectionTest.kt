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
