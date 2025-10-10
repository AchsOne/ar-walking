# WARP.md

This file provides guidance to WARP (warp.dev) when working with code in this repository.

## Project Overview

ArWalking is an Android app for Augmented Reality navigation that overlays navigation information onto the camera view. The app is completely offline and uses OpenCV for computer vision-based landmark recognition.

**Key Technologies:**
- **Language**: Kotlin with Kotlin DSL for Gradle
- **UI Framework**: Jetpack Compose
- **Computer Vision**: OpenCV for feature matching and landmark detection
- **Build System**: Gradle with version catalogs
- **Architecture**: Multi-module Android project with storage facade pattern

## Essential Development Commands

### Building and Running
```bash
# Build the entire project
./gradlew build

# Install debug version on connected device
./gradlew installDebug

# Run unit tests
./gradlew test

# Run instrumentation tests
./gradlew connectedAndroidTest

# Clean build artifacts
./gradlew clean

# Build release version
./gradlew assembleRelease
```

### Single Test Execution
```bash
# Run specific test class
./gradlew test --tests "com.example.arwalking.storage.*"

# Run specific test method
./gradlew test --tests "com.example.arwalking.OpenCvFeatureTest.testFeatureExtraction"

# Run instrumentation tests for specific package
./gradlew connectedAndroidTest -Pandroid.testInstrumentationRunnerArguments.package=com.example.arwalking.storage
```

### Debugging and Analysis
```bash
# Generate lint report
./gradlew lint

# Check dependencies
./gradlew dependencies

# List all tasks
./gradlew tasks
```

## Architecture Overview

### Module Structure
```
ArWalking/
├── app/                    # Main application module
├── opencv/                 # OpenCV module integration  
└── sdk/                    # SDK module (additional utilities)
```

### Core Architecture Components

**Storage System (ArWalkingStorageManager Facade)**
- `ArWalkingStorageManager`: Main facade for all storage operations
- `StorageDirectoryManager`: Manages landmark_images/, landmark_thumbnails/, landmark_metadata/, feature_maps/
- `ProjectDirectoryImageManager`: Loads images directly from project directory (no training mode required)
- `StorageConfig`: Performance targets and configuration constants
- `StoragePerformanceMonitor`: Tracks load times, cache hit rates

**Computer Vision Pipeline**
- `OpenCvFeatureTest`: ORB feature extraction testing
- `OpenCvMatchingTest`: Feature matching validation
- `ContextualFeatureMatcher`: Real-time feature matching during navigation
- `FeatureMappingModels`: Data classes for landmarks and matches

**UI Architecture**
- `MainActivity`: App entry point with OpenCV initialization and automated testing
- `RouteViewModel`: Central state management for navigation and feature matching
- `Navigation.kt`: Camera screen and AR overlay management
- Jetpack Compose screens in `/screens/` directory

### Storage Performance Targets
- Image loading: 5-15ms (LRU cache + optimized I/O)
- Thumbnail loading: 1-3ms (separate cache)
- Search operations: <1ms (in-memory index)
- Cache hit rate: >80%

### Key Data Flow
1. **App Launch**: OpenCV initialization → automatic feature extraction tests → storage system setup
2. **Navigation**: Camera frames → feature extraction → matching against landmark database → AR overlay updates
3. **Storage**: Images stored in app-internal directory with thumbnails, metadata, and feature maps

## Development Workflow

### Setting Up Development Environment
1. Ensure Android SDK is installed (compileSdk 36, minSdk 24, targetSdk 35)
2. Install OpenCV Android SDK (automatically handled by gradle)
3. Use Java 17 for compilation
4. Connect Android device or setup emulator with camera permissions

### Working with Computer Vision Features
- Feature extraction tests run automatically on app launch
- Test images are located in `assets/landmark_images/`
- Use `BuildConfig.DEBUG_FEATURE_MAPPING` to enable detailed logging
- Test landmark IDs: "PT-1-926", "PT-1-686" (referenced in MainActivity)

### Storage System Development
- All storage operations go through `ArWalkingStorageManager` facade
- Storage directories are managed automatically by `StorageDirectoryManager`
- Performance metrics are tracked via `StoragePerformanceMonitor`
- Images are loaded directly from project directory (no training mode)

### Testing Strategy
- Unit tests for storage components with JUnit
- Instrumentation tests for camera and OpenCV integration
- Automated feature matching tests run on app startup
- Performance benchmarks built into storage system

## Important Implementation Details

### OpenCV Integration
- OpenCV is initialized in `MainActivity.onCreate()`
- Feature extraction uses ORB algorithm
- Automatic testing validates feature extraction and matching on startup
- Features are preprocessed and cached for real-time matching

### Jetpack Compose UI
- Uses Compose Navigation for screen transitions
- Camera preview implemented with CameraX and AndroidView
- AR overlays use custom drawing with Canvas
- Navigation state managed through ViewModels

### Multi-Module Dependencies
- Main app depends on `:opencv` module
- Version catalog managed in `gradle/libs.versions.toml`
- Consistent dependency versions across modules

### Permissions and Security
- Camera permission handled with ActivityResultContracts
- App-internal storage for all data (`/data/data/com.example.arwalking/files/`)
- No external storage access or network connectivity required

## File Organization Patterns

### Storage Components
```
com.example.arwalking.storage/
├── ArWalkingStorageManager.kt      # Main facade
├── ProjectDirectoryImageManager.kt # Project image loading
├── StorageDirectoryManager.kt      # Directory management
├── StorageConfig.kt                # Configuration constants
└── StoragePerformanceMonitor.kt    # Performance tracking
```

### Computer Vision Components
```
com.example.arwalking.vision/
├── ContextualFeatureMatcher.kt     # Real-time matching
└── FeatureMappingModels.kt         # Data models
```

### UI Components
```
com.example.arwalking.screens/      # Compose screens
com.example.arwalking.components/   # Reusable UI components
```

## Common Development Patterns

When adding new storage functionality, extend the `ArWalkingStorageManager` facade rather than accessing storage components directly. 

When working with OpenCV operations, follow the pattern in `OpenCvFeatureTest` and `OpenCvMatchingTest` for proper error handling and resource management.

For UI development, use the established ViewModel pattern with StateFlow for reactive updates to Compose UI.