# AR-Walking System Test Guide

## Übersicht

Dieses Dokument beschreibt, wie das AR-Walking-System getestet werden kann, um sicherzustellen, dass alle Komponenten korrekt funktionieren:

1. **Route-Auswahl** → Laden der JSON-Route
2. **Landmark-Zuordnung** → Mapping von JSON-Landmarks zu Feature-Landmarks  
3. **Feature-Matching** → Kamera-Frame-Analyse und Landmark-Erkennung
4. **3D-Pfeil-Anzeige** → Richtungsanzeige basierend auf erkannten Landmarks

## System-Komponenten

### 1. Route-Loading (HomeScreen → RouteViewModel)
- **Datei**: `route.json` in `app/src/main/assets/`
- **Funktion**: Lädt die vordefinierte Route von Prof. Ludwig's Büro zum Haupteingang
- **Test**: Überprüfe Logs für "Route erfolgreich aus JSON geladen"

### 2. Feature-Mapping (LocalFeatureMapManager)
- **Landmarks**: 
  - `prof_ludwig_office` (Startpunkt)
  - `entrance_main` (Tür/Ausgang)
  - `stairs_central` (Korridor)
  - `elevator_bank` (Haupteingang/Ziel)
- **Test**: Überprüfe Logs für "Lokales Feature-Mapping erfolgreich initialisiert"

### 3. 3D-Pfeil-System (AR3DArrowOverlay)
- **Richtungsberechnung**: Basiert auf Landmark-Typ und Route-Fortschritt
- **Animation**: Pulsierender und schwebender Effekt
- **Test**: 3D-Pfeil sollte bei erkannten Landmarks erscheinen

## Test-Schritte

### Schritt 1: App starten
1. Öffne die App
2. Überprüfe Logs in Android Studio/Logcat
3. Suche nach "AR-WALKING SYSTEM VALIDATION START"

### Schritt 2: Route auswählen
1. Wähle "Büro Prof. Dr. Ludwig (PT 3.0.84C)" als Start
2. Wähle "Haupteingang" als Ziel
3. Tippe auf "Navigation starten"
4. Kamera-Berechtigung gewähren

### Schritt 3: Feature-Matching testen
1. Richte Kamera auf verschiedene Objekte
2. Überprüfe Logs für "Feature-Matches gefunden"
3. Achte auf Confidence-Werte (sollten > 0.6 sein für Anzeige)

### Schritt 4: 3D-Pfeil überprüfen
1. Bei erkannten Landmarks sollte ein 3D-Pfeil erscheinen
2. Pfeil-Farbe zeigt Confidence an:
   - **Grün**: Sehr sicher (≥0.9)
   - **Hellgrün**: Sicher (≥0.8)
   - **Gelb**: Okay (≥0.7)
   - **Orange**: Unsicher (<0.7)

## Debug-Logs

### Wichtige Log-Tags:
- `RouteViewModel`: Route-Loading und Navigation
- `LocalFeatureMapManager`: Feature-Mapping und Matching
- `SystemValidator`: System-Validierung
- `CameraScreen`: Frame-Processing

### Beispiel-Logs:
```
I/SystemValidator: ✓ Route erfolgreich geladen
I/SystemValidator: ✓ Feature-Mapping initialisiert
I/LocalFeatureMapManager: ✓ Match gefunden: Büro Prof. Ludwig (confidence=0.850, matches=25)
I/RouteViewModel: Landmark Büro Prof. Ludwig für aktuellen Schritt erkannt
```

## Fehlerbehebung

### Problem: Keine Feature-Matches
**Lösung**: 
- Überprüfe, ob OpenCV korrekt initialisiert wurde
- Stelle sicher, dass Kamera-Berechtigung gewährt wurde
- Teste mit verschiedenen Objekten/Texturen

### Problem: 3D-Pfeil erscheint nicht
**Lösung**:
- Überprüfe Confidence-Werte in Logs (müssen ≥0.7 sein)
- Stelle sicher, dass Feature-Mapping aktiviert ist
- Überprüfe Bildschirmposition-Berechnung

### Problem: Falsche Pfeil-Richtung
**Lösung**:
- Überprüfe Landmark-ID-Zuordnung in `convertJsonLandmarkIdToFeatureId()`
- Validiere Richtungsberechnung in `calculateArrowDirection()`

## Erweiterte Tests

### Manueller Feature-Match-Test:
```kotlin
// In SystemValidator.kt
systemValidator.simulateFeatureMatching(routeViewModel, "prof_ludwig_office")
```

### Schritt-Navigation-Test:
```kotlin
// Manuell durch Schritte navigieren
routeViewModel.setCurrentNavigationStep(1)
routeViewModel.nextNavigationStep()
```

## Erwartete Ergebnisse

1. **Route wird geladen**: 4-5 Navigationsschritte aus JSON
2. **Landmarks werden zugeordnet**: Jeder Schritt hat mindestens 1 Landmark
3. **Feature-Matching funktioniert**: Confidence > 0.6 für erkannte Objekte
4. **3D-Pfeil wird angezeigt**: Bei erfolgreichen Matches mit korrekter Richtung

## Performance-Metriken

- **Route-Loading**: < 1 Sekunde
- **Feature-Mapping-Init**: < 2 Sekunden  
- **Frame-Processing**: ~500ms pro Frame
- **Feature-Matching**: 10-50 Matches bei guten Bedingungen

## Nächste Schritte

1. **Echte Bilder hinzufügen**: Trainingsbilder für Landmarks aufnehmen
2. **Server-Integration**: Bilder vom Server laden
3. **Automatische Progression**: Automatischer Schritt-Wechsel bei Landmark-Erkennung
4. **Kalibrierung**: Verbesserte Positions- und Richtungsberechnung