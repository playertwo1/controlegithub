package com.playertwo.controlegithub

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException
import java.nio.ByteBuffer
import java.security.GeneralSecurityException
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first

internal val Context.sessionDataStore by preferencesDataStore(name = "secure_session")
internal val encryptedSessionKey = stringPreferencesKey("oauth_session")

internal data class SessionCredentials(
    val accessToken: String,
    val refreshToken: String? = null,
    val accessExpiresAtMillis: Long? = null,
    val refreshExpiresAtMillis: Long? = null,
    val userLogin: String? = null,
    val userProfileUrl: String? = null
) {
    init {
        require(accessToken.isNotBlank())
        require(refreshToken == null || refreshToken.isNotBlank())
        require(accessExpiresAtMillis == null || accessExpiresAtMillis > 0)
        require(refreshExpiresAtMillis == null || refreshExpiresAtMillis > 0)
        require(userLogin == null || userLogin.isNotBlank())
        require(userProfileUrl == null || userProfileUrl.isNotBlank())
    }

    override fun toString() = "SessionCredentials(hasRefreshToken=${refreshToken != null}, hasExpiry=${accessExpiresAtMillis != null}, hasUser=${userLogin != null})"
}

internal class GitHubSession(
    val user: GitHubUser,
    val credentials: SessionCredentials,
    val isOffline: Boolean = false
) {
    val accessToken: String get() = credentials.accessToken

    override fun toString() = "GitHubSession(userPresent=true, credentials=$credentials, isOffline=$isOffline)"
}

internal interface SessionCredentialStore {
    suspend fun save(credentials: SessionCredentials)
    suspend fun load(): SessionCredentials?
    suspend fun clear()
}

internal class SecureSessionStore(context: Context) : SessionCredentialStore {
    private val dataStore: DataStore<Preferences> = context.applicationContext.sessionDataStore

    override suspend fun save(credentials: SessionCredentials) {
        val plaintext = encode(credentials)
        val encrypted = encrypt(plaintext)
        val stored = Base64.encodeToString(encrypted, Base64.NO_WRAP)
        dataStore.edit { it[encryptedSessionKey] = stored }
    }

    override suspend fun load(): SessionCredentials? {
        val stored = dataStore.data.first()[encryptedSessionKey] ?: return null
        return try {
            decode(decrypt(Base64.decode(stored, Base64.NO_WRAP)))
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: GeneralSecurityException) {
            discardUnrecoverable()
            null
        } catch (_: IllegalArgumentException) {
            discardUnrecoverable()
            null
        } catch (_: IOException) {
            discardUnrecoverable()
            null
        }
    }

    override suspend fun clear() {
        dataStore.edit { it.remove(encryptedSessionKey) }
        synchronized(keyLock) { loadKeyStore().deleteEntry(KEY_ALIAS) }
    }

    private suspend fun discardUnrecoverable() {
        dataStore.edit { it.remove(encryptedSessionKey) }
        synchronized(keyLock) { loadKeyStore().deleteEntry(KEY_ALIAS) }
    }

    private fun encrypt(plaintext: ByteArray): ByteArray {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey())
        val iv = cipher.iv
        require(iv.size == IV_LENGTH)
        val ciphertext = cipher.doFinal(plaintext)
        return ByteBuffer.allocate(1 + iv.size + ciphertext.size)
            .put(FORMAT_VERSION)
            .put(iv)
            .put(ciphertext)
            .array()
    }

    private fun decrypt(blob: ByteArray): ByteArray {
        if (blob.size <= 1 + IV_LENGTH + GCM_TAG_LENGTH_BYTES || blob[0] != FORMAT_VERSION) {
            throw IOException("Session data invalid")
        }
        val iv = blob.copyOfRange(1, 1 + IV_LENGTH)
        val ciphertext = blob.copyOfRange(1 + IV_LENGTH, blob.size)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv))
        return cipher.doFinal(ciphertext)
    }

    private fun secretKey(): SecretKey = synchronized(keyLock) {
        val keyStore = loadKeyStore()
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey) ?: KeyGenerator
            .getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE_PROVIDER)
            .apply {
                init(
                    KeyGenParameterSpec.Builder(
                        KEY_ALIAS,
                        KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
                    )
                        .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                        .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                        .setKeySize(KEY_SIZE_BITS)
                        .setRandomizedEncryptionRequired(true)
                        .build()
                )
            }
            .generateKey()
    }

    private fun loadKeyStore(): KeyStore = KeyStore.getInstance(KEYSTORE_PROVIDER).apply { load(null) }

    private fun encode(credentials: SessionCredentials): ByteArray = ByteArrayOutputStream().use { bytes ->
        DataOutputStream(bytes).use { output ->
            output.writeInt(FORMAT_VERSION.toInt())
            output.writeUTF(credentials.accessToken)
            output.writeNullable(credentials.refreshToken)
            output.writeNullableLong(credentials.accessExpiresAtMillis)
            output.writeNullableLong(credentials.refreshExpiresAtMillis)
            output.writeNullable(credentials.userLogin)
            output.writeNullable(credentials.userProfileUrl)
        }
        bytes.toByteArray()
    }

    private fun decode(plaintext: ByteArray): SessionCredentials = DataInputStream(ByteArrayInputStream(plaintext)).use { input ->
        val version = input.readInt()
        if (version != 1 && version != 2) throw IOException("Session data invalid")
        val accessToken = input.readUTF().takeIf(String::isNotBlank) ?: throw IOException("Session data invalid")
        val refreshToken = input.readNullable()?.takeIf(String::isNotBlank)
        val accessExpiry = input.readNullableLong()
        val refreshExpiry = input.readNullableLong()
        val userLogin = if (version >= 2) input.readNullable()?.takeIf(String::isNotBlank) else null
        val userProfileUrl = if (version >= 2) input.readNullable()?.takeIf(String::isNotBlank) else null
        if (input.available() != 0) throw IOException("Session data invalid")
        SessionCredentials(accessToken, refreshToken, accessExpiry, refreshExpiry, userLogin, userProfileUrl)
    }

    private fun DataOutputStream.writeNullable(value: String?) {
        writeBoolean(value != null)
        if (value != null) writeUTF(value)
    }

    private fun DataOutputStream.writeNullableLong(value: Long?) {
        writeBoolean(value != null)
        if (value != null) writeLong(value)
    }

    private fun DataInputStream.readNullable(): String? = if (readBoolean()) readUTF() else null

    private fun DataInputStream.readNullableLong(): Long? = if (readBoolean()) readLong().takeIf { it > 0 }
        ?: throw IOException("Session data invalid") else null

    private companion object {
        const val KEYSTORE_PROVIDER = "AndroidKeyStore"
        const val KEY_ALIAS = "com.playertwo.controlegithub.session.aes"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val FORMAT_VERSION: Byte = 2
        const val KEY_SIZE_BITS = 256
        const val IV_LENGTH = 12
        const val GCM_TAG_LENGTH_BITS = 128
        const val GCM_TAG_LENGTH_BYTES = GCM_TAG_LENGTH_BITS / 8
        val keyLock = Any()
    }
}
