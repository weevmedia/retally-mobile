# ReTally

Scan a receipt, read every line item, and catch anything charged twice.

Kotlin Multiplatform app with a Compose Multiplatform UI shared by iOS and Android. One
codebase for the screens, the state machines, persistence and the cloud boundary; a thin
Swift/Kotlin host per platform for the things stores require (signing, ads SDK, splash).

```
ReTally/
├── shared/            Kotlin Multiplatform library → Android AAR + iOS "Shared" framework
│   └── src/
│       ├── commonMain/kotlin/com/retally/shared/
│       │   ├── core/          NanoRedux: State / Action / Effect / Store
│       │   ├── domain/        receipts, preferences, cloud sync boundary, ads config
│       │   ├── presentation/  one Contract per screen (Splash, Main, Home, Settings)
│       │   ├── ui/            Compose Multiplatform screens, theme, components
│       │   └── di/            Koin graph + PlatformDependencies
│       ├── androidMain/       AdView banner, Android Platform
│       ├── iosMain/           UIKitView banner bridge, IosApp entry point
│       └── commonTest/        Store unit tests (kotlin-test + turbine)
├── androidApp/        Android application module (splash theme, AdMob + UMP bootstrap)
├── iosApp/            Xcode project (SwiftUI host, GoogleMobileAds via SPM)
└── ios/               ← the original SwiftUI-only app, kept for reference; delete when ready
```

## Architecture

MVVM on top of clean-architecture layers, all in `shared`:

```
shared/src/commonMain/kotlin/com/retally/shared/
  core/          NanoRedux Store, ViewModel<S,A,E> base, Platform
  domain/        Pure Kotlin: models, repository/service interfaces, configs (no framework code)
  data/          Implementations: SettingsReceiptRepository, KtorCloudSyncService, ...
  presentation/  One package per screen: <Screen>Contract (State / Action / SideEffect / Store)
  ui/            Compose Multiplatform: stateless <Screen>Screen(state, dispatch) + iOS-only <Screen>Route
  di/            Koin modules (dataModule, storesModule)
```

Every screen is a `Contract` interface holding four things, exactly like the Sidearm app:

| Piece | Role |
|---|---|
| `State` | Immutable data class the screen renders. |
| `Action` | Sealed class of user/system intents. UI calls `dispatch(action)`. |
| `SideEffect` | Sealed class of one-shot events (navigate, snackbar, open URL). Delivered once through a `Channel`, never replayed on recomposition or rotation. |
| `Store` | Extends `core.Store<S, A, E>`; reduces actions into state, launches repository work on its own `CoroutineScope`. |

The **ViewModel** (`core.ViewModel<S,A,E>` base in `shared`, concrete classes per platform) owns one
Store for the screen's lifetime and clears it in `onCleared()`. Business rules stay in the Store
(unit-tested in `commonTest`); platform-only glue (camera intents, permission results) goes in the
platform ViewModel.

Data flows one way: `Repository` → `Store` → `State` → UI → `Action` → `Store`.

### Android: Fragment per screen + Navigation component

`androidApp` is a single `MainActivity` (AppCompat) hosting a `NavHostFragment` and a Material
`BottomNavigationView`. Routing lives in `res/navigation/nav_graph.xml` (Safe Args enabled):

```
splashFragment  --action_splash_to_home (popUpTo splash)-->  homeFragment  <-- bottom nav -->  settingsFragment
```

Each screen is one package in `androidApp/.../ui/<screen>/` with three files, as in Sidearm:

| File | Role |
|---|---|
| `<Screen>Fragment` | `BaseComposeFragment<S,A,E,VM>` subclass. Hosts a `ComposeView`, renders `ScreenContent(state)`, handles side effects in `onViewEffect` (navigate / snackbar / intent). |
| `<Screen>Screen` | Android entry composable `(state, dispatch, …)`. Binds Android-side inputs and renders the shared Compose Multiplatform screen from `shared/ui/<screen>`. |
| `<Screen>ViewModel` | Extends `core.ViewModel`, owns the shared `Store`; Android-only logic lives here. |
 ViewModels are bound with Koin's `viewModel {}` in
`androidApp/.../di/ViewModelModule.kt` and injected with `by viewModel()`; the shell `MainViewModel`
is activity-scoped (`by activityViewModel()`).

**Adding a screen (Android):** Contract + Store in `shared/presentation/<screen>`, stateless
composable in `shared/ui/<screen>`, register the Store in `storesModule`; then `androidApp/ui/<screen>/`
gets `<Screen>Fragment` + `<Screen>Screen` + `<Screen>ViewModel`, the ViewModel goes in
`viewModelModule`, and a `<fragment>` + `<action>`s (and `<argument>`s) go in `nav_graph.xml`.

### iOS: SwiftUI View + ViewModel per screen

`iosApp` mirrors Sidearm's iOS layout. Each screen is a folder under `ReTally/UI/<Screen>/` with:

| File | Role |
|---|---|
| `<Screen>ViewModel.swift` | `@MainActor ObservableObject`. Pulls its Store from `IosDependencies`, builds the Compose screen with `IosScreens.shared.<screen>(store:)`, collects side effects with `store.collectSideEffect { }` (navigation, VisionKit, links, messages) and clears the store in `deinit`. |
| `<Screen>View.swift` | SwiftUI view: hosts the Compose screen through `ComposeHostView` and owns navigation (`NavigationStack`, `fullScreenCover`, `TabView`). |

`UI/Root` switches splash → `UI/TabBar` (Home / Settings tabs); `UI/Home` pushes `UI/ScanReview`.
State is observed inside Compose straight from the store, so Swift never copies state — it only
reacts to effects. The Kotlin side of this bridge is `shared/src/iosMain/.../platform/`
(`IosApp.start`, `IosDependencies`, `IosScreens`), and `Store.collectState/collectSideEffect`
return a `Closeable` for Swift.

**Adding a screen (iOS):** Contract + Store in `shared/presentation/<screen>`, stateless composable in
`shared/ui/<screen>`, register the Store in `storesModule`, add `getXStore()` to `IosDependencies` and
`IosScreens.x(store:)`, then `UI/<Screen>/` gets `<Screen>View` + `<Screen>ViewModel`, and the Xcode
project gets the two files (they are listed in `project.pbxproj`).

### Receipt scanning

```
Home ──ScanClicked──▶ Store ──LaunchScanner──▶ host camera + OCR ──TextRecognized(lines)──▶ Store
     ◀──popBackStack──  ScanReview Store ◀──OpenScanReview(lines)──┘
```

The camera and OCR are platform code (VisionKit `VNDocumentCameraViewController` + Vision on iOS;
ML Kit document scanner + on-device text recognition on Android), each merging text fragments that
sit on the same printed row. Everything after that is shared: `domain/scan/ReceiptParser` turns the
lines into a `ParsedReceipt` (store, date, payment method, priced line items, duplicate flags) and
`presentation/scan/ScanReviewContract` holds the editable draft the shopper confirms before it is saved
through `ReceiptRepository`. The parser is a pure Kotlin port of the original iOS `ReceiptParser`
and is covered by `ReceiptParserTest`.

## Features in this build

- **Splash** — branded Compose splash that warms the Google Mobile Ads SDK (consent form via
  UMP first) while the logo animates; never blocks longer than 4 s.
- **Home** — receipt list with per-receipt duplicate badges, a summary card of total
  overcharge, and the primary *Scan receipt* CTA. Debug builds get a **+** button that adds a
  realistic sample receipt so the list can be exercised without a printer.
- **Scan & review** — document camera (VisionKit / ML Kit) → on-device OCR → shared parser →
  review screen with an editable store name, tap-to-toggle duplicate flags, per-item removal,
  the raw scanned text for transparency, and Save/Discard. Images never leave the device.
- **Settings** — theme (system/light/dark), duplicate highlighting, haptics, cloud backup
  toggle + *Back up now*, delete-all, privacy/terms/support links, version.
- **Ads** — a standard 320×50 AdMob banner anchored under the Home list. Slot collapses when
  consent is missing or the SDK is unavailable so layout never jumps.
- **Cloud placeholder** — `CloudSyncService` with a Ktor implementation of a proposed
  `POST /v1/receipts/batch` contract. Empty base URL keeps the app fully offline; debug builds
  demo the flow against `SimulatedCloudSyncService`.
- **Persistence** — receipts and preferences as JSON in `multiplatform-settings`
  (SharedPreferences / NSUserDefaults). Swap for SQLDelight when search across thousands of
  receipts becomes a feature; the repository interface does not change.

## Build & run

Requirements: JDK 17+, Android Studio (Ladybug or newer) with the KMP plugin, Xcode 16+.

```bash
./gradlew :shared:allTests                 # store unit tests (JVM + iOS simulator)
./gradlew :androidApp:assembleDebug        # debug APK
./gradlew :androidApp:bundleRelease        # Play Store AAB (needs keystore.properties)
open iosApp/ReTally.xcodeproj              # iOS — the "Compile Kotlin Framework" phase runs Gradle
```

First Xcode build resolves the Google Mobile Ads Swift package; let it finish before archiving.

## Before you ship

Everything below is a placeholder chosen so the app runs today:

1. **AdMob IDs** — replace Google's sample ids:
   - Android app id: `androidApp/build.gradle.kts` → `manifestPlaceholders["admobAppId"]`
   - Android banner unit: `ReTallyApplication.kt` → `homeBannerUnitId`
   - iOS app id: `iosApp/ReTally/Info.plist` → `GADApplicationIdentifier`
   - iOS banner unit: `iosApp/ReTally/ReTallyApp.swift` → `AppConfig.homeBannerUnitId`
   - Create a UMP privacy message in the AdMob console (Privacy & messaging → GDPR + US states)
     or the consent form will never show and ads will not serve in the EEA/UK.
2. **Legal URLs** — `SettingsContract.LegalLinks` defaults point at `retally.app/*`. Host real
   pages; both stores require a reachable privacy policy.
3. **Android signing** — copy `keystore.properties.example` → `keystore.properties`, generate a
   keystore into `signing/` (gitignored).
4. **iOS signing** — `DEVELOPMENT_TEAM` in the pbxproj is the one from the old project; change
   it under Signing & Capabilities if you ship from another team.
5. **App icons** — `AppIcon.png` (iOS) and `ic_launcher_foreground.xml` (Android) are a generated
   placeholder of the receipt glyph. Replace with final artwork; keep the mint `#17B08B` field.
6. **Cloud** — set `CLOUD_BASE_URL` (`androidApp/build.gradle.kts`) and `AppConfig.cloudBaseUrl`
   (iOS) once the backend exists; the request/response DTOs are in `CloudSyncService.kt`.
7. **Store listing** — App Store: fill in App Privacy (ads → "Advertising Data", "Device ID";
   receipts stay on device unless backup is on). Play: Data safety form + the
   `com.google.android.gms.permission.AD_ID` declaration is already in the manifest.

## Roadmap

- Parser tuning against real receipts (multi-line item names, weight-priced produce, quantities).
- Receipt detail screen (line items, duplicate highlights, edit/save).
- Real backend for `CloudSyncService`.
