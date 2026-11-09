package com.marvneo.companion.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SparkLabelIndicesTest {

    @Test
    fun `up to seven bars keep every label`() {
        for (count in 0..7) {
            assertEquals((0 until count).toSet(), sparkLabelIndices(count))
        }
    }

    @Test
    fun `every month length is thinned to a readable number of labels`() {
        for (count in 8..31) {
            val indices = sparkLabelIndices(count)
            assertTrue("$count bars produced no labels", indices.isNotEmpty())
            assertTrue("$count bars produced ${indices.size} labels", indices.size <= 7)
            assertTrue(indices.contains(0))
            assertTrue(indices.contains(count - 1))
            assertTrue(indices.all { it in 0 until count })
        }
    }

    @Test
    fun `thinned labels never sit next to each other`() {
        for (count in 8..31) {
            val sorted = sparkLabelIndices(count).sorted()
            sorted.zipWithNext().forEach { (a, b) ->
                assertFalse("$count bars put labels at $a and $b", b - a <= 1)
            }
        }
    }

    @Test
    fun `thirty-one bars keep the first and last day`() {
        val indices = sparkLabelIndices(31)
        assertTrue(indices.contains(0))
        assertTrue(indices.contains(30))
    }
}
