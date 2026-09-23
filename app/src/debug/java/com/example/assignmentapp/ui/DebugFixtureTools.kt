package com.example.assignmentapp.ui

import android.content.res.AssetManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.assignmentapp.data.MeasurementState
import com.example.assignmentapp.sensor.AccelerometerSample
import com.example.assignmentapp.sensor.SampleRespiratoryRateProcessor
import com.example.assignmentapp.sensor.VideoHeartRateProcessor
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val X_FIXTURE = "fixtures/respiratory_x.csv"
private const val Y_FIXTURE = "fixtures/respiratory_y.csv"
private const val Z_FIXTURE = "fixtures/respiratory_z.csv"

internal fun parseRespiratoryCsvFixtures(
    xCsv: String,
    yCsv: String,
    zCsv: String
): List<AccelerometerSample> {
    val xValues = parseAxis(xCsv, "X")
    val yValues = parseAxis(yCsv, "Y")
    val zValues = parseAxis(zCsv, "Z")

    require(xValues.size == yValues.size && yValues.size == zValues.size) {
        "Respiratory CSV files must contain the same number of samples."
    }

    return xValues.indices.map { index ->
        AccelerometerSample(
            timestampNanos = index.toLong(),
            x = xValues[index],
            y = yValues[index],
            z = zValues[index]
        )
    }
}

private fun parseAxis(csv: String, axis: String): List<Float> {
    val values = csv.lineSequence()
        .flatMap { line -> line.split(',').asSequence() }
        .map(String::trim)
        .filter(String::isNotEmpty)
        .map { token ->
            token.toFloatOrNull()?.takeIf { it.isFinite() }
                ?: throw IllegalArgumentException(
                    "$axis respiratory CSV contains a nonnumeric value."
                )
        }
        .toList()

    require(values.isNotEmpty()) { "$axis respiratory CSV contains no samples." }
    return values
}

@Composable
internal fun DebugFixtureTools(
    heartRateState: MeasurementState,
    respiratoryRateState: MeasurementState,
    onHeartRateStateChange: (MeasurementState) -> Unit,
    onRespiratoryRateStateChange: (MeasurementState) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val heartRateProcessor = remember(context) { VideoHeartRateProcessor(context) }
    val respiratoryRateProcessor = remember { SampleRespiratoryRateProcessor() }
    val heartRateBusy = heartRateState is MeasurementState.Collecting ||
        heartRateState is MeasurementState.Processing
    val respiratoryRateBusy = respiratoryRateState is MeasurementState.Collecting ||
        respiratoryRateState is MeasurementState.Processing

    val videoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { videoUri ->
        if (videoUri == null) return@rememberLauncherForActivityResult

        coroutineScope.launch {
            onHeartRateStateChange(MeasurementState.Processing)
            try {
                val beatsPerMinute = heartRateProcessor.calculate(videoUri)
                onHeartRateStateChange(MeasurementState.Success(beatsPerMinute.toDouble()))
            } catch (error: CancellationException) {
                onHeartRateStateChange(MeasurementState.Idle)
                throw error
            } catch (error: Exception) {
                onHeartRateStateChange(
                    MeasurementState.Error(
                        error.message ?: "Heart-rate processing failed."
                    )
                )
            }
        }
    }

    ElevatedCard(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Emulator tools",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            OutlinedButton(
                onClick = { videoPicker.launch(arrayOf("video/*")) },
                enabled = !heartRateBusy,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 56.dp),
                shape = MaterialTheme.shapes.extraLarge
            ) {
                Text("Choose heart-rate video")
            }
            OutlinedButton(
                onClick = {
                    coroutineScope.launch {
                        onRespiratoryRateStateChange(MeasurementState.Processing)
                        try {
                            val samples = withContext(Dispatchers.IO) {
                                parseRespiratoryCsvFixtures(
                                    xCsv = context.assets.readText(X_FIXTURE),
                                    yCsv = context.assets.readText(Y_FIXTURE),
                                    zCsv = context.assets.readText(Z_FIXTURE)
                                )
                            }
                            val breathsPerMinute = respiratoryRateProcessor.calculate(samples)
                            onRespiratoryRateStateChange(
                                MeasurementState.Success(breathsPerMinute.toDouble())
                            )
                        } catch (error: CancellationException) {
                            onRespiratoryRateStateChange(MeasurementState.Idle)
                            throw error
                        } catch (error: Exception) {
                            onRespiratoryRateStateChange(
                                MeasurementState.Error(
                                    error.message ?: "Respiratory fixture couldn't be loaded."
                                )
                            )
                        }
                    }
                },
                enabled = !respiratoryRateBusy,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 56.dp),
                shape = MaterialTheme.shapes.extraLarge
            ) {
                Text("Load respiratory CSVs")
            }
        }
    }
}

private fun AssetManager.readText(path: String): String =
    open(path).bufferedReader().use { it.readText() }
