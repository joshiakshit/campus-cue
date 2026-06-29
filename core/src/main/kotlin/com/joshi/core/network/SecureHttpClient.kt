package com.joshi.core.network

import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

object SecureHttpClient {
    fun build(authInterceptor: AuthInterceptor): OkHttpClient {
        val builder =
            OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .addInterceptor(authInterceptor)
                .addInterceptor(RetryInterceptor())

        return builder.build()
    }
}
