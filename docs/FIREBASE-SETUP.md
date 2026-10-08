# ReTally — Firebase setup (anonymous users first)

Goal of this milestone: every receipt a guest saves is also written to Firestore under an
anonymous Firebase uid, so it survives a new phone once they link an account later.
Code for this is already in the repo; the steps below are the console + project wiring.

## 1. Firebase console (10 min)
1. Open your ReTally project → **Build → Authentication → Get started → Sign-in method** →
   enable **Anonymous**. (Nothing else yet.)
2. **Build → Firestore Database → Create database** → *Production mode* → pick a region close
   to your users (e.g. `us-central1` / `nam5`). The region cannot be changed later.
3. **Project settings → Your apps → Add app → Android**: package `com.weevmedia.retally`, then add a
   **second** Android app for `com.weevmedia.retally.debug` (debug builds use that id). Download
   `google-services.json` — one file covers both — and save it as `androidApp/google-services.json`.
4. **Add app → iOS**: bundle id `com.weevmedia.retally`. Download
   `GoogleService-Info.plist` and drop it into `iosApp/ReTally/` next to `Info.plist`,
   ticking *Copy items if needed* and the ReTally target.
5. **Build → App Check** can wait until this works; enable it before release.

## 2. Deploy the rules and indexes
```bash
cd backend/firestore
npm install
firebase login
firebase use --add          # select the project
npm run deploy              # rules + indexes + storage rules
GOOGLE_APPLICATION_CREDENTIALS=./serviceAccount.json npm run provision   # optional catalogs
```
Until the rules are deployed, client writes fail with *PERMISSION_DENIED*.

## 3. Android
Already wired: `com.google.gms.google-services` plugin in `androidApp/build.gradle.kts`,
GitLive `firebase-auth` / `firebase-firestore` in `shared`, `CloudBackend.FIREBASE` in
`ReTallyApplication`. Sync Gradle after adding `google-services.json`, then:
```bash
./gradlew :shared:compileDebugKotlinAndroid :androidApp:assembleDebug
```

## 4. iOS
1. Xcode → project → **Package Dependencies → +** → `https://github.com/firebase/firebase-ios-sdk`
   → *Up to Next Major* 11.x → add products **FirebaseAuth** and **FirebaseFirestore** to the
   ReTally target (FirebaseCore comes with them).
2. Target → **Build Settings → Other Linker Flags** → add `-ObjC` (the Kotlin framework is
   static; Firebase's Objective-C categories need this to link).
3. `ReTallyApp.swift` already calls `FirebaseApp.configure()` before `IosApp.start`.
4. Rebuild the Kotlin framework and run:
```bash
./gradlew :shared:linkDebugFrameworkIosSimulatorArm64
```

## 5. Verify
1. Fresh install → scan (or long-press the scan icon in debug) → Save.
2. Firebase console → **Authentication → Users**: one anonymous user appeared.
3. **Firestore → Data**: `users/{uid}` profile, `users/{uid}/receipts/{id}` and its
   `lineItems` subcollection.
4. Kill the app, relaunch, save another receipt: same uid, second receipt.
5. Settings → **Sync now** re-uploads anything still marked unsynced (idempotent).

## What happens in code
| step | where |
|---|---|
| anonymous sign-in on first cloud write | `data/auth/FirebaseAuthRepository.ensureSignedIn()` |
| Receipt → schema-v1 documents (PII reduced to a category) | `data/cloud/FirestoreReceiptMapper` |
| batch write receipt + line items, `set(merge)` idempotent | `data/cloud/FirebaseCloudSyncService` |
| upload right after Save, outside the screen's lifetime | `domain/cloud/ReceiptBackup` (called from `ScanReviewContract.Store`) |
| retry of anything unsynced | Settings → Sync now (`SettingsContract.Store.syncNow`) |
| backend selection | `CloudConfig.backend` (`FIREBASE` / `REST`), `simulate` for tests |

## Next milestones
* Link Google / Apple / email to the anonymous uid (`linkWithCredential`) from Settings.
* Auth `onCreate` function → `accounts/{uid}`; scheduled purge of stale anonymous users.
* Upload the receipt image to Storage and set `image.storagePath`.
* Pull-down sync (Firestore → local) so a second device sees the receipts.
