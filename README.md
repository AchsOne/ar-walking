# 🧭 ArWalking

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

