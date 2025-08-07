# 3D Arrow Model für AR-Navigation

## Fehlende Datei: arrow.glb

Die App ist so konfiguriert, dass sie ein 3D-Pfeil-Modell (`arrow.glb`) aus diesem Verzeichnis lädt.

### So fügen Sie das 3D-Modell hinzu:

1. **Erstellen Sie ein 3D-Pfeil-Modell** in Blender, Maya oder einem anderen 3D-Programm
2. **Exportieren Sie es als GLB-Datei** (glTF Binary Format)
3. **Benennen Sie die Datei** `arrow.glb`
4. **Platzieren Sie sie** in diesem Verzeichnis: `app/src/main/assets/models/arrow.glb`

### Modell-Spezifikationen:

- **Format**: GLB (glTF Binary)
- **Größe**: Empfohlen < 1MB
- **Orientierung**: Pfeil zeigt in positive Z-Richtung
- **Farbe**: Bevorzugt weiß/neutral (wird programmatisch eingefärbt)
- **Komplexität**: Low-poly für bessere Performance

### Fallback-Verhalten:

Wenn `arrow.glb` nicht gefunden wird, verwendet die App automatisch 2D-Canvas-Rendering für den AR-Pfeil.

### Beispiel-Quellen für 3D-Modelle:

- [Sketchfab](https://sketchfab.com) (Suche nach "arrow")
- [TurboSquid](https://www.turbosquid.com)
- [Free3D](https://free3d.com)

**Hinweis**: Stellen Sie sicher, dass Sie die Lizenz des 3D-Modells beachten.