package com.playertwo.controlegithub

import android.view.WindowInsets
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class NavigationTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun missingOAuthConfigurationIsDisclosed() {
        if (BuildConfig.GITHUB_OAUTH_CLIENT_ID.isBlank()) {
            compose.onNodeWithText("Integração GitHub indisponível · app OAuth não configurado")
                .assertIsDisplayed()
        } else {
            compose.onNodeWithText("Client ID configurado · login será habilitado em próxima etapa")
                .assertIsDisplayed()
        }
    }

    @Test fun searchSurvivesOpeningRepositoryAndBack() {
        compose.onNodeWithText("Explorar demonstração").performClick()
        compose.onNodeWithText("Repos").performClick()
        compose.onNodeWithText("Buscar nome ou linguagem").performTextInput("Python")
        compose.onNodeWithText("ideias_standard").performClick()
        compose.onNodeWithText("Visão geral").assertIsDisplayed()
        compose.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        compose.waitForIdle()
        compose.onNodeWithText("Repositórios").assertIsDisplayed()
        compose.onNode(hasSetTextAction()).assertTextContains("Python")
    }

    @Test fun eachFloatingDestinationSelectsItsPage() {
        compose.onNodeWithText("Explorar demonstração").performClick()

        val destinations = listOf(
            "Início" to "Seu centro de comando",
            "Repos" to "Repositórios",
            "Trabalho" to "Seu trabalho",
            "Avisos" to "Notificações"
        )
        destinations.forEach { (destination, heading) ->
            compose.onNodeWithText(destination).performClick()
            compose.onNodeWithText(destination).assertIsSelected()
            compose.onNodeWithText(heading).assertIsDisplayed()
        }
    }

    @Test fun selectedDestinationSurvivesActivityRecreation() {
        compose.onNodeWithText("Explorar demonstração").performClick()
        compose.onNodeWithText("Repos").performClick()

        compose.activityRule.scenario.recreate()
        compose.waitForIdle()

        compose.onNodeWithText("Repos").assertIsSelected()
        compose.onNodeWithText("Repositórios").assertIsDisplayed()
    }

    @Test fun dockHidesWithKeyboardAndReturnsAfterDismissal() {
        compose.onNodeWithText("Explorar demonstração").performClick()
        compose.onNodeWithText("Repos").performClick()
        compose.onNodeWithText("Buscar nome ou linguagem").performClick()

        compose.waitUntil(5_000) {
            compose.activity.window.decorView.rootWindowInsets?.isVisible(WindowInsets.Type.ime()) == true
        }
        compose.onNodeWithText("Repos").assertDoesNotExist()

        compose.runOnIdle { compose.activity.window.insetsController?.hide(WindowInsets.Type.ime()) }
        compose.waitUntil(5_000) {
            compose.activity.window.decorView.rootWindowInsets?.isVisible(WindowInsets.Type.ime()) == false
        }
        compose.waitUntil(5_000) {
            compose.onAllNodesWithText("Repos").fetchSemanticsNodes().isNotEmpty()
        }
        val reposNode = compose.onNodeWithText("Repos")
        reposNode.assertIsDisplayed()

        val root = compose.activity.window.decorView
        val gestureInset = root.rootWindowInsets
            ?.getInsets(WindowInsets.Type.navigationBars())
            ?.bottom ?: 0
        val dockItemBottom = reposNode.fetchSemanticsNode().boundsInRoot.bottom
        assertTrue(
            "Dock item overlaps the system navigation area",
            dockItemBottom <= root.height - gestureInset
        )
    }
}
