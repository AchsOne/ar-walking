# 📁 ArWalking Storage Package

Dieses Package implementiert die komplette lokale Speicher-Architektur für ArWalking. **Kein Trainingsmodus erforderlich** - Bilder werden direkt aus dem Projektverzeichnis geladen!

## 📂 Package-Struktur

```
com.example.arwalking.storage/
├── ArWalkingStorageManager.kt      # Haupt-Storage-Manager (Facade)
├── ProjectDirectoryImageManager.kt # 🆕 Lädt Bilder aus Projektverzeichnis
├── StorageDirectoryManager.kt      # Verzeichnis-Verwaltung
├── StorageConfig.kt                # Konfiguration & Konstanten
├── StoragePerformanceMonitor.kt    # Performance-Überwachung
├── OptimizedImageManager.kt        # Optimierte Bild-Verwaltung (Fallback)
├── LocalImageStorage.kt            # Lokale Speicherung & Upload-Queue
├── ProjectImageTester.kt           # 🆕 Test-Klasse für Projektbilder
└── README.md                       # Diese Dokumentation
```

## 🎯 Hauptkomponenten

### **ArWalkingStorageManager** (Facade)
- **Zweck**: Einheitliche API für alle Storage-Operationen
- **Features**: Vereint alle Storage-Komponenten
- **Verwendung**: Haupteinstiegspunkt für die App

```kotlin
val storageManager = ArWalkingStorageManager(context)
val result = storageManager.saveImage(bitmap, "landmark_001", "Büro", "Beschreibung")
val thumbnail = storageManager.loadThumbnail("landmark_001")
```

### **StorageDirectoryManager**
- **Zweck**: Verwaltet die Verzeichnisstruktur
- **Features**: Erstellt und überwacht alle Storage-Verzeichnisse
- **Verzeichnisse**:
  - `landmark_images/` - Vollbilder (max. 2048px)
  - `landmark_thumbnails/` - Vorschaubilder (256x256px)
  - `landmark_metadata/` - JSON-Metadaten
  - `feature_maps/` - Computer Vision Daten

### **StorageConfig**
- **Zweck**: Zentrale Konfiguration aller Storage-Parameter
- **Features**: Performance-Ziele, Dateigrößen, Cache-Limits
- **Konstanten**: Alle Werte aus der Architektur-Spezifikation

### **StoragePerformanceMonitor**
- **Zweck**: Überwacht Performance-Ziele
- **Features**: Misst Ladezeiten, Cache-Hit-Rate, Success-Rate
- **Ziele**:
  - Bild laden: 5-15ms
  - Thumbnail: 1-3ms
  - Suche: <1ms
  - Upload: 50-200ms

### **OptimizedImageManager**
- **Zweck**: Hochperformante Bild-Verwaltung
- **Features**: LRU-Cache, Lazy Loading, Paginierung
- **Cache**: 50 Vollbilder + 100 Thumbnails

### **LocalImageStorage**
- **Zweck**: Fallback-Speicherung für Upload-Warteschlange
- **Features**: Offline-Speicherung, Upload-Queue-Management

## 🚀 Verwendung

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

### Bild speichern
```kotlin
// Vollständige Speicherung mit Thumbnail und Metadaten
val result = storageManager.saveImage(
    bitmap = capturedBitmap,
    landmarkId = "prof_office_001",
    landmarkName = "Professor Büro",
    description = "Büro im 2. Stock",
    category = "Training"
) { progress ->
    // Progress-Updates
    Log.d("Storage", progress)
}
```

### Bilder laden
```kotlin
// Thumbnail laden (schnell, 1-3ms)
val thumbnail = storageManager.loadThumbnail("prof_office_001")

// Vollbild laden (5-15ms)
val fullImage = storageManager.loadFullImage("prof_office_001")
```

### Paginierte Abfrage
```kotlin
// Lade 20 Bilder pro Seite
val result = storageManager.getLandmarksPaged(
    page = 0,
    pageSize = 20,
    searchQuery = "büro",
    category = "Training"
)

result.items.forEach { landmark ->
    println("${landmark.name}: ${landmark.description}")
}
```

### Performance-Monitoring
```kotlin
// Performance-Status abrufen
val status = storageManager.getStorageStatus()
println("Gesundheit: ${status.getHealthStatus()}")
println("Cache-Hit-Rate: ${status.cacheHitRate}%")
println("Durchschnittliche Ladezeit: ${status.averageLoadTimeMs}ms")

// Performance-Log ausgeben
storageManager.logPerformanceSummary()
```

## 📊 Performance-Ziele

| Operation | Ziel | Implementierung |
|-----------|------|----------------|
| **Bild laden** | 5-15ms | LRU-Cache + optimierte I/O |
| **Thumbnail** | 1-3ms | Separater Thumbnail-Cache |
| **Suche** | <1ms | In-Memory-Index |
| **Upload** | 50-200ms | Asynchrone Komprimierung |
| **Cache-Hit-Rate** | >80% | LRU-Algorithmus |

## 🔧 Konfiguration

### Cache-Größen
```kotlin
// Automatische Optimierung basierend auf verfügbarem RAM
val availableMemoryMB = 512 // Beispiel
val cacheConfig = storageManager.calculateOptimalCacheConfig(availableMemoryMB)
println("Optimale Bitmap-Cache-Größe: ${cacheConfig.bitmapCacheSize}")
```

### Speicher-Schätzung
```kotlin
// Schätze Speicherverbrauch für 100 Bilder
val estimate = storageManager.estimateStorageUsage(100)
println("Geschätzte Größe: ${estimate.totalSizeMB} MB")
```

## 🧹 Wartung

### Bereinigung
```kotlin
// Automatische Bereinigung
val cleanupResult = storageManager.cleanup()
println("${cleanupResult.totalFilesRemoved} Dateien entfernt")
println("${cleanupResult.totalSpaceFreedMB} MB freigegeben")
```

### Status-Überwachung
```kotlin
// Verzeichnis-Integrität prüfen
val status = storageManager.getStorageStatus()
if (!status.isHealthy) {
    Log.w("Storage", "Storage-System benötigt Wartung!")
}
```

## 📱 Offline-First Design

- ✅ **100% Offline-fähig** - Keine Internet-Verbindung erforderlich
- ✅ **Lokale Speicherung** - Alle Daten bleiben auf dem Gerät
- ✅ **Sofortige Verfügbarkeit** - Keine Wartezeiten
- ✅ **DSGVO-konform** - Keine Daten verlassen das Gerät
- ✅ **Performance-optimiert** - Ziele werden eingehalten

## 🔒 Sicherheit

- **App-interne Speicherung**: `/data/data/com.example.arwalking/files/`
- **Keine externen Zugriffe**: Nur die App kann auf die Daten zugreifen
- **Automatische Bereinigung**: Defekte Dateien werden automatisch entfernt
- **Integrität-Prüfung**: Regelmäßige Validierung der Datenstruktur

## 📈 Skalierung

Das Storage-System ist für **1000+ Bilder** optimiert:
- **Paginierung**: Nur 20 Bilder werden gleichzeitig geladen
- **LRU-Cache**: Intelligente Speicher-Verwaltung
- **Lazy Loading**: Bilder werden nur bei Bedarf geladen
- **Asynchrone I/O**: Keine UI-Blockierung

## 🐛 Debugging

### Performance-Logs aktivieren
```kotlin
// Detaillierte Performance-Logs
storageManager.logPerformanceSummary()

// Performance-Metriken zurücksetzen
storageManager.resetPerformanceMetrics()
```

### Status-Informationen
```kotlin
val status = storageManager.getStorageStatus()
Log.d("Storage", "Bilder: ${status.totalImages}")
Log.d("Storage", "Größe: ${status.totalSizeMB} MB")
Log.d("Storage", "Gesundheit: ${status.getHealthStatus()}")
```