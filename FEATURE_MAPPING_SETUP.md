# Feature-based Mapping Setup für AR Walking

## Übersicht

Dieses Setup implementiert Feature-based Mapping für die AR Walking Navigation mit OpenCV. Das System besteht aus:

1. **Android App** mit OpenCV Integration
2. **Python FastAPI Server** für Feature-Map Management
3. **Trainingsbilder-Upload** und Verarbeitung
4. **Lokale und Server-basierte Speicherung**

## 🚀 Quick Start

### 1. Server starten (Lokale Entwicklung)

```bash
# In das Server-Verzeichnis wechseln
cd server_example

# Python Virtual Environment erstellen
python -m venv venv
source venv/bin/activate  # Linux/Mac
# oder
venv\Scripts\activate     # Windows

# Dependencies installieren
pip install -r requirements.txt

# Server starten
python main.py
```

Der Server läuft dann auf `http://localhost:8080`

### 2. Mit Docker (Empfohlen)

```bash
cd server_example

# Server mit Docker Compose starten
docker-compose up -d

# Logs anzeigen
docker-compose logs -f feature-mapping-api

# Server stoppen
docker-compose down
```

### 3. Android App konfigurieren

Die App ist bereits konfiguriert und verwendet:
- **Entwicklung**: `http://10.0.2.2:8080/` (Android Emulator)
- **Gerät im lokalen Netzwerk**: `http://192.168.1.100:8080/`

## 📱 Android App Features

### Implementierte Funktionen

1. **Feature-Map Loading**
   - Lädt Feature-Maps vom Server
   - Lokaler Fallback bei Netzwerkproblemen
   - Automatische Synchronisation

2. **Real-time Feature Matching**
   - ORB Feature-Detection mit OpenCV
   - Live-Matching gegen Landmark-Datenbank
   - Confidence-basierte Filterung

3. **Training Image Upload**
   - Erfassung von Trainingsbildern über Kamera
   - Automatischer Upload an Server
   - Hintergrund-Verarbeitung

4. **Navigation Integration**
   - Erweiterte NavigationRoute mit Feature-Landmarks
   - Alternative Landmarks pro Navigationsschritt
   - Confidence-basierte Wegfindung

### Verwendung in der App

```kotlin
// Feature-Mapping initialisieren
routeViewModel.initializeFeatureMapping(context, useRealServer = false)

// Feature-Navigation laden
routeViewModel.loadFeatureNavigationRoute(context, "building_name", floor = 0)

// Kamera-Frame verarbeiten
routeViewModel.processFrameForFeatureMatching(cameraFrame)

// Trainings-Bild hochladen
routeViewModel.uploadTrainingImage(landmarkId, bitmap)
```

## 🔧 Konfiguration

### Android App Konfiguration

Die Konfiguration erfolgt über `FeatureMappingConfig.kt`:

```kotlin
// Server-URLs
const val DEV_BASE_URL = "http://10.0.2.2:8080/"
const val PROD_BASE_URL = "https://api.arwalking.com/"

// OpenCV Parameter
const val ORB_MAX_FEATURES = 1000
const val MIN_MATCH_CONFIDENCE = 0.6f
const val MATCH_DISTANCE_THRESHOLD = 50.0f
```

### Server Konfiguration

Umgebungsvariablen für den Server:

```bash
# .env Datei erstellen
ENVIRONMENT=development
LOG_LEVEL=INFO
MAX_UPLOAD_SIZE=10485760  # 10MB
DATABASE_URL=postgresql://user:pass@localhost:5432/arwalking
REDIS_URL=redis://localhost:6379
```

## 📊 API Endpoints

### Feature-Maps
- `GET /api/featuremap/{building}/{floor}` - Feature-Map laden
- `GET /api/featuremap/{building}/{floor}/version` - Version prüfen
- `POST /api/featuremap/sync` - Synchronisation

### Training Images
- `POST /api/training/upload` - Trainings-Bild hochladen
- `GET /api/training/status/{imageId}` - Verarbeitungsstatus
- `GET /api/landmarks/{building}/{floor}` - Landmarks auflisten

### Utilities
- `GET /api/stats` - Server-Statistiken
- `GET /images/{imageName}` - Bild herunterladen

## 🏗️ Architektur

### Android App Komponenten

```
RouteViewModel
├── FeatureMapManager
│   ├── OpenCV Feature Detection (ORB)
│   ├── Feature Matching
│   └── Local Caching
├── FeatureMapServerApi
│   ├── Retrofit HTTP Client
│   ├── Image Upload/Download
│   └── Synchronization
└── OpenCvCameraActivity
    ├── Real-time Processing
    ├── UI Overlay
    └── Training Image Capture
```

### Server Komponenten

```
FastAPI Server
├── Feature Map Management
├── Training Image Processing
│   ├── OpenCV Feature Extraction
│   ├── Storage Management
│   └── Background Processing
├── API Endpoints
└── File Storage
    ├── Original Images
    ├── Processed Features
    └── Feature Maps
```

## 📁 Datei-Struktur

### Android App
```
app/src/main/java/com/example/arwalking/
├── FeatureMappingModels.kt      # Datenmodelle
├── FeatureMapManager.kt         # Feature-Verarbeitung
├── FeatureMapServerApi.kt       # Server-Kommunikation
├── FeatureMappingConfig.kt      # Konfiguration
├── RouteViewModel.kt            # Erweitert für Feature-Mapping
└── OpenCvCameraActivity.kt      # Erweitert für Feature-UI
```

### Server
```
server_example/
├── main.py                      # FastAPI Server
├── requirements.txt             # Python Dependencies
├── Dockerfile                   # Container Build
├── docker-compose.yml           # Multi-Service Setup
└── storage/                     # Datenspeicher
    ├── images/                  # Trainingsbilder
    ├── features/                # Extrahierte Features
    └── maps/                    # Feature-Maps
```

## 🔍 Testing

### Android App testen

1. **Emulator starten** und App installieren
2. **Server starten** (siehe Quick Start)
3. **OpenCV Camera Activity** öffnen
4. **Feature-Matching** testen mit Kamera
5. **Trainings-Bilder** erfassen und hochladen

### Server testen

```bash
# API Dokumentation öffnen
open http://localhost:8080/docs

# Feature-Map laden
curl http://localhost:8080/api/featuremap/default_building/0

# Server-Statistiken
curl http://localhost:8080/api/stats
```

## 🚀 Deployment

### Lokale Entwicklung
- Python Server mit `python main.py`
- Android App im Debug-Modus

### Staging/Produktion
- Docker Container mit `docker-compose up`
- Cloud-Deployment (AWS, GCP, Azure)
- Load Balancer und CDN für Bilder

### Cloud-Optionen

#### AWS Deployment
```yaml
# ECS Task Definition
services:
  feature-mapping:
    image: your-registry/feature-mapping:latest
    environment:
      - DATABASE_URL=postgresql://...
      - S3_BUCKET=arwalking-images
```

#### Google Cloud Run
```yaml
# cloud-run.yaml
apiVersion: serving.knative.dev/v1
kind: Service
metadata:
  name: feature-mapping-api
spec:
  template:
    spec:
      containers:
      - image: gcr.io/project/feature-mapping:latest
        env:
        - name: DATABASE_URL
          value: postgresql://...
```

## 📈 Monitoring & Performance

### Metriken
- Feature-Matching Erfolgsrate
- API Response Times
- Upload-Geschwindigkeit
- Speicherverbrauch

### Logging
- Strukturierte Logs mit JSON
- Error Tracking
- Performance Monitoring

### Optimierungen
- Feature-Map Caching
- Bild-Kompression
- Batch-Processing
- CDN für statische Inhalte

## 🔒 Sicherheit

### API Security
- Rate Limiting
- Input Validation
- File Upload Limits
- CORS Configuration

### Daten-Schutz
- Verschlüsselte Übertragung (HTTPS)
- Sichere Speicherung
- Anonymisierung von Metadaten

## 🐛 Troubleshooting

### Häufige Probleme

1. **OpenCV nicht gefunden**
   ```bash
   # Android: OpenCV Modul prüfen
   # Server: pip install opencv-python
   ```

2. **Netzwerk-Verbindung fehlgeschlagen**
   ```kotlin
   // Android Emulator: 10.0.2.2 statt localhost
   // Gerät: IP-Adresse des Entwicklungsrechners
   ```

3. **Feature-Matching zu langsam**
   ```kotlin
   // ORB_MAX_FEATURES reduzieren
   // Bild-Auflösung verringern
   ```

4. **Server-Upload fehlgeschlagen**
   ```python
   # MAX_UPLOAD_SIZE erhöhen
   # Timeout-Werte anpassen
   ```

## 🔄 Nächste Schritte

1. **Erweiterte Features**
   - SLAM Integration
   - Deep Learning Features
   - Collaborative Mapping

2. **Performance Optimierung**
   - GPU-Beschleunigung
   - Edge Computing
   - Predictive Caching

3. **Produktions-Deployment**
   - CI/CD Pipeline
   - Monitoring Setup
   - Backup-Strategien

## 📚 Weitere Ressourcen

- [OpenCV Android Documentation](https://docs.opencv.org/4.x/d0/d6c/tutorial_py_table_of_contents_android.html)
- [FastAPI Documentation](https://fastapi.tiangolo.com/)
- [Android Camera2 API](https://developer.android.com/reference/android/hardware/camera2/package-summary)
- [Feature Detection Algorithms](https://docs.opencv.org/4.x/db/d27/tutorial_py_table_of_contents_feature2d.html)

---

**Status**: ✅ Implementierung abgeschlossen und getestet
**Version**: 1.0.0
**Letzte Aktualisierung**: $(date)