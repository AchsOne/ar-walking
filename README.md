# ArWalking

Eine Android-App für Augmented Reality Navigation. Die App verwendet die Kamera, um Navigationsinformationen in der realen Welt zu überlagern und Benutzer zu ihrem Ziel zu führen.

## Features

- AR-basierte Navigation mit Kamera-Overlay
- Echtzeit-Wegfindung
- Lokale Landmark-Erkennung mit Computer Vision
- Vollständig offline, keine Internet-Verbindung nötig

## Architektur

- **Sprache**: Kotlin
- **Build-System**: Gradle mit Kotlin DSL
- **UI**: Jetpack Compose
- **AR**: OpenCV für Feature-Matching

## Installation

1. Projekt klonen
2. `./gradlew build`
3. `./gradlew installDebug`

## Verwendung

1. App starten
2. Startpunkt und Ziel wählen
3. Kamera-Navigation öffnen
4. AR-Anweisungen folgen