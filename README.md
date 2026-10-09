# Relatives

A native Android person browser for the FamilySearch take-home exercise.

## Features

The list shows each person's portrait, full name, lifespan and birthplace in tappable lavender cards with circular avatars and chevrons. The app follows the system light/dark appearance. Saved data appears while the app refreshes; pull down or choose Refresh to retry. A failed refresh keeps saved content visible with a connection/service/data/storage message and the saved retrieval time. Successfully empty results, initial loading, uncached failures, and partially accepted records each have explicit presentations.

Tap a person to open the profile with a larger portrait, birth/death details, occupation, biography and relatives. Tap a relative card to open their profile. Relative portraits use paths already saved in profiles or summaries, preferring the profile; unknown paths show a placeholder without prefetching complete profiles. Missing occupation or biography is shown as “Not recorded”; living people have no invented death event. Previously opened profiles remain usable offline; a person known only from the list has no full saved profile until its endpoint is loaded.

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

Kotlin and Jetpack Compose suit the native Android role. One application module and manual dependency injection keep the two-screen application small and explainable. An application-scoped container owns the shared HTTP client, database, repository and portrait store. The list data flow is Retrofit → repository → Room → Flow → ViewModel StateFlow → Compose. Room holds the last successfully saved records so refresh failures do not discard previously loaded data.

Screen ViewModels observe disk-backed models and refresh once at creation. They track the initial store read separately from an absent snapshot, avoid duplicate refresh requests, and survive activity recreation. Compose collects immutable StateFlow with lifecycle awareness. A small repository interface enables deterministic ViewModel tests without a live database or network.

Navigation Compose uses typed person-ID routes, decoded through the navigation entry's SavedStateHandle at the ViewModel factory boundary. Different person IDs get separate entries and ViewModels, even during A → B → A browsing. A tap to the currently displayed person does nothing. Returning to the list retains its ViewModel and scroll state. Popping an entry cancels its ViewModel work; entries still on the back stack may finish loading and safely populate Room. App-bar and Android Back follow the normal navigation stack. Taps during entry transitions are ignored to avoid stacking duplicate destinations.

A shared portrait transition connects the tapped list/relative card to the profile header over approximately 300 ms, with a fade for surrounding content and unmatched Back transitions. Keys include the source navigation entry and row identity so repeated family routes do not share the wrong portrait. Navigation carries only a display hint (ID, name, lifespan and portrait path); the identity remains visible while full details load or fail, and the hint never becomes a saved full profile. Compose uses the system animation duration scale. The pinned Compose shared-transition API requires an opt-in localized to navigation.

### Records and malformed data

The list envelope is decoded first, with each person decoded and validated independently into our Kotlin models. Missing required fields, wrong types, invalid IDs and unusable names discard that person while the remaining records continue. Duplicate IDs keep the first valid record. Unknown JSON fields are ignored. The server's count is informational; the app uses the accepted record count. A skipped-record count is saved with the list so the UI can report partial results.

A genuinely empty list is a successful result. Invalid overall JSON, an invalid envelope, or a nonempty array with no valid people is a failed refresh that preserves the previous saved list. An individual profile must have valid core data and match the requested ID before it replaces the saved profile. Relative entries are decoded independently; invalid entries and duplicate ID/relationship links are skipped and counted. Even if every relative is invalid, a valid profile core remains usable with an explicit skipped count. Unknown relationship labels are preserved. Display dates such as “about 1838” stay unchanged; integer years are retained separately. Death may be null for a living person, and occupation may be null. Sources are ignored because they are not a requested display feature.

### Queryable persistence

Room uses four schema-defined tables: list metadata, ordered person summaries, complete profiles, and ordered relatives. List snapshots and profiles deliberately duplicate common fields: a new list response cannot mix newly fetched summary fields with older profile details or delete opened profiles. Relative targets need not already have a stored full profile.

List and profile replacements use database transactions. Transactional relation queries emit consistent snapshots, and a profile lookup uses its primary-key ID without loading every profile. List metadata distinguishes an empty successful response from a list that has never loaded. Version-one and version-two schemas are committed under `app/schemas`; a tested, non-destructive 1→2 migration adds the profile's skipped-relative count with default zero while retaining stored records.

Network and database work run asynchronously off the main thread. Refreshes are serialized through one cancellable mutex for this small dataset; networking finishes before the database transaction starts. Cancellation propagates rather than becoming a user-visible failure. Expected HTTP, transport, decoding, validation and SQLite failures have explicit result types; transactions preserve prior records if writes fail.

### Portrait files

Room retains the service portrait path. The portrait store resolves it against the service URL and derives a SHA-256 filename, avoiding machine-specific paths in the database. Images live in app-private `filesDir/portraits`, rather than the OS-evictable cache directory. A saved file is checked before requesting network data.

Downloads use the shared OkHttp client, a temporary file, an 8 MB limit, image-bound validation, and a same-directory rename after completion. Failed or canceled downloads remove temporary files before returning failure. Portrait failures do not invalidate person records. Both screens load portraits on demand, decode them at display size off the main thread, and show a placeholder on failure. Leaving composition cancels the image request; a successful records refresh also retries unavailable portraits. An 8 MB LRU bitmap cache supplies the already displayed decode immediately during navigation; the larger decode replaces it after the shared transition. Eviction drops cache references without recycling images still held by a screen. Cache contents are expendable: app-private files provide offline persistence. No separate image library is used.

Saved portraits have no automatic eviction or revalidation when their URL is unchanged. That is a deliberate simplification for 16 records; app data removal clears them. A production version would need a bounded storage policy and image versioning/revalidation.

### At 100,000 people

The service would first need server pagination; its current endpoint returns the entire list. I would then use Paging 3 with indexed Room queries, incremental response import, and bounded image storage. Reading and decoding all 100,000 records into memory would be inappropriate. Those mechanisms add little value for the supplied 16-person service and are not implemented here.

## Dependencies

- AndroidX Activity Compose (1.10.1): hosts Compose in an Android activity and supports edge-to-edge layout.
- Compose UI, Animation and Material 3 (BOM 2025.08.01): declarative UI, native shared-element transitions and standard Android components.
- AndroidX Lifecycle Compose and ViewModel Compose (2.9.2): lifecycle-aware state collection and screen ViewModels.
- Navigation Compose (2.9.3): typed ID-based routes, navigation-entry state ownership and standard Back handling.
- Android Gradle Plugin and Kotlin Compose compiler plugin: compile and package the Android application; the wrapper pins a reproducible Gradle version.
- Room runtime/ktx/compiler (2.7.2) and KSP (2.2.10-2.0.2): schema-defined queryable storage, generated DAOs, transactions and observable queries.
- Retrofit and its Kotlin serialization converter (3.0.0), Kotlin serialization JSON (1.9.0), and the Kotlin serialization compiler plugin: typed HTTP requests and generated DTO decoding, with per-record recovery at the boundary.
- Kotlin coroutines Android (1.10.2): cancellable asynchronous work, mutexes and flows.
- OkHttp (5.1.0): shared transport, timeouts and cancellable portrait streaming.
- JUnit (4.13.2), coroutines-test (1.10.2), and MockWebServer (5.1.0): JVM assertions and deterministic local HTTP/failure tests without relying on the live service.
- AndroidX Test core/runner (1.7.0) and extension JUnit (1.3.0): instrumentation on real Android SQLite and image decoding, including persistence across store recreation.
- Room testing (2.7.2): validate the migration against exported schemas and verify previously saved data remains readable.
- Compose UI test JUnit and its debug test manifest (versions selected by the Compose BOM): deterministic screen assertions, retry actions, portrait reload behavior and family navigation with failed refreshes.

## Verification and limitations

The base revision passed `assembleDebug`, `lintDebug` and all 28 JVM tests from a fresh Git clone with JDK 21.0.11 and SDK 36, using `ANDROID_HOME` without `local.properties` or private planning files. The resulting APK was installed on an API 36 emulator.

Run the automated checks with a running emulator or connected phone:

```shell
./gradlew testDebugUnitTest connectedDebugAndroidTest lintDebug assembleDebug
```

Tests concentrate on boundaries where failures could lose saved data or show the wrong person: actual HTTP decoding, real SQLite transactions/migration, file completion/cancellation, and navigation state. These provide more value than testing generated DTO getters or chasing a coverage percentage.

The tests cover the actual Retrofit boundary, mixed valid/invalid records, duplicate IDs, imprecise dates, living/null death, null occupation, failed refresh preservation, successful-empty versus uncached state, list refresh preserving profiles, relative replacement, SQLite rollback, cancellation, and persistence after database close/reopen. Portrait tests cover disk reuse without network, concurrent requests, invalid/oversized/interrupted responses, cancellation cleanup, and actual Android image decoding after store recreation. Debug builds permit cleartext HTTP only to localhost/127.0.0.1 so instrumentation can use MockWebServer; the service uses HTTPS.

The current run passed all 29 JVM tests and 33 Android instrumentation tests on API 36, along with `lintDebug` and `assembleDebug`. Checks cover list/profile ViewModel states, ID-specific refresh, duplicate request prevention, cancellation, required screen text, retry actions, partial-result disclosure, portrait decoding/retry, version-one migration, and cached A → B → A navigation followed by Back to the retained list. New checks exercise relative-path priority/missing targets/persistence, previews during pending or failed fetches, Back cancellation, bitmap reuse/upgrade/eviction, and rendered portrait growth/shrinkage at animation midpoints. Lint has zero errors and 19 unsuppressed warnings about newer tool/dependency versions and Android backup configuration.

The list-only live check loaded all 16 people and saved all 16 portraits through scrolling. After force-stop, airplane mode and disabled Wi-Fi, relaunch and scrolling still displayed saved text and portraits with failed-refresh feedback; portrait file hashes were unchanged.

The complete live check opened Hannah Ainsley, her relative Bartholomew Whitcomb, and his relative Amos Whitcomb. After force-stop and airplane-mode relaunch with Wi-Fi disabled, the list and all three profiles remained available through the same relative taps, including their portraits, birth/death details, occupations and biographies. Opening Ezra Whitcomb, whose full profile had not been loaded, showed “No saved profile is available” with Retry and Back. Original emulator network settings were restored afterward.

A fresh app state launched with airplane mode, Wi-Fi and mobile data disabled showed a connection explanation and Try again. After restoring saved data, a loaded profile survived rotation; birth details remained reachable in landscape. At 150% font scale, places and biography wrapped, Refresh and relative taps remained usable, and Android Back returned through the family stack to the list with scroll position retained. Font, rotation and network settings were restored.

The polished UI was checked in light and dark themes, at 150% font scale and in landscape. The three-profile offline family journey still passed with portraits on relative cards. An unopened relative retained its name/portrait preview with an explicit uncached failure. With system animation scales set to zero, navigation and Back still worked. Original appearance, animation, font, rotation and network settings were restored.

Measured implementation and verification intervals total approximately 65 minutes: three minutes for the foundation, eight for persistence, eight for the list, fifteen for profiles/navigation, six for final verification/documentation, and approximately twenty-five for UI/UX polish and additional checks. Collaborative planning and author review were not timed and are additional to those intervals; this is not a claim of total personal effort. Wall time from the first commit through the completed polishing phase was approximately 1 hour 50 minutes, including those discussions and review pauses; initial planning before that commit was not timed.

## Development assistance

AI assistance was used for planning, implementation, and verification. Technical decisions are reviewed with the author, who must be able to explain and defend the submitted code.

## Known gaps and another day

The required list, profiles, family navigation, typed models, queryable persistence and explicit states are implemented. Offline availability covers successfully saved records and successfully downloaded portraits; opening an uncached profile offline explains that no saved profile is available. Portrait downloads are separate from record transactions, so a canceled or failed image request can leave a placeholder even when the text is saved. Refresh happens on screen creation or user request; there is no background synchronization.

The interface uses app-owned lavender light/dark palettes, card layouts, separated profile sections and a shared portrait transition, built from standard Material components. There is no search, family tree, source viewer, full-screen portrait viewer, analytics or release signing. Verification used an API 36 emulator; minimum API 26 and a physical device have not been exercised. Lint warnings are reported above rather than suppressed.

With another day I would prioritize accessibility/TalkBack and device coverage, exercise more devices and text sizes, and add a bounded portrait retention/revalidation policy. For a larger service I would implement server pagination and Paging 3 before expanding features. I would replace the single refresh mutex with per-resource coordination if concurrent fetches become important.
