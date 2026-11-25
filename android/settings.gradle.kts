pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        // GeckoView artifacts live on Mozilla's Maven, not on Google/Maven Central.
        maven { url = uri("https://maven.mozilla.org/maven2/") }
    }
}

rootProject.name = "HoriBrowser"
include(":app")