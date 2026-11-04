# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

HouseAffairs (working name): an Android family household app (budgets, kitchen/pantry, medicines, plans, documents) shared across a household with per-member privacy. Kotlin, Jetpack Compose, Material 3, Hilt, Firebase Auth (Google only) and Cloud Firestore (offline-first), DataStore, WorkManager. Single Gradle module `:app`, package `com.houseaffairs.app`.

Product intent lives in `docs/PRODUCT_SPEC.md`. The behaviour rules actually implemented (Home ranking, budget/kitchen rules, rollover, location, digest, weekly review) are in `docs/ADD_AND_HOME.md`. Read the relevant section before changing a feature. The "Project layout" in README.md is out of date: the Add sheet lives in `feature/add`, and `feature/quickadd` holds only the parser.

## Commands

```bash
./gradlew testLocalUnitTest                     # all JVM unit tests
./gradlew testLocalUnitTest --tests 'com.houseaffairs.app.core.data.PantryMathTest'
./gradlew testLocalUnitTest --tests '*QuickAddParserTest.need*'   # one test by name
./gradlew compileLocalKotlin                    # fast compile check
./gradlew installLocal                          # install the emulator-wired build
```

Running locally with no Firebase project:

```bash
firebase emulators:start --project demo-houseaffairs --only auth,firestore   # UI on :4000
/opt/homebrew/share/android-commandlinetools/emulator/emulator -avd HouseAffairs_Pixel
./gradlew installLocal
```

- The `local` build type sets `BuildConfig.USE_FIREBASE_EMULATORS = true`. `di/AppModule.kt` then points Auth (9099) and Firestore (8080) at `10.0.2.2`, and the sign-in screen offers test accounts (Asha, Ravi, Mia) instead of Google.
- The emulators enforce the real `firestore.rules`. Their data is wiped when they stop, but the app's offline Firestore cache can keep showing old data after a restart.
- `adb` is at `/opt/homebrew/share/android-commandlinetools/platform-tools/adb`.
- Optional `tmdb.apiKey=…` in `local.properties` enables movie lookups; TVMaze, Open Library and iTunes need no key.

## Architecture

**Session and permissions.**
- `core/data/SessionManager` is the single source of truth: SignedOut, NeedsHousehold, or Ready(user, household, me). ViewModels read `session.state.value as? SessionState.Ready` to get the household id and uid.
- A `Member` has `moduleAccess` (money, stock, medicine, plans, vault). `ui/MainScaffold` hides tabs the member can't access, but enforcement is server-side in `firestore.rules`.

**Permission rules that must stay in sync.**
- `RolePolicy.defaultAccess` must match `defaultAccess()` in `firestore.rules`.
- Every household subcollection must be listed in `moduleOf()` in `firestore.rules`. Unknown collections are denied, so a new collection needs an entry there.
- Every document carries `createdBy` (fixed after create) and `visibleTo` (`["*"]` for everyone, or a list of uids).
- Rules aren't filters: list queries must use `whereArrayContainsAny("visibleTo", listOf(Visibility.EVERYONE, uid))`. Combined with `orderBy`, they need a composite index in `firestore.indexes.json`.

**Data layer (`core/data`).**
- Repositories wrap Firestore and expose `Flow`s via `observeSnapshots()` (`FirestoreFlows.kt`).
- Business rules are pure Kotlin functions in separate files, unit-tested with fixed dates: `BudgetMath`, `Envelopes`, `PantryMath`, `Attention`, `Digest`, `Trips`, `Cards`, `Geo`, `RightNow`, `LiveWell` and others. Put new money, kitchen or ranking logic in a pure function next to these and test it, rather than in a ViewModel or composable. Pass `today: LocalDate` in instead of calling `LocalDate.now()` inside the function.
- Money is `Long` minor units plus a currency code (`core/util/Money.kt`).
- Rollover and savings are never stored: `envelopes()` recomputes them from transactions and transfers since the budget's `startedOn`. Backdated expenses therefore correct past months automatically.

**Adding things: one parser, one form engine.**
- `feature/quickadd/QuickAddParser` turns a typed or spoken line into a `QuickAddDraft`.
  - `parse()` tries questions (`Ask`), then edits to saved entries (`Change`), then batches, then `parseSingle`.
  - `parseSingle` is an ordered chain of `parseX ?: …`. Order matters, and `parseTask` is the catch-all: anything unrecognised becomes a task. Add new patterns before it and add cases to `QuickAddParserTest`.
- `feature/add/EntryType` defines every addable kind as a schema: its collection, the module that guards it, and a list of `FieldSpec`s. Forms are generated from these, so a new kind or field is a schema change, not a new screen.
- `AddViewModel` maps each draft onto an `EntryType` plus prefilled form values. `EntryRepository` writes the entry, along with side effects such as an expense for a grocery that has a cost.
- **Unsure lines** (`QuickAddParser.isUnsure`: fell through to Task with no date, no "remind me", no doing-word) get a second opinion, in this order:
  1. what the family taught (`PhraseRepository`, `households/{hid}/phrases/{phraseKey}`, shared by all members and with its own rule in `firestore.rules`);
  2. the on-phone `KindClassifier` (naive Bayes on `SEED_EXAMPLES` plus the taught phrases);
  3. the `Assistant` interface, if Settings → Smart understanding is on (or the person taps "Ask Gemini").
- The `Assistant` also has `pick` (ranks real items such as dishes or the watch list and gives a reason; titles not in the options are dropped) and `coach` (the daily live-well nudge). Home uses them when Settings → Smart suggestions is on. What gets sent is built by the pure `pickInputs`/`coachSummary` in `feature/home/SmartPicks.kt`: titles and scores only, never amounts.
- `GeminiAssistant` (bound in `di/AssistantModule`) calls Gemini through Firebase AI Logic. `assistPrompt`/`draftFromAi` are pure and tested, and every field of the reply is validated. It only works against a real Firebase project with AI Logic enabled; in the `local` build every call fails and the app falls back silently. Correcting the kind on the suggestion card, or saving a guess, teaches the phrase.

**Undo instead of confirm dialogs.**
- Actions return a `SavedEntry(message, undo)`, usually built from a repository function that returns an undo lambda.
- Screens pass it to `onSaved`, and `MainScaffold.showUndo` shows the snackbar.
- Home reaches these callbacks through `LocalHomeNav` (`nav.saved`, `nav.edit`, `nav.open("plans/TASKS")`). A route of the form `tab/SUBTAB` opens a tab on a specific sub-tab.

**Home (`feature/home`).**
- Sections are `HomeSection` values. Each person's order and on/off switches are saved per device as a string (`parseLayout` / `encodeLayout`). When renaming or removing a section, add the old name to `RENAMED` so saved layouts migrate.
- "Today" is built by `buildToday()` (in `Attention.kt`) from `buildAttention()` plus open tasks. "Coming up" excludes whatever Today already shows.

**Long lists.** Pantry, Shopping, Medicines, Supplies and Where it's kept all use one component:
- `organize()` (`core/data/ListOrganizer.kt`, pure and tested) decides search, filter chips, "Needs a look", and which category groups are open.
- `core/ui/OrganizedList.kt` draws it. A list tab is one `itemList(...)` call, which lays out the Add button, the search box (chips past `LONG_LIST`), "Needs a look", sticky collapsible groups and the footer in the same order everywhere. Rows are the one-line `CompactRow`.
- Group emoji and colours come from `categoryStyle()`. Add a style there when adding a category. Kitchen lists use the stock accent and Things lists the vault accent.
- Groups a person opened or closed are saved per phone in `SettingsRepository.toggledGroups`.
- New item lists should reuse this, not hand-roll groups.

**Where things are kept.** Every physical item can have a place:
- Where it's kept entries (`keptAt` collection, `location` field; documents are the "Documents" kind and fill the Documents tab).
- Pantry, medicines and supplies use a `keptAt` field on the stock document, read into `PantryStock.keptAt`.
- "X is in Y" for an item already in pantry, supplies or medicines updates that item's place (`AddViewModel.placeExisting` → `EntryRepository.setPlace`). Anything else becomes a new Where it's kept entry.
- "Where is X?" searches all of them through the pure `whereIs()` in `ThingsRepository.kt`. Valuables are Vault-only, so kids get answers only from lists their role can see.

**Rules that span several files.**
- **Subscriptions:** a renewal becomes spending only once it's logged. `lastLogged` on the `recurring` doc records the latest one, and `Renewals.kt` works out what's owed. "Log it" in Today, or `onRenew = "Log it automatically"`, calls `MoneyRepository.logRenewal`, which uses a transaction for automatic logging.
- **Repeating tasks:** ticking one off (`HomeRepository.completeTask`) creates the next, with the due date and rota from `Repeats.kt`.
- **Restock:** predictions use ticked-off shopping items (`boughtAt`) plus groceries added as bought (`Restock.kt`, `PantryRepository.observeHistory`).
- **Private topics:** these live on the household doc (`privateTopics`). `AddViewModel.persist` narrows `visibleTo` for new entries on them.
- **Teens and medicines:** teens only see medicines where they're in `forWhom`. This is enforced by `canUseItem` in `firestore.rules`, and every medicine query must use `RolePolicy.ownMedicinesOnly`.
- **Share sheet:** shared text and links arrive through `MainActivity` → `SharedInbox` → the Add sheet. Routing is in `Shared.kt`.
- **Google Calendar sync** (`core/calendar`):
  - It's per person, opt-in from Settings. It uses the `calendar.app.created` scope, so it can only see the "HouseAffairs" calendar it creates.
  - `planSync()` is the pure two-way rule set: the app wins when both sides changed; deleting in Google only stops syncing that item.
  - `CalendarSyncer` applies the plan. `CalendarStore` keeps the agreed state per phone, and `CalendarWorker` runs every 6 hours.
  - It needs a real Firebase/Google Cloud project with the Calendar API enabled and the scope on the OAuth consent screen. It can't run against the emulator's demo project.
- **Family service keys:** OMDb and TMDB keys are set by the owner in Settings → Connections and stored on the household doc (`integrations`). `WatchRepository` uses them, falling back to `local.properties`.
- **Follow-ups:** after "bought", "watched" or "finished", a quick-options sheet appears (`FollowUps.show(...)`, hosted in `MainScaffold`). The builders are pure functions in `core/data/FollowUps.kt`.
- **Recipe fit:** `RecipeFit.missing` and `short` drive what's shown: "Missing: …" and "need more …", never "2 of 4". Ideas only list dishes you can cook, or ones that rescue expiring food.
- **Demo mode:** "Try the demo" signs in anonymously, creates "Demo home" and seeds it (`DemoSeeder`). It needs Anonymous sign-in enabled in the Firebase project.
- **Optional keys** in `local.properties`: `tmdb.apiKey` and `omdb.apiKey` (IMDb and Rotten Tomatoes).

**Notifications.** `notify/DigestWorker` sends one daily digest via WorkManager. `HouseAffairsApp` reschedules it whenever the notification settings change. The digest content comes from the pure `Digest.kt`.

## Conventions

- UI strings are hard-coded English (not yet in `strings.xml`), written as short plain sentences.
- Comments explain *why* a rule exists, usually in one line. Keep that density.
- Dependency versions are pinned together in `gradle/libs.versions.toml`.
- `app/google-services.json` is git-ignored and per developer. The `local` build uses `app/src/local/google-services.json`.
