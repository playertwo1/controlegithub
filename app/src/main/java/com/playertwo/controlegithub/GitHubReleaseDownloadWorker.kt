package com.playertwo.controlegithub

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.provider.MediaStore
import androidx.annotation.RequiresApi
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkInfo
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import java.io.File
import java.io.IOException
import java.util.Locale
import java.util.concurrent.CancellationException
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runInterruptible

internal object ReleaseDownloadKeys {
    const val OWNER = "owner"
    const val REPO = "repo"
    const val REPOSITORY_ID = "repository_id"
    const val ASSET_ID = "asset_id"
    const val ASSET_NAME = "asset_name"
    const val ASSET_SIZE = "asset_size"
    const val ASSET_DIGEST = "asset_digest"
    const val CHECKSUM_ASSET_ID = "checksum_asset_id"
    const val DESTINATION_URI = "destination_uri"
    const val DOWNLOADED = "downloaded"
    const val TOTAL = "total"
    const val HASH_STATUS = "hash_status"
    const val HASH_SOURCE = "hash_source"
    const val ACTUAL_HASH = "actual_hash"
    const val OUTPUT_URI = "output_uri"
    const val ERROR = "error"
}

internal const val RELEASE_DOWNLOAD_CHANNEL_ID = "release_downloads"
internal const val RELEASE_DOWNLOAD_WORK_TAG = "release-asset-download"
private const val RELEASE_DOWNLOAD_CHANNEL_NAME = "Downloads de releases"
private const val RELEASE_DOWNLOAD_DESTINATIONS_PREFS = "release_download_destinations"
private const val RELEASE_DOWNLOAD_NOTIFICATION_BASE = 31_000
private const val RELEASE_DOWNLOAD_COMPLETE_NOTIFICATION_BASE = 1_100_000
private const val RELEASE_DOWNLOAD_STAGING_PREFIX = "release-asset-"

internal fun releaseAssetWorkName(repositoryId: Long, assetId: Long) = "release-asset-$repositoryId-$assetId"

internal fun enqueueReleaseAssetDownload(
    context: Context,
    repository: GitHubRepository,
    asset: GitHubReleaseAsset,
    checksumAsset: GitHubReleaseAsset?,
    destinationUri: Uri? = null
) {
    val input = workDataOf(
        ReleaseDownloadKeys.OWNER to repository.owner,
        ReleaseDownloadKeys.REPO to repository.name,
        ReleaseDownloadKeys.REPOSITORY_ID to repository.id,
        ReleaseDownloadKeys.ASSET_ID to asset.id,
        ReleaseDownloadKeys.ASSET_NAME to asset.name,
        ReleaseDownloadKeys.ASSET_SIZE to asset.sizeBytes,
        ReleaseDownloadKeys.ASSET_DIGEST to (asset.digestSha256 ?: ""),
        ReleaseDownloadKeys.CHECKSUM_ASSET_ID to (checksumAsset?.id ?: 0L),
        ReleaseDownloadKeys.DESTINATION_URI to (destinationUri?.toString() ?: "")
    )
    val request = OneTimeWorkRequestBuilder<GitHubReleaseDownloadWorker>()
        .setInputData(input)
        .addTag(RELEASE_DOWNLOAD_WORK_TAG)
        .addTag(releaseAssetWorkName(repository.id, asset.id))
        .build()
    val workName = releaseAssetWorkName(repository.id, asset.id)
    destinationUri?.let { rememberReleaseDownloadDestination(context, workName, it) }
    try {
        WorkManager.getInstance(context).enqueueUniqueWork(workName, ExistingWorkPolicy.KEEP, request)
    } catch (failure: RuntimeException) {
        destinationUri?.let { cleanupReleaseDownloadDestination(context, it, releasePersistablePermission = true) }
        forgetReleaseDownloadDestination(context, workName)
        throw failure
    }
}

internal fun cancelReleaseDownloads(context: Context) {
    val manager = WorkManager.getInstance(context)
    val infos = runCatching { manager.getWorkInfosByTag(RELEASE_DOWNLOAD_WORK_TAG).get() }.getOrDefault(emptyList())
    manager.cancelAllWorkByTag(RELEASE_DOWNLOAD_WORK_TAG)
    infos.filter { it.state != WorkInfo.State.RUNNING }.forEach { info ->
        info.tags.firstOrNull { it.startsWith("release-asset-") && it != RELEASE_DOWNLOAD_WORK_TAG }?.let { workName ->
            cleanupQueuedReleaseDestination(context, workName)
        }
    }
}

internal fun cancelReleaseAssetDownload(context: Context, repositoryId: Long, assetId: Long, workInfo: WorkInfo) {
    val workName = releaseAssetWorkName(repositoryId, assetId)
    WorkManager.getInstance(context).cancelUniqueWork(workName)
    if (workInfo.state == WorkInfo.State.RUNNING) return
    cleanupQueuedReleaseDestination(context, workName)
}

internal fun rememberReleaseDownloadDestination(context: Context, workName: String, uri: Uri) {
    context.getSharedPreferences(RELEASE_DOWNLOAD_DESTINATIONS_PREFS, Context.MODE_PRIVATE)
        .edit().putString(workName, uri.toString()).apply()
}

private fun forgetReleaseDownloadDestination(context: Context, workName: String) {
    context.getSharedPreferences(RELEASE_DOWNLOAD_DESTINATIONS_PREFS, Context.MODE_PRIVATE)
        .edit().remove(workName).apply()
}

private fun cleanupQueuedReleaseDestination(context: Context, workName: String) {
    val preferences = context.getSharedPreferences(RELEASE_DOWNLOAD_DESTINATIONS_PREFS, Context.MODE_PRIVATE)
    preferences.getString(workName, null)?.let { value ->
        runCatching { Uri.parse(value) }.getOrNull()?.let {
            cleanupReleaseDownloadDestination(context, it, releasePersistablePermission = true)
        }
    }
    preferences.edit().remove(workName).apply()
}

internal fun cleanupReleaseDownloadDestination(context: Context, uri: Uri, releasePersistablePermission: Boolean = false) {
    runCatching { context.contentResolver.delete(uri, null, null) }
    if (releasePersistablePermission) {
        runCatching {
            context.contentResolver.releasePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
        }
    }
}

internal class GitHubReleaseDownloadWorker(context: Context, parameters: WorkerParameters) : CoroutineWorker(context, parameters) {
    @Volatile private var activeClient: GitHubReleaseAssetClient? = null

    override suspend fun doWork(): Result {
        val owner = inputData.getString(ReleaseDownloadKeys.OWNER)?.takeIf(String::isNotBlank) ?: return failure("invalid")
        val repositoryName = inputData.getString(ReleaseDownloadKeys.REPO)?.takeIf(String::isNotBlank) ?: return failure("invalid")
        val assetId = inputData.getLong(ReleaseDownloadKeys.ASSET_ID, 0)
        val assetName = inputData.getString(ReleaseDownloadKeys.ASSET_NAME)?.takeIf(String::isNotBlank) ?: return failure("invalid")
        val assetSize = inputData.getLong(ReleaseDownloadKeys.ASSET_SIZE, 0)
        val checksumAssetId = inputData.getLong(ReleaseDownloadKeys.CHECKSUM_ASSET_ID, 0)
        val destinationUri = inputData.getString(ReleaseDownloadKeys.DESTINATION_URI)
            ?.takeIf(String::isNotBlank)?.let(Uri::parse)
        if (assetId <= 0 || !assetName.endsWith(".apk", ignoreCase = true) || (Build.VERSION.SDK_INT < 29 && destinationUri == null)) {
            return failure("invalid")
        }

        val repository = GitHubRepository(
            id = inputData.getLong(ReleaseDownloadKeys.REPOSITORY_ID, 0),
            name = repositoryName,
            fullName = "$owner/$repositoryName",
            owner = owner,
            isPrivate = true,
            description = null,
            language = null,
            stars = 0
        )
        val staging = File(applicationContext.cacheDir, "$RELEASE_DOWNLOAD_STAGING_PREFIX$id.part")
        var publishedUri: Uri? = destinationUri
        val published = AtomicBoolean(false)
        try {
            staging.delete()
            val availableBytes = StatFs(applicationContext.cacheDir.path).availableBytes
            val copiesNeeded = if (Build.VERSION.SDK_INT >= 29) 2 else 1
            if (assetSize > 0 &&
                (availableBytes < MIN_STAGING_HEADROOM_BYTES || assetSize > (availableBytes - MIN_STAGING_HEADROOM_BYTES) / copiesNeeded)
            ) {
                return failure("no_space")
            }
            setForeground(foregroundInfo(null, null))
            val credentials = SecureSessionStore(applicationContext).load() ?: return failure("session")
            val client = GitHubReleaseAssetClient().also { activeClient = it }
            var lastProgressBytes = 0L
            val transferred = runInterruptible(Dispatchers.IO) {
                staging.outputStream().buffered().use { output ->
                    client.download(repository, assetId, credentials.accessToken, output, isCancelled = { isStopped || Thread.currentThread().isInterrupted }) { bytes, total ->
                        if (bytes - lastProgressBytes >= PROGRESS_INTERVAL_BYTES || total != null && bytes >= total) {
                            lastProgressBytes = bytes
                            setProgressAsync(progressData(bytes, total))
                            setForegroundAsync(foregroundInfo(bytes, total))
                        }
                    }
                }
            }
            if (assetSize > 0 && transferred.bytes != assetSize) return failure("size_mismatch")

            val expectedHash = inputData.getString(ReleaseDownloadKeys.ASSET_DIGEST)
                ?.takeIf(String::isNotBlank)
                ?.takeIf(::isSha256Hex)
                ?.lowercase(Locale.ROOT)
                ?.let { it to "GitHub" }
                ?: if (checksumAssetId > 0) {
                    val sums = runInterruptible(Dispatchers.IO) {
                        client.downloadText(repository, checksumAssetId, credentials.accessToken, isCancelled = {
                            isStopped || Thread.currentThread().isInterrupted
                        })
                    }
                    parseSha256Sums(sums, assetName)?.let { it to "SHA256SUMS" }
                } else null

            val hashStatus = when {
                expectedHash == null -> "unverified"
                expectedHash.first.equals(transferred.sha256Hex, ignoreCase = true) -> "verified"
                else -> {
                    staging.delete()
                    return failure("hash_mismatch", transferred.sha256Hex, expectedHash.second)
                }
            }
            publishedUri = if (Build.VERSION.SDK_INT >= 29) {
                runInterruptible(Dispatchers.IO) {
                    writePendingDownload(staging, releaseAssetFileName(assetName)) { createdUri -> publishedUri = createdUri }
                }
            } else {
                destinationUri ?: return failure("invalid")
            }
            if (isStopped) throw CancellationException()
            if (Build.VERSION.SDK_INT >= 29) {
                makeDownloadVisible(publishedUri!!)
                published.set(true)
            } else {
                try {
                    runInterruptible(Dispatchers.IO) {
                        copyToDocument(staging, publishedUri!!)
                        published.set(true)
                    }
                } catch (cancelled: CancellationException) {
                    if (!published.get()) throw cancelled
                }
            }
            if (destinationUri != null) runCatching {
                applicationContext.contentResolver.releasePersistableUriPermission(
                    destinationUri,
                    Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
            }
            notifyComplete(hashStatus)
            return Result.success(
                workDataOf(
                    ReleaseDownloadKeys.OUTPUT_URI to publishedUri.toString(),
                    ReleaseDownloadKeys.HASH_STATUS to hashStatus,
                    ReleaseDownloadKeys.HASH_SOURCE to (expectedHash?.second ?: "")
                )
            )
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: ReleaseAssetHttpException) {
            return failure("http_${failure.statusCode}")
        } catch (_: IOException) {
            if (isStopped) throw CancellationException()
            return failure("download_or_storage")
        } catch (_: SecurityException) {
            return failure("storage_permission")
        } finally {
            activeClient?.cancel()
            activeClient = null
            staging.delete()
            if (!published.get()) publishedUri?.let {
                cleanupReleaseDownloadDestination(applicationContext, it, releasePersistablePermission = it == destinationUri)
            }
            if (destinationUri != null) forgetReleaseDownloadDestination(applicationContext, releaseAssetWorkName(repository.id, assetId))
        }
    }

    @RequiresApi(29)
    private fun writePendingDownload(staging: File, requestedName: String, onCreated: (Uri) -> Unit): Uri {
        val resolver = applicationContext.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, uniqueDownloadName(requestedName))
            put(MediaStore.MediaColumns.MIME_TYPE, APK_MIME_TYPE)
            put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
            ?: throw IOException("Could not create Downloads item")
        var visible = false
        try {
            onCreated(uri)
            resolver.openOutputStream(uri, "w")?.buffered()?.use { output -> staging.inputStream().buffered().use { it.copyTo(output) } }
                ?: throw IOException("Could not open Downloads item")
            visible = true
            return uri
        } finally {
            if (!visible) resolver.delete(uri, null, null)
        }
    }

    private fun makeDownloadVisible(uri: Uri) {
        val visibleValues = ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }
        if (applicationContext.contentResolver.update(uri, visibleValues, null, null) != 1) {
            throw IOException("Could not publish Downloads item")
        }
    }

    @RequiresApi(29)
    private fun uniqueDownloadName(requestedName: String): String {
        val baseName = releaseAssetFileName(requestedName)
        val dot = baseName.lastIndexOf('.').takeIf { it > 0 } ?: baseName.length
        val stem = baseName.substring(0, dot)
        val extension = baseName.substring(dot)
        val resolver = applicationContext.contentResolver
        var suffix = 0
        while (true) {
            val name = if (suffix == 0) baseName else "$stem ($suffix)$extension"
            val cursor = resolver.query(
                MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                arrayOf(MediaStore.MediaColumns._ID),
                "${MediaStore.MediaColumns.DISPLAY_NAME}=? AND ${MediaStore.MediaColumns.RELATIVE_PATH}=?",
                arrayOf(name, "${Environment.DIRECTORY_DOWNLOADS}/"),
                null
            )
            val exists = cursor?.use { it.moveToFirst() } ?: false
            if (!exists) return name
            suffix++
        }
    }

    private fun copyToDocument(staging: File, uri: Uri) {
        applicationContext.contentResolver.openOutputStream(uri, "wt")?.buffered()?.use { output ->
            staging.inputStream().buffered().use { it.copyTo(output) }
        } ?: throw IOException("Could not open selected destination")
    }

    private fun progressData(downloaded: Long, total: Long?): Data = workDataOf(
        ReleaseDownloadKeys.DOWNLOADED to downloaded,
        ReleaseDownloadKeys.TOTAL to (total ?: -1L)
    )

    private fun foregroundInfo(downloaded: Long?, total: Long?): ForegroundInfo {
        ensureChannel(applicationContext)
        val percent = if (downloaded != null && total != null && total > 0) {
            ((downloaded * 100 / total).coerceIn(0, 100)).toInt()
        } else null
        val notification = Notification.Builder(applicationContext, RELEASE_DOWNLOAD_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_notification)
            .setContentTitle("Baixando APK de release")
            .setContentText(percent?.let { "$it%" } ?: "Transferência em andamento")
            .setProgress(100, percent ?: 0, percent == null)
            .setOnlyAlertOnce(true)
            .setOngoing(true)
            .addAction(
                Notification.Action.Builder(
                    null,
                    "Cancelar",
                    WorkManager.getInstance(applicationContext).createCancelPendingIntent(id)
                ).build()
            )
            .build()
        return if (Build.VERSION.SDK_INT >= 29) {
            ForegroundInfo(notificationId(), notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else ForegroundInfo(notificationId(), notification)
    }

    private fun notifyComplete(hashStatus: String) {
        val manager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (!manager.areNotificationsEnabled() || Build.VERSION.SDK_INT >= 33 &&
            applicationContext.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED
        ) return
        ensureChannel(applicationContext)
        val intent = Intent(applicationContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pending = PendingIntent.getActivity(
            applicationContext,
            completionNotificationId(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val hashText = when (hashStatus) {
            "verified" -> "SHA-256 conferido"
            else -> "SHA-256 não publicado"
        }
        manager.notify(
            completionNotificationId(),
            Notification.Builder(applicationContext, RELEASE_DOWNLOAD_CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_stat_notification)
                .setContentTitle("Download de release concluído")
                .setContentText("$hashText. Abra Releases para instalar.")
                .setContentIntent(pending)
                .setAutoCancel(true)
                .build()
        )
    }

    private fun notificationId() = RELEASE_DOWNLOAD_NOTIFICATION_BASE + (inputData.getLong(ReleaseDownloadKeys.ASSET_ID, 0) % 1_000_000L).toInt()
    private fun completionNotificationId() = RELEASE_DOWNLOAD_COMPLETE_NOTIFICATION_BASE +
        (inputData.getLong(ReleaseDownloadKeys.ASSET_ID, 0) % 1_000_000L).toInt()

    private fun failure(code: String, hash: String = "", source: String = ""): Result = Result.failure(
        workDataOf(
            ReleaseDownloadKeys.ERROR to code,
            ReleaseDownloadKeys.HASH_STATUS to if (code == "hash_mismatch") "mismatch" else "",
            ReleaseDownloadKeys.HASH_SOURCE to source,
            ReleaseDownloadKeys.ACTUAL_HASH to hash
        )
    )

    private companion object {
        const val MIN_STAGING_HEADROOM_BYTES = 1_048_576L
        const val PROGRESS_INTERVAL_BYTES = 256 * 1024L
        const val APK_MIME_TYPE = "application/vnd.android.package-archive"

        fun ensureChannel(context: Context) {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (manager.getNotificationChannel(RELEASE_DOWNLOAD_CHANNEL_ID) == null) {
                manager.createNotificationChannel(
                    NotificationChannel(
                        RELEASE_DOWNLOAD_CHANNEL_ID,
                        RELEASE_DOWNLOAD_CHANNEL_NAME,
                        NotificationManager.IMPORTANCE_LOW
                    ).apply { description = "Andamento dos downloads iniciados no app" }
                )
            }
        }
    }
}

internal fun releaseAssetFileName(name: String): String = name
    .substringAfterLast('/')
    .substringAfterLast('\\')
    .filterNot { it.isISOControl() || it in ":*?\"<>|" }
    .trim()
    .takeIf { it.isNotBlank() && it != "." && it != ".." }
    ?: "release.apk"
