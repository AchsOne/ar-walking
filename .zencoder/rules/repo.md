---
description: Repository Information Overview
alwaysApply: true
---

# ArWalking Information

## Summary
ArWalking is an Android application that provides augmented reality (AR) walking navigation using computer vision and feature matching. The app uses the device's camera to overlay navigation information on the real world, helping users navigate indoor environments.

## Structure
- **app/**: Main Android application module with Kotlin source code
- **opencv/**: OpenCV Android library module for computer vision
- **gradle/**: Gradle configuration files and version catalogs
- **landmark_images/**: Reference landmark images for feature matching
- **.zencoder/**: Documentation and rules for the project

## Language & Runtime
**Language**: Kotlin
**Version**: 2.0.0
**Build System**: Gradle with Kotlin DSL
**Package Manager**: Gradle

## Dependencies
**Main Dependencies**:
- Jetpack Compose (BOM 2024.04.01)
- AndroidX Core KTX (1.16.0)
- AndroidX Lifecycle (2.9.1)
- Jetpack Navigation Compose (2.9.2)
- CameraX (1.4.2)
- OpenCV for Android (local module)
- Kotlinx Coroutines (1.7.3)
- Gson (2.10.1)

**Development Dependencies**:
- JUnit (4.13.2)
- Espresso (3.6.1)
- Compose UI Testing

## Build & Installation
```bash
# Clone the repository
git clone https://github.com/your-username/ar-walking.git

# Open in Android Studio
# Android Studio Narwhal (2025.1.2) or later

# Build the project
./gradlew build

# Install on device
./gradlew installDebug
```

## Testing
**Framework**: JUnit, Espresso
**Test Location**: app/src/test/, app/src/androidTest/
**Run Command**:
```bash
# Run unit tests
./gradlew test

# Run instrumented tests
./gradlew connectedAndroidTest
```

## Project Components

### Navigation System
- **MainActivity**: Main entry point with Compose navigation
- **HomeScreen**: Route selection interface
- **Navigation**: Camera-based AR navigation screen

### AR & Computer Vision
- **OpenCvCameraActivity**: OpenCV-based camera processing
- **FeatureMatchingEngine**: Computer vision feature matching
- **LandmarkFeatureStorage**: Landmark data management

### UI Components
- **ARInfoIsland**: AR status and information display
- **AR3DArrowOverlay**: 3D arrow navigation overlay
- **LocationDropdown**: Location selection component
- **FeatureMatchOverlay**: Feature matching visualization

### Data Management
- **RouteViewModel**: Central state management
- **RouteRepository**: JSON route data handling
- **ArWalkingStorageManager**: Local storage system

## Android Configuration
- **Minimum SDK**: 24 (Android 7.0)
- **Target SDK**: 35 (Android 14)
- **Compile SDK**: 36
- **Java Compatibility**: Java 17