package com.playertwo.controlegithub

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.util.concurrent.atomic.AtomicLong
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SQLiteGitHubCacheStoreTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val dbName = "test_controlegithub_cache.db"
    private val clock = AtomicLong(1_000_000L)
    private lateinit var store: SQLiteGitHubCacheStore

    @Before
    fun setUp() {
        context.deleteDatabase(dbName)
        store = SQLiteGitHubCacheStore(context, dbName = dbName, nowMillis = { clock.get() })
    }

    @After
    fun tearDown() {
        store.close()
        context.deleteDatabase(dbName)
    }

    @Test
    fun putAndGet_storesAndRetrievesPayload() {
        val payload = """[{"id": 1, "name": "repo-one"}]"""
        val saved = store.put("alice", "/user/repos?page=1", payload, statusCode = 200, ttlMillis = 10_000L)
        assertTrue(saved)

        val retrieved = store.get("alice", "/user/repos?page=1")
        assertNotNull(retrieved)
        assertEquals("alice", retrieved?.accountLogin)
        assertEquals("/user/repos?page=1", retrieved?.resourceKey)
        assertEquals(payload, retrieved?.payload)
        assertEquals(200, retrieved?.statusCode)
        assertEquals(1_000_000L, retrieved?.createdAtMillis)
        assertEquals(1_010_000L, retrieved?.expiresAtMillis)
    }

    @Test
    fun get_expiredEntry_returnsNull() {
        store.put("alice", "/user/repos?page=1", "[]", statusCode = 200, ttlMillis = 5_000L)

        // Advance clock past expiration
        clock.set(1_006_000L)

        val retrieved = store.get("alice", "/user/repos?page=1")
        assertNull(retrieved)
    }

    @Test
    fun accountIsolation_userBCannotAccessUserAEntries() {
        val aliceData = """[{"name": "alice-private-repo"}]"""
        val bobData = """[{"name": "bob-repo"}]"""

        store.put("alice", "/user/repos", aliceData)
        store.put("bob", "/user/repos", bobData)

        val aliceCached = store.get("alice", "/user/repos")
        val bobCached = store.get("bob", "/user/repos")

        assertEquals(aliceData, aliceCached?.payload)
        assertEquals(bobData, bobCached?.payload)

        // Querying with non-existent or other accounts returns null
        assertNull(store.get("charlie", "/user/repos"))
    }

    @Test
    fun clearAccount_removesOnlyTargetAccountData() {
        store.put("alice", "/user/repos", "[1]")
        store.put("bob", "/user/repos", "[2]")

        store.clear("alice")

        assertNull(store.get("alice", "/user/repos"))
        assertNotNull(store.get("bob", "/user/repos"))
    }

    @Test
    fun clearAll_removesAllEntries() {
        store.put("alice", "/user/repos", "[1]")
        store.put("bob", "/user/repos", "[2]")

        store.clearAll()

        assertNull(store.get("alice", "/user/repos"))
        assertNull(store.get("bob", "/user/repos"))
    }

    @Test
    fun pruneAccount_capsEntriesAtMaximumLimit() {
        // Insert more than MAX_ENTRIES_PER_ACCOUNT (300)
        for (i in 1..310) {
            clock.addAndGet(100L)
            store.put("alice", "/resource/$i", """{"item": $i}""")
        }

        // Oldest entries 1..10 should have been pruned
        assertNull(store.get("alice", "/resource/1"))
        assertNull(store.get("alice", "/resource/10"))

        // Latest entries 300..310 must be preserved
        assertNotNull(store.get("alice", "/resource/300"))
        assertNotNull(store.get("alice", "/resource/310"))
    }

    @Test
    fun sensitiveEndpointsAndTokens_areRejectedByPut() {
        val tokenPayload = """{"token": "secret", "access_token": "ghp_123456"}"""
        val rejected1 = store.put("alice", "/user/repos", tokenPayload)
        assertFalse(rejected1)
        assertNull(store.get("alice", "/user/repos"))

        val loginKeyRejected = store.put("alice", "/login/oauth/access_token", "{}")
        assertFalse(loginKeyRejected)

        val actionsLogRejected = store.put("alice", "/repos/o/r/actions/jobs/1/logs", "some log")
        assertFalse(actionsLogRejected)
    }

    @Test
    fun putWithEtag_and_updateTimestamp_preservesEtagAndUpdatesExpiration() {
        store.put("alice", "/user/repos", "[1]", etag = "\"etag_1\"", ttlMillis = 10_000L)
        val initial = store.get("alice", "/user/repos")
        assertEquals("\"etag_1\"", initial?.etag)
        assertEquals(1_000_000L, initial?.createdAtMillis)

        clock.set(1_005_000L)
        val updated = store.updateTimestamp("alice", "/user/repos", revalidatedAtMillis = 1_005_000L, etag = "\"etag_2\"")
        assertTrue(updated)

        val retrieved = store.get("alice", "/user/repos")
        assertNotNull(retrieved)
        assertEquals("\"etag_2\"", retrieved?.etag)
        assertEquals(1_005_000L, retrieved?.createdAtMillis)
        assertEquals(1_005_000L + GitHubCacheStore.DEFAULT_TTL_MILLIS, retrieved?.expiresAtMillis)
        assertEquals("[1]", retrieved?.payload)
    }
}
