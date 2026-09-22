package com.example.assignmentapp.ui

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.lifecycle.awaitInstance
import androidx.camera.video.FileOutputOptions
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.assignmentapp.data.MeasurementState
import com.example.assignmentapp.sensor.AccelerometerSampleSource
import com.example.assignmentapp.sensor.AndroidAccelerometerSampleSource
import com.example.assignmentapp.sensor.RESPIRATORY_COLLECTION_DURATION_MILLIS
import com.example.assignmentapp.sensor.RespiratoryRateProcessor
import com.example.assignmentapp.sensor.SampleRespiratoryRateProcessor
import com.example.assignmentapp.sensor.VideoHeartRateProcessor
import com.example.assignmentapp.sensor.canStartRespiratoryCollection
import com.example.assignmentapp.sensor.collectRespiratorySamples
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val HEART_RATE_RECORDING_DURATION_MILLIS = 45_000L

internal fun heartRateCameraError(
    hasRearCamera: Boolean,
    hasTorch: Boolean
): String? = when {
    !hasRearCamera -> "A rear camera is not available on this device."
    !hasTorch -> "A camera torch is not available on this device."
    else -> null
}

internal fun heartRatePermissionState(granted: Boolean): MeasurementState =
    if (granted) {
        MeasurementState.Idle
    } else {
        MeasurementState.Error("Camera permission is required to measure heart rate.")
    }

internal fun canStartHeartRateRecording(
    isPreparingCamera: Boolean,
    hasActiveRecording: Boolean
): Boolean = !isPreparingCamera && !hasActiveRecording

internal fun deleteTemporaryVideo(file: File?) {
    file?.delete()
}

internal fun respiratoryRateHardwareError(hasAccelerometer: Boolean): String? =
    if (hasAccelerometer) {
        null
    } else {
        "An accelerometer is not available on this device."
    }

internal fun canContinueToSymptoms(
    heartRateState: MeasurementState,
    respiratoryRateState: MeasurementState
): Boolean = heartRateState is MeasurementState.Success &&
    respiratoryRateState is MeasurementState.Success

@Composable
fun HomeScreen(
    onRecordHealthData: () -> Unit,
    onDeleteAllDataConfirmed: suspend () -> Int,
    modifier: Modifier = Modifier
) {
    var showDeleteConfirmation by rememberSaveable { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()

    Box(modifier = modifier.fillMaxSize()) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 24.dp, vertical = 32.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                Text(
                    text = "CONTEXT MONITORING",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "Your health, in context",
                    style = MaterialTheme.typography.headlineLarge,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = "Start a guided check-in to record your vital signs and symptoms.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                ElevatedCard(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.extraLarge,
                    colors = CardDefaults.elevatedCardColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "A simple guided flow",
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                        Text(
                            text = "Measure heart rate and respiratory rate, then add symptom ratings.",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                }

                Spacer(modifier = Modifier.weight(1f))

                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(
                        onClick = onRecordHealthData,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 64.dp),
                        shape = MaterialTheme.shapes.extraLarge,
                        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 18.dp)
                    ) {
                        Text(
                            text = "Record health data",
                            style = MaterialTheme.typography.titleMedium
                        )
                    }

                    OutlinedButton(
                        onClick = { showDeleteConfirmation = true },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 56.dp),
                        shape = MaterialTheme.shapes.extraLarge,
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        )
                    ) {
                        Text("Delete all recorded data")
                    }
                }
            }
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(16.dp)
        )
    }

    if (showDeleteConfirmation) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmation = false },
            title = { Text("Delete all recorded data?") },
            text = {
                Text("Every saved health record on this device will be permanently removed.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteConfirmation = false
                        coroutineScope.launch {
                            val message = try {
                                if (onDeleteAllDataConfirmed() > 0) {
                                    "All recorded data was deleted."
                                } else {
                                    "There is no recorded data to delete."
                                }
                            } catch (error: CancellationException) {
                                throw error
                            } catch (_: Exception) {
                                "Recorded data couldn't be deleted. Try again."
                            }
                            snackbarHostState.showSnackbar(message)
                        }
                    }
                ) {
                    Text(
                        text = "Delete",
                        color = MaterialTheme.colorScheme.error
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmation = false }) {
                    Text("Cancel")
                }
            },
            shape = MaterialTheme.shapes.extraLarge
        )
    }
}

@Composable
internal fun VitalsScreen(
    heartRateState: MeasurementState,
    respiratoryRateState: MeasurementState,
    onHeartRateStateChange: (MeasurementState) -> Unit,
    onRespiratoryRateStateChange: (MeasurementState) -> Unit,
    onContinue: () -> Unit,
    modifier: Modifier = Modifier,
    respiratorySampleSourceOverride: AccelerometerSampleSource? = null,
    respiratoryRateProcessorOverride: RespiratoryRateProcessor? = null,
    respiratoryCollectionDurationMillis: Long = RESPIRATORY_COLLECTION_DURATION_MILLIS
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val mainExecutor = remember(context) { ContextCompat.getMainExecutor(context) }
    val coroutineScope = rememberCoroutineScope()
    val heartRateProcessor = remember(context) { VideoHeartRateProcessor(context) }
    val defaultRespiratorySampleSource = remember(context) {
        AndroidAccelerometerSampleSource(context)
    }
    val respiratorySampleSource =
        respiratorySampleSourceOverride ?: defaultRespiratorySampleSource
    val defaultRespiratoryRateProcessor = remember { SampleRespiratoryRateProcessor() }
    val respiratoryRateProcessor =
        respiratoryRateProcessorOverride ?: defaultRespiratoryRateProcessor
    val screenActive = remember { mutableStateOf(true) }
    val packageManager = context.packageManager
    @SuppressLint("UnsupportedChromeOsCameraSystemFeature")
    val hasRearCamera = remember(packageManager) {
        packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA)
    }
    val hasTorch = remember(packageManager) {
        packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_FLASH)
    }
    val cameraHardwareError = heartRateCameraError(hasRearCamera, hasTorch)
    var cameraPermissionGranted by rememberSaveable {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
        )
    }
    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        cameraPermissionGranted = granted
        onHeartRateStateChange(heartRatePermissionState(granted))
    }
    var activeRecording by remember { mutableStateOf<Recording?>(null) }
    var boundCameraProvider by remember { mutableStateOf<ProcessCameraProvider?>(null) }
    var boundCamera by remember { mutableStateOf<Camera?>(null) }
    var temporaryVideoFile by remember { mutableStateOf<File?>(null) }
    var isPreparingCamera by remember { mutableStateOf(false) }
    var cancellationRequested by remember { mutableStateOf(false) }
    var recordingProgress by remember { mutableFloatStateOf(0f) }
    var respiratoryCollectionJob by remember { mutableStateOf<Job?>(null) }
    var respiratoryProgress by remember { mutableFloatStateOf(0f) }
    val accelerometerError = respiratoryRateHardwareError(
        respiratorySampleSource.isAvailable
    )

    LaunchedEffect(accelerometerError, respiratoryRateState) {
        if (accelerometerError != null && respiratoryRateState is MeasurementState.Idle) {
            onRespiratoryRateStateChange(MeasurementState.Error(accelerometerError))
        }
    }

    fun prepareHeartRateCamera() {
        when {
            cameraHardwareError != null -> onHeartRateStateChange(
                MeasurementState.Error(cameraHardwareError)
            )
            cameraPermissionGranted -> onHeartRateStateChange(MeasurementState.Idle)
            else -> cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    val cameraReady = cameraHardwareError == null && cameraPermissionGranted

    fun releaseHeartRateResources(deleteVideo: Boolean) {
        val recording = activeRecording
        activeRecording = null
        recording?.stop()
        boundCamera?.cameraControl?.enableTorch(false)
        boundCamera = null
        boundCameraProvider?.unbindAll()
        boundCameraProvider = null
        isPreparingCamera = false
        if (deleteVideo) {
            deleteTemporaryVideo(temporaryVideoFile)
            temporaryVideoFile = null
        }
    }

    fun startHeartRateRecording() {
        if (!canStartHeartRateRecording(isPreparingCamera, activeRecording != null)) return
        if (!cameraReady) {
            prepareHeartRateCamera()
            return
        }

        isPreparingCamera = true
        cancellationRequested = false
        recordingProgress = 0f

        coroutineScope.launch {
            try {
                val cameraProvider = ProcessCameraProvider.awaitInstance(context)
                boundCameraProvider = cameraProvider
                if (!cameraProvider.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA)) {
                    releaseHeartRateResources(deleteVideo = true)
                    onHeartRateStateChange(
                        MeasurementState.Error(
                            "A rear camera is not available on this device."
                        )
                    )
                    return@launch
                }

                val recorder = Recorder.Builder().build()
                val videoCapture = VideoCapture.withOutput(recorder)
                cameraProvider.unbindAll()
                val camera = cameraProvider.bindToLifecycle(
                    lifecycleOwner,
                    CameraSelector.DEFAULT_BACK_CAMERA,
                    videoCapture
                )
                boundCamera = camera
                if (!camera.cameraInfo.hasFlashUnit()) {
                    releaseHeartRateResources(deleteVideo = true)
                    onHeartRateStateChange(
                        MeasurementState.Error(
                            "A camera torch is not available on this device."
                        )
                    )
                    return@launch
                }

                val torchRequest = camera.cameraControl.enableTorch(true)
                withContext(Dispatchers.IO) { torchRequest.get() }
                val outputFile = File.createTempFile(
                    "heart-rate-",
                    ".mp4",
                    context.cacheDir
                )
                temporaryVideoFile = outputFile
                val outputOptions = FileOutputOptions.Builder(outputFile)
                    .setDurationLimitMillis(HEART_RATE_RECORDING_DURATION_MILLIS)
                    .build()

                activeRecording = videoCapture.output
                    .prepareRecording(context, outputOptions)
                    .start(mainExecutor) { event ->
                        recordingProgress = (
                            event.recordingStats.recordedDurationNanos / 1_000_000f /
                                HEART_RATE_RECORDING_DURATION_MILLIS
                            ).coerceIn(0f, 1f)

                        when (event) {
                            is VideoRecordEvent.Start -> {
                                onHeartRateStateChange(MeasurementState.Collecting)
                            }

                            is VideoRecordEvent.Finalize -> {
                                activeRecording = null
                                releaseHeartRateResources(deleteVideo = false)

                                val wasCancelled = cancellationRequested
                                cancellationRequested = false
                                when {
                                    !screenActive.value -> {
                                        releaseHeartRateResources(deleteVideo = true)
                                    }

                                    wasCancelled -> {
                                        recordingProgress = 0f
                                        releaseHeartRateResources(deleteVideo = true)
                                        onHeartRateStateChange(MeasurementState.Idle)
                                    }

                                    event.error == VideoRecordEvent.Finalize.ERROR_NONE ||
                                        event.error == VideoRecordEvent.Finalize
                                            .ERROR_DURATION_LIMIT_REACHED -> {
                                        recordingProgress = 1f
                                        onHeartRateStateChange(MeasurementState.Processing)
                                        coroutineScope.launch {
                                            try {
                                                val beatsPerMinute = heartRateProcessor.calculate(
                                                    Uri.fromFile(outputFile)
                                                )
                                                if (screenActive.value) {
                                                    onHeartRateStateChange(
                                                        MeasurementState.Success(
                                                            beatsPerMinute.toDouble()
                                                        )
                                                    )
                                                }
                                            } catch (error: CancellationException) {
                                                throw error
                                            } catch (error: Exception) {
                                                if (screenActive.value) {
                                                    onHeartRateStateChange(
                                                        MeasurementState.Error(
                                                            error.message
                                                                ?: "Heart-rate processing failed."
                                                        )
                                                    )
                                                }
                                            } finally {
                                                deleteTemporaryVideo(outputFile)
                                                if (temporaryVideoFile == outputFile) {
                                                    temporaryVideoFile = null
                                                }
                                            }
                                        }
                                    }

                                    else -> {
                                        recordingProgress = 0f
                                        releaseHeartRateResources(deleteVideo = true)
                                        onHeartRateStateChange(
                                            MeasurementState.Error(
                                                "Heart-rate recording failed. Try again."
                                            )
                                        )
                                    }
                                }
                            }

                            else -> Unit
                        }
                    }
                isPreparingCamera = false
            } catch (error: CancellationException) {
                releaseHeartRateResources(deleteVideo = true)
                throw error
            } catch (_: Exception) {
                releaseHeartRateResources(deleteVideo = true)
                if (screenActive.value) {
                    onHeartRateStateChange(
                        MeasurementState.Error("Heart-rate recording couldn't start.")
                    )
                }
            }
        }
    }

    fun cancelHeartRateRecording() {
        if (!cancellationRequested) {
            cancellationRequested = true
            activeRecording?.stop()
        }
    }

    fun startRespiratoryRateCollection() {
        if (!canStartRespiratoryCollection(respiratoryCollectionJob != null)) {
            return
        }
        if (accelerometerError != null) {
            onRespiratoryRateStateChange(MeasurementState.Error(accelerometerError))
            return
        }

        respiratoryProgress = 0f
        onRespiratoryRateStateChange(MeasurementState.Collecting)
        lateinit var collectionJob: Job
        collectionJob = coroutineScope.launch(start = CoroutineStart.LAZY) {
            try {
                val samples = collectRespiratorySamples(
                    sampleSource = respiratorySampleSource,
                    collectionDurationMillis = respiratoryCollectionDurationMillis,
                    onProgress = { progress ->
                        if (screenActive.value) {
                            respiratoryProgress = progress
                        }
                    }
                )
                if (!screenActive.value) return@launch

                onRespiratoryRateStateChange(MeasurementState.Processing)
                val breathsPerMinute = respiratoryRateProcessor.calculate(samples)
                if (screenActive.value) {
                    onRespiratoryRateStateChange(
                        MeasurementState.Success(breathsPerMinute.toDouble())
                    )
                }
            } catch (error: CancellationException) {
                if (screenActive.value) {
                    respiratoryProgress = 0f
                    onRespiratoryRateStateChange(MeasurementState.Idle)
                }
                throw error
            } catch (error: Exception) {
                if (screenActive.value) {
                    respiratoryProgress = 0f
                    onRespiratoryRateStateChange(
                        MeasurementState.Error(
                            error.message ?: "Respiratory-rate measurement failed."
                        )
                    )
                }
            } finally {
                if (respiratoryCollectionJob === collectionJob) {
                    respiratoryCollectionJob = null
                }
            }
        }
        respiratoryCollectionJob = collectionJob
        collectionJob.start()
    }

    fun cancelRespiratoryRateCollection() {
        respiratoryCollectionJob?.cancel()
    }

    DisposableEffect(lifecycleOwner, respiratorySampleSource) {
        screenActive.value = true
        val lifecycleObserver = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) {
                val activeRespiratoryCollection = respiratoryCollectionJob
                if (activeRespiratoryCollection != null) {
                    activeRespiratoryCollection.cancel()
                    respiratorySampleSource.stop()
                    respiratoryProgress = 0f
                    onRespiratoryRateStateChange(MeasurementState.Idle)
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(lifecycleObserver)

        onDispose {
            screenActive.value = false
            lifecycleOwner.lifecycle.removeObserver(lifecycleObserver)
            releaseHeartRateResources(deleteVideo = true)
            val activeRespiratoryCollection = respiratoryCollectionJob
            activeRespiratoryCollection?.cancel()
            respiratorySampleSource.stop()
            if (activeRespiratoryCollection != null) {
                onRespiratoryRateStateChange(MeasurementState.Idle)
            }
        }
    }

    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 32.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            Text(
                text = "HEALTH CHECK-IN",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = "Vital signs",
                style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.onBackground
            )
            Text(
                text = "Measure your heart rate and respiratory rate, then add symptom ratings.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            MeasurementSection(
                title = "Heart rate",
                instructions = "Softly cover the rear camera and flash with the pad of your index finger. Keep still during the 45-second recording.",
                state = heartRateState,
                unit = "bpm",
                idleLabel = when {
                    cameraReady -> "Ready to record"
                    else -> "Camera access needed"
                },
                actionLabel = when {
                    isPreparingCamera -> "Preparing camera"
                    !cameraReady -> "Set up camera"
                    else -> "Start 45-second recording"
                },
                actionEnabled = !isPreparingCamera && activeRecording == null,
                onAction = {
                    if (cameraReady) startHeartRateRecording() else prepareHeartRateCamera()
                },
                onRetry = {
                    if (cameraReady) startHeartRateRecording() else prepareHeartRateCamera()
                },
                progress = recordingProgress,
                onCancel = if (cancellationRequested) null else ::cancelHeartRateRecording,
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
            )

            MeasurementSection(
                title = "Respiratory rate",
                instructions = "Lie down and place the phone flat on your chest. Keep still and breathe normally during the 45-second measurement.",
                state = respiratoryRateState,
                unit = "breaths/min",
                idleLabel = if (accelerometerError == null) {
                    "Ready to measure"
                } else {
                    "Motion sensor unavailable"
                },
                actionLabel = "Start 45-second measurement",
                actionEnabled = canStartRespiratoryCollection(
                    respiratoryCollectionJob != null
                ),
                onAction = ::startRespiratoryRateCollection,
                onRetry = if (accelerometerError == null) {
                    ::startRespiratoryRateCollection
                } else {
                    null
                },
                progress = respiratoryProgress,
                onCancel = ::cancelRespiratoryRateCollection,
                containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                contentColor = MaterialTheme.colorScheme.onTertiaryContainer
            )

            ContinueToSymptomsButton(
                heartRateState = heartRateState,
                respiratoryRateState = respiratoryRateState,
                onContinue = onContinue
            )
        }
    }
}

@Composable
internal fun ContinueToSymptomsButton(
    heartRateState: MeasurementState,
    respiratoryRateState: MeasurementState,
    onContinue: () -> Unit,
    modifier: Modifier = Modifier
) {
    OutlinedButton(
        onClick = onContinue,
        enabled = canContinueToSymptoms(heartRateState, respiratoryRateState),
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp),
        shape = MaterialTheme.shapes.extraLarge
    ) {
        Text("Continue to symptoms")
    }
}

@Composable
internal fun MeasurementSection(
    title: String,
    instructions: String,
    state: MeasurementState,
    unit: String,
    idleLabel: String,
    onRetry: (() -> Unit)?,
    containerColor: Color,
    contentColor: Color,
    actionLabel: String? = null,
    actionEnabled: Boolean = true,
    onAction: () -> Unit = { },
    progress: Float? = null,
    onCancel: (() -> Unit)? = null
) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.elevatedCardColors(containerColor = containerColor)
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                color = contentColor
            )
            Text(
                text = instructions,
                style = MaterialTheme.typography.bodyLarge,
                color = contentColor
            )

            when (state) {
                MeasurementState.Idle -> {
                    Text(
                        text = idleLabel,
                        style = MaterialTheme.typography.labelLarge,
                        color = contentColor
                    )
                    actionLabel?.let { label ->
                        Button(
                            onClick = onAction,
                            enabled = actionEnabled
                        ) {
                            Text(label)
                        }
                    }
                }

                MeasurementState.Collecting -> {
                    Text("Collecting", color = contentColor)
                    if (progress == null) {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    } else {
                        LinearProgressIndicator(
                            progress = { progress },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Text(
                            text = "${(progress * 100).toInt()}%",
                            style = MaterialTheme.typography.labelLarge,
                            color = contentColor
                        )
                    }
                    onCancel?.let { cancel ->
                        OutlinedButton(
                            onClick = cancel,
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = contentColor
                            )
                        ) {
                            Text("Cancel")
                        }
                    }
                }

                MeasurementState.Processing -> {
                    Text("Processing", color = contentColor)
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }

                is MeasurementState.Success -> {
                    Text(
                        text = "${state.value.toInt()} $unit",
                        style = MaterialTheme.typography.headlineMedium,
                        color = contentColor
                    )
                }

                is MeasurementState.Error -> {
                    Text(
                        text = state.message,
                        style = MaterialTheme.typography.bodyLarge,
                        color = contentColor
                    )
                    onRetry?.let { retry ->
                        OutlinedButton(
                            onClick = retry,
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = contentColor
                            )
                        ) {
                            Text("Retry")
                        }
                    }
                }
            }
        }
    }
}
