# HouseAffairs (working name)

A family household app for Android: budgets, groceries and medicines, plans, and documents, shared across the family with per-member privacy.

The product spec is in [docs/PRODUCT_SPEC.md](docs/PRODUCT_SPEC.md).

## Stack

Kotlin · Jetpack Compose · Material 3 (Material You colours, light/dark) · Hilt · Firebase Auth (Google only) · Cloud Firestore (offline-first) · Credential Manager · DataStore.

## What works (week 1)

- **Google sign-in** (Credential Manager → Firebase Auth).
- **Households:** create one (currency picked from the phone's locale), or join with a 6-character invite code that lasts 48 hours.
- **Roles:** Owner, Adult, Teen, Kid and Helper. Each role sets which tabs a member sees. The owner can change roles. Access is enforced by `firestore.rules`.
- **Home:** a greeting, a quick-add bar, a getting-started checklist with a progress bar, and a placeholder for the Today feed.
- **Add anything** (the + button), by voice or by form:
  - Speak or type a line and it pre-fills the right form. "Netflix 649 monthly", "crocin for Riya expires March", "Riya birthday 12 Dec", "paneer expires Friday" and "milk over" all work.
  - Save in one tap, or tap **Add details**.
  - Or pick one of 12 kinds: expense, subscription, grocery, medicine, to buy, task, event, wish, recipe, place, goal, and document (coming soon). Then fill a form that shows essentials first and the rest under **More details**. A mic in the form fills it by voice.
  - **Who can see this** (Everyone / Only me / Choose people) on every entry, for sensitive items.
  - Each person only sees the kinds their role allows.
  - Field-by-field coverage of the original brief is in [docs/ADD_AND_HOME.md](docs/ADD_AND_HOME.md).
- **Money:**
  - Set a monthly budget (salary-day month, savings goal, split by category).
  - See **what's left**: spent, planned and due, with per-category bars and pace.
  - Plan spends ahead, and mark them paid when they happen.
  - A grocery or medicine with a cost lands in the budget automatically.
- **Stock**, the kitchen loop:
  - **Pantry:** quantities with a "how much is left" bar.
  - **Recipes:** ingredients with quantities, plus "what can I cook?" ranking.
  - **Shopping:** ticking an item off restocks the pantry.
  - **Medicines.**
  - Say or type **"made paneer sabzi"**: the pantry goes down, and anything low or missing goes on the shopping list.
- **Home stats strip:** budget left, tasks left, next birthday or anniversary, and what's expiring. The eye button hides amounts.
- **Home, by priority:**
  - **Needs attention** ranks overdue and expired items (Urgent), then things due or expiring today (Today), then birthdays, renewals and expiries coming up (Soon).
  - **Filter chips:** Money, Kitchen, Health, Plans.
  - **Customise Home:** reorder or hide sections.
- **Settings:** theme (System/Light/Dark), the home's currency, family members and roles, invites, and sign-out.
- The Money, Stock, Plans and Vault tabs are placeholders for weeks 2–4.

## Try it locally, no Firebase account needed

The `local` build type points the app at the [Firebase Local Emulator Suite](https://firebase.google.com/docs/emulator-suite) on your Mac. It uses three test accounts (Asha, Ravi and Mia) instead of real Google sign-in, so you can try invites and roles on a single device.

1. Start the emulators. Leave this running; the emulator UI is at http://127.0.0.1:4000.
   ```bash
   firebase emulators:start --project demo-houseaffairs --only auth,firestore
   ```
2. Start an Android emulator. A virtual device named `HouseAffairs_Pixel` has already been created.
   ```bash
   /opt/homebrew/share/android-commandlinetools/emulator/emulator -avd HouseAffairs_Pixel
   ```
3. Install and run the app.
   ```bash
   ./gradlew installLocal
   ```

The emulators enforce the real `firestore.rules`, so permissions behave exactly as they will in production. Emulator data is wiped when you stop them.

## One-time setup for real devices (≈15 minutes, free)

1. **Install [Android Studio](https://developer.android.com/studio).** It brings the Android SDK and Gradle.
2. **Create a Firebase project** at <https://console.firebase.google.com> on the free Spark plan.
3. **Add an Android app** with the package name `com.houseaffairs.app`. Add your debug SHA-1, which you can print with:
   ```bash
   keytool -list -v -keystore ~/.android/debug.keystore -alias androiddebugkey -storepass android
   ```
   The debug keystore is created the first time Android Studio builds the app.
4. **Turn on Google sign-in:** go to Authentication → Sign-in method and enable Google. This also creates the web OAuth client the app needs.
5. **Download `google-services.json`** into `app/`. It is git-ignored. Download it again after step 4 so it includes the OAuth client.
6. **Create the Firestore database:** go to Firestore Database → Create database (production mode), then deploy the rules:
   ```bash
   npm install -g firebase-tools
   ```
   ```bash
   firebase login
   ```
   ```bash
   firebase deploy --only firestore:rules,firestore:indexes --project <your-project-id>
   ```
7. **Open this folder in Android Studio.** Let it sync, then run on a phone or an emulator. The emulator needs a Google Play image so Google sign-in works.

Run the unit tests (the quick-add parser and the role policy):
```bash
./gradlew testLocalUnitTest
```
`./gradlew` is created the first time Android Studio syncs. You can also run `gradle wrapper` yourself.

## Project layout

```
app/src/main/java/com/houseaffairs/app/
  core/model        Household, Member, Role, RolePolicy (default access per role)
  core/data         Auth, Household, Session (who/where/what-can-I-see), QuickAdd, Settings repositories
  core/designsystem Theme (dynamic colour, light/dark override)
  core/ui           Shared composables (avatar, currency picker, placeholders)
  feature/*         auth, household setup, home, quickadd (parser + sheet), settings
  ui/               Root (session routing) and the main scaffold (tabs filtered by permissions)
firestore.rules     Server-side permission model
```

## Renaming the app later

Change `app_name` in `app/src/main/res/values/strings.xml` and `applicationId` in `app/build.gradle.kts`. Then register the new package in Firebase and download a new `google-services.json`.

## Notes

- Strings are hard-coded in English for now. Move them to `strings.xml` before translating.
- Dependency versions are pinned to a known-compatible set. Android Studio will suggest upgrades, which are safe to take together.
