package com.campuscue.domain.usecase

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class UpdatePolicyTest {
    @Test
    fun `evaluate returns none when latest is current`() {
        assertEquals(UpdateAvailability.NONE, UpdatePolicy.evaluate(10, manifest(latestVersionCode = 10)))
    }

    @Test
    fun `evaluate returns optional when latest is newer`() {
        assertEquals(UpdateAvailability.OPTIONAL, UpdatePolicy.evaluate(10, manifest(latestVersionCode = 11)))
    }

    @Test
    fun `evaluate returns required when current is below minimum supported`() {
        assertEquals(
            UpdateAvailability.REQUIRED,
            UpdatePolicy.evaluate(10, manifest(latestVersionCode = 12, minSupportedVersionCode = 11)),
        )
    }

    @Test
    fun `evaluate returns invalid when latest version is missing`() {
        assertEquals(UpdateAvailability.INVALID, UpdatePolicy.evaluate(10, manifest(latestVersionCode = null)))
    }

    @Test
    fun `validateInstallableManifest requires apk url and checksum`() {
        assertTrue(UpdatePolicy.validateInstallableManifest(manifest()))
        assertFalse(UpdatePolicy.validateInstallableManifest(manifest(apkUrl = "")))
        assertFalse(UpdatePolicy.validateInstallableManifest(manifest(sha256 = "")))
    }

    @Test
    fun `sha256 matches known bytes and rejects mismatch`(
        @TempDir tempDir: File,
    ) {
        val file = File(tempDir, "sample.apk").apply { writeText("campuscue") }
        val hash = UpdateHash.sha256("campuscue".toByteArray())

        assertTrue(UpdateHash.matches(file, hash))
        assertFalse(UpdateHash.matches(file, "deadbeef"))
    }

    private fun manifest(
        latestVersionCode: Int? = 11,
        latestVersionName: String? = "1.0.1",
        minSupportedVersionCode: Int? = 0,
        apkUrl: String? = "https://updates.example.com/campuscue.apk",
        sha256: String? = "abc123",
    ): UpdateManifest =
        UpdateManifest(
            latestVersionCode = latestVersionCode,
            latestVersionName = latestVersionName,
            minSupportedVersionCode = minSupportedVersionCode,
            apkUrl = apkUrl,
            sha256 = sha256,
            sizeBytes = 42L,
            releaseNotes = "Fixes",
            publishedAt = "2026-05-22T00:00:00Z",
        )
}
