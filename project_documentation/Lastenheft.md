# Lastenheft – *AR Walking*

---

## 1. Projektziele

| **ID** | **Ziel** | **Beschreibung** | **Priorität** |
|:------:|-----------|------------------|:--------------:|
| 1 | Unterstützung bei der Navigation | Nutzer sollen mithilfe der Smartphone-Kamera visuell durch eine Umgebung geführt werden. | Muss |
| 2 | Erweiterte Realität als Orientierungshilfe | Informationen und Wegmarkierungen sollen in Echtzeit in die Kameraansicht eingeblendet werden. | Muss |
| 3 | Lokale Orientierung ohne GPS | Die App soll gänzlich ohne GPS funktionieren, um auch in Innenräumen oder abgeschirmten Bereichen nutzbar zu sein. | Muss |
| 4 | Nutzerfreundlichkeit | Die Bedienung soll intuitiv erfolgen, ohne dass technisches Vorwissen nötig ist. | Muss |
| 5 | Forschungszweck / Prototyp | Das Projekt dient als Demonstrator für AR-basierte Wegführung und als Grundlage für zukünftige Erweiterungen. | Soll |

---

## 2. Systemkontext

**Beschreibung:**  
AR Walking ist eine Android-App, die die Kamera des Geräts verwendet, um reale Objekte bzw. Wegpunkte zu erkennen und diese durch virtuelle Overlays zu ergänzen.  
Das System interagiert ausschließlich mit der Kamera, dem internen Speicher und optional mit externen Dateien (z. B. gespeicherten Wegpunkten).

### Systemgrenzen
- **Innerhalb des Systems:** Bildverarbeitung (OpenCV), Feature-Matching (ORB), Rendering von AR-Elementen, Benutzeroberfläche, Datenhaltung von Wegpunkten.  
- **Außerhalb des Systems:** GPS, externe Karten- oder Navigationsdienste, Cloud-basierte Bildverarbeitung (nur optional vorgesehen).

### Akteure

| **Akteur** | **Beschreibung / Rolle** |
|:------------|---------------------------|
| Benutzer | Führt die App aus, betrachtet Kameraansicht, interagiert mit Wegpunkten. |
| Android-System | Stellt Kamera-API, Sensoren und Render-Framework bereit. |
| OpenCV-Bibliothek | Wird zur Bildanalyse und Feature-Erkennung verwendet. |

### Schnittstellen

| **Schnittstelle**    | **Beschreibung** |
|:---------------------|------------------|
| Kamera-Schnittstelle | Zugriff auf Kamerastream zur Echtzeit-Analyse. |
| AR-Darstellung       | Bereitstellung der AR-Ebene zur Platzierung virtueller Objekte. |
| Dateisystem          | Speicherung lokaler Wegpunktdaten. |
| Sensoren (optional)  | Nutzung von Gyroskop und Beschleunigungssensor zur Positionsstabilisierung. |

---

## 3. Funktionale und Nichtfunktionale Anforderungen

| **ID** | **Anforderung** | **Beschreibung** | **Priorität** | **Kategorie** |
|:------:|-----------------|------------------|:--------------:|----------------|
| 1 | Erkennung von Wegpunkten | Die App soll mithilfe der Smartphone-Kamera und OpenCV (ORB-Feature-Matching) visuelle Marker oder Wegpunkte erkennen. | Muss | Funktional |
| 2 | AR-Darstellung | Die App soll erkannte Wegpunkte in der Kameraansicht mit virtuellen Overlays (z. B. Pfeilen, Texten) versehen. | Muss | Funktional |
| 3 | Routenführung | Die App soll mehrere Wegpunkte miteinander verbinden und eine logische Reihenfolge (Route) anzeigen. | Soll | Funktional |
| 4 | Benutzeroberfläche | Die App soll eine einfache UI bieten, um Wegpunkte per Text zu suchen und auszuwählen, Routen zu speichern und eine Route zu wählen. | Soll | Funktional |
| 5 | Performance | Die App soll Wegpunkte in Echtzeit (< 1 s Verzögerung) erkennen und darstellen. | Muss | Nichtfunktional |
| 6 | Kompatibilität | Die App soll auf gängigen Android-Geräten (Android 10 +) mit Standard-Kamerasensoren laufen. | Muss | Nichtfunktional |
| 7 | Energieeffizienz | Die App soll energieeffizient arbeiten, um den Akkuverbrauch zu minimieren. | Soll | Nichtfunktional |
| 8 | Usability | Die Benutzeroberfläche soll intuitiv und auch für ungeübte Nutzer verständlich sein. | Muss | Nichtfunktional |
| 9 | Erweiterbarkeit | Das System soll modular aufgebaut sein, sodass neue AR-Features oder Tracking-Algorithmen leicht integriert werden können. | Soll | Nichtfunktional |
| 10 | Datenschutz | Die App darf keine Kamerabilder oder Standortdaten ohne Zustimmung speichern oder übertragen. | Muss | Nichtfunktional |
| 11 | Stabilität | Die App darf während des Betriebs (z. B. bei längerem Tracking) nicht abstürzen. | Muss | Nichtfunktional |
| 12 | Wartbarkeit | Der Quellcode soll klar strukturiert und dokumentiert sein, um spätere Änderungen zu erleichtern. | Soll | Nichtfunktional |
