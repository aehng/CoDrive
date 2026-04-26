package com.codrive.ai.orchestration

import com.codrive.ai.model.ActionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class Tier1NavigationRouterTest {
    private val router = Tier1NavigationRouter()

    @Test
    fun matchesExplicitHomeCommand() {
        val directive = router.match("please go home")

        assertEquals(ActionType.HOME, directive?.actionType)
        assertEquals("Going home.", directive?.voiceFeedback)
    }

    @Test
    fun matchesExplicitBackCommand() {
        val directive = router.match("could you go back")

        assertEquals(ActionType.BACK, directive?.actionType)
        assertEquals("Going back.", directive?.voiceFeedback)
    }

    @Test
    fun matchesExplicitRecentsCommand() {
        val directive = router.match("open recents")

        assertEquals(ActionType.RECENTS, directive?.actionType)
        assertEquals("Opening recents.", directive?.voiceFeedback)
    }

    @Test
    fun matchesNotificationShadeCommand() {
        val directive = router.match("open notifications")

        assertEquals(ActionType.OPEN_NOTIFICATIONS, directive?.actionType)
        assertEquals("Opening notifications.", directive?.voiceFeedback)
    }

    @Test
    fun matchesSwipeDownCommand() {
        val directive = router.match("swipe down")

        assertEquals(ActionType.SWIPE_DOWN, directive?.actionType)
        assertEquals("Swiping down.", directive?.voiceFeedback)
    }

    @Test
    fun ignoresIncidentalBackWords() {
        assertNull(router.match("backpack"))
        assertNull(router.match("rollback the change"))
    }
}

