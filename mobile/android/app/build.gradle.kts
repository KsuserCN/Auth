import java.net.URI

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

fun loadEnvFile(path: String): Map<String, String> {
    val file = rootProject.file(path)
    if (!file.exists()) return emptyMap()

    return buildMap {
        file.forEachLine { rawLine ->
            val line = rawLine.trim()
            if (line.isEmpty() || line.startsWith("#")) return@forEachLine
            val delimiter = line.indexOf('=')
            if (delimiter <= 0) return@forEachLine
            val key = line.substring(0, delimiter).trim()
            val value = line.substring(delimiter + 1).trim().removeSurrounding("\"")
            put(key, value)
        }
    }
}

val debugEnv = loadEnvFile(".env.development")
val releaseEnv = loadEnvFile(".env.production")

fun envValue(
    env: Map<String, String>,
    key: String,
    defaultValue: String,
): String = env[key]?.takeIf { it.isNotBlank() } ?: defaultValue

fun escapeGradleString(value: String): String = value
    .replace("\\", "\\\\")
    .replace("\"", "\\\"")

fun assetStatementsValue(originHint: String): String {
    val normalizedOrigin = runCatching {
        val uri = URI(originHint.trim())
        val scheme = uri.scheme?.lowercase()
        val host = uri.host?.lowercase()
        if (scheme != "https" || host.isNullOrBlank()) {
            null
        } else {
            "$scheme://$host"
        }
    }.getOrNull()

    if (normalizedOrigin == null) {
        return "[]"
    }

    return """[{"include":"$normalizedOrigin/.well-known/assetlinks.json"}]"""
}

fun defaultUpdateManifestUrl(passkeyOriginHint: String): String {
    val normalizedOrigin = runCatching {
        val uri = URI(passkeyOriginHint.trim())
        val scheme = uri.scheme?.lowercase()
        val host = uri.host?.lowercase()
        if (scheme != "https" || host.isNullOrBlank()) {
            null
        } else {
            "$scheme://$host"
        }
    }.getOrNull() ?: "https://auth.ksuser.cn"

    return "$normalizedOrigin/downloads/latest/android.json"
}

fun com.android.build.api.dsl.ApplicationBuildType.configureEnvironment(
    env: Map<String, String>,
    appEnvDefault: String,
) {
    val apiBaseUrl = envValue(env, "API_BASE_URL", "https://api.ksuser.cn")
    val passkeyRpId = envValue(env, "PASSKEY_RP_ID", "auth.ksuser.cn")
    val passkeyOriginHint = envValue(env, "PASSKEY_ORIGIN_HINT", "https://auth.ksuser.cn")
    val updateManifestUrl = envValue(env, "UPDATE_MANIFEST_URL", defaultUpdateManifestUrl(passkeyOriginHint))
    val qqMobileAppId = envValue(env, "QQ_MOBILE_APP_ID", "1903977704")

    buildConfigField("String", "API_BASE_URL", "\"${escapeGradleString(apiBaseUrl)}\"")
    buildConfigField("String", "PASSKEY_RP_ID", "\"${escapeGradleString(passkeyRpId)}\"")
    buildConfigField("String", "PASSKEY_ORIGIN_HINT", "\"${escapeGradleString(passkeyOriginHint)}\"")
    buildConfigField("String", "UPDATE_MANIFEST_URL", "\"${escapeGradleString(updateManifestUrl)}\"")
    buildConfigField("String", "QQ_MOBILE_APP_ID", "\"${escapeGradleString(qqMobileAppId)}\"")
    buildConfigField("String", "APP_ENV", "\"${envValue(env, "APP_ENV", appEnvDefault)}\"")
    buildConfigField(
        "boolean",
        "ENABLE_HTTP_LOGGING",
        envValue(env, "ENABLE_HTTP_LOGGING", if (appEnvDefault == "development") "true" else "false"),
    )
    resValue("string", "asset_statements", assetStatementsValue(passkeyOriginHint))
    manifestPlaceholders["QQ_MOBILE_APP_ID"] = qqMobileAppId
    manifestPlaceholders["QQ_MOBILE_APP_SCHEME"] = if (qqMobileAppId.isBlank()) {
        "ksuserqqnotconfigured"
    } else {
        "tencent$qqMobileAppId"
    }
}

android {
    namespace = "cn.ksuser.auth"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        applicationId = "cn.ksuser.auth"
        minSdk = 24
        targetSdk = 36
        versionCode = 12
        versionName = "1.0.0.12"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        debug {
            configureEnvironment(debugEnv, "development")
        }
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            configureEnvironment(releaseEnv, "production")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
        buildConfig = true
        resValues = true
    }
    lint {
        // Avoid blocking release packaging on lintVital when lint artifacts
        // cannot be fetched in restricted network environments.
        checkReleaseBuilds = false
    }
}

dependencies {
    implementation(files("libs/open_sdk_3.5.19_r9483ffc7_lite.jar"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
     implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.org.jetbrains.kotlinx.coroutines.android)
    implementation(libs.com.squareup.retrofit2.retrofit)
    implementation(libs.com.squareup.retrofit2.converter.gson)
    implementation(libs.com.squareup.okhttp3.okhttp)
    implementation(libs.com.squareup.okhttp3.logging.interceptor)
    implementation(libs.com.google.code.gson.gson)
    implementation(libs.androidx.security.crypto)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.credentials)
    implementation(libs.androidx.credentials.play.services.auth)
    implementation(libs.io.coil.kt.coil.compose)
    implementation(libs.androidx.camera.core)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)
    implementation(libs.com.google.mlkit.barcode.scanning)
    testImplementation(libs.junit)
    testImplementation(libs.org.jetbrains.kotlinx.coroutines.test)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
