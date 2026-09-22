package com.example.assignmentapp

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.assignmentapp.data.MeasurementState
import com.example.assignmentapp.ui.HealthViewModel
import com.example.assignmentapp.ui.MeasurementSection
import com.example.assignmentapp.ui.canStartHeartRateRecording
import com.example.assignmentapp.ui.deleteTemporaryVideo
import com.example.assignmentapp.ui.heartRateCameraError
import com.example.assignmentapp.ui.heartRatePermissionState
import com.example.assignmentapp.ui.theme.AssignmentAppTheme
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class VitalsScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun permissionDenialShowsRetryableError() {
        val state = heartRatePermissionState(granted = false)

        setMeasurement(state = state)

        composeRule.onNodeWithText("Camera permission is required to measure heart rate.")
            .assertIsDisplayed()
        composeRule.onNodeWithText("Retry").assertIsDisplayed()
    }

    @Test
    fun unsupportedCameraOrTorchReturnsClearError() {
        val cameraError = heartRateCameraError(hasRearCamera = false, hasTorch = true)

        assertEquals("A rear camera is not available on this device.", cameraError)
        assertEquals(
            "A camera torch is not available on this device.",
            heartRateCameraError(hasRearCamera = true, hasTorch = false)
        )
        setMeasurement(state = MeasurementState.Error(requireNotNull(cameraError)))
        composeRule.onNodeWithText(cameraError).assertIsDisplayed()
    }

    @Test
    fun collectingCanBeCancelled() {
        var cancellationCalls = 0
        setMeasurement(
            state = MeasurementState.Collecting,
            progress = 0.5f,
            onCancel = { cancellationCalls++ }
        )

        composeRule.onNodeWithText("Cancel").performClick()

        composeRule.runOnIdle { assertEquals(1, cancellationCalls) }
    }

    @Test
    fun duplicateRecordingStartIsBlocked() {
        assertTrue(
            canStartHeartRateRecording(
                isPreparingCamera = false,
                hasActiveRecording = false
            )
        )
        assertFalse(
            canStartHeartRateRecording(
                isPreparingCamera = true,
                hasActiveRecording = false
            )
        )
        assertFalse(
            canStartHeartRateRecording(
                isPreparingCamera = false,
                hasActiveRecording = true
            )
        )

        setMeasurement(
            state = MeasurementState.Idle,
            actionLabel = "Start 45-second recording",
            actionEnabled = false
        )
        composeRule.onNodeWithText("Start 45-second recording").assertIsNotEnabled()
    }

    @Test
    fun processingStateShowsProgress() {
        setMeasurement(state = MeasurementState.Processing)

        composeRule.onNodeWithText("Processing").assertIsDisplayed()
    }

    @Test
    fun retryAndCancellationPreserveRespiratoryResult() {
        val viewModel = HealthViewModel()
        val respiratoryResult = MeasurementState.Success(16.0)
        var retryCalls = 0
        viewModel.updateRespiratoryRate(respiratoryResult)
        viewModel.updateHeartRate(MeasurementState.Error("Recording failed."))
        setMeasurement(
            state = viewModel.heartRateState,
            onRetry = {
                retryCalls++
                viewModel.updateHeartRate(MeasurementState.Idle)
            }
        )

        composeRule.onNodeWithText("Retry").performClick()

        composeRule.runOnIdle {
            assertEquals(1, retryCalls)
            assertEquals(respiratoryResult, viewModel.respiratoryRateState)
            assertEquals(16.0, viewModel.session.respiratoryRate)

            viewModel.updateHeartRate(MeasurementState.Collecting)
            viewModel.updateHeartRate(MeasurementState.Idle)
            viewModel.updateHeartRate(MeasurementState.Collecting)
            assertEquals(respiratoryResult, viewModel.respiratoryRateState)
            assertEquals(16.0, viewModel.session.respiratoryRate)
        }
    }

    @Test
    fun temporaryVideoCleanupDeletesFile() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val file = File.createTempFile("heart-rate-test-", ".mp4", context.cacheDir)

        assertTrue(file.exists())
        deleteTemporaryVideo(file)
        assertFalse(file.exists())
    }

    private fun setMeasurement(
        state: MeasurementState,
        actionLabel: String? = null,
        actionEnabled: Boolean = true,
        onRetry: () -> Unit = { },
        progress: Float? = null,
        onCancel: (() -> Unit)? = null
    ) {
        composeRule.setContent {
            AssignmentAppTheme {
                MeasurementSection(
                    title = "Heart rate",
                    instructions = "Keep still during recording.",
                    state = state,
                    unit = "bpm",
                    idleLabel = "Ready to record",
                    onRetry = onRetry,
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    actionLabel = actionLabel,
                    actionEnabled = actionEnabled,
                    progress = progress,
                    onCancel = onCancel
                )
            }
        }
    }
}
