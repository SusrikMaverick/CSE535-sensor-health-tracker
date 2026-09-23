package com.example.assignmentapp

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.assignmentapp.ui.HomeScreen
import com.example.assignmentapp.ui.theme.AssignmentAppTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HomeNavigationTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun recordHealthDataNavigatesToVitals() {
        composeRule.onNodeWithText("Record health data").performClick()

        composeRule.onNodeWithText("Vital signs").assertIsDisplayed()
    }
}

@RunWith(AndroidJUnit4::class)
class HomeDeletionTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun cancelDoesNotDeleteRecords() {
        var deleteCalls = 0
        setHomeContent {
            deleteCalls += 1
            1
        }

        openDeleteDialog()
        composeRule.onNodeWithText("Cancel").performClick()

        composeRule.runOnIdle { assertEquals(0, deleteCalls) }
    }

    @Test
    fun deletionSuccessShowsFeedback() {
        setHomeContent { 1 }

        confirmDeletion()

        composeRule.onNodeWithText("All recorded data was deleted.").assertIsDisplayed()
    }

    @Test
    fun emptyDatabaseShowsFeedback() {
        setHomeContent { 0 }

        confirmDeletion()

        composeRule.onNodeWithText("There is no recorded data to delete.").assertIsDisplayed()
    }

    @Test
    fun deletionFailureShowsFeedback() {
        setHomeContent { error("Database failure") }

        confirmDeletion()

        composeRule.onNodeWithText("Recorded data couldn't be deleted. Try again.")
            .assertIsDisplayed()
    }

    @Test
    fun compactAndLandscapeLargeTextLayoutsKeepBottomActionReachable() {
        var landscape by mutableStateOf(false)
        composeRule.setContent {
            AssignmentAppTheme {
                CompositionLocalProvider(
                    LocalDensity provides Density(density = 1f, fontScale = 2f)
                ) {
                    Box(
                        modifier = Modifier.size(
                            width = if (landscape) 600.dp else 320.dp,
                            height = if (landscape) 320.dp else 400.dp
                        )
                    ) {
                        HomeScreen(
                            onRecordHealthData = { },
                            onDeleteAllDataConfirmed = { 0 }
                        )
                    }
                }
            }
        }

        composeRule.onNodeWithText("Delete all recorded data")
            .performScrollTo()
            .assertIsDisplayed()
        composeRule.runOnIdle { landscape = true }
        composeRule.onNodeWithText("Delete all recorded data")
            .performScrollTo()
            .assertIsDisplayed()
    }

    private fun setHomeContent(deleteAll: suspend () -> Int) {
        composeRule.setContent {
            AssignmentAppTheme {
                HomeScreen(
                    onRecordHealthData = { },
                    onDeleteAllDataConfirmed = deleteAll
                )
            }
        }
    }

    private fun openDeleteDialog() {
        composeRule.onNodeWithText("Delete all recorded data").performClick()
    }

    private fun confirmDeletion() {
        openDeleteDialog()
        composeRule.onNodeWithText("Delete").performClick()
    }
}
