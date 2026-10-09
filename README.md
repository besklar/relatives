# Relatives

A native Android person browser for the FamilySearch take-home exercise.

## Development status

The project is being built in small, independently verified changes. The planned sequence is Android setup, typed records and persistence, the people list, profiles and relative navigation, then offline verification and final documentation.

The records and portrait persistence layers are implemented and tested. The current screen is still the Android foundation; list and profile UI integration comes next.

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

Kotlin and Jetpack Compose suit the native Android role. One application module and manual dependency injection keep the two-screen application small and explainable. An application-scoped container owns the shared HTTP client, database, repository and portrait store. The data flow is Retrofit → repository → Room → Flow → ViewModel StateFlow → Compose; the screen integration is the next step. Room holds the last successfully saved records so refresh failures do not discard previously loaded data.

### Records and malformed data

The list envelope is decoded first, with each person decoded and validated independently into our Kotlin models. Missing required fields, wrong types, invalid IDs and unusable names discard that person while the remaining records continue. Duplicate IDs keep the first valid record. Unknown JSON fields are ignored. The server's count is informational; the app uses the accepted record count. A skipped-record count is saved with the list so the UI can report partial results.

A genuinely empty list is a successful result. Invalid overall JSON, an invalid envelope, or a nonempty array with no valid people is a failed refresh that preserves the previous saved list. An individual profile must have valid core data and match the requested ID before it replaces the saved profile. Currently, an invalid relative rejects that profile refresh as well. Display dates such as “about 1838” stay unchanged; integer years are retained separately. Death may be null for a living person, and occupation may be null.

### Queryable persistence

Room uses four schema-defined tables: list metadata, ordered person summaries, complete profiles, and ordered relatives. List snapshots and profiles deliberately duplicate common fields: a new list response cannot mix newly fetched summary fields with older profile details or delete opened profiles. Relative targets need not already have a stored full profile.

List and profile replacements use database transactions. Transactional relation queries emit consistent snapshots, and a profile lookup uses its primary-key ID without loading every profile. List metadata distinguishes an empty successful response from a list that has never loaded. The version-one schema is committed under `app/schemas`.

Network and database work run asynchronously off the main thread. Refreshes are serialized through one cancellable mutex for this small dataset; networking finishes before the database transaction starts. Cancellation propagates rather than becoming a user-visible failure. Expected HTTP, transport, decoding, validation and SQLite failures have explicit result types; transactions preserve prior records if writes fail.

### Portrait files

Room retains the service portrait path. The portrait store resolves it against the service URL and derives a SHA-256 filename, avoiding machine-specific paths in the database. Images live in app-private `filesDir/portraits`, rather than the OS-evictable cache directory. A saved file is checked before requesting network data.

Downloads use the shared OkHttp client, a temporary file, an 8 MB limit, image-bound validation, and a same-directory rename after completion. Failed or canceled downloads remove temporary files. Portrait failures do not invalidate person records. Screen integration will load portraits on demand and decode them at display size off the main thread.

Saved portraits have no automatic eviction or revalidation when their URL is unchanged. That is a deliberate simplification for 16 records; app data removal clears them. A production version would need a bounded storage policy and image versioning/revalidation.

### At 100,000 people

The service would first need server pagination; its current endpoint returns the entire list. I would then use Paging 3 with indexed Room queries, incremental response import, and bounded image storage. Reading and decoding all 100,000 records into memory would be inappropriate. Those mechanisms add little value for the supplied 16-person service and are not implemented here.

## Dependencies

- AndroidX Activity Compose: hosts Compose in an Android activity and supports edge-to-edge layout.
- Compose UI and Material 3 (BOM 2025.08.01): declarative UI and standard Android components.
- AndroidX Lifecycle Compose and ViewModel Compose (2.9.2): reserved for lifecycle-aware state collection and screen ViewModels in the following feature slices.
- Android Gradle Plugin and Kotlin Compose compiler plugin: compile and package the Android application; the wrapper pins a reproducible Gradle version.
- Room runtime/ktx and KSP (2.7.2 and 2.2.10-2.0.2): schema-defined queryable storage, generated DAOs, transactions and observable queries.
- Retrofit and its Kotlin serialization converter (3.0.0), Kotlin serialization JSON (1.9.0), and the Kotlin serialization compiler plugin: typed HTTP requests and generated DTO decoding, with per-record recovery at the boundary.
- Kotlin coroutines Android (1.10.2): cancellable asynchronous work, mutexes and flows.
- OkHttp (5.1.0): shared transport, timeouts and cancellable portrait streaming.
- JUnit (4.13.2), coroutines-test (1.10.2), and MockWebServer (5.1.0): JVM assertions and deterministic local HTTP/failure tests without relying on the live service.
- AndroidX Test core/runner (1.7.0) and extension JUnit (1.3.0): instrumentation on real Android SQLite and image decoding, including persistence across store recreation. Room's runtime APIs suffice for these tests; no migration-test library is needed for schema version one.

## Verification and limitations

The foundation passed `assembleDebug` and `lintDebug` with JDK 21.0.11 and SDK 36, and a fresh Git clone built with `ANDROID_HOME` and no `local.properties`. The app was installed and launched on an Android API 36 emulator, and the visible screen and app name were checked.

Run the data-layer checks with a running emulator or connected phone:

```shell
./gradlew testDebugUnitTest connectedDebugAndroidTest lintDebug assembleDebug
```

The tests cover the actual Retrofit boundary, mixed valid/invalid records, duplicate IDs, imprecise dates, living/null death, null occupation, failed refresh preservation, successful-empty versus uncached state, list refresh preserving profiles, relative replacement, SQLite rollback, cancellation, and persistence after database close/reopen. Portrait tests cover disk reuse without network, concurrent requests, invalid/oversized/interrupted responses, cancellation cleanup, and actual Android image decoding after store recreation. Debug builds permit cleartext HTTP only to localhost/127.0.0.1 so instrumentation can use MockWebServer; the service uses HTTPS.

The data-layer run passed all 14 JVM tests and 11 Android instrumentation tests on API 36, along with `lintDebug` and `assembleDebug`. Lint has zero errors and 18 unsuppressed warnings about newer tool/dependency versions and Android backup configuration.

Full force-quit/airplane-mode UI verification is pending until the list and profile screens are implemented. Final documentation will also describe what another day would enable.

Time spent so far: approximately three minutes implementing/verifying the Android foundation and eight minutes implementing/verifying the data layer, plus collaborative planning that was not timed. This will be updated as features are completed.

## Development assistance

AI assistance is being used for planning, implementation, and verification. Technical decisions are reviewed with the author, who must be able to explain and defend the submitted code.
