package com.example.assignmentapp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.assignmentapp.data.HealthDatabase
import com.example.assignmentapp.data.HealthRecordEntity
import com.example.assignmentapp.data.HealthSessionSaveCoordinator
import com.example.assignmentapp.data.SessionSaveStatus
import com.example.assignmentapp.ui.HealthViewModel
import com.example.assignmentapp.ui.HomeScreen
import com.example.assignmentapp.ui.SymptomsContent
import com.example.assignmentapp.ui.VitalsScreen
import com.example.assignmentapp.ui.theme.AssignmentAppTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val HomeRoute = "home"
private const val VitalsRoute = "vitals"
private const val SymptomsRoute = "symptoms"
private const val SaveSuccessDisplayMillis = 2_000L

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AssignmentAppTheme {
                AssignmentAppNavHost()
            }
        }
    }
}

@Composable
internal fun AssignmentAppNavHost(
    healthViewModel: HealthViewModel = viewModel(),
    insertHealthRecordOverride: (suspend (HealthRecordEntity) -> Long)? = null,
    saveSuccessDisplayMillis: Long = SaveSuccessDisplayMillis
) {
    val navController = rememberNavController()
    val context = LocalContext.current
    val healthRecordDao = remember {
        HealthDatabase.getInstance(context).healthRecordDao()
    }

    Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = HomeRoute,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(HomeRoute) {
                HomeScreen(
                    onRecordHealthData = {
                        healthViewModel.resetSession()
                        navController.navigate(VitalsRoute)
                    },
                    onDeleteAllDataConfirmed = healthRecordDao::deleteAll
                )
            }
            composable(VitalsRoute) {
                VitalsScreen(
                    heartRateState = healthViewModel.heartRateState,
                    respiratoryRateState = healthViewModel.respiratoryRateState,
                    onHeartRateStateChange = healthViewModel::updateHeartRate,
                    onRespiratoryRateStateChange = healthViewModel::updateRespiratoryRate,
                    onContinue = { navController.navigate(SymptomsRoute) }
                )
            }
            composable(SymptomsRoute) { navBackStackEntry ->
                val saveCoordinator = remember(
                    navBackStackEntry,
                    healthRecordDao,
                    insertHealthRecordOverride
                ) {
                    HealthSessionSaveCoordinator(
                        insertRecord = insertHealthRecordOverride ?: healthRecordDao::insert
                    )
                }
                val saveStatus by saveCoordinator.status.collectAsStateWithLifecycle()
                val coroutineScope = rememberCoroutineScope()

                BackHandler(
                    enabled = saveStatus is SessionSaveStatus.Saving ||
                        saveStatus is SessionSaveStatus.Saved
                ) {
                    // Keep this destination active through the write and brief success feedback.
                }

                LaunchedEffect(saveStatus) {
                    if (saveStatus is SessionSaveStatus.Saved) {
                        delay(saveSuccessDisplayMillis)
                        healthViewModel.resetSession()
                        navController.popBackStack(HomeRoute, inclusive = false)
                    }
                }

                fun saveCurrentSession() {
                    val sessionSnapshot = healthViewModel.session
                    coroutineScope.launch {
                        saveCoordinator.save(sessionSnapshot)
                    }
                }

                SymptomsContent(
                    session = healthViewModel.session,
                    saveState = saveStatus,
                    onRatingChange = healthViewModel::updateSymptomRating,
                    onUpload = ::saveCurrentSession,
                    onRetry = ::saveCurrentSession
                )
            }
        }
    }
}
