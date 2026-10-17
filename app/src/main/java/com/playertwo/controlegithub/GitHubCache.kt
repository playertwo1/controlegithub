package com.playertwo.controlegithub

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

internal data class CachedResponse(
    val accountLogin: String,
    val resourceKey: String,
    val payload: String,
    val statusCode: Int,
    val createdAtMillis: Long,
    val expiresAtMillis: Long,
    val etag: String? = null
)

internal interface GitHubCacheStore {
    fun get(accountLogin: String, resourceKey: String): CachedResponse?
    fun put(
        accountLogin: String,
        resourceKey: String,
        payload: String,
        statusCode: Int = 200,
        ttlMillis: Long = DEFAULT_TTL_MILLIS,
        etag: String? = null
    ): Boolean
    fun updateTimestamp(
        accountLogin: String,
        resourceKey: String,
        revalidatedAtMillis: Long,
        ttlMillis: Long = DEFAULT_TTL_MILLIS,
        etag: String? = null
    ): Boolean
    fun clear(accountLogin: String): Boolean
    fun clearAll(): Boolean

    companion object {
        const val DEFAULT_TTL_MILLIS = 24 * 60 * 60 * 1000L // 24 hours
        const val MAX_ENTRIES_PER_ACCOUNT = 300
        const val MAX_PAYLOAD_BYTES = 1024 * 1024 // 1 MB per payload
    }
}

internal fun isCacheableResource(resourceKey: String, payload: String): Boolean {
    val normalizedKey = resourceKey.trim().lowercase(Locale.ROOT)
    if (normalizedKey.contains("/login") ||
        normalizedKey.contains("/oauth") ||
        normalizedKey.contains("/device") ||
        normalizedKey.contains("/authorizations") ||
        normalizedKey.contains("/tokens")
    ) {
        return false
    }
    if (normalizedKey.contains("/actions/jobs/") && normalizedKey.contains("/logs")) {
        return false
    }
    if (normalizedKey.contains("/actions/runs/") && normalizedKey.contains("/logs")) {
        return false
    }
    if (payload.contains("\"access_token\"") ||
        payload.contains("\"refresh_token\"") ||
        payload.contains("\"client_secret\"")
    ) {
        return false
    }
    if (payload.length > GitHubCacheStore.MAX_PAYLOAD_BYTES) {
        return false
    }
    return true
}

internal class SQLiteGitHubCacheStore(
    context: Context,
    dbName: String = "controlegithub_cache.db",
    private val nowMillis: () -> Long = System::currentTimeMillis
) : SQLiteOpenHelper(context, dbName, null, DATABASE_VERSION), GitHubCacheStore {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS response_cache (
                account_login TEXT NOT NULL,
                resource_key TEXT NOT NULL,
                payload TEXT NOT NULL,
                status_code INTEGER NOT NULL,
                created_at INTEGER NOT NULL,
                expires_at INTEGER NOT NULL,
                etag TEXT,
                PRIMARY KEY (account_login, resource_key)
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_cache_account ON response_cache (account_login)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) {
            try {
                db.execSQL("ALTER TABLE response_cache ADD COLUMN etag TEXT")
            } catch (_: Exception) {
                db.execSQL("DROP TABLE IF EXISTS response_cache")
                onCreate(db)
            }
        }
    }

    override fun get(accountLogin: String, resourceKey: String): CachedResponse? {
        val cleanAccount = accountLogin.trim().lowercase(Locale.ROOT)
        val cleanKey = resourceKey.trim()
        val now = nowMillis()
        return try {
            val cursor = readableDatabase.query(
                TABLE_NAME,
                arrayOf(COL_ACCOUNT, COL_KEY, COL_PAYLOAD, COL_STATUS, COL_CREATED, COL_EXPIRES, COL_ETAG),
                "$COL_ACCOUNT = ? AND $COL_KEY = ? AND $COL_EXPIRES > ?",
                arrayOf(cleanAccount, cleanKey, now.toString()),
                null, null, null
            )
            cursor.use {
                if (it.moveToFirst()) {
                    CachedResponse(
                        accountLogin = it.getString(0),
                        resourceKey = it.getString(1),
                        payload = it.getString(2),
                        statusCode = it.getInt(3),
                        createdAtMillis = it.getLong(4),
                        expiresAtMillis = it.getLong(5),
                        etag = it.getString(6)
                    )
                } else null
            }
        } catch (_: Exception) {
            null
        }
    }

    override fun put(
        accountLogin: String,
        resourceKey: String,
        payload: String,
        statusCode: Int,
        ttlMillis: Long,
        etag: String?
    ): Boolean {
        val cleanAccount = accountLogin.trim().lowercase(Locale.ROOT)
        val cleanKey = resourceKey.trim()
        if (!isCacheableResource(cleanKey, payload)) return false

        val now = nowMillis()
        val expires = now + ttlMillis
        return try {
            val db = writableDatabase
            val values = ContentValues().apply {
                put(COL_ACCOUNT, cleanAccount)
                put(COL_KEY, cleanKey)
                put(COL_PAYLOAD, payload)
                put(COL_STATUS, statusCode)
                put(COL_CREATED, now)
                put(COL_EXPIRES, expires)
                put(COL_ETAG, etag)
            }
            db.insertWithOnConflict(TABLE_NAME, null, values, SQLiteDatabase.CONFLICT_REPLACE)
            pruneAccount(db, cleanAccount)
            true
        } catch (_: Exception) {
            false
        }
    }

    override fun updateTimestamp(
        accountLogin: String,
        resourceKey: String,
        revalidatedAtMillis: Long,
        ttlMillis: Long,
        etag: String?
    ): Boolean {
        val cleanAccount = accountLogin.trim().lowercase(Locale.ROOT)
        val cleanKey = resourceKey.trim()
        val expires = revalidatedAtMillis + ttlMillis
        return try {
            val values = ContentValues().apply {
                put(COL_CREATED, revalidatedAtMillis)
                put(COL_EXPIRES, expires)
                if (etag != null) put(COL_ETAG, etag)
            }
            writableDatabase.update(
                TABLE_NAME,
                values,
                "$COL_ACCOUNT = ? AND $COL_KEY = ?",
                arrayOf(cleanAccount, cleanKey)
            ) > 0
        } catch (_: Exception) {
            false
        }
    }

    override fun clear(accountLogin: String): Boolean {
        val cleanAccount = accountLogin.trim().lowercase(Locale.ROOT)
        return try {
            writableDatabase.delete(TABLE_NAME, "$COL_ACCOUNT = ?", arrayOf(cleanAccount))
            true
        } catch (_: Exception) {
            false
        }
    }

    override fun clearAll(): Boolean {
        return try {
            writableDatabase.delete(TABLE_NAME, null, null)
            true
        } catch (_: Exception) {
            false
        }
    }

    private fun pruneAccount(db: SQLiteDatabase, accountLogin: String) {
        try {
            val now = nowMillis()
            db.delete(TABLE_NAME, "$COL_ACCOUNT = ? AND $COL_EXPIRES <= ?", arrayOf(accountLogin, now.toString()))
            db.execSQL(
                """
                DELETE FROM $TABLE_NAME WHERE $COL_ACCOUNT = ? AND $COL_KEY NOT IN (
                    SELECT $COL_KEY FROM $TABLE_NAME WHERE $COL_ACCOUNT = ?
                    ORDER BY $COL_CREATED DESC LIMIT ?
                )
                """.trimIndent(),
                arrayOf(accountLogin, accountLogin, GitHubCacheStore.MAX_ENTRIES_PER_ACCOUNT.toString())
            )
        } catch (_: Exception) {
            // best-effort pruning
        }
    }

    private companion object {
        const val DATABASE_VERSION = 2
        const val TABLE_NAME = "response_cache"
        const val COL_ACCOUNT = "account_login"
        const val COL_KEY = "resource_key"
        const val COL_PAYLOAD = "payload"
        const val COL_STATUS = "status_code"
        const val COL_CREATED = "created_at"
        const val COL_EXPIRES = "expires_at"
        const val COL_ETAG = "etag"
    }
}

@Composable
internal fun OfflineCacheBadge(
    cachedAtMillis: Long?,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .testTag("cache-offline-indicator"),
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                Icons.Outlined.Info,
                contentDescription = "Modo offline",
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            val dateText = if (cachedAtMillis != null && cachedAtMillis > 0) {
                val formatted = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date(cachedAtMillis))
                "Exibindo dados em cache offline ($formatted)"
            } else {
                "Exibindo dados em cache offline"
            }
            Text(
                text = dateText,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

