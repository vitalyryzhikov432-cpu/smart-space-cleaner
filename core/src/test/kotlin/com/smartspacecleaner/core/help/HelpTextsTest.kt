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
