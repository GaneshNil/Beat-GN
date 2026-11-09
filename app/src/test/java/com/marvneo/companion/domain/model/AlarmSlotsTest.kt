package com.marvneo.companion.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AlarmSlotsTest {

    @Test
    fun `an empty set allocates slot zero`() {
        assertEquals(0, AlarmSlots.firstFree(emptyList()))
    }

    @Test
    fun `a gap in the occupied slots is reused instead of overwriting the next one`() {
        // The old UI used alarms.size and would have returned slot 3 here, overwriting an alarm.
        assertEquals(1, AlarmSlots.firstFree(listOf(0, 2, 3)))
        assertEquals(0, AlarmSlots.firstFree(listOf(1, 2, 3, 4)))
        assertEquals(4, AlarmSlots.firstFree(listOf(0, 1, 2, 3)))
    }

    @Test
    fun `all five slots occupied returns null`() {
        assertNull(AlarmSlots.firstFree((0 until AlarmSlots.COUNT).toList()))
        assertNull(AlarmSlots.firstFree(listOf(4, 0, 3, 1, 2)))
    }

    @Test
    fun `duplicates and out-of-range values do not hide a free slot`() {
        assertEquals(1, AlarmSlots.firstFree(listOf(0, 0, 0)))
        assertEquals(2, AlarmSlots.firstFree(listOf(0, 1, 99, -1)))
    }
}
