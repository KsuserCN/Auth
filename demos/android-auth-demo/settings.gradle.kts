pluginManagement {
    repositories { google(); mavenCentral(); gradlePluginPortal() }
}
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        providers.gradleProperty("KSUSER_SDK_REPOSITORY").orNull?.let { repository ->
            maven { url = uri(repository) }
        }
    }
}
rootProject.name = "Ksuser Android Auth Demo"
include(":app")
if (!providers.gradleProperty("KSUSER_SDK_REPOSITORY").isPresent) {
    includeBuild("../../sdk/android") {
        dependencySubstitution {
            substitute(module("cn.ksuser:ksuser-auth-android")).using(project(":sdk"))
        }
    }
}
