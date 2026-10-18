package com.playertwo.controlegithub

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class KanbanStoreTest {

    private class TestMemoryKanbanStore : KanbanStore {
        private val storage = mutableMapOf<String, MutableMap<String, KanbanColumn>>()

        override suspend fun load(accountLogin: String): Map<String, KanbanColumn> {
            return storage[accountLogin.lowercase()]?.toMap().orEmpty()
        }

        override suspend fun setColumn(
            accountLogin: String,
            issueIdentity: String,
            column: KanbanColumn
        ): Map<String, KanbanColumn> {
            val userMap = storage.getOrPut(accountLogin.lowercase()) { mutableMapOf() }
            userMap[issueIdentity.lowercase()] = column
            return userMap.toMap()
        }

        override suspend fun clear(accountLogin: String) {
            storage.remove(accountLogin.lowercase())
        }
    }

    @Test
    fun parseEntriesHandlesValidAndCorruptedInputs() {
        val entries = setOf(
            "owner/repo/1=TODO",
            "owner/repo/2=IN_PROGRESS",
            "owner/repo/3=IN_REVIEW",
            "owner/repo/4=DONE",
            "corrupted_no_separator",
            "corrupted_empty_val=",
            "=TODO",
            "owner/repo/5=INVALID_COLUMN",
            ""
        )
        val parsed = DataStoreKanbanStore.parseEntries(entries)

        assertEquals(4, parsed.size)
        assertEquals(KanbanColumn.TODO, parsed["owner/repo/1"])
        assertEquals(KanbanColumn.IN_PROGRESS, parsed["owner/repo/2"])
        assertEquals(KanbanColumn.IN_REVIEW, parsed["owner/repo/3"])
        assertEquals(KanbanColumn.DONE, parsed["owner/repo/4"])
    }

    @Test
    fun resolveKanbanColumnDefaultsBasedOnIssueState() {
        val openIssue = GitHubIssue(
            number = 10,
            title = "Open issue",
            state = GitHubIssueState.OPEN,
            repository = "owner/repo",
            author = "dev",
            assignees = emptyList(),
            labels = emptyList(),
            updatedAt = Instant.EPOCH
        )
        val closedIssue = openIssue.copy(number = 11, state = GitHubIssueState.CLOSED)

        // Without mapping:
        assertEquals(KanbanColumn.TODO, resolveKanbanColumn(openIssue, emptyMap()))
        assertEquals(KanbanColumn.DONE, resolveKanbanColumn(closedIssue, emptyMap()))

        // With explicit mapping:
        val mapping = mapOf("owner/repo/10" to KanbanColumn.IN_PROGRESS, "owner/repo/11" to KanbanColumn.IN_REVIEW)
        assertEquals(KanbanColumn.IN_PROGRESS, resolveKanbanColumn(openIssue, mapping))
        assertEquals(KanbanColumn.IN_REVIEW, resolveKanbanColumn(closedIssue, mapping))
    }

    @Test
    fun storeOperationsAndAccountIsolation() = runBlocking {
        val store = TestMemoryKanbanStore()

        // Empty load
        assertEquals(emptyMap<String, KanbanColumn>(), store.load("user1"))

        // Add to user1
        store.setColumn("user1", "owner/repo/1", KanbanColumn.IN_PROGRESS)
        assertEquals(mapOf("owner/repo/1" to KanbanColumn.IN_PROGRESS), store.load("user1"))

        // Update column for same issue
        store.setColumn("user1", "owner/repo/1", KanbanColumn.DONE)
        assertEquals(mapOf("owner/repo/1" to KanbanColumn.DONE), store.load("user1"))

        // Account isolation: user2 has separate space
        assertEquals(emptyMap<String, KanbanColumn>(), store.load("user2"))
        store.setColumn("user2", "owner/repo/1", KanbanColumn.TODO)
        assertEquals(mapOf("owner/repo/1" to KanbanColumn.TODO), store.load("user2"))
        assertEquals(mapOf("owner/repo/1" to KanbanColumn.DONE), store.load("user1"))

        // Clear user1
        store.clear("user1")
        assertEquals(emptyMap<String, KanbanColumn>(), store.load("user1"))
        assertEquals(mapOf("owner/repo/1" to KanbanColumn.TODO), store.load("user2"))
    }

    @Test
    fun clearKanbanBeforeLogoutHandlesExceptions() = runBlocking {
        val successStore = TestMemoryKanbanStore()
        assertTrue(clearKanbanBeforeLogout("user", successStore))

        val failingStore = object : KanbanStore {
            override suspend fun load(accountLogin: String) = emptyMap<String, KanbanColumn>()
            override suspend fun setColumn(accountLogin: String, issueIdentity: String, column: KanbanColumn) = emptyMap<String, KanbanColumn>()
            override suspend fun clear(accountLogin: String) = throw IllegalStateException("disk error")
        }
        assertFalse(clearKanbanBeforeLogout("user", failingStore))

        val cancellingStore = object : KanbanStore {
            override suspend fun load(accountLogin: String) = emptyMap<String, KanbanColumn>()
            override suspend fun setColumn(accountLogin: String, issueIdentity: String, column: KanbanColumn) = emptyMap<String, KanbanColumn>()
            override suspend fun clear(accountLogin: String) = throw CancellationException("cancelled")
        }
        var caughtCancellation = false
        try {
            clearKanbanBeforeLogout("user", cancellingStore)
        } catch (e: CancellationException) {
            caughtCancellation = true
        }
        assertTrue(caughtCancellation)
    }
}
