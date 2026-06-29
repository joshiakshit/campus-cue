package com.campuscue.data.repository

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import com.campuscue.BuildConfig
import com.campuscue.data.api.UpdateCheckRequest
import com.campuscue.data.api.UpdateCheckResponse
import com.campuscue.data.api.WorkerApi
import com.campuscue.domain.usecase.UpdateAvailability
import com.campuscue.domain.usecase.UpdateHash
import com.campuscue.domain.usecase.UpdateManifest
import com.campuscue.domain.usecase.UpdatePolicy
import com.joshi.core.storage.PreferencesStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

data class UpdateInfo(
    val latestVersionCode: Int,
    val latestVersionName: String,
    val minSupportedVersionCode: Int,
    val apkUrl: String,
    val sha256: String,
    val sizeBytes: Long?,
    val releaseNotes: String?,
    val publishedAt: String?,
    val availability: UpdateAvailability,
) {
    val isRequired: Boolean = availability == UpdateAvailability.REQUIRED
}

data class DownloadedUpdate(
    val info: UpdateInfo,
    val file: File,
)

sealed interface UpdateCheckResult {
    data object NoUpdate : UpdateCheckResult

    data class Available(val info: UpdateInfo) : UpdateCheckResult
}

enum class InstallLaunchResult { INSTALLER_OPENED, PERMISSION_SETTINGS_OPENED }

@Singleton
class UpdateRepository
    @Inject
    constructor(
        private val workerApi: WorkerApi,
        private val preferencesStore: PreferencesStore,
        @ApplicationContext private val context: Context,
    ) {
        private val downloadClient =
            OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(90, TimeUnit.SECONDS)
                .writeTimeout(90, TimeUnit.SECONDS)
                .build()

        suspend fun isForceReauthActive(): Boolean {
            val response =
                workerApi.checkUpdate(
                    UpdateCheckRequest(
                        versionCode = BuildConfig.VERSION_CODE,
                        versionName = BuildConfig.VERSION_NAME,
                        channel = if (BuildConfig.DEBUG) "debug" else "release",
                    ),
                )
            return response.forceReauth
        }

        suspend fun checkForUpdate(): UpdateCheckResult {
            val response =
                workerApi.checkUpdate(
                    UpdateCheckRequest(
                        versionCode = BuildConfig.VERSION_CODE,
                        versionName = BuildConfig.VERSION_NAME,
                        channel = if (BuildConfig.DEBUG) "debug" else "release",
                    ),
                )
            return response.toUpdateCheckResult()
        }

        suspend fun checkDailyIfNeeded(): UpdateCheckResult? {
            val now = System.currentTimeMillis()
            val lastCheck = preferencesStore.getString(LAST_UPDATE_CHECK_KEY).first().toLongOrNull() ?: 0L
            if (now - lastCheck < DAILY_CHECK_INTERVAL_MS) return null
            preferencesStore.putString(LAST_UPDATE_CHECK_KEY, now.toString())
            return checkForUpdate()
        }

        suspend fun downloadUpdate(info: UpdateInfo): DownloadedUpdate =
            withContext(Dispatchers.IO) {
                require(info.apkUrl.startsWith("https://")) { "Update APK URL must use HTTPS" }
                val updatesDir = File(context.cacheDir, "updates").apply { mkdirs() }
                val tempFile = File(updatesDir, "campuscue-${info.latestVersionCode}.apk.download")
                val finalFile = File(updatesDir, "campuscue-${info.latestVersionCode}.apk")

                val request = Request.Builder().url(info.apkUrl).build()
                downloadClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) error("Update download failed (${response.code})")
                    val body = response.body ?: error("Update download returned an empty body")
                    tempFile.outputStream().use { output ->
                        body.byteStream().use { input -> input.copyTo(output) }
                    }
                }

                if (info.sizeBytes != null && info.sizeBytes > 0 && tempFile.length() != info.sizeBytes) {
                    tempFile.delete()
                    error("Downloaded APK size did not match update metadata")
                }
                if (!UpdateHash.matches(tempFile, info.sha256)) {
                    tempFile.delete()
                    error("Downloaded APK checksum did not match update metadata")
                }

                if (finalFile.exists()) finalFile.delete()
                check(tempFile.renameTo(finalFile)) { "Could not prepare downloaded update" }
                DownloadedUpdate(info, finalFile)
            }

        fun launchInstall(downloaded: DownloadedUpdate): InstallLaunchResult {
            if (!canRequestPackageInstalls()) {
                openInstallPermissionSettings()
                return InstallLaunchResult.PERMISSION_SETTINGS_OPENED
            }

            val uri =
                FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    downloaded.file,
                )
            val intent =
                Intent(Intent.ACTION_VIEW)
                    .setDataAndType(uri, APK_MIME_TYPE)
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            return InstallLaunchResult.INSTALLER_OPENED
        }

        fun openInstallPermissionSettings() {
            val intent =
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
                } else {
                    Intent(Settings.ACTION_SECURITY_SETTINGS)
                }.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        }

        fun canRequestPackageInstalls(): Boolean =
            Build.VERSION.SDK_INT < Build.VERSION_CODES.O || context.packageManager.canRequestPackageInstalls()

        private fun UpdateCheckResponse.toUpdateCheckResult(): UpdateCheckResult {
            val manifest = toManifest()
            val availability = UpdatePolicy.evaluate(BuildConfig.VERSION_CODE, manifest)
            if (availability == UpdateAvailability.NONE) return UpdateCheckResult.NoUpdate
            if (availability == UpdateAvailability.INVALID || !UpdatePolicy.validateInstallableManifest(manifest)) {
                error("Update metadata is incomplete")
            }
            return UpdateCheckResult.Available(
                UpdateInfo(
                    latestVersionCode = manifest.latestVersionCode!!,
                    latestVersionName = manifest.latestVersionName!!,
                    minSupportedVersionCode = manifest.minSupportedVersionCode ?: 0,
                    apkUrl = manifest.apkUrl!!,
                    sha256 = manifest.sha256!!,
                    sizeBytes = manifest.sizeBytes,
                    releaseNotes = manifest.releaseNotes,
                    publishedAt = manifest.publishedAt,
                    availability = availability,
                ),
            )
        }

        private fun UpdateCheckResponse.toManifest(): UpdateManifest =
            UpdateManifest(
                latestVersionCode = latestVersionCode,
                latestVersionName = latestVersionName,
                minSupportedVersionCode = minSupportedVersionCode,
                apkUrl = apkUrl,
                sha256 = sha256,
                sizeBytes = sizeBytes,
                releaseNotes = releaseNotes,
                publishedAt = publishedAt,
            )

        suspend fun getLastNotifiedVersionCode(): Int = preferencesStore.getInt(LAST_NOTIFIED_VERSION_KEY).first()

        suspend fun setLastNotifiedVersionCode(code: Int) {
            preferencesStore.putInt(LAST_NOTIFIED_VERSION_KEY, code)
        }

        private companion object {
            const val LAST_UPDATE_CHECK_KEY = "last_update_check_ms"
            const val LAST_NOTIFIED_VERSION_KEY = "last_notified_version_code"
            const val DAILY_CHECK_INTERVAL_MS = 24L * 60L * 60L * 1000L
            const val APK_MIME_TYPE = "application/vnd.android.package-archive"
        }
    }
