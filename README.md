# ARWalking

## ARCore Frame Pipeline: Direct Y-Plane (Performance)

This app now processes ARCore camera frames using the Y-Plane (luma) directly from ARCore’s YUV_420_888 images. This avoids the previous NV21 → JPEG → Bitmap conversion, substantially reducing CPU cost, GC pressure, and latency for feature extraction.

- What changed
  - ARCoreArrowView acquires the Image via frame.acquireCameraImage() and converts the Y plane to an OpenCV Mat (CV_8UC1) without creating a Bitmap.
  - RouteViewModel exposes processGrayMatForFeatureMatching(grayMat: Mat), which runs the same AKAZE + Hamming matching pipeline as before, but starting from grayscale Mats.
  - The user-visible camera remains unfiltered; preprocessing is only done off-screen for feature extraction.

- Why this is faster
  - No JPEG compression/decompression and no Bitmap allocations per frame.
  - Fewer large heap allocations → fewer GC pauses.
  - Direct grayscale is ideal for AKAZE and matching.

- Resolution and rate
  - Frames are downscaled to a practical max dimension (≈1280 px) for a good speed/quality tradeoff.
  - Processing is throttled (default: every 500 ms) to keep UI responsive and AR tracking stable.

- Fallback
  - If Y-Plane extraction fails for any reason, the legacy Bitmap path is still available as a fallback.

- How to switch back (if needed)
  - In ARCoreArrowView.tryAcquireCameraImage, call processFrameForFeatureMatching(bitmap) with the legacy imageToBitmap(image) path and disable the imageToGrayMat(image) branch.

No changes are required to your assets or landmarks; the feature cache and matching logic remain the same.

Eine Android-App für Augmented Reality Navigation.  
Die App verwendet die Kamera, um Navigationsinformationen in der realen Welt zu überlagern und Benutzer zu ihrem Ziel zu führen.

## 🚀 Features

- AR-basierte Navigation mit Kamera-Overlay
- Echtzeit-Wegfindung
- Lokale Landmark-Erkennung mit Computer Vision
- Vollständig offline, keine Internet-Verbindung nötig

## 🏗 Architektur

- **Sprache**: Kotlin  
- **Build-System**: Gradle mit Kotlin DSL  
- **UI**: Jetpack Compose  
- **AR & CV**: OpenCV für Feature-Matching  

## ⚙️ Installation

1. Repository klonen  
2. `./gradlew build`  
3. `./gradlew installDebug`  

## 📁 Storage-System (ArWalking Storage Package)

Dieses Package implementiert die komplette lokale Speicher-Architektur für ArWalking.  
**Kein Trainingsmodus erforderlich** – Bilder werden direkt aus dem Projektverzeichnis geladen.

### Package-Struktur
```
com.example.arwalking.storage/
├── ArWalkingStorageManager.kt      # Haupt-Storage-Manager (Facade)
├── ProjectDirectoryImageManager.kt # Lädt Bilder aus Projektverzeichnis
├── StorageDirectoryManager.kt      # Verzeichnis-Verwaltung
├── StorageConfig.kt                # Konfiguration & Konstanten
├── StoragePerformanceMonitor.kt    # Performance-Überwachung
├── OptimizedImageManager.kt        # Optimierte Bild-Verwaltung (Fallback)
├── LocalImageStorage.kt            # Lokale Speicherung & Upload-Queue
├── ProjectImageTester.kt           # Test-Klasse für Projektbilder
└── README.md                       # Diese Dokumentation
```

## 🔑 Hauptkomponenten

### ArWalkingStorageManager (Facade)
- Einheitliche API für alle Storage-Operationen  
- Vereint alle Komponenten  
- Beispiel:
```kotlin
val storageManager = ArWalkingStorageManager(context)
val result = storageManager.saveImage(bitmap, "landmark_001", "Büro", "Beschreibung")
val thumbnail = storageManager.loadThumbnail("landmark_001")
```

### StorageDirectoryManager
- Verwaltet alle Verzeichnisse:
  - `landmark_images/` – Vollbilder (max. 2048px)
  - `landmark_thumbnails/` – Thumbnails (256x256px)
  - `landmark_metadata/` – JSON-Metadaten
  - `feature_maps/` – Computer-Vision-Daten

### StorageConfig
- Globale Konfiguration: Performance-Ziele, Dateigrößen, Cache-Limits  

### StoragePerformanceMonitor
- Überwacht Ladezeiten, Cache-Hit-Rate und Erfolgsquote  
- Ziele:
  - Bild laden: 5–15 ms  
  - Thumbnail: 1–3 ms  
  - Suche: <1 ms  
  - Upload: 50–200 ms  

### LocalImageStorage
- Offline-Speicherung mit Upload-Queue  
- DSGVO-konforme lokale Speicherung  

## 🧠 Automatische Verarbeitung

Die App wird automatisch:
1. Features aus den Bildern extrahieren (ORB-Features)
2. Die Features für schnelles Matching vorverarbeiten
3. Die Bilder im lokalen Cache speichern
4. Das Feature-Matching in Echtzeit durchführen

## 🧩 Verwendung

### Initialisierung
```kotlin
class MainActivity : ComponentActivity() {
    private lateinit var storageManager: ArWalkingStorageManager
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        storageManager = ArWalkingStorageManager(this)
    }
}
```

### Beispiel: Bild speichern
```kotlin
val result = storageManager.saveImage(
    bitmap = capturedBitmap,
    landmarkId = "prof_office_001",
    landmarkName = "Professor Büro",
    description = "Büro im 2. Stock",
    category = "Training"
)
```

## 📊 Performance-Ziele

| Operation | Ziel | Implementierung |
|-----------|------|----------------|
| Bild laden | 5–15 ms | LRU-Cache + optimierte I/O |
| Thumbnail | 1–3 ms | Separater Cache |
| Suche | <1 ms | In-Memory-Index |
| Upload | 50–200 ms | Asynchrone Komprimierung |
| Cache-Hit-Rate | >80 % | LRU-Algorithmus |

## 🔒 Sicherheit

- App-interne Speicherung: `/data/data/com.example.arwalking/files/`  
- Keine externen Zugriffe  
- Automatische Bereinigung defekter Dateien  
- Regelmäßige Integritätsprüfung  

## 📈 Skalierung

- Optimiert für 1000+ Bilder  
- Paginierung (20 Bilder pro Seite)  
- Lazy Loading  
- Asynchrone I/O  

## 🧹 Wartung

```kotlin
val cleanupResult = storageManager.cleanup()
println("${cleanupResult.totalFilesRemoved} Dateien entfernt")
```

## 🐛 Debugging
```kotlin
storageManager.logPerformanceSummary()
storageManager.resetPerformanceMetrics()
```

