package com.playertwo.controlegithub

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class NavigationTest {
    @get:Rule val compose = createAndroidComposeRule<NavigationTestActivity>()

    @Test fun missingOAuthConfigurationIsDisclosed() {
        if (BuildConfig.GITHUB_OAUTH_CLIENT_ID.isBlank()) {
            compose.onNodeWithText("Conectar ao GitHub").performClick()
            compose.onNodeWithText("Integração GitHub indisponível · app OAuth não configurado")
                .assertIsDisplayed()
        }
    }

    @Test fun repositoriesTabRequiresAnAccountAndDoesNotShowDemoRows() {
        compose.onNodeWithText("Explorar demonstração").performClick()
        compose.onNodeWithText("Repos").performClick()
        compose.onNodeWithText("Seus repositórios").assertIsDisplayed()
        compose.onNodeWithText("Conecte sua conta GitHub para ver os repositórios que ela pode acessar.")
            .assertIsDisplayed()
        compose.onNodeWithText("ideias_standard").assertDoesNotExist()
    }

    @Test fun eachFloatingDestinationSelectsItsPage() {
        compose.onNodeWithText("Explorar demonstração").performClick()

        val destinations = listOf(
            "Início" to "Seu centro de comando",
            "Repos" to "Seus repositórios",
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
        compose.onNodeWithText("Seus repositórios").assertIsDisplayed()
    }
}
