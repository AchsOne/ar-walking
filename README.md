🧭 ArWalking

Android‑App für AR‑Navigation per Kamera‑Overlay.  
Die App erkennt lokale Landmarken in Kamerabildern und blendet darauf basierend Navigationshinweise ein.

🚀 Features

•  AR‑Overlay (Pfeile/Text) in Jetpack Compose (Canvas)
•  Landmarken‑Matching mit AKAZE (MLDB) + BFMatcher (Hamming), KNN(2) + Lowe‑Ratio
•  Schrittfortschaltung entlang einer vorgegebenen Route (offline)
•  Optionales Info‑Overlay (Keypoints, Match‑Zahlen, Konfidenz, Framezeiten)

🏗 Architektur

•  Sprache: Kotlin (App), Java‑Bindings für OpenCV
•  Build: Gradle (Kotlin DSL)
•  UI: Jetpack Compose
•  CV: OpenCV (AKAZE/MLDB) + BFMatcher (Hamming); kein RANSAC/keine Poseschätzung
•  Daten: Landmark‑Assets lokal (assets/landmark_images), Route‑JSON lokal; _L/_R‑Varianten für Abbiegungen
•  Caching: In‑Memory‑Cache der extrahierten Features (nicht der Bilder)

⚙️ Installation

Voraussetzungen
•  Android Studio (aktuell), Android SDK/Build‑Tools
•  Testgerät mit Kamera (mind. Android 8.0 empfohlen), Kamera‑Berechtigung

Schritte
1. Repository klonen
2. In Android Studio öffnen, Gradle Sync
3. App auf ein Gerät installieren (Run/Install Debug)

Alternativ (CLI)
•  ./gradlew assembleDebug  
•  ./gradlew installDebug

▶️ Nutzung

•  Route und Landmarken sind lokal eingebunden.
•  Kamera öffnen, Gerät kurz auf die Landmarke richten; bei bestätigtem Match wird der nächste Schritt ausgelöst und der Overlay‑Pfeil eingeblendet.
•  Seitliche Landmarken (z. B. Fahrstuhl/Ziel) ggf. aktiv ins Sichtfeld nehmen.

🧠 Wie es funktioniert (Kurz)

1) CameraX liefert Frames  
2) AKAZE extrahiert Keypoints + MLDB‑Deskriptoren  
3) Matching gegen gecachte Route‑Landmarken via BFMatcher (Hamming), KNN(2) + Lowe‑Ratio  
4) Aus Matches werden Match‑Zahl/Confidence berechnet  
5) Bei Schwellwert‑Erfüllung: Schrittfortschaltung + Overlay‑Update

Stabilisierung: 500 ms Frame‑Intervall, Mindest‑Konfidenz/Mindest‑Matchanzahl.  
Keine Homographie/RANSAC‑Poseschätzung.

📁 Daten & Assets

•  Route: lokale JSON
•  Landmarken‑Bilder: assets/landmark_images/<id>.jpg (+ optional <id>_L.jpg / <id>_R.jpg)
•  Features: Laufzeit‑Extraktion; im Arbeitsspeicher gecached

⚠️ Bekannte Grenzen

•  Keine Poseschätzung: Ausrichtung aus Routen-/Step‑Kontext, nicht aus Homographie
•  Seitliche/verdeckt liegende Landmarken erfordern aktives Ausrichten der Kamera
•  Sehr schnelle Bewegung kann das Matching kurzzeitig beeinträchtigen
•  Landmarken wurden im Prototyp meist aus einer Richtung erfasst

🔒 Datenschutz

•  Vollständig offline: keine Cloud, keine Übertragung.  
•  Kamerabilder werden nur in‑memory verarbeitet, nicht gespeichert.

📜 Lizenzen

•  OpenCV (BSD‑3‑Clause), AndroidX/Jetpack Compose/Kotlin (Apache‑2.0)  
•  Lizenzhinweise siehe Third‑Party‑Notices/Lizenzen im Repository
