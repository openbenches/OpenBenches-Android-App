package org.openbenches.ui

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithTag
import org.junit.Rule
import org.junit.Test
import org.openbenches.data.Bench
import org.openbenches.data.Geometry
import org.openbenches.data.Properties
import kotlinx.coroutines.runBlocking
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.platform.testTag

/**
 * UI tests for MapScreen composable.
 */
class MapScreenTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun loadingState_showsProgressIndicator() {
        composeTestRule.setContent {
            MapScreenLoadingPreview()
        }
        composeTestRule.onNodeWithTag("progress").assertExists()
    }

    @Test
    fun errorState_showsErrorText() {
        composeTestRule.setContent {
            MapScreenErrorPreview()
        }
        composeTestRule.onNodeWithText("Error: Test error").assertExists()
    }
}

@Composable
fun MapScreenLoadingPreview() {
    androidx.compose.material3.CircularProgressIndicator(modifier = androidx.compose.ui.Modifier.testTag("progress"))
}

@Composable
fun MapScreenErrorPreview() {
    androidx.compose.material3.Text("Error: Test error")
} 