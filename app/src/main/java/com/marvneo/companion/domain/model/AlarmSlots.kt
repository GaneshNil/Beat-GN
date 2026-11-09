package com.marvneo.companion.domain.model

/** Allocation rules for the watch's five alarm slots. Pure so it can be unit-tested. */
object AlarmSlots {

    const val COUNT = 5

    /**
     * The first unused slot in `0..4`, or null when every slot is occupied.
     *
     * The previous UI derived the next slot from the *number* of stored alarms, which overwrote an
     * existing alarm whenever the occupied slots had a gap (for example `{0, 2, 3}` -> slot 3).
     */
    fun firstFree(usedSlots: Collection<Int>): Int? {
        val used = usedSlots.toSet()
        return (0 until COUNT).firstOrNull { it !in used }
    }
}
