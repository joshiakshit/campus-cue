package com.joshi.core.crash

import android.content.Context
import io.sentry.Sentry
import io.sentry.android.core.SentryAndroid
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CrashReporter
    @Inject
    constructor() {
        fun initialize(
            context: Context,
            dsn: String,
            environment: String,
            release: String,
            debug: Boolean,
        ) {
            if (dsn.isBlank()) return
            SentryAndroid.init(context) { options ->
                options.dsn = dsn
                options.environment = environment
                options.release = release
                options.isDebug = debug
                options.isSendDefaultPii = false
            }
        }

        fun captureException(throwable: Throwable) {
            Sentry.captureException(throwable)
        }

        fun setUser(
            id: String,
            email: String? = null,
        ) {
            val user =
                io.sentry.protocol.User().apply {
                    this.id = id
                    this.email = email
                }
            Sentry.setUser(user)
        }

        fun addBreadcrumb(message: String) {
            Sentry.addBreadcrumb(message)
        }

        fun clearUser() {
            Sentry.setUser(null)
        }
    }
