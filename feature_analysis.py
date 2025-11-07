#!/usr/bin/env python3
"""
Analysiert die Landmark-Bilder und simuliert Feature-Extraktion
    !HINWEIS!: Dieses Skript ist optional und dient nur zur Entwicklungsanalyse/Testzwecke.
"""

import os
import glob

# Pfad zu den Landmark-Bildern
landmark_dir = "/Users/florian/Documents/GitHub/ar-walking/app/src/main/assets/landmark_images/"

def analyze_landmark_images():
    """Analysiert die verfügbaren Landmark-Bilder"""
    
    # Finde alle Bilder
    image_files = glob.glob(os.path.join(landmark_dir, "*.jpg"))
    
    print("🔍 LANDMARK-BILDER ANALYSE")
    print("=" * 50)
    
    if not image_files:
        print("❌ PROBLEM: Keine Landmark-Bilder gefunden!")
        return False
    
    print(f"📁 Gefundene Bilder: {len(image_files)}")
    
    for img_path in sorted(image_files):
        filename = os.path.basename(img_path)
        landmark_id = filename.replace('.jpg', '')
        file_size = os.path.getsize(img_path) / (1024*1024)  # MB
        
        print(f"  📸 {landmark_id}: {file_size:.1f} MB")
    
    # Simuliere erwartete Feature-Extraktion
    print("\n🎯 ERWARTETE FEATURE-EXTRAKTION")
    print("=" * 50)
    
    expected_features_per_image = 1500
    min_matches_needed = 20
    
    for img_path in sorted(image_files):
        filename = os.path.basename(img_path)
        landmark_id = filename.replace('.jpg', '')
        
        # Simulation basierend auf Bildgröße und -qualität
        file_size_mb = os.path.getsize(img_path) / (1024*1024)
        
        # Schätzung: Größere Bilder = mehr Details = mehr Features
        estimated_features = min(int(file_size_mb * 300), expected_features_per_image)
        
        # Matching-Wahrscheinlichkeit
        match_probability = "HOCH" if estimated_features > 800 else "MITTEL" if estimated_features > 400 else "NIEDRIG"
        
        print(f"  🔍 {landmark_id}: ~{estimated_features} Features ({match_probability})")
    
    print(f"\n📊 MATCHING-ANALYSE")
    print("=" * 50)
    print(f"  • ORB-Einstellung: {expected_features_per_image} Features pro Bild")
    print(f"  • Mindest-Matches: {min_matches_needed}")
    print(f"  • Erwartete Match-Rate bei guten Bildern: 60-80%")
    
    # Route-Analyse
    print(f"\n🗺️ ROUTE-INTEGRATION")
    print("=" * 50)
    
    route_landmarks = [
        "PT-1-86", "PT-1-697", "PT-1-566", "PT-1-764", 
        "PT-1-926", "PT-1-747", "PT-1-686"
    ]
    
    available_landmarks = [os.path.basename(f).replace('.jpg', '') for f in image_files]
    
    for landmark in route_landmarks:
        status = "✅ VERFÜGBAR" if landmark in available_landmarks else "❌ FEHLT"
        print(f"  {landmark}: {status}")
    
    missing_count = len([l for l in route_landmarks if l not in available_landmarks])
    coverage = (len(route_landmarks) - missing_count) / len(route_landmarks) * 100
    
    print(f"\n📈 ABDECKUNG: {coverage:.0f}% ({len(route_landmarks)-missing_count}/{len(route_landmarks)})")
    
    if coverage >= 80:
        print("🎉 SEHR GUT: Route-Abdeckung ausreichend!")
    elif coverage >= 60:
        print("⚠️ OK: Route-Abdeckung akzeptabel, aber verbesserbar")
    else:
        print("❌ PROBLEM: Route-Abdeckung zu niedrig!")
    
    return coverage >= 60

if __name__ == "__main__":


    success = analyze_landmark_images()
    
    print(f"\n{'='*50}")
    if success:
        print("✅ FAZIT: Feature-Matching sollte funktionieren!")
        print("💡 TIPP: Starte die App und prüfe die Logs für echte Feature-Counts")
    else:
        print("❌ FAZIT: Probleme beim Feature-Matching zu erwarten!")
        print("💡 TIPP: Mehr/bessere Landmark-Bilder hinzufügen")
    
    print(f"\n⚠️ HINWEIS: Dieses Skript ist ein Development-Tool und wird von der App nicht ausgeführt.")
    print(f"   Die tatsächliche Feature-Extraktion erfolgt in LandmarkMatchingManager.kt mit AKAZE.")
