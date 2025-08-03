# Training Mode und 3D AR-Pfeil Implementierung

## Übersicht

Diese Dokumentation beschreibt die Implementierung der beiden Hauptfeatures:

1. **Training Mode**: Echte Kamera-Aufnahme mit automatischem Server-Upload
2. **3D AR-Pfeil**: Feature-based Mapping mit 3D-Pfeil-Anzeige

## 1. Training Mode

### Funktionalität
- Öffnet die echte Gerätekamera (nicht simuliert)
- Ermöglicht die Auswahl eines Landmarks mit korrekter ID
- Nimmt Fotos auf und lädt sie automatisch auf den Server hoch
- Zeigt Upload-Status in Echtzeit an

### Implementierung

#### TrainingImageCapture.kt
- **RealCameraCaptureContent**: Neue Komponente mit echter Kamera-Integration
- **captureImageFromCamera**: Funktion für echte Bildaufnahme
- **imageProxyToBitmap**: Konvertierung von Kamera-Frames zu Bitmap
- **Automatischer Upload**: Nach erfolgreicher Aufnahme wird das Bild sofort hochgeladen

#### Wichtige Änderungen:
```kotlin
// Echte Kamera-Aufnahme statt Simulation
imageCapture.takePicture(
    object : ImageCapture.OnImageCapturedCallback() {
        override fun onCaptureSuccess(image: ImageProxy) {
            val bitmap = imageProxyToBitmap(image)
            onCapture(bitmap) // Automatischer Upload
        }
    }
)
```

### Verwendung
1. Im Navigationsbildschirm auf das Training-Icon klicken
2. Landmark aus der Liste auswählen (mit korrekter ID)
3. Kamera öffnet sich automatisch
4. Foto aufnehmen
5. Automatischer Upload auf Server mit Landmark-ID

## 2. 3D AR-Pfeil Feature

### Funktionalität
- Erkennt Landmarks über Feature-based Mapping (OpenCV)
- Zeigt einen 3D-Pfeil an der erkannten Position an
- Pfeil ist fest im Raum positioniert und folgt der Richtungsangabe
- Animierter Pfeil mit Pulsieren und Schweben

### Implementierung

#### AR3DArrowOverlay.kt
- **Animated3DArrowOverlay**: Hauptkomponente für 3D-Pfeil-Anzeige
- **draw3DArrowShape**: Zeichnet 3D-Pfeil mit Schatten und Glanz-Effekten
- **calculateArrowPosition**: Berechnet Position basierend auf Feature-Matches
- **Animation**: Pulsieren und Schweben für lebendigen AR-Effekt

#### LocalFeatureMapManager.kt Erweiterungen
- **calculateScreenPosition**: Berechnet Bildschirmposition der erkannten Features
- **calculateEstimatedDistance**: Schätzt Entfernung basierend auf Feature-Größe
- **Verbesserte Feature-Matches**: Mehr Daten für präzise AR-Positionierung

#### FeatureMatchResult Erweiterung
```kotlin
data class FeatureMatchResult(
    val landmark: FeatureLandmark,
    val matchCount: Int,
    val confidence: Float,
    val distance: Float? = null,           // Neue Entfernungsschätzung
    val screenPosition: PointF? = null     // Neue Bildschirmposition
)
```

### 3D-Pfeil Eigenschaften
- **Farbe**: Basierend auf Confidence (Grün = sicher, Gelb = okay, Orange = unsicher)
- **Größe**: Skaliert mit Confidence-Level
- **Animation**: Pulsiert und schwebt für Aufmerksamkeit
- **Position**: Fest im Raum, basierend auf erkannten Features
- **Richtung**: Zeigt in Navigationsrichtung

### Verwendung
1. Feature Mapping wird automatisch aktiviert wenn verfügbar
2. Kamera erkennt Landmarks über OpenCV Feature-Matching
3. Bei Confidence ≥ 70% wird 3D-Pfeil angezeigt
4. Pfeil zeigt Position und Richtung des erkannten Landmarks

## Integration in Navigation.kt

### 3D-Pfeil Integration
```kotlin
// 3D Arrow Overlay (main AR feature)
Animated3DArrowOverlay(
    matches = featureMatches,
    isFeatureMappingEnabled = isFeatureMappingEnabled,
    screenWidth = screenWidth,
    screenHeight = screenHeight,
    modifier = Modifier.fillMaxSize()
)
```

### Training Mode Integration
```kotlin
// Training Image Capture Dialog
TrainingImageCapture(
    isVisible = showTrainingCapture,
    onDismiss = { showTrainingCapture = false },
    onCaptureImage = { bitmap, landmark ->
        // Automatischer Upload mit korrekter Landmark-ID
        routeViewModel.saveTrainingImage(
            landmarkId = landmark.id,
            bitmap = bitmap,
            landmarkName = landmark.name,
            description = landmark.description
        )
    }
)
```

## Technische Details

### Kamera-Berechtigungen
- CAMERA-Berechtigung wird automatisch angefordert
- Fallback-Verhalten bei fehlenden Berechtigungen

### Server-Upload
- Multipart Form Data Upload
- Automatische Retry-Logik
- Lokale Speicherung als Fallback

### Feature-Matching
- OpenCV ORB Feature-Detector
- BF Matcher für Feature-Vergleich
- Confidence-basierte Filterung
- Bildschirmposition-Berechnung

### Performance
- Asynchrone Verarbeitung
- Frame-Rate-Optimierung (alle 500ms)
- Memory-Management für Bitmaps

## Konfiguration

### Server-Endpoints
```kotlin
// Android Emulator
"http://10.0.2.2:8080/landmark_images/upload"

// Echtes Gerät im lokalen Netzwerk
"http://192.168.1.100:8080/landmark_images/upload"
```

### Feature-Matching Parameter
```kotlin
val orb = ORB.create(
    1000,  // maxFeatures
    1.2f,  // scaleFactor
    8,     // nlevels
    31,    // edgeThreshold
    0,     // firstLevel
    2,     // WTA_K
    ORB.HARRIS_SCORE,
    31,    // patchSize
    20     // fastThreshold
)
```

## Debugging

### Logs
- `TrainingImageCapture`: Kamera-Aufnahme und Upload-Status
- `LocalFeatureMapManager`: Feature-Matching und Positionsberechnung
- `RouteViewModel`: Koordination zwischen Komponenten

### Debug-Overlays
- `FeatureMatchDebugOverlay`: Zeigt Feature-Match-Details
- `FeatureMappingStatusIndicator`: Status des Feature-Mappings

## Zukünftige Verbesserungen

1. **Echte 3D-Modelle**: Integration von glTF-Loader für arrow.glb
2. **Kamera-Kalibrierung**: Präzisere AR-Positionierung
3. **SLAM-Integration**: Verbesserte räumliche Verfolgung
4. **Machine Learning**: Bessere Landmark-Erkennung
5. **Cloud-Synchronisation**: Automatische Feature-Map-Updates

## Troubleshooting

### Häufige Probleme
1. **Kamera öffnet sich nicht**: Berechtigungen prüfen
2. **Upload schlägt fehl**: Server-Verbindung und Endpoints prüfen
3. **3D-Pfeil erscheint nicht**: Feature-Matching-Confidence zu niedrig
4. **Performance-Probleme**: Frame-Rate-Einstellungen anpassen

### Lösungsansätze
- Logs in Android Studio überprüfen
- Server-Status testen
- Feature-Map-Qualität verbessern
- Kamera-Einstellungen optimieren