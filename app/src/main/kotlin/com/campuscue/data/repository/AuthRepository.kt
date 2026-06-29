package com.campuscue.data.repository

import android.util.Base64
import com.campuscue.data.api.AccessStatusRequest
import com.campuscue.data.api.AuthApi
import com.campuscue.data.api.SpecialAccessRequest
import com.campuscue.data.api.UserRegistration
import com.campuscue.data.api.WorkerApi
import com.campuscue.domain.model.JwtPayload
import com.campuscue.domain.model.UserInfo
import com.joshi.core.security.TokenManager
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

enum class AccessStatus {
    APPROVED,
    PENDING,
}

enum class LoginMethod(val apiValue: String) {
    PHONE("phone"),
    EMAIL("email"),
}

data class AccessCheckResult(
    val status: AccessStatus,
    val requiresReauth: Boolean = false,
    val banned: Boolean = false,
)

@Singleton
class AuthRepository
    @Inject
    constructor(
        private val authApi: AuthApi,
        private val workerApi: WorkerApi,
        private val tokenManager: TokenManager,
        private val json: Json,
    ) {
        private companion object {
            const val APP_VERSION = "2.4.8"
        }

        private val refreshMutex = Mutex()

        suspend fun requestOtp(
            contact: String,
            method: LoginMethod = LoginMethod.PHONE,
        ): String {
            val deviceId = getOrCreateDeviceId()
            val response =
                authApi.requestOtp(
                    mapOf(
                        "method" to method.apiValue,
                        "contact" to contact,
                        "lastmodifiedby" to contact,
                        "deviceid" to deviceId,
                        "appversion" to APP_VERSION,
                    ),
                )
            return response.data?.username ?: contact
        }

        suspend fun requestOtp(phone: String): String = requestOtp(phone, LoginMethod.PHONE)

        suspend fun validateOtp(
            contact: String,
            otp: String,
            username: String = contact,
        ): UserInfo {
            val deviceId = getOrCreateDeviceId()
            val response =
                authApi.validateOtp(
                    mapOf(
                        "otp" to otp,
                        "contact" to contact,
                        "username" to username,
                        "lastmodifiedby" to contact,
                        "deviceid" to deviceId,
                        "appversion" to APP_VERSION,
                    ),
                )

            val token =
                response.data?.token
                    ?: error("Login response did not include tokens")

            val userInfo = decodeUserInfo(token.accessToken)
            tokenManager.setActiveAdmno(userInfo.admno)
            tokenManager.saveTokens(token.accessToken, token.refreshToken)
            tokenManager.saveUserMeta(userInfo.email, userInfo.phoneNumber.ifBlank { contact })
            tokenManager.addAccount(admno = userInfo.admno, name = userInfo.name, email = userInfo.email)
            return userInfo
        }

        suspend fun checkAccessStatus(
            user: UserInfo = getUserInfo() ?: error("Not logged in"),
            forceReRegister: Boolean = false,
        ): AccessStatus = checkAccessStatusResult(user, forceReRegister).status

        suspend fun checkAccessStatusResult(
            user: UserInfo = getUserInfo() ?: error("Not logged in"),
            forceReRegister: Boolean = false,
        ): AccessCheckResult {
            val response =
                workerApi.getAccessStatus(
                    AccessStatusRequest(
                        admno = user.admno,
                        name = user.name,
                        forceReRegister = forceReRegister,
                    ),
                )
            val status =
                if (response.status.equals("approved", ignoreCase = true)) {
                    AccessStatus.APPROVED
                } else {
                    AccessStatus.PENDING
                }
            return AccessCheckResult(
                status = status,
                requiresReauth = response.requiresReauth,
                banned = response.banned,
            )
        }

        suspend fun requestSpecialAccess(
            referralName: String,
            user: UserInfo = getUserInfo() ?: error("Not logged in"),
        ): String {
            val response =
                workerApi.requestSpecialAccess(
                    SpecialAccessRequest(
                        admno = user.admno,
                        name = user.name,
                        referralName = referralName,
                    ),
                )
            if (!response.ok) {
                error(response.message ?: "Special access request failed")
            }
            return response.message ?: "Special access requested"
        }

        @Suppress("ThrowsCount")
        suspend fun refreshTokenIfNeeded(): String {
            val access = tokenManager.getAccessToken() ?: throw SessionExpiredException()
            if (!isExpired(access)) return access

            return refreshMutex.withLock {
                val current = tokenManager.getAccessToken() ?: throw SessionExpiredException()
                if (!isExpired(current)) {
                    current
                } else {
                    val refresh = tokenManager.getRefreshToken() ?: throw SessionExpiredException()
                    if (isExpired(refresh)) throw SessionExpiredException()

                    val response =
                        authApi.refreshToken(
                            mapOf(
                                "refreshtoken" to refresh,
                                "accesstoken" to current,
                                "lastmodifiedby" to (tokenManager.getEmail() ?: tokenManager.getPhone() ?: ""),
                            ),
                        )

                    val token = response.data?.token ?: throw SessionExpiredException()
                    tokenManager.saveTokens(token.accessToken, token.refreshToken)
                    token.accessToken
                }
            }
        }

        fun getOrCreateDeviceId(): String {
            tokenManager.getDeviceId()?.let { return it }
            val deviceId = UUID.randomUUID().toString()
            tokenManager.saveDeviceId(deviceId)
            return deviceId
        }

        fun getUserInfo(): UserInfo? {
            val access = tokenManager.getAccessToken() ?: return null
            return try {
                decodeUserInfo(access)
            } catch (_: Exception) {
                null
            }
        }

        suspend fun registerUserSilently() {
            try {
                val user = getUserInfo() ?: return
                workerApi.registerUser(UserRegistration(admno = user.admno, name = user.name))
            } catch (_: Exception) {
            }
        }

        fun isLoggedIn(): Boolean = tokenManager.hasTokens()

        fun logout() {
            tokenManager.clearCurrentAccount()
        }

        private fun decodeUserInfo(accessToken: String): UserInfo {
            val parts = accessToken.split(".")
            require(parts.size >= 2) { "Invalid JWT" }
            val payload = String(Base64.decode(paddedBase64(parts[1]), Base64.URL_SAFE or Base64.NO_WRAP))
            val jwt = json.decodeFromString<JwtPayload>(payload)
            val admno = jwt.admno.ifBlank { jwt.preferredUsername }
            return UserInfo(
                admno = admno,
                brId = jwt.brId,
                name = jwt.name,
                email = jwt.email,
                phoneNumber = jwt.phoneNumber,
                clientId = jwt.clientId,
                preferredUsername = jwt.preferredUsername,
            )
        }

        private fun isExpired(token: String): Boolean {
            return try {
                val parts = token.split(".")
                if (parts.size < 2) return true
                val payload = String(Base64.decode(paddedBase64(parts[1]), Base64.URL_SAFE or Base64.NO_WRAP))
                val jwt = json.decodeFromString<JwtPayload>(payload)
                jwt.exp < (System.currentTimeMillis() / 1000) + 60
            } catch (_: Exception) {
                true
            }
        }

        private fun paddedBase64(value: String): String {
            val remainder = value.length % 4
            return if (remainder == 0) value else value + "=".repeat(4 - remainder)
        }
    }

class SessionExpiredException : Exception("Session expired")
