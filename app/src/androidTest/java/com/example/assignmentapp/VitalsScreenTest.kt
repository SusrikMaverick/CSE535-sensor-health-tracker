package com.example.assignmentapp

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.assignmentapp.data.MeasurementState
import com.example.assignmentapp.sensor.AccelerometerSample
import com.example.assignmentapp.sensor.AccelerometerSampleSource
import com.example.assignmentapp.sensor.RespiratoryRateProcessor
import com.example.assignmentapp.ui.ContinueToSymptomsButton
import com.example.assignmentapp.ui.HealthViewModel
import com.example.assignmentapp.ui.MeasurementSection
import com.example.assignmentapp.ui.VitalsScreen
import com.example.assignmentapp.ui.canStartHeartRateRecording
import com.example.assignmentapp.ui.deleteTemporaryVideo
import com.example.assignmentapp.ui.heartRateCameraError
import com.example.assignmentapp.ui.heartRatePermissionState
import com.example.assignmentapp.ui.respiratoryRateHardwareError
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
        composeRule.onNodeWithContentDescription("Heart rate collection progress")
            .assertIsDisplayed()

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

        composeRule.onNodeWithText("Calculating estimate — keep this screen open.")
            .assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Heart rate processing progress")
            .assertIsDisplayed()
    }

    @Test
    fun missingAccelerometerReturnsClearError() {
        val error = respiratoryRateHardwareError(hasAccelerometer = false)

        assertEquals("An accelerometer is not available on this device.", error)
        setMeasurement(
            title = "Respiratory rate",
            state = MeasurementState.Error(requireNotNull(error)),
            unit = "breaths/min",
            onRetry = null
        )

        composeRule.onNodeWithText(error).assertIsDisplayed()
        composeRule.onNodeWithText("Retry").assertDoesNotExist()
    }

    @Test
    fun respiratoryInstructionsAndResultAreDisplayed() {
        val instructions =
            "Lie down and place the phone flat on your chest. Keep still and breathe normally during the 45-second measurement."
        setMeasurement(
            title = "Respiratory rate",
            instructions = instructions,
            state = MeasurementState.Success(16.0),
            unit = "breaths/min"
        )

        composeRule.onNodeWithText(instructions).assertIsDisplayed()
        composeRule.onNodeWithText("16 breaths/min").assertIsDisplayed()
    }

    @Test
    fun respiratoryCollectionProcessesSamplesAndCleansUp() {
        val sampleSource = FakeAccelerometerSampleSource(emitSamplesOnStart = true)
        val processor = object : RespiratoryRateProcessor {
            override suspend fun calculate(samples: List<AccelerometerSample>): Int {
                assertEquals(12, samples.size)
                return 16
            }
        }
        setVitalsScreen(
            sampleSource = sampleSource,
            processor = processor,
            collectionDurationMillis = 1L
        )

        composeRule.onNodeWithText("Coursework estimates only — not medical advice.")
            .assertIsDisplayed()
        composeRule.onNodeWithText("Start 45-second measurement")
            .performScrollTo()
            .performClick()
        composeRule.waitUntil(timeoutMillis = 5_000L) { sampleSource.stopCount == 1 }

        composeRule.onNodeWithText("16 breaths/min").assertIsDisplayed()
        composeRule.runOnIdle {
            assertEquals(1, sampleSource.startCount)
            assertEquals(1, sampleSource.stopCount)
        }
    }

    @Test
    fun cancellingRespiratoryCollectionStopsTheSensorAndAllowsRetry() {
        val sampleSource = FakeAccelerometerSampleSource()
        setVitalsScreen(
            sampleSource = sampleSource,
            processor = object : RespiratoryRateProcessor {
                override suspend fun calculate(samples: List<AccelerometerSample>) = 16
            },
            collectionDurationMillis = 60_000L
        )

        composeRule.onNodeWithText("Start 45-second measurement")
            .performScrollTo()
            .performClick()
        composeRule.onNodeWithText("Cancel")
            .performScrollTo()
            .performClick()
        composeRule.waitUntil(timeoutMillis = 5_000L) { sampleSource.stopCount == 1 }

        composeRule.onNodeWithText("Start 45-second measurement")
            .assertIsDisplayed()
            .assertIsEnabled()
    }

    @Test
    fun respiratoryRegistrationFailureCanBeRetried() {
        val sampleSource = FakeAccelerometerSampleSource(
            emitSamplesOnStart = true,
            registrationSucceeds = false
        )
        setVitalsScreen(
            sampleSource = sampleSource,
            processor = object : RespiratoryRateProcessor {
                override suspend fun calculate(samples: List<AccelerometerSample>) = 16
            },
            collectionDurationMillis = 1L
        )

        composeRule.onNodeWithText("Start 45-second measurement")
            .performScrollTo()
            .performClick()
        composeRule.onNodeWithText("Accelerometer data collection couldn't start.")
            .assertIsDisplayed()
        composeRule.runOnIdle { sampleSource.registrationSucceeds = true }
        composeRule.onNodeWithText("Retry")
            .performScrollTo()
            .performClick()
        composeRule.waitUntil(timeoutMillis = 5_000L) { sampleSource.stopCount == 1 }

        composeRule.onNodeWithText("16 breaths/min").assertIsDisplayed()
        composeRule.runOnIdle { assertEquals(2, sampleSource.startCount) }
    }

    @Test
    fun disposingVitalsCancelsCollectionAndStopsTheSensor() {
        val sampleSource = FakeAccelerometerSampleSource()
        var showVitals by mutableStateOf(true)
        var heartRateState by mutableStateOf<MeasurementState>(MeasurementState.Processing)
        var respiratoryRateState by mutableStateOf<MeasurementState>(MeasurementState.Idle)
        composeRule.setContent {
            AssignmentAppTheme {
                if (showVitals) {
                    VitalsScreen(
                        heartRateState = heartRateState,
                        respiratoryRateState = respiratoryRateState,
                        onHeartRateStateChange = { heartRateState = it },
                        onRespiratoryRateStateChange = { respiratoryRateState = it },
                        onContinue = { },
                        respiratorySampleSourceOverride = sampleSource,
                        respiratoryRateProcessorOverride = object : RespiratoryRateProcessor {
                            override suspend fun calculate(
                                samples: List<AccelerometerSample>
                            ) = 16
                        },
                        respiratoryCollectionDurationMillis = 60_000L
                    )
                } else {
                    Text("Vitals closed")
                }
            }
        }

        composeRule.onNodeWithText("Start 45-second measurement")
            .performScrollTo()
            .performClick()
        composeRule.waitUntil(timeoutMillis = 5_000L) { sampleSource.startCount == 1 }
        composeRule.runOnIdle { showVitals = false }
        composeRule.waitUntil(timeoutMillis = 5_000L) { sampleSource.stopCount == 1 }

        composeRule.onNodeWithText("Vitals closed").assertIsDisplayed()
        composeRule.runOnIdle {
            assertEquals(MeasurementState.Idle, heartRateState)
            assertEquals(MeasurementState.Idle, respiratoryRateState)
        }
    }

    @Test
    fun backgroundingResetsInterruptedHeartMeasurementAndPreservesCompletedResult() {
        val lifecycleOwner = TestLifecycleOwner()
        var heartRateState by mutableStateOf<MeasurementState>(MeasurementState.Processing)
        var respiratoryRateState by mutableStateOf<MeasurementState>(
            MeasurementState.Success(16.0)
        )
        composeRule.setContent {
            CompositionLocalProvider(LocalLifecycleOwner provides lifecycleOwner) {
                AssignmentAppTheme {
                    VitalsScreen(
                        heartRateState = heartRateState,
                        respiratoryRateState = respiratoryRateState,
                        onHeartRateStateChange = { heartRateState = it },
                        onRespiratoryRateStateChange = { respiratoryRateState = it },
                        onContinue = { },
                        respiratorySampleSourceOverride = FakeAccelerometerSampleSource()
                    )
                }
            }
        }

        composeRule.runOnIdle {
            lifecycleOwner.handle(Lifecycle.Event.ON_CREATE)
            lifecycleOwner.handle(Lifecycle.Event.ON_START)
            lifecycleOwner.handle(Lifecycle.Event.ON_RESUME)
            lifecycleOwner.handle(Lifecycle.Event.ON_PAUSE)
            lifecycleOwner.handle(Lifecycle.Event.ON_STOP)
        }

        composeRule.runOnIdle {
            assertEquals(MeasurementState.Idle, heartRateState)
            assertEquals(MeasurementState.Success(16.0), respiratoryRateState)
        }
    }

    @Test
    fun continueToSymptomsIsDisabledUntilBothMeasurementsSucceed() {
        setContinueButton(
            heartRateState = MeasurementState.Success(72.0),
            respiratoryRateState = MeasurementState.Idle
        )

        composeRule.onNodeWithText("Continue to symptoms").assertIsNotEnabled()
    }

    @Test
    fun continueToSymptomsInvokesNavigationAfterBothMeasurementsSucceed() {
        var navigationCalls = 0
        setContinueButton(
            heartRateState = MeasurementState.Success(72.0),
            respiratoryRateState = MeasurementState.Success(16.0),
            onContinue = { navigationCalls++ }
        )

        composeRule.onNodeWithText("Continue to symptoms")
            .assertIsEnabled()
            .performClick()

        composeRule.runOnIdle { assertEquals(1, navigationCalls) }
    }

    @Test
    fun navHostGatesSymptomsAndPreservesTheMeasuredSession() {
        val viewModel = HealthViewModel()
        composeRule.setContent {
            AssignmentAppTheme {
                AssignmentAppNavHost(healthViewModel = viewModel)
            }
        }
        composeRule.onNodeWithText("Record health data").performClick()
        composeRule.onNodeWithText("Continue to symptoms")
            .performScrollTo()
            .assertIsNotEnabled()

        composeRule.runOnIdle {
            viewModel.updateHeartRate(MeasurementState.Success(72.0))
            viewModel.updateRespiratoryRate(MeasurementState.Success(16.0))
        }
        composeRule.onNodeWithText("Continue to symptoms")
            .performScrollTo()
            .assertIsEnabled()
            .performClick()

        composeRule.onNodeWithText("How are you feeling?").assertIsDisplayed()
        composeRule.runOnIdle {
            assertEquals(72.0, viewModel.session.heartRate)
            assertEquals(16.0, viewModel.session.respiratoryRate)
        }
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
        title: String = "Heart rate",
        instructions: String = "Keep still during recording.",
        state: MeasurementState,
        unit: String = "bpm",
        actionLabel: String? = null,
        actionEnabled: Boolean = true,
        onRetry: (() -> Unit)? = { },
        progress: Float? = null,
        onCancel: (() -> Unit)? = null
    ) {
        composeRule.setContent {
            AssignmentAppTheme {
                MeasurementSection(
                    title = title,
                    instructions = instructions,
                    state = state,
                    unit = unit,
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

    private fun setContinueButton(
        heartRateState: MeasurementState,
        respiratoryRateState: MeasurementState,
        onContinue: () -> Unit = { }
    ) {
        composeRule.setContent {
            AssignmentAppTheme {
                ContinueToSymptomsButton(
                    heartRateState = heartRateState,
                    respiratoryRateState = respiratoryRateState,
                    onContinue = onContinue
                )
            }
        }
    }

    private fun setVitalsScreen(
        sampleSource: AccelerometerSampleSource,
        processor: RespiratoryRateProcessor,
        collectionDurationMillis: Long
    ) {
        composeRule.setContent {
            var respiratoryRateState by remember {
                mutableStateOf<MeasurementState>(MeasurementState.Idle)
            }
            AssignmentAppTheme {
                VitalsScreen(
                    heartRateState = MeasurementState.Success(72.0),
                    respiratoryRateState = respiratoryRateState,
                    onHeartRateStateChange = { },
                    onRespiratoryRateStateChange = { respiratoryRateState = it },
                    onContinue = { },
                    respiratorySampleSourceOverride = sampleSource,
                    respiratoryRateProcessorOverride = processor,
                    respiratoryCollectionDurationMillis = collectionDurationMillis
                )
            }
        }
    }
}

private class FakeAccelerometerSampleSource(
    private val emitSamplesOnStart: Boolean = false,
    var registrationSucceeds: Boolean = true
) : AccelerometerSampleSource {
    override val isAvailable = true
    @Volatile
    var startCount = 0
        private set
    @Volatile
    var stopCount = 0
        private set
    private var listener: ((AccelerometerSample) -> Unit)? = null

    override fun start(onSample: (AccelerometerSample) -> Unit): Boolean {
        startCount++
        if (!registrationSucceeds) return false
        listener = onSample
        if (emitSamplesOnStart) {
            repeat(12) { index ->
                onSample(
                    AccelerometerSample(
                        timestampNanos = index.toLong(),
                        x = 0f,
                        y = 0f,
                        z = 10f
                    )
                )
            }
        }
        return true
    }

    override fun stop() {
        if (listener != null) {
            stopCount++
            listener = null
        }
    }
}

private class TestLifecycleOwner : LifecycleOwner {
    private val registry = LifecycleRegistry(this)

    override val lifecycle: Lifecycle = registry

    fun handle(event: Lifecycle.Event) {
        registry.handleLifecycleEvent(event)
    }
}
