# GLB/GLTF 3D-Modelle für AR-Navigation

Hier können Sie Ihre eigenen 3D-Modelle für die AR-Navigation platzieren.

## Unterstützte Formate:
- **GLB** (binäres GLTF) - empfohlen ✅
- **GLTF** (JSON + separate Dateien)

## Beispiel-Modelle:
- `navigation_arrow.glb` - 3D-Pfeil für Richtungsangaben
- `waypoint_marker.glb` - Wegpunkt-Marker
- `destination_flag.glb` - Ziel-Flagge

## Usage im Code:
```kotlin
// Laden Sie Ihr GLB-Modell:
val gltfRenderer = GLTFModelRenderer(context)
gltfRenderer.initialize()

// Platzieren Sie es automatisch bei Landmark-Erkennung:
renderer3D.addGLTFModel(
    modelPath = "models/navigation_arrow.glb", // ← Ihr GLB hier
    worldPosition = landmarkWorldPosition,
    scale = 0.2f, // 20cm Größe
    rotationY = navigationDirection
)
```

## Modell-Anforderungen:
- **Größe**: < 5MB pro Modell (für App-Performance)
- **Polygone**: < 10.000 Dreiecke (für mobile GPU)
- **Texturen**: Power-of-2 Größe (512x512, 1024x1024)
- **Format**: GLTF 2.0 Standard

## Empfohlene Tools:
- **Blender** (kostenlos) - Exportiert direkt zu GLB
- **Sketchfab** - Viele kostenlose AR-ready Modelle
- **Google Poly** - 3D-Modell-Bibliothek