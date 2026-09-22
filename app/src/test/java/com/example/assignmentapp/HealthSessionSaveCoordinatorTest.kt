package com.example.assignmentapp

import com.example.assignmentapp.data.HealthRecordEntity
import com.example.assignmentapp.data.HealthSession
import com.example.assignmentapp.data.HealthSessionSaveCoordinator
import com.example.assignmentapp.data.SessionSaveOutcome
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

        val outcome = coordinator.save(session)

        assertEquals(SessionSaveOutcome.Saved(37L), outcome)
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

        val first = coordinator.save(completeSession())
        val repeated = coordinator.save(completeSession())

        assertEquals(SessionSaveOutcome.Saved(9L), first)
        assertEquals(SessionSaveOutcome.AlreadySaved(9L), repeated)
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
        assertEquals(
            SessionSaveOutcome.AlreadySaving,
            coordinator.save(completeSession())
        )
        assertEquals(1, insertCalls)

        finishInsert.complete(Unit)
        assertEquals(SessionSaveOutcome.Saved(14L), first.await())
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

        val failed = coordinator.save(session)

        assertTrue(failed is SessionSaveOutcome.Failed)
        assertTrue(coordinator.status.value is SessionSaveStatus.Error)
        assertEquals(originalSession, session)
        assertEquals(1, insertCalls)

        shouldFail = false
        val retried = coordinator.save(session)

        assertEquals(SessionSaveOutcome.Saved(22L), retried)
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

        val failed = coordinator.save(HealthSession())

        assertTrue(failed is SessionSaveOutcome.Failed)
        assertTrue(coordinator.status.value is SessionSaveStatus.Error)
        assertEquals(0, insertCalls)

        val retried = coordinator.save(completeSession())

        assertEquals(SessionSaveOutcome.Saved(5L), retried)
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
