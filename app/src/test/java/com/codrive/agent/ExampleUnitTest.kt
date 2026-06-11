package com.codrive.ai

import com.codrive.ai.model.AgentPolicy
import org.junit.Test

import org.junit.Assert.*

/**
 * Example local unit test, which will execute on the development machine (host).
 *
 * See [testing documentation](http://d.android.com/tools/testing).
 */
class ExampleUnitTest {
    @Test
    fun policyThresholdIsConfiguredForTracerBulletSafety() {
        assertEquals(0.8, AgentPolicy.confidenceClarificationThreshold, 0.0)
        assertEquals(0.2, AgentPolicy.hardConfirmationThreshold, 0.0)
        assertTrue(AgentPolicy.shouldClarify(0.79))
        assertTrue(AgentPolicy.shouldRequireConfirmation(0.19))
    }
}
