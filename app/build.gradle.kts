plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.devtools.ksp")
}

android {
    namespace = "com.example.posapp"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.example.posapp"
        minSdk = 21
        targetSdk = 34
        versionCode = 1
        versionName = "0.1"
    }

    buildFeatures {
        compose = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.3"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    val roomVersion = "2.6.1"

    implementation("androidx.core:core-ktx:1.10.1")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("androidx.activity:activity-compose:1.9.0")

    // Compose
    implementation("androidx.compose.ui:ui:1.5.0")
    implementation("androidx.compose.material:material:1.5.0")
    implementation("androidx.compose.ui:ui-tooling-preview:1.5.0")
    implementation("androidx.compose.material:material-icons-extended:1.5.0")
    implementation("androidx.navigation:navigation-compose:2.6.0")

    // Lifecycle
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.6.1")

    // Coil for images
    implementation("io.coil-kt:coil-compose:2.4.0")

    // Gson for JSON export (or use kotlinx.serialization)
    implementation("com.google.code.gson:gson:2.10.1")

    // --- AGREGA ESTO PARA LA BASE DE DATOS (ROOM) ---
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1") // Para usar corutinas
    ksp("androidx.room:room-compiler:2.6.1") // El compilador mágico

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")

    // Preferences DataStore para configuración de usuario
    implementation("androidx.datastore:datastore-preferences:1.0.0")
}
