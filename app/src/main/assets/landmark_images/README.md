# Landmark Training Images

Dieses Verzeichnis enthält die Trainingsbilder für das Feature Matching System.

## Landmark-IDs aus final-route.json

Die Route verwendet folgende eindeutige Landmark-IDs:

### Benötigte Landmark-Bilder:
- `PT-1-566.jpg` - Tür/Durchgang (landmarkFromInstruction)
- `PT-1-764.jpg` - Tür/Durchgang (landmarkFromInstruction)  
- `PT-1-926.jpg` - Tür/Durchgang (landmarkFromInstruction)
- `PT-1-747.jpg` - Tür/Durchgang (landmarkFromInstruction)
- `PT-1-686.jpg` - Tür/Durchgang (landmarkFromInstruction)

### Alternative Dateinamen (falls Bindestriche Probleme machen):
- `PT_1_566.jpg`
- `PT_1_764.jpg`
- `PT_1_926.jpg`
- `PT_1_747.jpg`
- `PT_1_686.jpg`

## Wie füge ich neue Trainingsbilder hinzu?

### Methode 1: Direkt in Assets (für Entwicklung)

1. Mache Fotos von den Landmarks mit deinem Handy
2. Benenne die Bilder nach dem Schema: `{landmark_id}.jpg` (z.B. `PT-1-566.jpg`)
3. Kopiere die Bilder in dieses Verzeichnis
4. Die App lädt automatisch Features aus der `final-route.json`

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