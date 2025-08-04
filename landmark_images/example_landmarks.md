# 📋 Beispiel-Landmarks für ArWalking

Diese Datei zeigt, welche Landmark-Bilder du für eine vollständige Navigation hinzufügen solltest.

## 🏢 PT-Gebäude (Physik/Technik)

### **Eingang & Orientierung**
- `pt_entrance_main.jpg` - Haupteingang des PT-Gebäudes
- `pt_entrance_side.jpg` - Seiteneingang (falls vorhanden)
- `pt_building_sign.jpg` - Gebäudeschild "PT"
- `campus_map_board.jpg` - Campus-Übersichtstafel

### **Vertikale Navigation**
- `pt_elevator_bank.jpg` - Aufzüge im Erdgeschoss
- `pt_stairs_central.jpg` - Haupttreppe (Erdgeschoss)
- `pt_stairs_floor2.jpg` - Treppe zum 2. Stock
- `pt_stairs_floor3.jpg` - Treppe zum 3. Stock

### **Horizontale Navigation**
- `pt_corridor_floor1.jpg` - Hauptkorridor 1. Stock
- `pt_corridor_floor2.jpg` - Hauptkorridor 2. Stock  
- `pt_corridor_floor3.jpg` - Hauptkorridor 3. Stock
- `pt_corridor_junction.jpg` - Korridor-Kreuzung

### **Ziel: Professor Ludwig's Büro**
- `prof_ludwig_office.jpg` - Bürotür PT 3.0.84C
- `prof_ludwig_nameplate.jpg` - Namensschild an der Tür
- `pt_room_384c.jpg` - Raumnummer 3.0.84C
- `pt_floor3_section.jpg` - Bereich um das Büro

### **Notausgänge & Sicherheit**
- `pt_exit_emergency.jpg` - Notausgangsschild
- `pt_fire_extinguisher.jpg` - Feuerlöscher (Orientierungspunkt)
- `pt_emergency_plan.jpg` - Fluchtplan

## 🎯 Prioritätsliste

### **Phase 1: Grundausstattung (5 Bilder)**
1. `pt_entrance_main.jpg` - Startpunkt
2. `pt_elevator_bank.jpg` - Vertikale Navigation
3. `pt_corridor_floor3.jpg` - Horizontale Navigation
4. `prof_ludwig_office.jpg` - Zielpunkt
5. `pt_exit_emergency.jpg` - Sicherheit

### **Phase 2: Erweiterte Navigation (10 Bilder)**
6. `pt_stairs_central.jpg` - Alternative zu Aufzug
7. `pt_corridor_junction.jpg` - Orientierung im Korridor
8. `prof_ludwig_nameplate.jpg` - Eindeutige Identifikation
9. `pt_building_sign.jpg` - Gebäude-Identifikation
10. `campus_map_board.jpg` - Campus-Orientierung

### **Phase 3: Vollständige Abdeckung (15+ Bilder)**
11. `pt_entrance_side.jpg` - Alternative Eingänge
12. `pt_corridor_floor1.jpg` - Vollständige Stockwerk-Abdeckung
13. `pt_corridor_floor2.jpg` - Vollständige Stockwerk-Abdeckung
14. `pt_stairs_floor2.jpg` - Detaillierte Treppenhäuser
15. `pt_stairs_floor3.jpg` - Detaillierte Treppenhäuser

## 📸 Foto-Tipps

### **Aufnahme-Winkel**
- **Frontal**: Direkt vor dem Objekt (Türen, Schilder)
- **Schräg**: 45° Winkel für räumliche Tiefe
- **Weitwinkel**: Für Korridore und große Räume
- **Detail**: Nahaufnahmen von Schildern und Nummern

### **Beleuchtung**
- **Tageslicht**: Beste Qualität bei natürlichem Licht
- **Kunstlicht**: Gleichmäßige Beleuchtung in Innenräumen
- **Vermeiden**: Gegenlicht, starke Schatten, Überbelichtung

### **Komposition**
- **Zentriert**: Wichtige Objekte in der Bildmitte
- **Horizontal**: Gerade Linien für professionelles Aussehen
- **Scharf**: Autofokus auf das wichtigste Element
- **Sauber**: Keine temporären Objekte (Personen, Taschen)

## 🔧 Technische Spezifikationen

### **Dateinamen-Beispiele**
```
pt_entrance_main.jpg           # Haupteingang
prof_ludwig_office.jpg         # Büro (Ziel)
pt_corridor_floor3.jpg         # Korridor 3. Stock
pt_elevator_bank.jpg           # Aufzüge
pt_stairs_central.jpg          # Zentrale Treppe
```

### **Bildqualität**
- **Format**: JPEG (.jpg) bevorzugt
- **Auflösung**: 1920x1440 oder 1600x1200
- **Qualität**: 90-95% JPEG-Komprimierung
- **Dateigröße**: 500KB - 2MB pro Bild

### **Metadaten (optional)**
Du kannst zusätzliche JSON-Dateien mit Metadaten erstellen:
```json
// prof_ludwig_office.json
{
  "id": "prof_ludwig_office",
  "name": "Professor Ludwig's Büro",
  "description": "Büro PT 3.0.84C im 3. Stock",
  "building": "PT",
  "floor": 3,
  "room": "3.0.84C",
  "category": "destination",
  "keywords": ["büro", "professor", "ludwig", "ziel"]
}
```

## ✅ Checkliste

### **Vor dem Fotografieren**
- [ ] Kamera/Handy vollständig geladen
- [ ] Ausreichend Speicherplatz verfügbar
- [ ] Liste der gewünschten Landmarks erstellt
- [ ] Optimale Tageszeit gewählt (gute Beleuchtung)

### **Beim Fotografieren**
- [ ] Mehrere Winkel pro Landmark
- [ ] Scharfe, gut beleuchtete Bilder
- [ ] Charakteristische Merkmale erfasst
- [ ] Keine Personen im Bild (Datenschutz)

### **Nach dem Fotografieren**
- [ ] Bilder auf Mac übertragen
- [ ] Nach Schema umbenannt
- [ ] In `/Users/florian/Documents/GitHub/ar-walking/landmark_images/` kopiert
- [ ] App getestet - Bilder werden erkannt
- [ ] Performance geprüft (Ladezeiten)

## 🚀 Schnellstart

1. **Mache 5 Fotos**: Eingang, Aufzug, Korridor, Büro, Ausgang
2. **Benenne sie um**: `pt_entrance_main.jpg`, `pt_elevator_bank.jpg`, etc.
3. **Kopiere sie** in den `landmark_images` Ordner
4. **Starte die App** - die Bilder werden automatisch erkannt!

**Fertig!** Du hast jetzt eine funktionierende AR-Navigation ohne Trainingsmodus.