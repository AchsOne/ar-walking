# 📸 Landmark Images Directory

Dieses Verzeichnis enthält die Landmark-Bilder für die ArWalking App. Die Bilder werden direkt aus diesem Ordner geladen - **kein Trainingsmodus erforderlich!**

## 📂 Verzeichnis-Pfad
```
/Users/florian/Documents/GitHub/ar-walking/landmark_images/
```

## 🖼️ Unterstützte Bildformate
- **JPEG**: `.jpg`, `.jpeg`
- **PNG**: `.png`
- **WebP**: `.webp`

## 📝 Datei-Namenskonvention

Die Bilder sollten nach folgendem Schema benannt werden:
```
{landmark_id}.{extension}
```

### Beispiele:
```
PT-1-86.jpg                     # Professor Ludwig's Büro (PT 3.0.84C)
PT-1-566.png                    # Tür/Doorway
PT-1-697.jpeg                   # Entry Point
PT-1-692.webp                   # Branch Point
PT-1-691.jpg                    # Branch Point
```

## 🎯 Erforderliche Landmark-IDs

**WICHTIG**: Die Bilder müssen exakt die Landmark-IDs aus der route.json verwenden!

Basierend auf der aktuellen Route benötigst du Bilder für folgende Landmarks:

### **Aus der route.json extrahierte Landmark-IDs:**
- `PT-1-86` - Prof. Ludwig Büro (PT 3.0.84C) - Office
- `PT-1-566` - Tür/Doorway 
- `PT-1-697` - Entry Point
- `PT-1-764` - Landmark aus Route
- `PT-1-926` - Landmark aus Route
- `PT-1-747` - Landmark aus Route
- `PT-1-686` - Landmark aus Route

**Vollständige Liste der benötigten Bilder:**
```
PT-1-86.jpg      # Prof. Ludwig Büro (PT 3.0.84C)
PT-1-566.jpg     # Tür/Doorway
PT-1-697.jpg     # Entry Point
PT-1-764.jpg     # Landmark aus Route
PT-1-926.jpg     # Landmark aus Route
PT-1-747.jpg     # Landmark aus Route
PT-1-686.jpg     # Landmark aus Route
```

### **Weitere Landmarks aus der Route:**
Überprüfe deine route.json für weitere Landmark-IDs und erstelle entsprechende Bilder.

## 📏 Bildanforderungen

### **Optimale Bildgröße**
- **Auflösung**: 1024x768 bis 2048x1536 Pixel
- **Seitenverhältnis**: 4:3 oder 16:9
- **Dateigröße**: 200KB - 2MB pro Bild

### **Qualitätsrichtlinien**
- ✅ **Scharfe Bilder** - Keine verwackelten Aufnahmen
- ✅ **Gute Beleuchtung** - Vermeide zu dunkle oder überbelichtete Bereiche
- ✅ **Charakteristische Merkmale** - Türschilder, Logos, markante Objekte
- ✅ **Verschiedene Winkel** - Mehrere Perspektiven desselben Landmarks
- ✅ **Konsistente Qualität** - JPEG-Qualität 85-95%

### **Was vermeiden**
- ❌ Verwackelte oder unscharfe Bilder
- ❌ Zu dunkle oder überbelichtete Aufnahmen
- ❌ Bilder mit vielen Personen (Datenschutz)
- ❌ Temporäre Objekte (Baustellen, Werbung)

## 🚀 Bilder hinzufügen

### **Methode 1: Direkt kopieren**
1. Mache Fotos mit deinem Handy oder Kamera
2. Übertrage die Bilder auf deinen Mac
3. Benenne sie nach dem Schema `{landmark_id}.{extension}`
4. Kopiere sie in diesen Ordner
5. Die App lädt sie automatisch beim nächsten Start

### **Methode 2: Drag & Drop**
1. Öffne diesen Ordner im Finder
2. Ziehe die Bilder direkt hinein
3. Benenne sie entsprechend um

## 🔄 Automatische Verarbeitung

Die App wird automatisch:
- ✅ **Bilder scannen** beim App-Start
- ✅ **Thumbnails generieren** (256x256px)
- ✅ **LRU-Cache verwenden** für schnelle Ladezeiten
- ✅ **Performance optimieren** (Ziel: 5-15ms Ladezeit)

## 📊 Performance-Ziele

| Operation | Ziel | Beschreibung |
|-----------|------|--------------|
| **Vollbild laden** | 5-15ms | Aus lokalem Cache oder Datei |
| **Thumbnail laden** | 1-3ms | Aus Cache (automatisch generiert) |
| **Bildsuche** | <1ms | Durchsuchen verfügbarer Bilder |

## 🛠️ Debugging

### **Logs prüfen**
Die App loggt alle Bildoperationen mit dem Tag `ProjectDirectoryImageManager`:
```
adb logcat | grep ProjectDirectoryImageManager
```

### **Verfügbare Bilder anzeigen**
```kotlin
val imageManager = ProjectDirectoryImageManager(context)
val landmarks = imageManager.getAvailableLandmarks()
landmarks.forEach { landmark ->
    Log.d("Images", "${landmark.id}: ${landmark.filename} (${landmark.getFileSizeKB()} KB)")
}
```

### **Cache-Statistiken**
```kotlin
val cacheStats = imageManager.getCacheStats()
Log.d("Cache", "Bitmap Hit Rate: ${cacheStats.getBitmapHitRate()}%")
Log.d("Cache", "Thumbnail Hit Rate: ${cacheStats.getThumbnailHitRate()}%")
```

## 📁 Beispiel-Struktur

```
landmark_images/
├── PT-1-86.jpg                     # 1.2 MB, 1920x1440 - Prof. Ludwig Büro
├── PT-1-566.png                    # 800 KB, 1024x768 - Tür/Doorway
├── PT-1-697.jpeg                   # 650 KB, 1600x1200 - Entry Point
├── PT-1-692.jpg                    # 900 KB, 1280x960 - Branch Point
├── PT-1-691.webp                   # 400 KB, 1024x768 - Branch Point
└── PT-1-690.jpg                    # 750 KB, 1200x900 - Branch Point
```

## 🔧 Erweiterte Konfiguration

### **Cache-Größen anpassen**
In `ProjectDirectoryImageManager.kt`:
```kotlin
const val BITMAP_CACHE_SIZE = 50        # Anzahl Vollbilder im Cache
const val THUMBNAIL_CACHE_SIZE = 100    # Anzahl Thumbnails im Cache
const val THUMBNAIL_SIZE = 256          # Thumbnail-Größe in Pixeln
```

### **Unterstützte Formate erweitern**
```kotlin
val extensions = listOf("jpg", "jpeg", "png", "webp", "bmp", "gif")
```

## ✅ Vorteile dieser Lösung

- 🚀 **Kein Trainingsmodus** - Bilder einfach hinzufügen und fertig
- 📱 **Offline-First** - Alle Bilder lokal verfügbar
- ⚡ **Hochperformant** - LRU-Cache für schnelle Ladezeiten
- 🔧 **Einfache Verwaltung** - Direkte Dateiverwaltung im Finder
- 🎯 **Flexibel** - Verschiedene Bildformate unterstützt
- 🛡️ **DSGVO-konform** - Keine Daten verlassen das Gerät

## 🎉 Los geht's!

1. Mache Fotos von den gewünschten Landmarks
2. Kopiere sie in diesen Ordner
3. Benenne sie entsprechend
4. Starte die App - die Bilder werden automatisch erkannt!

**Tipp**: Beginne mit 3-5 wichtigen Landmarks und erweitere dann schrittweise.