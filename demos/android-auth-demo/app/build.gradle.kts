import java.net.URI
import java.security.KeyStore
import java.security.MessageDigest
import java.util.Properties
import groovy.json.JsonOutput

plugins { id("com.android.application") }

val demoConfiguration = Properties().apply {
    rootProject.file("DemoConfiguration.properties").inputStream().use(::load)
}
fun demoValue(gradleKey: String, configKey: String): String = providers.gradleProperty(gradleKey)
    .getOrElse(requireNotNull(demoConfiguration.getProperty(configKey)) { "Missing demo configuration: $configKey" })
val callbackUri = demoValue("KSUSER_REDIRECT_URI", "redirectUri")
val callback = URI(callbackUri)
require(callback.scheme == "https" && callback.host != null && callback.rawUserInfo == null
    && callback.port in listOf(-1, 443) && callback.rawQuery == null && callback.rawFragment == null
    && callback.rawPath.startsWith("/") && callback.normalize() == callback) {
    "KSUSER_REDIRECT_URI must be a registered HTTPS callback without query or fragment"
}
fun quoted(value: String) = "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\""

android {
    namespace = "cn.ksuser.demo.auth"
    compileSdk { version = release(36) { minorApiLevel = 1 } }
    defaultConfig {
        applicationId = "cn.ksuser.demo.auth"
        minSdk = 24
        targetSdk = 36
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        versionCode = 1
        versionName = "0.1.0"
        manifestPlaceholders["callbackHost"] = callback.host
        // Android's IntentFilter matches Uri.path, while the SDK independently checks the encoded URI.
        manifestPlaceholders["callbackPath"] = callback.path
        buildConfigField("String", "REDIRECT_URI", quoted(callbackUri))
        mapOf(
            "OAUTH_CLIENT_ID" to "oauthClientId",
            "OFFICIAL_CLIENT_ID" to "officialClientId",
            "API_BASE_URL" to "apiBaseUrl",
            "WEB_BASE_URL" to "webBaseUrl",
            "OFFICIAL_ISSUER" to "officialIssuer",
            "PROVIDER_CERTIFICATES" to "providerCertificates",
        ).forEach { (key, configKey) ->
            buildConfigField("String", key, quoted(demoValue("KSUSER_$key", configKey)))
        }
    }
    buildFeatures { buildConfig = true }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation("cn.ksuser:ksuser-auth-android:0.1.0")
    implementation("androidx.activity:activity-ktx:1.8.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.9.2")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.2")
    androidTestImplementation("androidx.test.ext:junit:1.3.0")
    androidTestImplementation("androidx.test:core:1.7.0")
    androidTestImplementation("androidx.test:runner:1.7.0")
}

tasks.register("generateDebugAssetLinks") {
    group = "verification"
    description = "Generate assetlinks.json for the actual debug signing certificate; does not deploy it."
    dependsOn("validateSigningDebug")
    val output = rootProject.layout.buildDirectory.file("association/debug/assetlinks.json")
    outputs.file(output)
    // Certificates may change independently of Gradle inputs on a developer machine.
    outputs.upToDateWhen { false }
    doLast {
        val signing = android.signingConfigs.getByName("debug")
        val store = KeyStore.getInstance(signing.storeType ?: "JKS")
        requireNotNull(signing.storeFile).inputStream().use { store.load(it, signing.storePassword?.toCharArray()) }
        val certificate = requireNotNull(store.getCertificate(signing.keyAlias)) { "Debug signing certificate missing" }
        val fingerprint = MessageDigest.getInstance("SHA-256").digest(certificate.encoded)
            .joinToString(":") { "%02X".format(it) }
        val statement = listOf(mapOf(
            "relation" to listOf("delegate_permission/common.handle_all_urls"),
            "target" to mapOf("namespace" to "android_app", "package_name" to android.defaultConfig.applicationId,
                "sha256_cert_fingerprints" to listOf(fingerprint)),
        ))
        val file = output.get().asFile
        file.parentFile.mkdirs()
        file.writeText(JsonOutput.prettyPrint(JsonOutput.toJson(statement)) + "\n")
        logger.lifecycle("Deploy {} to https://{}/.well-known/assetlinks.json", file, callback.host)
        logger.lifecycle("Register Android package {} with SHA-256 {}", android.defaultConfig.applicationId, fingerprint)
    }
}
