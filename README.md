# 🧭 ArWalking

Eine Android-App für Augmented Reality Navigation.  
Die App verwendet die Kamera, um Navigationsinformationen in der realen Welt zu überlagern und Benutzer zu ihrem Ziel zu führen.

## 🚀 Features

- AR-basierte Navigation mit Kamera-Overlay
- Echtzeit-Wegfindung
- Lokale Landmark-Erkennung

## 🏗 Architektur

- **Sprache**: Kotlin  
- **Build-System**: Gradle mit Kotlin DSL  
- **UI**: Jetpack Compose  
- **AR & CV**: OpenCV für Feature-Matching  

## ⚙️ Installation

1. Repository klonen  
2. `./gradlew build`  
3. `./gradlew installDebug`

## 🧠 Automatische Verarbeitung

Die App wird automatisch:
1. Features aus den Bildern extrahieren
2. Die Features für schnelles Matching vorverarbeiten
3. Die Bilder im lokalen Cache speichern
4. Das Feature-Matching in Echtzeit durchführen


