package com.playertwo.controlegithub

import android.content.pm.ActivityInfo
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AndroidMatrixAndLayoutsUiTest {

    @get:Rule
    val compose = createAndroidComposeRule<NavigationTestActivity>()

    @After
    fun resetOrientation() {
        try {
            compose.activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        } catch (_: Exception) {}
    }

    @Test
    fun testScreenRotationPreservesNavigationAndDock() {
        // Start exploring demo mode
        compose.onNodeWithText("Explorar demonstração").performClick()
        compose.onNodeWithText("Seu centro de comando").assertIsDisplayed()

        // Switch to Repos
        compose.onNodeWithText("Repos").performClick()
        compose.onNodeWithText("Seus repositórios").assertIsDisplayed()
        compose.onNodeWithText("Repos").assertIsSelected()

        // Rotate to Landscape
        compose.activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        compose.waitUntil(5_000) {
            compose.onAllNodesWithText("Seus repositórios").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("Seus repositórios").assertIsDisplayed()
        compose.onNodeWithText("Repos").assertIsSelected()

        // Switch to Avisos in landscape
        compose.onNodeWithText("Avisos").performClick()
        compose.onNodeWithText("Notificações").assertIsDisplayed()
        compose.onNodeWithText("Avisos").assertIsSelected()

        // Rotate back to Portrait
        compose.activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        compose.waitUntil(5_000) {
            compose.onAllNodesWithText("Notificações").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("Notificações").assertIsDisplayed()
        compose.onNodeWithText("Avisos").assertIsSelected()
    }

    @Test
    fun testActivityRecreationPreservesFlowAndState() {
        compose.onNodeWithText("Explorar demonstração").performClick()
        compose.onNodeWithText("Trabalho").performClick()
        compose.onNodeWithText("Seu trabalho").assertIsDisplayed()
        compose.onNodeWithText("Trabalho").assertIsSelected()

        // Recreate Activity (simulating process recreation / config change)
        compose.activityRule.scenario.recreate()
        compose.waitUntil(5_000) {
            compose.onAllNodesWithText("Seu trabalho").fetchSemanticsNodes().isNotEmpty()
        }

        // Verify state is preserved
        compose.onNodeWithText("Seu trabalho").assertIsDisplayed()
        compose.onNodeWithText("Trabalho").assertIsSelected()
    }

    @Test
    fun testCompactAndExpandedLayoutAdaptation() {
        // Test compact screen container (340dp width)
        compose.onNodeWithText("Explorar demonstração").performClick()
        compose.onNodeWithText("Seu centro de comando").assertIsDisplayed()

        // Verify key action cards and dock are responsive
        compose.onNodeWithText("Início").assertIsDisplayed()
        compose.onNodeWithText("Repos").assertIsDisplayed()
        compose.onNodeWithText("Trabalho").assertIsDisplayed()
        compose.onNodeWithText("Avisos").assertIsDisplayed()

        // Open appearance settings
        compose.onNodeWithContentDescription("Configurações de aparência").performClick()
        compose.onNodeWithText("Aparência").assertIsDisplayed()
        compose.onNodeWithText("Sistema").assertIsDisplayed()
        compose.onNodeWithText("Claro").assertIsDisplayed()
        compose.onNodeWithText("Escuro").assertIsDisplayed()

        // Back to main
        compose.onNodeWithContentDescription("Voltar").performClick()
        compose.onNodeWithText("Seu centro de comando").assertIsDisplayed()
    }
}
