package com.example.assignmentapp

import com.example.assignmentapp.data.HealthRecordEntity
import com.example.assignmentapp.data.HealthSession
import com.example.assignmentapp.data.HealthSessionSaveCoordinator
import com.example.assignmentapp.data.SessionSaveStatus
import com.example.assignmentapp.data.Symptom
import com.example.assignmentapp.data.toSession
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HealthSessionSaveCoordinatorTest {
    @Test
    fun successfulSaveInsertsOneCompleteSessionSnapshot() = runTest {
        val insertedRecords = mutableListOf<HealthRecordEntity>()
        val session = completeSession()
        val coordinator = HealthSessionSaveCoordinator(
            insertRecord = { record ->
                insertedRecords += record
                37L
            },
            timestampProvider = { 123L }
        )

        coordinator.save(session)

        assertEquals(SessionSaveStatus.Saved(37L), coordinator.status.value)
        assertEquals(1, insertedRecords.size)
        assertEquals(123L, insertedRecords.single().timestamp)
        assertEquals(session, insertedRecords.single().toSession())
    }

    @Test
    fun repeatedSaveAfterSuccessDoesNotInsertDuplicateRecord() = runTest {
        var insertCalls = 0
        val coordinator = HealthSessionSaveCoordinator(
            insertRecord = {
                insertCalls++
                9L
            }
        )

        coordinator.save(completeSession())
        coordinator.save(completeSession())

        assertEquals(1, insertCalls)
        assertEquals(SessionSaveStatus.Saved(9L), coordinator.status.value)
    }

    @Test
    fun concurrentSaveIsIgnoredInsteadOfQueued() = runTest {
        val insertStarted = CompletableDeferred<Unit>()
        val finishInsert = CompletableDeferred<Unit>()
        var insertCalls = 0
        val coordinator = HealthSessionSaveCoordinator(
            insertRecord = {
                insertCalls++
                insertStarted.complete(Unit)
                finishInsert.await()
                14L
            }
        )

        val first = async { coordinator.save(completeSession()) }
        insertStarted.await()

        assertEquals(SessionSaveStatus.Saving, coordinator.status.value)
        coordinator.save(completeSession())
        assertEquals(1, insertCalls)

        finishInsert.complete(Unit)
        first.await()
        assertEquals(SessionSaveStatus.Saved(14L), coordinator.status.value)
        assertEquals(1, insertCalls)
    }

    @Test
    fun failedSavePreservesSessionAndAllowsExplicitRetry() = runTest {
        val session = completeSession()
        val originalSession = session.copy(symptomRatings = session.symptomRatings.toMap())
        val insertedRecords = mutableListOf<HealthRecordEntity>()
        var shouldFail = true
        var insertCalls = 0
        val coordinator = HealthSessionSaveCoordinator(
            insertRecord = { record ->
                insertCalls++
                if (shouldFail) error("Database unavailable")
                insertedRecords += record
                22L
            }
        )

        coordinator.save(session)

        assertTrue(coordinator.status.value is SessionSaveStatus.Error)
        assertEquals(originalSession, session)
        assertEquals(1, insertCalls)

        shouldFail = false
        coordinator.save(session)

        assertEquals(SessionSaveStatus.Saved(22L), coordinator.status.value)
        assertEquals(2, insertCalls)
        assertEquals(originalSession, insertedRecords.single().toSession())
    }

    @Test
    fun invalidSessionFailsBeforeInsertAndCanBeRetriedWithVitals() = runTest {
        var insertCalls = 0
        val coordinator = HealthSessionSaveCoordinator(
            insertRecord = {
                insertCalls++
                5L
            }
        )

        coordinator.save(HealthSession())

        assertTrue(coordinator.status.value is SessionSaveStatus.Error)
        assertEquals(0, insertCalls)

        coordinator.save(completeSession())

        assertEquals(SessionSaveStatus.Saved(5L), coordinator.status.value)
        assertEquals(1, insertCalls)
    }

    private fun completeSession(): HealthSession = HealthSession(
        heartRate = 72.0,
        respiratoryRate = 16.0,
        symptomRatings = Symptom.entries.associateWith { symptom ->
            symptom.ordinal % 6
        }
    )
}
