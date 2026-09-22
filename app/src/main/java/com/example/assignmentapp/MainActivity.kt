package com.example.assignmentapp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.assignmentapp.data.HealthDatabase
import com.example.assignmentapp.data.MeasurementState
import com.example.assignmentapp.ui.HealthViewModel
import com.example.assignmentapp.ui.HomeScreen
import com.example.assignmentapp.ui.SymptomsScreen
import com.example.assignmentapp.ui.VitalsScreen
import com.example.assignmentapp.ui.theme.AssignmentAppTheme

private const val HomeRoute = "home"
private const val VitalsRoute = "vitals"
private const val SymptomsRoute = "symptoms"

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
private fun AssignmentAppNavHost() {
    val navController = rememberNavController()
    val healthViewModel: HealthViewModel = viewModel()
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
                    onRespiratoryRetry = {
                        healthViewModel.updateRespiratoryRate(MeasurementState.Idle)
                    },
                    onContinue = { navController.navigate(SymptomsRoute) }
                )
            }
            composable(SymptomsRoute) {
                SymptomsScreen(
                    onContinue = {
                        navController.popBackStack(HomeRoute, inclusive = false)
                    }
                )
            }
        }
    }
}
