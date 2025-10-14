# build.gradle Dependencies für Snapchat-Style AR System

Füge diese Dependencies zu deiner `app/build.gradle` (Module: App) Datei hinzu:

```kotlin
dependencies {
    // Bestehende Dependencies bleiben...
    
    // === Snapchat-Style AR Navigation System ===
    
    // ARCore & Sceneform für 3D-Rendering
    implementation 'com.google.ar:core:1.41.0'
    implementation 'com.google.ar.sceneform:core:1.17.1' 
    implementation 'com.google.ar.sceneform:animation:1.17.1'
    implementation 'com.google.ar.sceneform:assets:1.17.1'
    
    // Compose BOM (falls nicht vorhanden)
    implementation platform('androidx.compose:compose-bom:2024.02.00')
    implementation 'androidx.compose.ui:ui'
    implementation 'androidx.compose.ui:ui-tooling-preview'
    implementation 'androidx.compose.material3:material3'
    
    // Activity Compose
    implementation 'androidx.activity:activity-compose:1.8.2'
    
    // Lifecycle & Coroutines
    implementation 'androidx.lifecycle:lifecycle-runtime-ktx:2.7.0'
    implementation 'org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3'
    
    // JSON Parsing (Gson - falls nicht vorhanden)
    implementation 'com.google.code.gson:gson:2.10.1'
    
    // Permissions
    implementation 'androidx.core:core-ktx:1.12.0'
    
    // Debugging (Optional)
    debugImplementation 'androidx.compose.ui:ui-tooling'
    debugImplementation 'androidx.compose.ui:ui-test-manifest'
}
```

## Android Block Ergänzungen

Füge auch diese Konfiguration zu deiner `build.gradle` hinzu:

```kotlin
android {
    compileSdk 34

    defaultConfig {
        applicationId "com.example.arwalking"
        minSdk 24  // ARCore benötigt mindestens API 24
        targetSdk 34
        // ...
    }
    
    buildFeatures {
        compose true
    }
    
    compileOptions {
        sourceCompatibility JavaVersion.VERSION_17
        targetCompatibility JavaVersion.VERSION_17
    }
    
    kotlinOptions {
        jvmTarget = "17"
    }
    
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.8"
    }
    
    // Für ARCore/Sceneform
    packagingOptions {
        pickFirst "**/libc++_shared.so"
        pickFirst "**/libjsc.so"
    }
}
```

## Proguard/R8 Regeln

Erstelle `proguard-rules.pro` falls nicht vorhanden:

```
# ARCore
-keep class com.google.ar.** { *; }

# Sceneform  
-keep class com.google.ar.sceneform.** { *; }

# Gson
-keep class com.example.arwalking.** { *; }
```

## Nachdem Dependencies hinzugefügt:

1. **Sync Project** in Android Studio
2. **Clean & Rebuild** Project  
3. **Stelle sicher** dass `arrow.glb` in `app/src/main/assets/models/` existiert
4. **Starte** die `ARNavigationActivity`

## Testen der Integration:

Die neue Activity zeigt:
- ✅ Simulierte Landmark-Erkennung alle 5 Sekunden
- ✅ 3D-Pfeile fest im Raum platziert (Snapchat-Style)  
- ✅ Automatische Routenanalyse aus route.json
- ✅ Status-Overlay mit Informationen
- ✅ Kamera-Berechtigungen Management

Die Simulation läuft automatisch durch deine route.json Landmarks:
- Prof. Ludwig Office → Büro Eingang → Türen → Aufzug → etc.

Jeder erkannte Landmark wird mit der passenden Pfeil-Richtung angezeigt!