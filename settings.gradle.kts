pluginManagement {
    repositories {
        google()            // Plugins de Android (AGP)
        mavenCentral()      // Plugins de Kotlin y otros
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "MuseoDeLosMuseos"

include(":app")
