package com.marvneo.companion.data.repository

import com.marvneo.companion.data.db.dao.HealthDao
import com.marvneo.companion.data.db.entity.AlarmRecord
import com.marvneo.companion.data.db.entity.AlarmSyncState
import com.marvneo.companion.domain.model.AlarmSlots
import com.marvneo.companion.domain.model.CommandOutcome
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** Serializes phone storage and watch writes for the five shared alarm slots. */
class AlarmRepository(
    private val dao: HealthDao,
    private val writeToWatch: suspend (AlarmRecord) -> CommandOutcome,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
) {
    val alarms = dao.getAllAlarms()
    private val _busy = MutableStateFlow(false)
    val busy = _busy.asStateFlow()
    private val _error = MutableStateFlow<String?>(null)
    val error = _error.asStateFlow()

    fun save(alarm: AlarmRecord, newSlot: Boolean, sendWhenReady: Boolean) = edit {
        val slot = if (newSlot) {
            AlarmSlots.firstFree(alarms.first().map { it.id })
        } else {
            dao.getAlarm(alarm.id)?.id
        }
        if (slot == null) {
            _error.value = if (newSlot) "All phone alarm slots are in use" else "The alarm was removed"
            return@edit
        }
        if (!AlarmSlots.isValid(slot, alarm.hour, alarm.minute, alarm.repeatDays, alarm.isEnabled)) {
            _error.value = "Check the alarm time and repeat days"
            return@edit
        }
        val record = alarm.copy(id = slot, syncState = AlarmSyncState.PENDING, updatedAt = System.currentTimeMillis())
        dao.upsertAlarm(record)
        if (sendWhenReady) sendRecord(record)
    }

    fun send(slot: Int) = edit {
        val record = dao.getAlarm(slot)
        if (record == null) {
            _error.value = "The alarm was removed"
        } else {
            sendRecord(record)
        }
    }

    fun delete(slot: Int) = edit { dao.deleteAlarm(slot) }

    private suspend fun sendRecord(record: AlarmRecord) {
        dao.updateAlarmSyncState(record.id, AlarmSyncState.PENDING, System.currentTimeMillis())
        val outcome = writeToWatch(record)
        val state = when (outcome) {
            is CommandOutcome.AwaitingAcknowledgement -> AlarmSyncState.SENT
            is CommandOutcome.Acknowledged -> AlarmSyncState.SYNCED
            else -> AlarmSyncState.FAILED
        }
        if (outcome is CommandOutcome.Failed) _error.value = outcome.reason
        dao.updateAlarmSyncState(record.id, state, System.currentTimeMillis())
    }

    private fun edit(action: suspend () -> Unit) {
        if (!_busy.compareAndSet(false, true)) return
        _error.value = null
        scope.launch {
            try {
                action()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: RuntimeException) {
                _error.value = "Could not update alarms on this phone"
            } finally {
                _busy.value = false
            }
        }
    }
}
