import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
    alias(libs.plugins.room)
}

val localProps =
    Properties().apply {
        val f = rootProject.file("local.properties")
        if (f.exists()) load(f.inputStream())
    }
val apiAuthToken = localProps.getProperty("API_AUTH_TOKEN", "")
val appSecret = localProps.getProperty("APP_SECRET", "")
val workerUrl = localProps.getProperty("WORKER_URL", "")
val sentryDsn = localProps.getProperty("SENTRY_DSN", "")
val releaseStoreFile = localProps.getProperty("RELEASE_STORE_FILE", "")
val releaseStorePassword = localProps.getProperty("RELEASE_STORE_PASSWORD", "")
val releaseKeyAlias = localProps.getProperty("RELEASE_KEY_ALIAS", "")
val releaseKeyPassword = localProps.getProperty("RELEASE_KEY_PASSWORD", "")

android {
    namespace = "com.campuscue"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.campuscue"
        minSdk = 26
        targetSdk = 35
        versionCode = 43
        versionName = "1.6.7"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        buildConfigField("String", "API_AUTH_TOKEN", "\"$apiAuthToken\"")
        buildConfigField("String", "APP_SECRET", "\"$appSecret\"")
        buildConfigField("String", "WORKER_URL", "\"$workerUrl\"")
        buildConfigField("String", "SENTRY_DSN", "\"$sentryDsn\"")
    }

    signingConfigs {
        create("release") {
            if (releaseStoreFile.isNotBlank()) {
                storeFile = file(releaseStoreFile)
            }
            storePassword = releaseStorePassword
            keyAlias = releaseKeyAlias
            keyPassword = releaseKeyPassword
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            signingConfig = signingConfigs.getByName("release")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    composeCompiler {
        stabilityConfigurationFile = project.layout.projectDirectory.file("compose-stability.conf")
    }

    room {
        schemaDirectory("$projectDir/schemas")
    }
}

dependencies {
    implementation(project(":core"))
    implementation(libs.hilt.android)
    implementation(libs.work.runtime)
    implementation(libs.work.hilt)
    implementation(libs.zxing.core)
    implementation(libs.mlkit.barcode)
    implementation(libs.camera.core)
    implementation(libs.camera.camera2)
    implementation(libs.camera.lifecycle)
    implementation(libs.camera.view)
    implementation(libs.profileinstaller)

    testImplementation(libs.junit5.api)
    testRuntimeOnly(libs.junit5.engine)
    testImplementation(libs.mockk)
    testImplementation(libs.turbine)
    testImplementation(libs.coroutines.test)

    ksp(libs.hilt.compiler)
    ksp(libs.androidx.hilt.compiler)
    ksp(libs.room.compiler)
}

tasks.withType<Test> {
    useJUnitPlatform()
}

tasks.register("validateReleaseConfig") {
    doLast {
        check(apiAuthToken.isNotBlank()) { "API_AUTH_TOKEN must be set in local.properties for release builds." }
        check(appSecret.isNotBlank()) { "APP_SECRET must be set in local.properties for release builds." }
        check(workerUrl.startsWith("https://")) { "WORKER_URL must be an https:// URL in local.properties for release builds." }
        check(releaseStoreFile.isNotBlank()) { "RELEASE_STORE_FILE must be set in local.properties for release builds." }
        check(file(releaseStoreFile).exists()) { "RELEASE_STORE_FILE does not exist: $releaseStoreFile" }
        check(releaseStorePassword.isNotBlank()) { "RELEASE_STORE_PASSWORD must be set in local.properties." }
        check(releaseKeyAlias.isNotBlank()) { "RELEASE_KEY_ALIAS must be set in local.properties." }
        check(releaseKeyPassword.isNotBlank()) { "RELEASE_KEY_PASSWORD must be set in local.properties." }
    }
}

tasks.matching { it.name == "packageRelease" || it.name == "packageReleaseBundle" }.configureEach {
    dependsOn("validateReleaseConfig")
}
