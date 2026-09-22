package com.example.assignmentapp.data

import java.util.concurrent.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex

private const val DEFAULT_SAVE_ERROR_MESSAGE =
    "Could not save this health session. Your entries are still here; please try again."

sealed interface SessionSaveStatus {
    data object Ready : SessionSaveStatus
    data object Saving : SessionSaveStatus
    data class Saved(val recordId: Long) : SessionSaveStatus
    data class Error(val message: String) : SessionSaveStatus
}

sealed interface SessionSaveOutcome {
    data class Saved(val recordId: Long) : SessionSaveOutcome
    data class Failed(val message: String) : SessionSaveOutcome
    data object AlreadySaving : SessionSaveOutcome
    data class AlreadySaved(val recordId: Long) : SessionSaveOutcome
}

/**
 * Coordinates a single Room insert for one health-session flow.
 *
 * Calls made while an insert is running are ignored instead of queued, which prevents a rapid
 * double tap from becoming an automatic retry if the first insert fails. A failed insert leaves
 * the supplied [HealthSession] untouched and a later, explicit call to [save] can retry it.
 * Create a fresh coordinator for the next recording flow.
 */
class HealthSessionSaveCoordinator(
    private val insertRecord: suspend (HealthRecordEntity) -> Long,
    private val timestampProvider: () -> Long = System::currentTimeMillis
) {
    private val saveMutex = Mutex()
    private val _status = MutableStateFlow<SessionSaveStatus>(SessionSaveStatus.Ready)

    val status: StateFlow<SessionSaveStatus> = _status.asStateFlow()

    suspend fun save(session: HealthSession): SessionSaveOutcome {
        if (!saveMutex.tryLock()) return SessionSaveOutcome.AlreadySaving

        try {
            val savedStatus = _status.value as? SessionSaveStatus.Saved
            if (savedStatus != null) {
                return SessionSaveOutcome.AlreadySaved(savedStatus.recordId)
            }

            _status.value = SessionSaveStatus.Saving

            return try {
                val recordId = insertRecord(session.toEntity(timestampProvider()))
                _status.value = SessionSaveStatus.Saved(recordId)
                SessionSaveOutcome.Saved(recordId)
            } catch (cancellation: CancellationException) {
                _status.value = SessionSaveStatus.Ready
                throw cancellation
            } catch (_: Exception) {
                _status.value = SessionSaveStatus.Error(DEFAULT_SAVE_ERROR_MESSAGE)
                SessionSaveOutcome.Failed(DEFAULT_SAVE_ERROR_MESSAGE)
            }
        } finally {
            saveMutex.unlock()
        }
    }
}
