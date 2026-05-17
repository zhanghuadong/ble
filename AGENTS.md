# AGENTS.md

## Cursor Cloud specific instructions

### Overview

This is `blelib`, an Android BLE (Bluetooth Low Energy) library module (`com.dongzi.blelib`). It is a standalone Android library (AAR), not a full Android application. There is no runnable app — the "hello world" for this project is building the AAR successfully.

### Environment

- **Android SDK** is installed at `/opt/android-sdk`. The `ANDROID_HOME` environment variable must be set to this path before running Gradle commands.
- **Java 21** (OpenJDK) is available system-wide.
- **Gradle wrapper** (`./gradlew`) is present in the project root. Always use the wrapper instead of system Gradle.

### Key commands

| Task | Command |
|------|---------|
| Build debug AAR | `ANDROID_HOME=/opt/android-sdk ./gradlew assembleDebug` |
| Build release AAR | `ANDROID_HOME=/opt/android-sdk ./gradlew assembleRelease` |
| Run unit tests | `ANDROID_HOME=/opt/android-sdk ./gradlew test` |
| Run lint | `ANDROID_HOME=/opt/android-sdk ./gradlew lint` |
| List all tasks | `ANDROID_HOME=/opt/android-sdk ./gradlew tasks` |

### Known issues / caveats

- **Lint will fail** with an `ExpiredTargetSdkVersion` error because `targetSdkVersion` is 30 (Google Play now requires 33+). This is a pre-existing codebase issue, not an environment problem.
- **Build warnings** about `buildToolsVersion "30.0.3"` being below AGP 8.2.2's minimum (34.0.0) are expected; AGP auto-upgrades to 34.0.0 at build time.
- **No test sources** exist yet — `./gradlew test` completes successfully but runs zero tests.
- **No runnable app**: this is a library module only. There is no `main` entry point or Android app module. Build verification produces AAR files in `build/outputs/aar/`.
- **Instrumented tests** (`connectedAndroidTest`) require a physical device or emulator with BLE support and cannot run in this headless cloud environment.
