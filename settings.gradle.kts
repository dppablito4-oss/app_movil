pluginManagement {
	// Kotlin 2.3 requiere R8 8.13.19 o superior para procesar sus metadatos.
	// Se fija aquí para mantener AGP 8.13 y evitar una migración prematura a AGP 9.
	buildscript {
		repositories {
			google()
			mavenCentral()
			maven {
				url = uri("https://storage.googleapis.com/r8-releases/raw")
			}
		}
		dependencies {
			classpath("com.android.tools:r8:8.13.19")
		}
	}

	repositories {
		gradlePluginPortal()
		google()
		mavenCentral()
	}
	plugins {
		id("com.android.application") version "8.13.1"
		id("org.jetbrains.kotlin.android") version "2.3.21"
		id("org.jetbrains.kotlin.plugin.compose") version "2.3.21"
		id("org.jetbrains.kotlin.plugin.serialization") version "2.3.21"
		id("com.google.devtools.ksp") version "2.3.11"
	}
}

dependencyResolutionManagement {
	repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
	repositories {
		google()
		mavenCentral()
	}
}

rootProject.name = "SpaceSale"
include(":app")
