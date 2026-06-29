package com.joshi.core.security

interface SecretProvider {
    val apiAuthToken: String
    val appSecret: String
    val workerUrl: String
    val sentryDsn: String
}
