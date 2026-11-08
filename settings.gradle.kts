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
    }
}

rootProject.name = "AI Classroom"
include(":app")
include(":feature-exam")
include(":feature-tools-fs")
include(":feature-tools-net")
include(":feature-plugin")
