package com.playertwo.controlegithub

import android.view.WindowInsets
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
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

    @Test fun dockExposesAccessibleTabsInOrderWithLargeTouchTargets() {
        compose.onNodeWithText("Explorar demonstração").performClick()

        val labels = listOf("Início", "Repos", "Trabalho", "Avisos")
        val density = compose.activity.resources.displayMetrics.density
        val centers = labels.map { label ->
            val node = compose.onNodeWithText(label)
            node.assertIsDisplayed()
            node.assertHasClickAction()
            node.assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab))

            val bounds = node.fetchSemanticsNode().boundsInRoot
            assertTrue("$label target is narrower than 48dp", bounds.width / density >= 48f)
            assertTrue("$label target is shorter than 48dp", bounds.height / density >= 48f)
            bounds.center.x
        }
        assertTrue("Dock destinations are not in visual order", centers.zipWithNext().all { (left, right) -> left < right })

        labels.forEach { selected ->
            compose.onNodeWithText(selected).performClick()
            labels.forEach { label ->
                if (label == selected) compose.onNodeWithText(label).assertIsSelected()
                else compose.onNodeWithText(label).assertIsNotSelected()
            }
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
        val searchField = compose.onNodeWithText("Buscar nome ou linguagem")
        searchField.assertIsDisplayed()
        searchField.performTextInput("Python")
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
