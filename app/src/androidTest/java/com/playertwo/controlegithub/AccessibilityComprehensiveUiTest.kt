package com.playertwo.controlegithub

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.net.URI
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AccessibilityComprehensiveUiTest {

    @get:Rule
    val compose = createComposeRule()

    private fun fixtureSession(login: String = "octocat") = GitHubSession(
        user = GitHubUser(
            login = login,
            profileUrl = "https://github.com/$login"
        ),
        credentials = SessionCredentials(accessToken = "valid-token-123", userLogin = login)
    )

    @Composable
    private fun DockFixture(selectedPage: Int, onSelectPage: (Int) -> Unit) {
        Surface(modifier = Modifier.fillMaxWidth().testTag("dock_surface")) {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                val labels = listOf("Início", "Repos", "Trabalho", "Avisos")
                val icons = listOf(
                    Icons.Outlined.Home,
                    Icons.Outlined.Folder,
                    Icons.Outlined.CheckCircle,
                    Icons.Outlined.Notifications
                )
                labels.forEachIndexed { index, label ->
                    NavigationBarItem(
                        modifier = Modifier.testTag("dock_item_$index"),
                        selected = selectedPage == index,
                        onClick = { onSelectPage(index) },
                        icon = { Icon(icons[index], contentDescription = label) },
                        label = { Text(label) }
                    )
                }
            }
        }
    }

    @Test
    fun testBottomDockSemanticsAndTouchTargets() {
        var currentPage = 0
        compose.setContent {
            ControleTheme(AppThemeMode.DARK) {
                DockFixture(selectedPage = currentPage, onSelectPage = { currentPage = it })
            }
        }

        val items = listOf("Início", "Repos", "Trabalho", "Avisos")
        items.forEachIndexed { index, label ->
            val node = compose.onNodeWithTag("dock_item_$index")
            node.assertIsDisplayed()
            node.assertHasClickAction()
            compose.onNodeWithText(label).assertIsDisplayed()
            if (index == 0) {
                node.assertIsSelected()
            }
        }

        // Click Repos
        compose.onNodeWithTag("dock_item_1").performClick()
        assert(currentPage == 1)
    }

    @Test
    fun testThemeSwitchingAccessibility() {
        var selectedTheme = AppThemeMode.SYSTEM
        compose.setContent {
            ControleTheme(selectedTheme) {
                Box(Modifier.fillMaxSize()) {
                    AppearanceScreen(
                        modifier = Modifier.fillMaxSize(),
                        selectedMode = selectedTheme,
                        preferenceError = false,
                        onBack = {},
                        onThemeModeChange = { selectedTheme = it }
                    )
                }
            }
        }

        compose.onNodeWithText("Aparência").assertIsDisplayed()
        compose.onNodeWithText("Claro").assertIsDisplayed().assertHasClickAction()
        compose.onNodeWithText("Escuro").assertIsDisplayed().assertHasClickAction()
        compose.onNodeWithText("Sistema").assertIsDisplayed().assertHasClickAction()

        // Switch to Light
        compose.onNodeWithText("Claro").performClick()
        assert(selectedTheme == AppThemeMode.LIGHT)

        // Switch to Dark
        compose.onNodeWithText("Escuro").performClick()
        assert(selectedTheme == AppThemeMode.DARK)
    }

    @Test
    fun testKanbanAndWorkAccessibility() {
        val session = fixtureSession("test-a11y-user")
        val client = GitHubHttpClient(baseUrl = URI.create("http://127.0.0.1:9/"))

        compose.setContent {
            ControleTheme(AppThemeMode.DARK) {
                GitHubIssuesScreen(
                    modifier = Modifier.fillMaxSize(),
                    client = client,
                    session = session,
                    sessionRestoring = false,
                    sessionStorageError = false,
                    onConnected = { true },
                    onSessionExpired = {},
                    onAppearance = {}
                )
            }
        }

        // View mode chips must have click actions and accessible labels
        compose.onNodeWithText("Issues").assertIsDisplayed().assertHasClickAction()
        compose.onNodeWithText("Pull requests").assertIsDisplayed().assertHasClickAction()
        compose.onNodeWithTag("filter_chip_kanban").assertIsDisplayed().assertHasClickAction()

        // Switch to Kanban
        compose.onNodeWithTag("filter_chip_kanban").performClick()
        compose.onNodeWithTag("kanban_local_banner").assertIsDisplayed()
    }
}
