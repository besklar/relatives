# Relatives

A native Android person browser for the FamilySearch take-home exercise.

## Development status

The project is being built in small, independently verified changes. The planned sequence is Android setup, typed records and persistence, the people list, profiles and relative navigation, then offline verification and final documentation.

The current application is the Android foundation only. It does not fetch people or provide offline storage yet.

## Build and run

Use Android Studio Quail 4 (2026.1.4 Patch 1) or a compatible installation, JDK 21, and Android SDK platform 36. The project pins Gradle 8.13, Android Gradle Plugin 8.12.0, and Kotlin 2.2.10. Java/Kotlin compilation targets JVM 17. The minimum device version is Android 8.0 (API 26).

Open the repository in Android Studio, select JDK 21 as the Gradle JVM, let Gradle sync, and run the `app` configuration on a phone or emulator. Android Studio's current bundled JDK may be newer; explicitly select JDK 21.

For terminal builds, set `JAVA_HOME` to your JDK 21 installation and `ANDROID_HOME` to your Android SDK directory, then run:

```shell
./gradlew assembleDebug lintDebug
```

Install the debug APK with an attached phone or running emulator:

```shell
"$ANDROID_HOME/platform-tools/adb" install -r app/build/outputs/apk/debug/app-debug.apk
"$ANDROID_HOME/platform-tools/adb" shell am start -n com.besklar.relatives/.MainActivity
```

The Gradle wrapper is committed. No API keys, credentials, or source edits are required. Android Studio may generate an ignored `local.properties`; terminal builds use `ANDROID_HOME` without that file.

## Approach

Kotlin and Jetpack Compose suit the native Android role. One application module and manual dependency injection keep the two-screen application small and explainable. The planned data flow is Retrofit → repository → Room → Flow → ViewModel StateFlow → Compose. Room will hold the last successfully saved records so refresh failures do not discard previously loaded data.

## Dependencies

- AndroidX Activity Compose: hosts Compose in an Android activity and supports edge-to-edge layout.
- Compose UI and Material 3 (BOM 2025.08.01): declarative UI and standard Android components.
- AndroidX Lifecycle Compose and ViewModel Compose (2.9.2): reserved for lifecycle-aware state collection and screen ViewModels in the following feature slices.
- Android Gradle Plugin and Kotlin Compose compiler plugin: compile and package the Android application; the wrapper pins a reproducible Gradle version.

## Verification and limitations

`assembleDebug` and `lintDebug` pass with JDK 21.0.11 and SDK 36. A fresh Git clone also builds with `ANDROID_HOME` and no `local.properties`. The app was installed and launched on an Android API 36 emulator, and the visible screen and app name were checked. Lint reports no errors and seven warnings about newer tool/dependency versions and Android backup configuration; these are not suppressed.

Meaningful automated tests will begin with the records and persistence layer rather than testing generated boilerplate. The final submission will document offline process-restart verification, scaling to 100,000 records, concurrency, failure handling, known gaps, and what another day would enable.

Time spent so far: approximately five minutes implementing and verifying the Android foundation, plus earlier collaborative planning that was not timed. This will be updated as features are completed.

## Development assistance

AI assistance is being used for planning, implementation, and verification. Technical decisions are reviewed with the author, who must be able to explain and defend the submitted code.
