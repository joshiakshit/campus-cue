package com.campuscue.domain.usecase

import java.io.File
import java.security.MessageDigest
import java.util.Locale

enum class UpdateAvailability { NONE, OPTIONAL, REQUIRED, INVALID }

data class UpdateManifest(
    val latestVersionCode: Int?,
    val latestVersionName: String?,
    val minSupportedVersionCode: Int?,
    val apkUrl: String?,
    val sha256: String?,
    val sizeBytes: Long?,
    val releaseNotes: String?,
    val publishedAt: String?,
)

object UpdatePolicy {
    fun evaluate(
        currentVersionCode: Int,
        manifest: UpdateManifest,
    ): UpdateAvailability {
        val latest = manifest.latestVersionCode ?: return UpdateAvailability.INVALID
        if (latest <= 0) return UpdateAvailability.INVALID

        val minSupported = manifest.minSupportedVersionCode ?: 0
        return when {
            currentVersionCode < minSupported && latest > currentVersionCode -> UpdateAvailability.REQUIRED
            latest > currentVersionCode -> UpdateAvailability.OPTIONAL
            else -> UpdateAvailability.NONE
        }
    }

    fun validateInstallableManifest(manifest: UpdateManifest): Boolean =
        manifest.latestVersionCode != null &&
            manifest.latestVersionName.isNullOrBlank().not() &&
            manifest.apkUrl.isNullOrBlank().not() &&
            manifest.sha256.isNullOrBlank().not()
}

object UpdateHash {
    fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256")
            .digest(bytes)
            .joinToString("") { "%02x".format(it) }

    fun sha256(file: File): String =
        file.inputStream().use { stream ->
            val digest = MessageDigest.getInstance("SHA-256")
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val read = stream.read(buffer)
                if (read <= 0) break
                digest.update(buffer, 0, read)
            }
            digest.digest().joinToString("") { "%02x".format(it) }
        }

    fun matches(
        file: File,
        expectedSha256: String,
    ): Boolean = sha256(file).lowercase(Locale.US) == expectedSha256.lowercase(Locale.US)
}
