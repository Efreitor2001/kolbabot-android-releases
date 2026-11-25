pluginManagement {
    repositories { google(); mavenCentral(); gradlePluginPortal() }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        // The Android test platform resolves Protobuf separately on GitHub runners.
        maven("https://maven-central.storage-download.googleapis.com/maven2/") {
            content { includeGroup("com.google.protobuf") }
        }
        mavenCentral()
        maven("https://developer.huawei.com/repo/") {
            content { includeGroupByRegex("com\\.huawei(\\..*)?") }
        }
    }
}
rootProject.name = "KolbaBot"
include(":app")
