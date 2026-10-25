plugins {
    id("com.android.library")
    `maven-publish`
}

group = "cn.ksuser"
version = "0.1.0"

android {
    namespace = "cn.ksuser.auth.sdk"
    compileSdk { version = release(36) { minorApiLevel = 1 } }
    defaultConfig {
        minSdk = 24
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    publishing { singleVariant("release") { withSourcesJar() } }
}

dependencies {
    api(libs.org.jetbrains.kotlinx.coroutines.android)
    implementation(libs.com.squareup.okhttp3.okhttp)
    implementation(libs.com.google.code.gson.gson)
    implementation("androidx.browser:browser:1.9.0")
    testImplementation(libs.junit)
    testImplementation(libs.org.jetbrains.kotlinx.coroutines.test)
    testImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation("androidx.test:core:1.7.0")
    androidTestImplementation("androidx.test:runner:1.7.0")
}

publishing {
    repositories { maven { name = "local"; url = uri(rootProject.layout.buildDirectory.dir("maven")) } }
}
afterEvaluate {
    publishing.publications.create<MavenPublication>("release") {
        from(components["release"])
        groupId = "cn.ksuser"
        artifactId = "ksuser-auth-android"
        version = project.version.toString()
        pom {
            name = "Ksuser Auth Android SDK"
            description = "Public-client OAuth and official OIDC app-to-app authorization for Android"
            url = "https://github.com/KsuserCN/Auth"
        }
    }
}
