package com.example.assignmentapp.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun HomeScreen(
    onContinue: () -> Unit,
    modifier: Modifier = Modifier
) {
    PlaceholderScreen(
        title = "Health monitoring",
        description = "Record vital signs and symptoms from one simple flow.",
        actionLabel = "Open vitals",
        onAction = onContinue,
        modifier = modifier
    )
}

@Composable
fun VitalsScreen(
    onContinue: () -> Unit,
    modifier: Modifier = Modifier
) {
    PlaceholderScreen(
        title = "Vital signs",
        description = "Heart-rate and respiratory-rate measurements will appear here.",
        actionLabel = "Open symptoms",
        onAction = onContinue,
        modifier = modifier
    )
}

@Composable
fun SymptomsScreen(
    onContinue: () -> Unit,
    modifier: Modifier = Modifier
) {
    PlaceholderScreen(
        title = "Symptoms",
        description = "Symptom ratings and local saving will appear here.",
        actionLabel = "Return home",
        onAction = onContinue,
        modifier = modifier
    )
}

@Composable
private fun PlaceholderScreen(
    title: String,
    description: String,
    actionLabel: String,
    onAction: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            ElevatedCard(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.large
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleLarge
                    )
                    Text(
                        text = description,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Button(onClick = onAction) {
                        Text(actionLabel)
                    }
                }
            }
        }
    }
}
