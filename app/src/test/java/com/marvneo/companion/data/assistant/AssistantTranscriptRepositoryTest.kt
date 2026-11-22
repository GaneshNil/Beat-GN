package com.marvneo.companion.data.assistant

import com.marvneo.companion.data.db.dao.VoiceDao
import com.marvneo.companion.data.db.entity.VoiceMessageRecord
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class AssistantTranscriptRepositoryTest {
    @Test
    fun `a confirmed clear continues after the call returns and repeat taps share one deletion`() = runBlocking {
        val started = CompletableDeferred<Unit>()
        val finish = CompletableDeferred<Unit>()
        var deletes = 0
        val repo = AssistantTranscriptRepository(TestVoiceDao {
            deletes++
            started.complete(Unit)
            finish.await()
        }, scope = this)
        repo.clear()
        started.await()
        repo.clear()
        finish.complete(Unit)
        repo.clearing.first { !it }
        assertEquals(1, deletes)
        assertNull(repo.clearError.value)
    }

    @Test
    fun `a failed deletion reports an error and allows a retry`() = runBlocking {
        var attempts = 0
        val repo = AssistantTranscriptRepository(TestVoiceDao {
            if (++attempts == 1) throw IllegalStateException("database failure")
        }, scope = this)
        repo.clear()
        repo.clearing.first { !it }
        assertNotNull(repo.clearError.value)
        repo.clear()
        repo.clearing.first { !it }
        assertEquals(2, attempts)
        assertNull(repo.clearError.value)
    }

    private class TestVoiceDao(private val onClear: suspend () -> Unit) : VoiceDao {
        override fun getAllMessages() = flowOf(emptyList<VoiceMessageRecord>())
        override fun countMessages() = flowOf(0)
        override suspend fun insertMessage(record: VoiceMessageRecord): Long = error("unused")
        override suspend fun clearHistory() = onClear()
    }
}
