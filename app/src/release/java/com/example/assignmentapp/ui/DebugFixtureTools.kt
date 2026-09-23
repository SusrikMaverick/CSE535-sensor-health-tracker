package com.example.assignmentapp.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.example.assignmentapp.data.MeasurementState

@Suppress("UNUSED_PARAMETER")
@Composable
internal fun DebugFixtureTools(
    heartRateState: MeasurementState,
    respiratoryRateState: MeasurementState,
    onHeartRateStateChange: (MeasurementState) -> Unit,
    onRespiratoryRateStateChange: (MeasurementState) -> Unit,
    modifier: Modifier = Modifier
) = Unit
