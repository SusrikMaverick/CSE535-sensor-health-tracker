package com.example.assignmentapp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
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

    Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = HomeRoute,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(HomeRoute) {
                HomeScreen(onContinue = { navController.navigate(VitalsRoute) })
            }
            composable(VitalsRoute) {
                VitalsScreen(onContinue = { navController.navigate(SymptomsRoute) })
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
