# ArWalking - Augmented Reality Navigation App

ArWalking is an Android application that provides augmented reality (AR) walking navigation using computer vision and feature matching. The app uses the device's camera to overlay navigation information on the real world, helping users navigate indoor environments.

## 🚀 Features

- **AR Navigation**: Real-time augmented reality overlays showing navigation directions
- **Feature Matching**: Computer vision-based landmark recognition using OpenCV
- **Indoor Navigation**: Specialized for indoor environments like university buildings
- **Route Planning**: JSON-based route configuration with step-by-step instructions
- **Modern UI**: Built with Jetpack Compose for a smooth, modern user experience
- **Camera Integration**: Real-time camera preview with AR overlays

## 📱 Screenshots

The app provides:
- Home screen with route selection
- AR camera view with navigation overlays
- Feature matching indicators
- Step-by-step navigation guidance

## 🏗️ Architecture

### Project Structure
```
ar-walking/
├── app/                          # Main Android application module
│   ├── src/main/java/           # Kotlin source code
│   │   └── com/example/arwalking/
│   │       ├── components/      # Reusable UI components
│   │       ├── data/           # Data models and repositories
│   │       ├── screens/        # Screen composables
│   │       ├── storage/        # Local storage management
│   │       ├── ui/theme/       # UI theme and styling
│   │       └── utils/          # Utility classes
│   ├── src/main/res/           # Android resources
│   │   ├── drawable/           # Vector drawables and images
│   │   ├── layout/            # XML layouts (for OpenCV activities)
│   │   └── values/            # Colors, strings, themes
│   └── src/main/assets/        # Asset files
│       ├── routes/            # Route JSON files
│       ├── landmark_images/   # Reference landmark images
│       └── landmark_features/ # Processed feature data
├── opencv/                      # OpenCV Android module
└── gradle/                     # Gradle configuration
```

### Komponenten-Übersicht

Die ArWalking-App ist modular aufgebaut und besteht aus verschiedenen spezialisierten Komponenten:

#### 1. Navigation & Routing System
- **MainActivity.kt**: Haupteinstiegspunkt der App mit Jetpack Compose Navigation
- **HomeScreen.kt**: Startbildschirm für Routenauswahl und Zielkonfiguration
- **Navigation.kt**: Hauptnavigationsbildschirm mit AR-Kamera-Integration
- **RouteViewModel.kt**: Zentrale Zustandsverwaltung für Routen, Feature-Matching und Navigation
- **RouteRepository.kt**: Lädt und verwaltet Routendaten aus JSON-Assets
- **NavigationRoute.kt**: Datenmodelle für Navigationsrouten und -schritte

#### 2. AR & Computer Vision
- **OpenCvCameraActivity.kt**: OpenCV-basierte Kameraverarbeitung und Bildanalyse
- **FeatureMatchingEngine.kt**: Computer-Vision-Engine für Landmark-Erkennung und Feature-Matching
- **LandmarkFeatureStorage.kt**: Verwaltung und Speicherung von Landmark-Features und Bilddaten
- **ARTrackingSystem.kt**: AR-Koordinatensystem-Management und Pose-Schätzung (Stub-Implementation)

#### 3. UI Components & Overlays
- **ARInfoIsland.kt**: AR-Status-Anzeige mit Scan-Status und Navigationsinformationen
- **AR3DArrowOverlay.kt**: 3D-Pfeil-Overlay für Richtungsanweisungen
- **Animated3DArrowOverlay.kt**: Animierte Version des 3D-Pfeils mit Bewegungseffekten
- **FeatureMatchOverlay.kt**: Visualisierung von Feature-Matches und Erkennungsvertrauen
- **LocationDropdown.kt**: Dropdown-Komponente für Standort- und Zielauswahl
- **NavigationDrawer.kt**: Seitliches Navigationsmenü
- **MenuOverlay.kt**: Overlay-Menü für zusätzliche Optionen
- **ARScanStatus.kt**: Status-Komponente für AR-Scanning-Feedback

#### 4. Storage & Data Management
- **ArWalkingStorageManager.kt**: Zentrales Storage-System für Bilder und Metadaten
- **LocalImageStorage.kt**: Lokale Bildspeicherung und -verwaltung
- **OptimizedImageManager.kt**: Optimierte Bildverarbeitung und Komprimierung
- **ProjectDirectoryImageManager.kt**: Verwaltung von Bildern im Projektverzeichnis
- **FavoritesRepository.kt**: Speicherung und Verwaltung von Lieblingsrouten

#### 5. Data Models & Structures
- **RouteData.kt**: Datenmodelle für JSON-basierte Routendefinitionen
- **BuildingStructure.kt**: Gebäudestruktur-Definitionen und Stockwerk-Informationen
- **FavoriteRoute.kt**: Datenmodell für gespeicherte Lieblingsrouten
- **FeatureLandmark.kt**: Landmark-Definitionen mit Feature-Daten
- **FeatureNavigationRoute.kt**: Spezielle Routen für Feature-basierte Navigation

#### 6. Utility & Configuration
- **FeatureMappingConfig.kt**: Konfiguration für Feature-Mapping-Parameter
- **JsonUtils.kt**: Hilfsfunktionen für JSON-Verarbeitung
- **GradientUtils.kt**: UI-Hilfsfunktionen für Farbverläufe
- **Color.kt**: App-weite Farbdefinitionen und Themes

#### 7. Debug & Development
- **LandmarkDebugOverlay.kt**: Debug-Overlay für Landmark-Entwicklung
- **FeatureMappingStatusIndicator.kt**: Status-Indikator für Feature-Mapping-Prozesse
- **ExpandedARInfoIsland.kt**: Erweiterte AR-Info-Anzeige für detaillierte Informationen

### Key Components (Hauptkomponenten)

#### 1. Navigation System
- **MainActivity.kt**: Main entry point with Compose navigation
- **HomeScreen.kt**: Route selection interface
- **Navigation.kt**: Camera-based AR navigation screen

#### 2. AR & Computer Vision
- **OpenCvCameraActivity.kt**: OpenCV-based camera processing
- **FeatureMatchingEngine.kt**: Computer vision feature matching
- **LandmarkFeatureStorage.kt**: Landmark data management

#### 3. UI Components
- **ARInfoIsland.kt**: AR status and information display
- **AR3DArrowOverlay.kt**: 3D arrow navigation overlay
- **LocationDropdown.kt**: Location selection component
- **FeatureMatchOverlay.kt**: Feature matching visualization

#### 4. Data Management
- **RouteViewModel.kt**: Central state management
- **RouteRepository.kt**: JSON route data handling
- **ArWalkingStorageManager.kt**: Local storage system

## 🛠️ Technical Stack

### Core Technologies
- **Language**: Kotlin 2.0.0
- **Build System**: Gradle with Kotlin DSL
- **UI Framework**: Jetpack Compose
- **Computer Vision**: OpenCV for Android
- **Architecture**: MVVM with ViewModels
- **Navigation**: Jetpack Navigation Compose

### Key Dependencies
```kotlin
// Core Android
androidx-core-ktx = "1.16.0"
androidx-lifecycle-runtime-ktx = "2.9.1"

// Jetpack Compose
androidx-compose-bom = "2024.04.01"
androidx-activity-compose = "1.10.1"
androidx-navigation-compose = "2.9.2"

// Camera
androidx-camera-core = "1.4.2"
androidx-camera-lifecycle = "1.4.2"

// Coroutines
kotlinx-coroutines-core = "1.7.3"
kotlinx-coroutines-android = "1.7.3"

// JSON Processing
gson = "2.10.1"
```

### Android Configuration
- **Minimum SDK**: 24 (Android 7.0)
- **Target SDK**: 35 (Android 14)
- **Compile SDK**: 36
- **Java Compatibility**: Java 17

## 🚀 Getting Started

### Prerequisites
- Android Studio Narwhal (2025.1.2) or later
- Java 17 or later
- Android SDK with API level 24+
- Physical Android device (recommended for AR features)

### Installation

1. **Clone the repository**
   ```bash
   git clone https://github.com/your-username/ar-walking.git
   cd ar-walking
   ```

2. **Open in Android Studio**
   - Launch Android Studio
   - Select "Open an existing project"
   - Navigate to the cloned directory

3. **Sync project**
   - Android Studio will automatically sync Gradle dependencies
   - Wait for the sync to complete

4. **Build the project**
   ```bash
   ./gradlew build
   ```

5. **Install on device**
   ```bash
   ./gradlew installDebug
   ```

### Required Permissions
The app requires the following permissions:
- **Camera**: For AR navigation and feature matching
- **Internet**: For potential future server-based features
- **Storage**: For local landmark data storage

## 📋 Usage

### Basic Navigation Flow

1. **Launch the app**
   - The home screen displays available routes
   - Select start and destination locations

2. **Start AR Navigation**
   - Grant camera permission when prompted
   - Point camera at the environment
   - Follow AR overlays and navigation instructions

3. **Feature Matching**
   - The app automatically recognizes landmarks
   - AR indicators show recognition confidence
   - Navigation arrows guide you to the destination

### Route Configuration

Routes are defined in JSON format in `app/src/main/assets/route.json`:

```json
{
  "route": {
    "path": [
      {
        "xmlName": "Building Name",
        "levelInfo": {
          "storey": "0",
          "storeyName": "Ground Floor"
        },
        "routeParts": [
          {
            "instruction": "Walk straight ahead",
            "instructionDe": "Gehen Sie geradeaus",
            "distance": 50.0,
            "duration": 30,
            "landmarks": ["landmark_1", "landmark_2"]
          }
        ]
      }
    ]
  }
}
```

### Adding New Landmarks

1. **Add landmark images** to `app/src/main/assets/landmark_images/`
2. **Update route JSON** to reference new landmarks
3. **Feature extraction** happens automatically on first run

## 🔧 Development

### Key Classes Overview

#### RouteViewModel
Central state management for:
- Route data loading from JSON
- Feature matching coordination
- AR state management
- Navigation step tracking

#### FeatureMatchingEngine
Computer vision processing:
- OpenCV-based feature detection
- Landmark recognition
- Confidence scoring
- Real-time frame processing

#### ARTrackingSystem
AR coordinate management:
- Camera pose estimation
- 3D coordinate transformations
- Overlay positioning
- Tracking stability

### Adding New Features

1. **New UI Components**: Add to `components/` package
2. **New Screens**: Add to `screens/` package with navigation setup
3. **Data Models**: Add to `data/` package
4. **Storage**: Extend `ArWalkingStorageManager` for new data types

### Testing

```bash
# Run unit tests
./gradlew test

# Run instrumented tests
./gradlew connectedAndroidTest

# Run specific test class
./gradlew test --tests "com.example.arwalking.RouteViewModelTest"
```

## 📊 Performance Considerations

### Optimization Features
- **Efficient feature matching**: Optimized OpenCV processing
- **Memory management**: Proper bitmap recycling
- **Background processing**: Coroutines for heavy operations
- **Storage optimization**: Compressed landmark data

### Recommended Device Specs
- **RAM**: 4GB+ recommended
- **Camera**: Autofocus capability
- **Sensors**: Gyroscope and accelerometer for better AR
- **Storage**: 100MB+ free space for landmark data

## 🐛 Troubleshooting

### Common Issues

1. **Camera permission denied**
   - Check app permissions in device settings
   - Restart the app after granting permission

2. **Feature matching not working**
   - Ensure good lighting conditions
   - Check if landmark images are properly loaded
   - Verify OpenCV initialization

3. **AR overlays misaligned**
   - Calibrate device sensors
   - Ensure stable camera positioning
   - Check device orientation handling

4. **Build errors**
   - Clean and rebuild: `./gradlew clean build`
   - Check Java version compatibility
   - Verify all dependencies are resolved

### Debug Features

Enable debug mode in `BuildConfig.DEBUG` for:
- Detailed logging
- Feature matching visualization
- Performance metrics
- Storage system diagnostics

## 🤝 Contributing

1. Fork the repository
2. Create a feature branch (`git checkout -b feature/amazing-feature`)
3. Commit your changes (`git commit -m 'Add amazing feature'`)
4. Push to the branch (`git push origin feature/amazing-feature`)
5. Open a Pull Request

### Code Style
- Follow Kotlin coding conventions
- Use meaningful variable and function names
- Add documentation for public APIs
- Write unit tests for new features

## 📄 License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

## 🙏 Acknowledgments

- OpenCV community for computer vision capabilities
- Android Jetpack team for modern Android development tools
- Contributors and testers who helped improve the app

## 📞 Support

For support and questions:
- Create an issue on GitHub
- Check the troubleshooting section
- Review existing documentation

---

**Note**: This app is designed for indoor navigation and works best in well-lit environments with distinctive visual landmarks.