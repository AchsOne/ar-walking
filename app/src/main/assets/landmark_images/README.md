# Landmark Training Images

Dieses Verzeichnis enthält die Trainingsbilder für das Feature Mapping System.

## Wie füge ich neue Trainingsbilder hinzu?

### Methode 1: Direkt in Assets (für Entwicklung)

1. Mache Fotos von den Landmarks mit deinem Handy
2. Benenne die Bilder nach dem Schema: `{landmark_id}.jpg`
3. Kopiere die Bilder in dieses Verzeichnis
4. Aktualisiere die `feature_map_default.json` falls nötig

### Methode 2: Über die App (zur Laufzeit)

1. Öffne die AR Walking App
2. Gehe zur Kamera-Navigation
3. Halte die Kamera auf ein Landmark
4. Tippe auf den "Training Image" Button (falls implementiert)
5. Das Bild wird automatisch gespeichert und verarbeitet

## Beispiel-Landmarks für die aktuelle Route

Basierend auf der `route.json` solltest du Fotos von folgenden Stellen machen:

### Prof. Ludwig's Büro Route:
- `prof_ludwig_office.jpg` - Bürotür von Prof. Ludwig (PT 3.0.84C)
- `corridor_main.jpg` - Hauptkorridor im 3. Stock
- `stairs_central.jpg` - Treppe im PT-Gebäude
- `elevator_bank.jpg` - Aufzüge im PT-Gebäude
- `entrance_main.jpg` - Haupteingang des PT-Gebäudes
- `exit_sign.jpg` - Notausgangsschild

## Tipps für gute Trainingsbilder:

1. **Gute Beleuchtung** - Vermeide zu dunkle oder überbelichtete Bilder
2. **Scharfe Bilder** - Keine verwackelten Aufnahmen
3. **Verschiedene Winkel** - Mache mehrere Bilder aus verschiedenen Blickwinkeln
4. **Charakteristische Features** - Achte auf markante Merkmale (Türschilder, Logos, etc.)
5. **Konsistente Größe** - Bilder sollten etwa 1024x768 Pixel haben
6. **JPEG Format** - Verwende JPEG mit guter Qualität (85-95%)

## Dateiformat:

```
landmark_images/
├── entrance_main.jpg
├── stairs_central.jpg
├── elevator_bank.jpg
├── prof_ludwig_office.jpg
├── corridor_main.jpg
└── exit_sign.jpg
```

## Automatische Verarbeitung:

Die App wird automatisch:
1. Features aus den Bildern extrahieren (ORB-Features)
2. Die Features für schnelles Matching vorverarbeiten
3. Die Bilder im lokalen Cache speichern
4. Das Feature Matching in Echtzeit durchführen

## Debugging:

Falls das Feature Matching nicht funktioniert:
1. Überprüfe die Logs nach "LocalFeatureMapManager"
2. Stelle sicher, dass die Bilder geladen werden
3. Prüfe, ob Features extrahiert werden
4. Teste mit verschiedenen Beleuchtungsbedingungen