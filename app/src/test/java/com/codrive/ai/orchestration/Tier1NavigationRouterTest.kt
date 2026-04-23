package com.codrive.ai.orchestration

import android.accessibilityservice.AccessibilityService
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class Tier1NavigationRouterTest {
    private val router = Tier1NavigationRouter()

    @Test
    fun matchesExplicitHomeCommand() {
        val directive = router.match("please go home")

        assertEquals(AccessibilityService.GLOBAL_ACTION_HOME, directive?.globalAction)
        assertEquals("Going home.", directive?.voiceFeedback)
    }

    @Test
    fun matchesExplicitBackCommand() {
        val directive = router.match("could you go back")

        assertEquals(AccessibilityService.GLOBAL_ACTION_BACK, directive?.globalAction)
        assertEquals("Going back.", directive?.voiceFeedback)
    }

    @Test
    fun matchesExplicitRecentsCommand() {
        val directive = router.match("open recents")

        assertEquals(AccessibilityService.GLOBAL_ACTION_RECENTS, directive?.globalAction)
        assertEquals("Opening recents.", directive?.voiceFeedback)
    }

    @Test
    fun ignoresIncidentalBackWords() {
        assertNull(router.match("backpack"))
        assertNull(router.match("rollback the change"))
    }
}

