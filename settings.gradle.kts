pluginManagement {
	repositories {
		gradlePluginPortal()
		google()
		mavenCentral()
	}
	plugins {
		id("com.android.application") version "8.13.1"
		id("org.jetbrains.kotlin.android") version "1.9.10"
		id("com.google.devtools.ksp") version "1.9.10-1.0.13"
	}
}

dependencyResolutionManagement {
	repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
	repositories {
		google()
		mavenCentral()
	}
}

rootProject.name = "PABLITO FAST"
include(":app")
