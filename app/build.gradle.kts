import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application) // Es una aplicación Android
    alias(libs.plugins.kotlin.android)      // Escrita en Kotlin
    alias(libs.plugins.kotlin.compose)      // Que usa Jetpack Compose
    alias(libs.plugins.kotlin.kapt)         // Procesador de anotaciones para Room
}

android {
    namespace = "cl.triskeledu.museo"
    compileSdk = 36

    defaultConfig {
        applicationId = "cl.triskeledu.museo"
        minSdk = 26        // Android 8.0 Oreo, igual que los ejemplos
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true // Activa Jetpack Compose
    }

    // Los esquemas que exporta Room se usan en la prueba de migración (androidTest).
    sourceSets["androidTest"].assets.srcDir("$projectDir/schemas")
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.core)

    // Room / SQLite
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    kapt(libs.androidx.room.compiler)

    // Mapa OpenStreetMap
    implementation(libs.osmdroid.android)

    // Escáner QR de Google: abre su propia pantalla de cámara y devuelve el texto leído.
    implementation(libs.play.services.code.scanner)
    // Generador de imágenes QR para probar los códigos de cada ficha.
    implementation(libs.zxing.core)

    debugImplementation(libs.androidx.ui.tooling)

    testImplementation(libs.junit)
    testImplementation(libs.org.json)
    androidTestImplementation(libs.androidx.room.testing)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.test.runner)
}

kapt {
    correctErrorTypes = true
    arguments {
        // Room exporta aquí un JSON por cada versión de la base (necesario para migraciones).
        arg("room.schemaLocation", "$projectDir/schemas")
    }
}
