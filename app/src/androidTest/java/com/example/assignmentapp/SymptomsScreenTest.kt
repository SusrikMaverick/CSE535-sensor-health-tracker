package com.example.assignmentapp

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertValueEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.assignmentapp.data.HealthSession
import com.example.assignmentapp.data.Symptom
import com.example.assignmentapp.ui.SymptomsContent
import com.example.assignmentapp.ui.SymptomsSaveState
import com.example.assignmentapp.ui.theme.AssignmentAppTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SymptomsScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun measuredVitalsAreDisplayedAsReadOnlyValues() {
        setSymptomsContent()

        composeRule.onNodeWithText("72 bpm").assertIsDisplayed()
        composeRule.onNodeWithText("16 breaths/min").assertIsDisplayed()
        composeRule
            .onNodeWithContentDescription("Heart rate, 72 beats per minute, read only")
            .assertContentDescriptionEquals("Heart rate, 72 beats per minute, read only")
        composeRule
            .onNodeWithContentDescription(
                "Respiratory rate, 16 breaths per minute, read only"
            )
            .assertContentDescriptionEquals(
                "Respiratory rate, 16 breaths per minute, read only"
            )
    }

    @Test
    fun allSymptomsStartAtRatingZero() {
        setSymptomsContent()

        Symptom.entries.forEach { symptom ->
            composeRule.onNodeWithTag("symptom_${symptom.name.lowercase()}")
                .performScrollTo()
                .assertIsDisplayed()
            composeRule.onNodeWithText(symptom.displayName).assertIsDisplayed()
            (0..5).forEach { rating ->
                composeRule.onNodeWithTag("${symptom.name.lowercase()}_rating_$rating")
                    .assertExists()
            }
            composeRule.onNodeWithTag("${symptom.name.lowercase()}_rating_0")
                .assertIsSelected()
        }
        composeRule.onAllNodesWithText("Selected rating: 0 out of 5")
            .assertCountEquals(Symptom.entries.size)
    }

    @Test
    fun selectingNauseaRatingFiveUpdatesVisibleTextAndSemantics() {
        var session by mutableStateOf(completeSession())
        composeRule.setContent {
            AssignmentAppTheme {
                SymptomsContent(
                    session = session,
                    saveState = SymptomsSaveState.Idle,
                    onRatingChange = { symptom, rating ->
                        session = session.copy(
                            symptomRatings = session.symptomRatings + (symptom to rating)
                        )
                    },
                    onUpload = { },
                    onRetry = { }
                )
            }
        }

        composeRule.onNodeWithTag("nausea_rating_5")
            .performScrollTo()
            .performClick()
            .assertIsSelected()
            .assertValueEquals("Selected")
        composeRule.onNodeWithText("Selected rating: 5 out of 5")
            .assertIsDisplayed()
    }

    @Test
    fun selectingOneSymptomLeavesUntouchedSymptomsAtZero() {
        var session by mutableStateOf(completeSession())
        composeRule.setContent {
            AssignmentAppTheme {
                SymptomsContent(
                    session = session,
                    saveState = SymptomsSaveState.Idle,
                    onRatingChange = { symptom, rating ->
                        session = session.copy(
                            symptomRatings = session.symptomRatings + (symptom to rating)
                        )
                    },
                    onUpload = { },
                    onRetry = { }
                )
            }
        }

        composeRule.onNodeWithTag("nausea_rating_5")
            .performScrollTo()
            .performClick()

        Symptom.entries.filterNot { it == Symptom.NAUSEA }.forEach { symptom ->
            composeRule.onNodeWithTag("${symptom.name.lowercase()}_rating_0")
                .assertIsSelected()
        }
        composeRule.onAllNodesWithText("Selected rating: 0 out of 5")
            .assertCountEquals(Symptom.entries.size - 1)
    }

    @Test
    fun uploadButtonInvokesCallbackOnce() {
        var uploadCalls = 0
        setSymptomsContent(onUpload = { uploadCalls++ })

        composeRule.onNodeWithTag("upload_symptoms")
            .performScrollTo()
            .assertIsEnabled()
            .performClick()

        composeRule.runOnIdle { assertEquals(1, uploadCalls) }
    }

    @Test
    fun missingVitalDisablesUpload() {
        setSymptomsContent(session = completeSession().copy(respiratoryRate = null))

        composeRule.onNodeWithTag("upload_symptoms")
            .performScrollTo()
            .assertIsNotEnabled()
        composeRule.onNodeWithText(
            "Complete both vital measurements before uploading symptoms."
        ).assertExists()
    }

    @Test
    fun savingDisablesUploadAndRatingControls() {
        setSymptomsContent(saveState = SymptomsSaveState.Saving)

        composeRule.onNodeWithTag("nausea_rating_0")
            .performScrollTo()
            .assertIsNotEnabled()
        composeRule.onNodeWithTag("upload_symptoms")
            .performScrollTo()
            .assertIsNotEnabled()
        composeRule.onNodeWithText("Saving your check-in…")
            .performScrollTo()
            .assertIsDisplayed()
    }

    @Test
    fun errorPreservesValuesAndRetryInvokesCallback() {
        var retryCalls = 0
        val session = completeSession().copy(
            symptomRatings = completeSession().symptomRatings + (Symptom.NAUSEA to 4)
        )
        setSymptomsContent(
            session = session,
            saveState = SymptomsSaveState.Error("The record could not be stored."),
            onRetry = { retryCalls++ }
        )

        composeRule.onNodeWithText("72 bpm").assertExists()
        composeRule.onNodeWithText("16 breaths/min").assertExists()
        composeRule.onNodeWithTag("nausea_rating_4")
            .performScrollTo()
            .assertIsSelected()
        composeRule.onNodeWithText("Selected rating: 4 out of 5")
            .assertIsDisplayed()
        composeRule.onNodeWithText("The record could not be stored.")
            .performScrollTo()
            .assertIsDisplayed()
        composeRule.onNodeWithText("Retry")
            .performScrollTo()
            .performClick()

        composeRule.runOnIdle { assertEquals(1, retryCalls) }
    }

    @Test
    fun successShowsFeedbackAndPreventsAnotherUpload() {
        setSymptomsContent(saveState = SymptomsSaveState.Success)

        composeRule.onNodeWithText("Check-in saved")
            .performScrollTo()
            .assertIsDisplayed()
        composeRule.onNodeWithTag("upload_symptoms")
            .performScrollTo()
            .assertIsNotEnabled()
        composeRule.onNodeWithText("Symptoms uploaded").assertIsDisplayed()
    }

    private fun setSymptomsContent(
        session: HealthSession = completeSession(),
        saveState: SymptomsSaveState = SymptomsSaveState.Idle,
        onUpload: () -> Unit = { },
        onRetry: () -> Unit = { }
    ) {
        composeRule.setContent {
            AssignmentAppTheme {
                SymptomsContent(
                    session = session,
                    saveState = saveState,
                    onRatingChange = { _, _ -> },
                    onUpload = onUpload,
                    onRetry = onRetry
                )
            }
        }
    }

    private fun completeSession() = HealthSession(
        heartRate = 72.0,
        respiratoryRate = 16.0
    )
}
