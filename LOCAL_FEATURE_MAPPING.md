# Lokales Feature Mapping - Setup Guide

## 🎯 Übersicht

Das **lokale** Feature Mapping System läuft komplett auf dem Android-Gerät ohne Server oder Docker. Alle Trainingsbilder und Feature-Verarbeitung erfolgen direkt auf dem Handy.

## ✅ Was wurde implementiert

### 1. **LocalFeatureMapManager**
- Läuft komplett lokal ohne Server-Abhängigkeit
- Verwendet OpenCV für Feature-Extraktion (ORB-Features)
- Speichert Trainingsbilder direkt auf dem Gerät
- Automatische Feature-Map Erstellung und Verwaltung

### 2. **UI-Komponenten**
- **FeatureMatchOverlay**: Zeigt erkannte Landmarks in Echtzeit
- **TrainingImageCapture**: Interface zum Aufnehmen neuer Trainingsbilder
- **FeatureMappingStatusIndicator**: Status-Anzeige für das System

### 3. **Integration in Navigation**
- Automatisches Frame-Processing während der Navigation
- Echtzeit-Landmark-Erkennung mit Confidence-Anzeige
- Training-Button in der Navigations-UI

## 📱 Wie du Trainingsbilder hinzufügst

### Methode 1: Über die App (Empfohlen)

1. **Starte die Navigation**:
   - Öffne die AR Walking App
   - Wähle "Büro Prof. Dr. Ludwig (PT 3.0.84C)" → "Haupteingang"
   - Starte die Kamera-Navigation

2. **Training-Modus aktivieren**:
   - Tippe auf das 📍-Icon oben rechts (neben dem Stern)
   - Der Training-Dialog öffnet sich

3. **Landmark auswählen**:
   - Wähle ein existierendes Landmark aus der Liste
   - Oder erstelle ein neues mit "Neues Landmark erstellen"

4. **Foto aufnehmen**:
   - Richte die Kamera auf das markante Merkmal
   - Tippe auf "Foto"
   - Das Bild wird automatisch verarbeitet und gespeichert

### Methode 2: Direkt in Assets (für Entwicklung)

1. **Bilder vorbereiten**:
   ```bash
   # Erstelle das Verzeichnis falls nicht vorhanden
   mkdir -p app/src/main/assets/landmark_images
   
   # Kopiere deine Trainingsbilder
   cp /pfad/zu/deinen/bildern/*.jpg app/src/main/assets/landmark_images/
   ```

2. **Bilder benennen** nach dem Schema: `{landmark_id}.jpg`
   - `prof_ludwig_office.jpg` - Bürotür von Prof. Ludwig
   - `corridor_main.jpg` - Hauptkorridor im 3. Stock
   - `stairs_central.jpg` - Treppe im PT-Gebäude
   - `elevator_bank.jpg` - Aufzüge
   - `entrance_main.jpg` - Haupteingang

3. **App neu starten** - Die Bilder werden automatisch geladen

## 🎯 Empfohlene Landmarks für die Prof. Ludwig Route

Basierend auf der `route.json` solltest du Fotos von folgenden Stellen machen:

### Wichtige Landmarks:
1. **Prof. Ludwig's Bürotür** (`prof_ludwig_office.jpg`)
   - Türschild "Prof. Dr. Ludwig (PT 3.0.84C)"
   - Aus 2-3 verschiedenen Winkeln

2. **Hauptkorridor 3. Stock** (`corridor_main.jpg`)
   - Charakteristische Merkmale des Korridors
   - Beleuchtung, Türen, Schilder

3. **Treppe PT-Gebäude** (`stairs_central.jpg`)
   - Treppenhaus mit Stockwerk-Schildern
   - Geländer und markante Merkmale

4. **Aufzüge** (`elevator_bank.jpg`)
   - Aufzugstüren mit Stockwerk-Anzeige
   - Rufknöpfe und Schilder

5. **Haupteingang** (`entrance_main.jpg`)
   - Eingangstür des PT-Gebäudes
   - Gebäude-Schild oder Hausnummer

## 📋 Tipps für perfekte Trainingsbilder

### ✅ Gute Bilder:
- **Scharf und stabil** - keine Verwacklung
- **Gute Beleuchtung** - weder zu dunkel noch überbelichtet
- **Charakteristische Details** - Schilder, Logos, markante Formen
- **Mehrere Winkel** - 2-3 Bilder pro Landmark aus verschiedenen Blickwinkeln
- **Realistische Perspektive** - aus der Höhe, wie ein Nutzer das Landmark sehen würde

### ❌ Vermeide:
- Verwackelte oder unscharfe Bilder
- Zu viele Menschen im Bild (die sich bewegen)
- Temporäre Objekte (Poster, die oft gewechselt werden)
- Zu generische Merkmale (leere Wände ohne Details)
- Extreme Lichtverhältnisse (Gegenlicht, tiefe Schatten)

## 🔧 System-Status prüfen

### In der App:
1. **Status-Indikator**: Grüner Punkt oben rechts = System aktiv
2. **Erkannte Landmarks**: Werden oben links mit Confidence-Level angezeigt
3. **Training verfügbar**: 📍-Icon ist sichtbar wenn System läuft

### Debug-Logs:
```bash
# Logcat für Feature Mapping filtern
adb logcat | grep -E "(LocalFeatureMapManager|FeatureMatch|RouteViewModel)"
```

Wichtige Log-Nachrichten:
- `"Lokales Feature-Mapping erfolgreich initialisiert"` ✅
- `"Feature-Map geladen: X Landmarks"` ✅
- `"Feature-Matches gefunden: X"` ✅ (während Navigation)

## 🚀 Nächste Schritte

1. **Sammle Trainingsbilder**:
   - Gehe die Prof. Ludwig Route ab
   - Mache Fotos von allen wichtigen Landmarks
   - Teste verschiedene Beleuchtungssituationen

2. **Teste das System**:
   - Starte die Navigation
   - Prüfe, ob Landmarks erkannt werden
   - Achte auf die Confidence-Werte (sollten >60% sein)

3. **Optimiere bei Bedarf**:
   - Füge mehr Trainingsbilder hinzu wenn Erkennung schlecht
   - Entferne verwirrende Bilder
   - Passe Confidence-Schwellen an

## 🔧 Technische Details

### OpenCV Konfiguration:
- **Feature Detector**: ORB (schnell und robust)
- **Max Features**: 1000 pro Bild
- **Matching Threshold**: 50.0
- **Min Confidence**: 60%
- **Frame Processing**: Alle 500ms

### Dateisystem:
```
Android App:
├── assets/
│   ├── feature_map_default.json     # Standard Feature-Map
│   └── landmark_images/             # Trainingsbilder (Assets)
│       ├── prof_ludwig_office.jpg
│       └── ...
└── files/ (Laufzeit)
    ├── feature_maps/                # Lokale Feature-Maps
    ├── landmark_images/             # Heruntergeladene Bilder
    └── training_images/             # Zur Laufzeit aufgenommene Bilder
```

## 🐛 Troubleshooting

### Problem: "Feature-Mapping Initialisierung fehlgeschlagen"
**Lösung:**
1. Prüfe, ob OpenCV korrekt geladen wurde
2. Stelle sicher, dass `feature_map_default.json` in Assets existiert
3. Überprüfe App-Berechtigungen

### Problem: Keine Landmarks werden erkannt
**Lösung:**
1. Prüfe, ob Trainingsbilder vorhanden sind
2. Teste mit besserer Beleuchtung
3. Halte die Kamera stabiler
4. Prüfe Logs auf Fehler beim Feature-Matching

### Problem: Falsche Landmarks werden erkannt
**Lösung:**
1. Füge mehr spezifische Trainingsbilder hinzu
2. Entferne ähnliche/verwirrende Bilder
3. Erhöhe die Confidence-Schwelle

Das lokale Feature Mapping System ist jetzt einsatzbereit! 🎉

**Keine Server, kein Docker, keine Komplexität - alles läuft direkt auf deinem Handy!** 📱