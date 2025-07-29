---
description: Repository Information Overview
alwaysApply: true
---

# ArWalking Information

## Summary
ArWalking is an Android application that provides augmented reality (AR) walking navigation. The app uses the device's camera to overlay navigation information on the real world, helping users navigate to their destinations.

## Structure
- **app/**: Main Android application module
  - **src/main/java/**: Contains Kotlin source code
  - **src/main/res/**: Contains Android resources (layouts, drawables, etc.)
- **gradle/**: Contains Gradle configuration files and wrapper
  - **wrapper/**: Gradle wrapper files
  - **libs.versions.toml**: Version catalog for dependency management

## Language & Runtime
**Language**: Kotlin
**Version**: Kotlin 2.0.0
**Build System**: Gradle (with Kotlin DSL)
**Package Manager**: Gradle

## Dependencies
**Main Dependencies**:
- AndroidX Core KTX (1.16.0)
- AndroidX Lifecycle Runtime KTX (2.9.1)
- AndroidX Activity Compose (1.10.1)
- AndroidX Compose BOM (2024.04.01)
- AndroidX Navigation Compose (2.9.2)
- AndroidX Camera (1.4.2)
- BlurView (1.6.6)

**Development Dependencies**:
- JUnit (4.13.2)
- AndroidX JUnit (1.2.1)
- Espresso Core (3.6.1)

## Build & Installation
```bash
# Build the project
./gradlew build

# Install on connected device
./gradlew installDebug

# Run tests
./gradlew test
```

## Application Structure
**Main Entry Point**: `MainActivity.kt`
**Package Structure**:
- `com.example.arwalking`: Main package
  - `ui`: UI-related classes and themes
  - `screens`: Screen composables (HomeScreen, CameraNavigation)
- `components`: Reusable UI components

**Navigation**:
The app uses Jetpack Compose Navigation with the following routes:
- `home`: The main home screen
- `camera_navigation/{destination}`: Camera navigation screen with destination parameter

## Android Configuration
**Minimum SDK**: 24
**Target SDK**: 35
**Compile SDK**: 35
**Java Compatibility**: Java 11