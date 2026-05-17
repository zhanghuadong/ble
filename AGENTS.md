# AGENTS.md

## Project Overview

This is an Android BLE (Bluetooth Low Energy) library (`com.dongzi.blelib`) providing APIs for scanning, connecting, and communicating with BLE peripherals. It is a **library module** (not a standalone application) that produces an AAR artifact.

## Cursor Cloud specific instructions

### Environment Requirements

- **JDK 17** is required (AGP 7.4.2 does not support Java 21). Set via `gradle.properties` → `org.gradle.java.home=/usr/lib/jvm/java-17-openjdk-amd64`.
- **Android SDK** must be installed at `/opt/android-sdk` with `platforms;android-30`, `build-tools;30.0.3`, and `platform-tools`. The path is set in `local.properties`.
- The project uses **Gradle 7.6.4** with **Android Gradle Plugin 7.4.2** (configured in `settings.gradle`).

### Build Commands

```bash
export ANDROID_SDK_ROOT=/opt/android-sdk
export JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64

./gradlew assembleDebug      # Build debug AAR
./gradlew assembleRelease    # Build release AAR
./gradlew test               # Run JVM unit tests (no test sources currently exist)
./gradlew lint               # Run Android lint (will fail due to pre-existing ExpiredTargetSdkVersion error in build.gradle)
```

### Known Issues

- **Lint `ExpiredTargetSdkVersion` error**: The existing `build.gradle` sets `targetSdkVersion 30`, which triggers a lint error. This is a pre-existing issue in the original code.
- **Namespace deprecation warning**: The package is set via `AndroidManifest.xml`'s `package` attribute instead of the `namespace` property in `build.gradle`. This produces a deprecation warning but does not block the build.
- **No test sources**: The repo declares test dependencies (`junit`, `espresso`) but contains no test files under `src/test/` or `src/androidTest/`.
- `local.properties` is gitignored in most Android projects but is checked in here to enable builds in CI/cloud environments. If the Android SDK path changes, update `local.properties`.
