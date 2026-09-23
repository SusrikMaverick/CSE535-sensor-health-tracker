package com.example.assignmentapp.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.unit.dp
import com.example.assignmentapp.data.HealthSession
import com.example.assignmentapp.data.SessionSaveStatus
import com.example.assignmentapp.data.Symptom
import kotlin.math.roundToInt

/**
 * Activity 2 content for rating symptoms and saving the current health session.
 *
 * State is owned by the caller so ratings survive recomposition and a failed save can be retried
 * without losing either vital measurement.
 */
@Composable
fun SymptomsContent(
    session: HealthSession,
    saveState: SessionSaveStatus,
    onRatingChange: (Symptom, Int) -> Unit,
    onUpload: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    val inputsEnabled = saveState !is SessionSaveStatus.Saving &&
        saveState !is SessionSaveStatus.Saved
    val hasCompleteVitals = session.heartRate != null && session.respiratoryRate != null

    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "SYMPTOM CHECK-IN",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "How are you feeling?",
                    style = MaterialTheme.typography.headlineLarge,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = "Review your measurements, then rate each symptom from 0 to 5.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            VitalsSummary(session = session)

            if (!hasCompleteVitals) {
                Text(
                    text = "Complete both vital measurements before uploading symptoms.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.error
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = "Rate your symptoms",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = "0 means none • 5 means severe",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Symptom.entries.forEach { symptom ->
                SymptomRatingCard(
                    symptom = symptom,
                    rating = session.symptomRatings[symptom] ?: 0,
                    enabled = inputsEnabled,
                    onRatingChange = { rating -> onRatingChange(symptom, rating) }
                )
            }

            SaveFeedback(
                saveState = saveState,
                onRetry = onRetry
            )

            Button(
                onClick = onUpload,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 56.dp)
                    .semantics { testTag = "upload_symptoms" },
                enabled = saveState is SessionSaveStatus.Ready && hasCompleteVitals,
                shape = MaterialTheme.shapes.large
            ) {
                if (saveState is SessionSaveStatus.Saving) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp
                    )
                }
                Text(
                    text = when (saveState) {
                        SessionSaveStatus.Ready -> "Upload symptoms"
                        SessionSaveStatus.Saving -> "Saving…"
                        is SessionSaveStatus.Saved -> "Symptoms uploaded"
                        is SessionSaveStatus.Error -> "Upload symptoms"
                    },
                    modifier = Modifier.padding(horizontal = 8.dp)
                )
            }
        }
    }
}

@Composable
private fun VitalsSummary(
    session: HealthSession,
    modifier: Modifier = Modifier
) {
    ElevatedCard(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer
        )
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "Your measurements",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
            Text(
                text = "Coursework estimates only — not medical advice.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                VitalValue(
                    label = "Heart rate",
                    value = session.heartRate,
                    unit = "bpm",
                    spokenUnit = "beats per minute",
                    modifier = Modifier.weight(1f)
                )
                VitalValue(
                    label = "Respiratory rate",
                    value = session.respiratoryRate,
                    unit = "breaths/min",
                    spokenUnit = "breaths per minute",
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun VitalValue(
    label: String,
    value: Double?,
    unit: String,
    spokenUnit: String,
    modifier: Modifier = Modifier
) {
    val formattedValue = value?.roundToInt()?.toString() ?: "Not measured"
    val displayedValue = if (value == null) formattedValue else "$formattedValue $unit"
    val spokenValue = if (value == null) formattedValue else "$formattedValue $spokenUnit"

    Surface(
        modifier = modifier.semantics(mergeDescendants = true) {
            contentDescription = "$label, $spokenValue, read only"
        },
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = displayedValue,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = if (value == null) "Required" else "Recorded",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
private fun SymptomRatingCard(
    symptom: Symptom,
    rating: Int,
    enabled: Boolean,
    onRatingChange: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    ElevatedCard(
        modifier = modifier
            .fillMaxWidth()
            .semantics { testTag = "symptom_${symptom.name.lowercase()}" },
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = symptom.displayName,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )
                Surface(
                    shape = MaterialTheme.shapes.small,
                    color = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Text(
                        text = "$rating / 5",
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }

            RatingSelector(
                symptom = symptom,
                selectedRating = rating,
                enabled = enabled,
                onRatingChange = onRatingChange
            )

        }
    }
}

@Composable
private fun RatingSelector(
    symptom: Symptom,
    selectedRating: Int,
    enabled: Boolean,
    onRatingChange: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .selectableGroup()
    ) {
        if (maxWidth < 320.dp) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                RatingRow(
                    ratings = 0..2,
                    symptom = symptom,
                    selectedRating = selectedRating,
                    enabled = enabled,
                    onRatingChange = onRatingChange
                )
                RatingRow(
                    ratings = 3..5,
                    symptom = symptom,
                    selectedRating = selectedRating,
                    enabled = enabled,
                    onRatingChange = onRatingChange
                )
            }
        } else {
            RatingRow(
                ratings = 0..5,
                symptom = symptom,
                selectedRating = selectedRating,
                enabled = enabled,
                onRatingChange = onRatingChange
            )
        }
    }
}

@Composable
private fun RatingRow(
    ratings: IntRange,
    symptom: Symptom,
    selectedRating: Int,
    enabled: Boolean,
    onRatingChange: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        ratings.forEach { rating ->
            RatingChoice(
                symptom = symptom,
                rating = rating,
                selected = rating == selectedRating,
                enabled = enabled,
                onClick = { onRatingChange(rating) },
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun RatingChoice(
    symptom: Symptom,
    rating: Int,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val shape = if (selected) MaterialTheme.shapes.small else CircleShape
    val containerColor = when {
        selected -> MaterialTheme.colorScheme.primary
        enabled -> MaterialTheme.colorScheme.surfaceVariant
        else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
    }
    val contentColor = if (selected) {
        MaterialTheme.colorScheme.onPrimary
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

    Surface(
        modifier = modifier
            .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
            .clip(shape)
            .selectable(
                selected = selected,
                enabled = enabled,
                role = Role.RadioButton,
                onClick = onClick
            )
            .semantics {
                contentDescription = "${symptom.displayName}, rating $rating out of 5"
                stateDescription = if (selected) "Selected" else "Not selected"
                testTag = "${symptom.name.lowercase()}_rating_$rating"
            },
        shape = shape,
        color = containerColor,
        contentColor = contentColor,
        border = if (selected) {
            BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
        } else {
            BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        }
    ) {
        Box(
            modifier = Modifier.heightIn(min = 48.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = rating.toString(),
                style = MaterialTheme.typography.labelLarge
            )
        }
    }
}

@Composable
private fun SaveFeedback(
    saveState: SessionSaveStatus,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    when (saveState) {
        SessionSaveStatus.Ready -> Unit

        SessionSaveStatus.Saving -> {
            ElevatedCard(
                modifier = modifier
                    .fillMaxWidth()
                    .semantics { liveRegion = LiveRegionMode.Polite },
                shape = MaterialTheme.shapes.large,
                colors = CardDefaults.elevatedCardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "Saving your check-in…",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    Text(
                        text = "Please keep this screen open.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
        }

        is SessionSaveStatus.Saved -> {
            ElevatedCard(
                modifier = modifier
                    .fillMaxWidth()
                    .semantics { liveRegion = LiveRegionMode.Polite },
                shape = MaterialTheme.shapes.large,
                colors = CardDefaults.elevatedCardColors(
                    containerColor = MaterialTheme.colorScheme.tertiaryContainer
                )
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "Check-in saved",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onTertiaryContainer
                    )
                    Text(
                        text = "Your measurements and symptom ratings are stored on this device.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onTertiaryContainer
                    )
                }
            }
        }

        is SessionSaveStatus.Error -> {
            ElevatedCard(
                modifier = modifier
                    .fillMaxWidth()
                    .semantics { liveRegion = LiveRegionMode.Polite },
                shape = MaterialTheme.shapes.large,
                colors = CardDefaults.elevatedCardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer
                )
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "Couldn’t save your check-in",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                    Text(
                        text = saveState.message,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                    OutlinedButton(onClick = onRetry) {
                        Text("Retry")
                    }
                }
            }
        }
    }
}
