package com.playertwo.controlegithub

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test

class MarkdownEditorUiTest {
    @get:Rule
    val compose = createComposeRule()

    @Test fun previewRendersWithTheReadingComponent() {
        compose.setContent {
            var text by remember { mutableStateOf("") }
            MarkdownEditor(
                value = text,
                onValueChange = { text = it },
                label = "Corpo",
                fieldTag = "editor_body"
            )
        }
        compose.onNodeWithTag("editor_body").performTextInput("**negrito**")
        compose.onNodeWithTag("editor_body_preview").performClick()
        compose.waitUntil(10_000) {
            compose.onAllNodesWithTag("editor_body_rendered").fetchSemanticsNodes().isNotEmpty() &&
                compose.onAllNodesWithText("**negrito**").fetchSemanticsNodes().isEmpty() &&
                compose.onAllNodesWithText("negrito").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("negrito").assertIsDisplayed()
    }

    @Test fun draftRestoresAfterLeavingTheEditor() {
        val store = MemoryMarkdownDraftStore()
        val key = markdownDraftKey("conta", "acme/app", "issue-1-comment")
        val shown = androidx.compose.runtime.mutableStateOf(true)
        compose.setContent {
            if (shown.value) EditorHarness(store, key)
        }
        compose.onNodeWithTag("editor_body").performTextInput("rascunho local")
        compose.waitUntil(10_000) { runBlocking { store.load(key) } == "rascunho local" }
        shown.value = false
        compose.waitForIdle()
        shown.value = true
        compose.waitUntil(10_000) {
            compose.onAllNodesWithText("rascunho local").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("rascunho local").assertIsDisplayed()
    }
}

@androidx.compose.runtime.Composable
private fun EditorHarness(store: MarkdownDraftStore, key: String) {
    CompositionLocalProvider(LocalMarkdownDraftStore provides store) {
        var text by remember { mutableStateOf("") }
        MarkdownEditor(
            value = text,
            onValueChange = { text = it },
            label = "Corpo",
            fieldTag = "editor_body",
            draftKey = key
        )
    }
}
