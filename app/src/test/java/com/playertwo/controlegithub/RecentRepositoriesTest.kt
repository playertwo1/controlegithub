package com.playertwo.controlegithub

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RecentRepositoriesTest {
    @Test
    fun `applyRecentVisit orders by MRU and deduplicates case-insensitively without exceeding 10 items`() {
        var list = emptyList<RecentRepositoryEntry>()

        for (index in 1..12) {
            list = applyRecentVisit(
                current = list,
                entry = RecentRepositoryEntry(
                    id = index.toLong(),
                    owner = "octocat",
                    name = "repo-$index",
                    fullName = "octocat/repo-$index"
                )
            )
        }

        assertEquals(MAX_RECENT_REPOSITORIES, list.size)
        assertEquals("octocat/repo-12", list.first().fullName)
        assertEquals("octocat/repo-3", list.last().fullName)

        // Revisit repo-5 with different casing moves it to index 0 without duplicating
        list = applyRecentVisit(
            current = list,
            entry = RecentRepositoryEntry(
                id = 5L,
                owner = "OctoCat",
                name = "Repo-5",
                fullName = "OctoCat/Repo-5",
                isPrivate = true
            )
        )

        assertEquals(MAX_RECENT_REPOSITORIES, list.size)
        assertEquals("OctoCat/Repo-5", list.first().fullName)
        assertEquals(5L, list.first().id)
        assertTrue(list.first().isPrivate)
        assertEquals(1, list.count { it.fullName.equals("octocat/repo-5", ignoreCase = true) })
    }

    @Test
    fun `applyRecentVisit preserves positive id when revisited with unresolved id and updates unresolved id when resolved`() {
        val initial = applyRecentVisit(
            current = emptyList(),
            entry = RecentRepositoryEntry(
                id = UNRESOLVED_GITHUB_REPOSITORY_ID,
                owner = "octocat",
                name = "hello-world",
                fullName = "octocat/hello-world"
            )
        )
        assertEquals(UNRESOLVED_GITHUB_REPOSITORY_ID, initial.single().id)

        val resolved = applyRecentVisit(
            current = initial,
            entry = RecentRepositoryEntry(
                id = 42L,
                owner = "octocat",
                name = "hello-world",
                fullName = "octocat/hello-world",
                isPrivate = true
            )
        )
        assertEquals(42L, resolved.single().id)
        assertTrue(resolved.single().isPrivate)

        val revisitedUnresolved = applyRecentVisit(
            current = resolved,
            entry = RecentRepositoryEntry(
                id = UNRESOLVED_GITHUB_REPOSITORY_ID,
                owner = "octocat",
                name = "hello-world",
                fullName = "octocat/hello-world",
                isPrivate = true
            )
        )
        assertEquals(42L, revisitedUnresolved.single().id)
    }

    @Test
    fun `applyRecentVisit ignores invalid entries`() {
        val valid = listOf(
            RecentRepositoryEntry(
                id = 1L,
                owner = "octocat",
                name = "valid",
                fullName = "octocat/valid"
            )
        )

        assertEquals(
            valid,
            applyRecentVisit(
                valid,
                RecentRepositoryEntry(id = 2L, owner = "octocat", name = "other", fullName = "wrong/other")
            )
        )
        assertEquals(
            valid,
            applyRecentVisit(
                valid,
                RecentRepositoryEntry(id = -1L, owner = "octocat", name = "neg", fullName = "octocat/neg")
            )
        )
        assertEquals(
            valid,
            applyRecentVisit(
                valid,
                RecentRepositoryEntry(id = 3L, owner = "octo cat", name = "space", fullName = "octo cat/space")
            )
        )
    }

    @Test
    fun `encode and decode recent repositories round trip and ignore malformed items`() {
        val entries = listOf(
            RecentRepositoryEntry(id = 10L, owner = "octocat", name = "first", fullName = "octocat/first", isPrivate = true),
            RecentRepositoryEntry(id = 20L, owner = "octocat", name = "second", fullName = "octocat/second", isPrivate = false)
        )

        val encoded = encodeRecentRepositories(entries)
        val decoded = decodeRecentRepositories(encoded)
        assertEquals(entries, decoded)
        assertEquals(emptyList<RecentRepositoryEntry>(), decodeRecentRepositories("not-json"))
    }

    @Test
    fun `in memory store isolates accounts and clears on logout`() = runBlocking {
        val store = InMemoryRecentRepositoriesStore()
        val aliceEntry = RecentRepositoryEntry(id = 1L, owner = "alice", name = "alpha", fullName = "alice/alpha")
        val bobEntry = RecentRepositoryEntry(id = 2L, owner = "bob", name = "beta", fullName = "bob/beta")

        store.recordVisit("Alice", aliceEntry)
        store.recordVisit("bob", bobEntry)

        assertEquals(listOf(aliceEntry), store.load("alice"))
        assertEquals(listOf(bobEntry), store.load("BOB"))

        assertTrue(clearRecentRepositoriesBeforeLogout("ALICE", store))
        assertTrue(store.load("alice").isEmpty())
        assertEquals(listOf(bobEntry), store.load("bob"))
    }
}
