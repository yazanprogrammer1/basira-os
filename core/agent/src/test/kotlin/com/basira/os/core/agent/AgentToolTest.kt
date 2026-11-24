package com.basira.os.core.agent

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AgentToolTest {

    @Test
    fun `wire names are unique snake_case`() {
        val names = AgentTool.entries.map { it.wireName }
        assertEquals(names.size, names.toSet().size)
        assertTrue(names.all { it.matches(Regex("[a-z]+(_[a-z]+)*")) })
    }

    @Test
    fun `tools that act on page content are flagged for confirmation`() {
        assertTrue(AgentTool.FILL_FORM.changesState)
        assertTrue(AgentTool.CLICK_TEXT.changesState)
    }
}
