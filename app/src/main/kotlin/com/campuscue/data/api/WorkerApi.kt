package com.campuscue.data.api

import kotlinx.serialization.Serializable
import retrofit2.http.Body
import retrofit2.http.POST

@Serializable
data class UpdateCheckRequest(
    val versionCode: Int,
    val versionName: String,
    val platform: String = "android",
    val channel: String,
)

@Serializable
data class UpdateCheckResponse(
    val latestVersionCode: Int? = null,
    val latestVersionName: String? = null,
    val minSupportedVersionCode: Int? = null,
    val apkUrl: String? = null,
    val sha256: String? = null,
    val sizeBytes: Long? = null,
    val releaseNotes: String? = null,
    val publishedAt: String? = null,
    val forceReauth: Boolean = false,
)

@Serializable
data class UserRegistration(
    val admno: String,
    val name: String,
)

@Serializable
data class UserRegistrationResponse(
    val ok: Boolean = false,
)

@Serializable
data class AccessStatusRequest(
    val admno: String,
    val name: String,
    val forceReRegister: Boolean = false,
)

@Serializable
data class AccessStatusResponse(
    val status: String = "pending",
    val message: String? = null,
    val requiresReauth: Boolean = false,
    val banned: Boolean = false,
)

@Serializable
data class SpecialAccessRequest(
    val admno: String,
    val name: String,
    val referralName: String,
)

@Serializable
data class SpecialAccessResponse(
    val ok: Boolean = false,
    val message: String? = null,
)

interface WorkerApi {
    @POST("update/check")
    suspend fun checkUpdate(
        @Body body: UpdateCheckRequest,
    ): UpdateCheckResponse

    @POST("users/register")
    suspend fun registerUser(
        @Body body: UserRegistration,
    ): UserRegistrationResponse

    @POST("access/status")
    suspend fun getAccessStatus(
        @Body body: AccessStatusRequest,
    ): AccessStatusResponse

    @POST("access/special")
    suspend fun requestSpecialAccess(
        @Body body: SpecialAccessRequest,
    ): SpecialAccessResponse
}
