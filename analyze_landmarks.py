#!/usr/bin/env python3
"""
Analysiert die verfügbaren Landmarks aus der route.json für das Feature-based Mapping
"""

import json
import os
from collections import defaultdict

def analyze_landmarks():
    print("🗺️  AR Walking - Landmark Analyse")
    print("=" * 60)
    
    route_file = "app/src/main/assets/route.json"
    
    if not os.path.exists(route_file):
        print("❌ route.json nicht gefunden!")
        return
    
    with open(route_file, 'r', encoding='utf-8') as f:
        data = json.load(f)
    
    landmarks = []
    landmark_types = defaultdict(int)
    
    # Durchsuche alle Route-Teile nach Landmarks
    for path_item in data['route']['path']:
        for route_part in path_item['routeParts']:
            if 'landmarks' in route_part:
                for landmark in route_part['landmarks']:
                    landmark_id = landmark.get('id', '')
                    landmark_name = landmark.get('nameDe', '') or landmark.get('nameEn', '')
                    landmark_type = landmark.get('type', 'unknown')
                    x = landmark.get('x', 0)
                    y = landmark.get('y', 0)
                    
                    landmarks.append({
                        'id': landmark_id,
                        'name': landmark_name,
                        'type': landmark_type,
                        'x': x,
                        'y': y,
                        'instruction': route_part.get('instructionDe', '')
                    })
                    
                    landmark_types[landmark_type] += 1
    
    print(f"📍 Gefundene Landmarks: {len(landmarks)} Stück")
    print()
    
    # Zeige Landmark-Typen
    print("🏷️  Landmark-Typen:")
    for ltype, count in sorted(landmark_types.items()):
        print(f"   • {ltype}: {count} Stück")
    print()
    
    # Zeige wichtige Landmarks mit Namen
    print("📋 Wichtige Landmarks (mit Namen):")
    important_landmarks = [l for l in landmarks if l['name'].strip()]
    
    for landmark in important_landmarks:
        print(f"   🏢 {landmark['name']}")
        print(f"      ID: {landmark['id']}")
        print(f"      Typ: {landmark['type']}")
        print(f"      Position: ({landmark['x']}, {landmark['y']})")
        print(f"      Kontext: {landmark['instruction'][:60]}...")
        print()
    
    # Zeige alle Landmark-IDs für Bilder
    print("📸 Benötigte Trainingsbilder:")
    print("   Erstelle Fotos für diese Landmark-IDs:")
    print()
    
    for landmark in landmarks:
        if landmark['name'].strip() or landmark['type'] in ['Office', 'doorway', 'Entry']:
            suggested_filename = f"{landmark['id'].replace('-', '_').lower()}.jpg"
            description = landmark['name'] if landmark['name'].strip() else f"{landmark['type']} (ID: {landmark['id']})"
            print(f"   📷 {suggested_filename}")
            print(f"      Beschreibung: {description}")
            print(f"      Typ: {landmark['type']}")
    
    print()
    print("💾 Aktueller Status der Trainingsbilder:")
    
    # Prüfe vorhandene Bilder
    landmark_images_dir = "app/src/main/assets/landmark_images"
    if os.path.exists(landmark_images_dir):
        existing_images = [f for f in os.listdir(landmark_images_dir) if f.endswith('.jpg')]
        print(f"   Vorhandene Bilder: {len(existing_images)}")
        
        if existing_images:
            for img in existing_images:
                print(f"   ✅ {img}")
        else:
            print("   ❌ Keine Trainingsbilder vorhanden!")
    else:
        print("   ❌ landmark_images Verzeichnis nicht gefunden!")
    
    print()
    print("🔧 Nächste Schritte:")
    print("   1. Gehe zum PT-Gebäude, 3. Stock")
    print("   2. Mache Fotos von Prof. Ludwigs Büro (PT 3.0.84C)")
    print("   3. Fotografiere wichtige Türen und Eingänge")
    print("   4. Speichere Bilder als: pt_1_86.jpg, pt_1_566.jpg, etc.")
    print("   5. Kopiere sie nach app/src/main/assets/landmark_images/")

if __name__ == "__main__":
    analyze_landmarks()