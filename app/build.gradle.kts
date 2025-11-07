plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.example.arwalking"
    compileSdk = 36
    // Ensure Build Tools 35+ for 16KB ZIP alignment support
    buildToolsVersion = "35.0.0"

    defaultConfig {
        applicationId = "com.example.arwalking"
        minSdk = 24
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    // Use NDK r27 for 16KB page size support
    ndkVersion = "27.0.12077973"

    buildTypes {
        debug {
            buildConfigField("boolean", "DEBUG_FEATURE_MAPPING", "true")
        }
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            buildConfigField("boolean", "DEBUG_FEATURE_MAPPING", "false")
        }
    }

    buildFeatures {
        buildConfig = true
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlin {
        jvmToolchain(17)
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
    }
    
    packaging {
        jniLibs {
            pickFirsts += "**/libc++_shared.so"
            // Ensure modern packaging so libs remain properly aligned/uncompressed in APK
            useLegacyPackaging = false
        }
    }
}

dependencies {
    // AndroidX Core & Lifecycle
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)

    // OpenCV
    implementation(project(":sdk")) {
        exclude(group = "com.android.support")
    }

    // ARCore mit der neuesten Version
    implementation("com.google.ar:core:1.45.0")

    // Compose
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    // For KeyboardOptions and KeyboardActions:
    implementation("androidx.compose.ui:ui-text:1.7.6")
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.compose.runtime.livedata)
    implementation(libs.androidx.appcompat)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)

    // Compose Material Icons Extended (for CloudUpload, PhotoCamera etc.)
    implementation("androidx.compose.material:material-icons-extended:1.6.0")

    // Navigation
    implementation(libs.androidx.navigation.compose)

    // DataStore (persist favorites)
    implementation("androidx.datastore:datastore-preferences:1.1.1")

    // Camera (Jetpack CameraX)
    implementation(libs.androidx.camera.core)
    implementation("androidx.camera:camera-camera2:1.2.3")
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)

    // Sceneform für AR-Rendering (wird von ARCoreArrowView verwendet)
    implementation("com.google.ar.sceneform:core:1.17.1")
    implementation("com.google.ar.sceneform.ux:sceneform-ux:1.17.1")


    // Coroutines (for local processing)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.android)

    // Testing
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
    implementation(libs.gson)
}