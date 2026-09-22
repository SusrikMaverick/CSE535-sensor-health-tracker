package com.example.assignmentapp

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.assignmentapp.data.HealthRecordEntity
import com.example.assignmentapp.data.HealthSession
import com.example.assignmentapp.data.MeasurementState
import com.example.assignmentapp.data.Symptom
import com.example.assignmentapp.ui.HealthViewModel
import com.example.assignmentapp.ui.theme.AssignmentAppTheme
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SymptomsFlowTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun successfulUploadSavesOnceReturnsHomeAndResetsTheSession() {
        val viewModel = HealthViewModel()
        val insertedRecord = AtomicReference<HealthRecordEntity?>()
        val insertCalls = AtomicInteger(0)
        setNavHost(viewModel) { record ->
            insertCalls.incrementAndGet()
            insertedRecord.set(record)
            41L
        }
        openSymptoms(viewModel)

        composeRule.onNodeWithTag("nausea_rating_5")
            .performScrollTo()
            .performClick()
        composeRule.onNodeWithTag("upload_symptoms")
            .performScrollTo()
            .performClick()
        composeRule.waitUntil(timeoutMillis = 5_000L) {
            composeRule.onAllNodesWithText("Your health, in context")
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
        composeRule.onNodeWithText("Your health, in context").assertIsDisplayed()

        val savedRecord = requireNotNull(insertedRecord.get())
        composeRule.runOnIdle {
            assertEquals(1, insertCalls.get())
            assertEquals(72.0, savedRecord.heartRate, 0.0)
            assertEquals(16.0, savedRecord.respiratoryRate, 0.0)
            assertEquals(5, savedRecord.nausea)
            assertEquals(0, savedRecord.headache)
            assertEquals(HealthSession(), viewModel.session)
            assertEquals(MeasurementState.Idle, viewModel.heartRateState)
            assertEquals(MeasurementState.Idle, viewModel.respiratoryRateState)
        }

        composeRule.onNodeWithText("Record health data").performClick()
        composeRule.onNodeWithText("Vital signs").assertIsDisplayed()
        composeRule.runOnIdle { assertEquals(HealthSession(), viewModel.session) }
    }

    @Test
    fun failedUploadPreservesEntriesAndRetrySavesExactlyOnce() {
        val viewModel = HealthViewModel()
        val attempts = AtomicInteger(0)
        val savedRecord = AtomicReference<HealthRecordEntity?>()
        setNavHost(viewModel) { record ->
            if (attempts.incrementAndGet() == 1) {
                error("Database unavailable")
            }
            savedRecord.set(record)
            52L
        }
        openSymptoms(viewModel)

        composeRule.onNodeWithTag("cough_rating_4")
            .performScrollTo()
            .performClick()
        composeRule.onNodeWithTag("upload_symptoms")
            .performScrollTo()
            .performClick()
        composeRule.onNodeWithText("Couldn’t save your check-in")
            .performScrollTo()
            .assertIsDisplayed()

        composeRule.runOnIdle {
            assertEquals(72.0, requireNotNull(viewModel.session.heartRate), 0.0)
            assertEquals(16.0, requireNotNull(viewModel.session.respiratoryRate), 0.0)
            assertEquals(4, viewModel.session.symptomRatings[Symptom.COUGH])
        }
        composeRule.onNodeWithText("Retry")
            .performScrollTo()
            .performClick()
        composeRule.waitUntil(timeoutMillis = 5_000L) {
            composeRule.onAllNodesWithText("Your health, in context")
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
        composeRule.onNodeWithText("Your health, in context").assertIsDisplayed()

        assertEquals(2, attempts.get())
        assertNotNull(savedRecord.get())
        assertEquals(4, savedRecord.get()?.cough)
    }

    private fun setNavHost(
        viewModel: HealthViewModel,
        insertRecord: suspend (HealthRecordEntity) -> Long
    ) {
        composeRule.setContent {
            AssignmentAppTheme {
                AssignmentAppNavHost(
                    healthViewModel = viewModel,
                    insertHealthRecordOverride = insertRecord,
                    saveSuccessDisplayMillis = 0L
                )
            }
        }
    }

    private fun openSymptoms(viewModel: HealthViewModel) {
        composeRule.onNodeWithText("Record health data").performClick()
        composeRule.runOnIdle {
            viewModel.updateHeartRate(MeasurementState.Success(72.0))
            viewModel.updateRespiratoryRate(MeasurementState.Success(16.0))
        }
        composeRule.onNodeWithText("Continue to symptoms")
            .performScrollTo()
            .performClick()
        composeRule.onNodeWithText("How are you feeling?").assertIsDisplayed()
    }
}
