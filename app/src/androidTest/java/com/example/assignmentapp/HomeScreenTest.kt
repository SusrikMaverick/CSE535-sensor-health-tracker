package com.example.assignmentapp

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
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
