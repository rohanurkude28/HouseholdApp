# HouseAffairs — Product & Architecture Spec (v0.1, beta)

> Working name. Android first, global (multi-currency), family-shared, assistant-style.

---

## 1. Product principles (the bar every screen must clear)

| Principle | What it means in practice |
|---|---|
| **One-thumb, ≤3 taps** | Any common action (add expense, mark "milk is over", tick a task) is reachable from a single global **+** / voice button in ≤3 taps. |
| **Progressive detail** | Every form opens with 1–2 required fields (amount + category, or item name). Everything else sits behind "More details". Smart defaults fill the rest (today, me, last-used payment method, base currency). |
| **Assistant, not a database** | The Home screen is a *feed of what needs attention today*, not a menu. The app suggests; the user confirms. |
| **Quiet by default** | One daily digest + only truly time-critical pushes. Users tune it per module. |
| **Private by design** | Visibility is enforced server-side, not just hidden in the UI. |
| **Offline first** | Works in a basement grocery store. Syncs when back online. |

### Interaction patterns to use everywhere
- **Universal quick-add sheet**: type or speak — "450 groceries DMart", "milk over", "Riya dentist Friday 5pm", "add Dune 2 to watch list". Parsed into a draft card → one tap to save.
- **Sliders / steppers** for quantity ("how much left?" 0–100% slider for pantry items, rather than typing grams).
- **Progress bars & rings** for budgets, goals, weekly goals.
- **Swipe actions** on list rows (swipe right = done/used up, swipe left = snooze/edit).
- **Share-sheet entry**: share an Instagram reel / Amazon link / Maps place / PDF bill *into* HouseAffairs and it lands in the right list.
- **Undo snackbar** instead of "Are you sure?" dialogs.
- **Material 3 + dynamic colour**, light / dark / system theme, large-text friendly.

---

## 2. Information architecture (5 tabs)

| Tab | Contains |
|---|---|
| **Home** | Today feed (due tasks, expiring items, budget pace, upcoming bills/subscriptions, birthdays), quick-add, monthly snapshot card |
| **Money** | Budgets, transactions, subscriptions & recurring, savings goals, card cycles, monthly/yearly overview |
| **Stock** | Unified inventory: Grocery, Medicine, Household supplies; shopping list; recipes & "what can I cook" |
| **Plans** | Tasks & chores, events/birthdays, weekly goals, family lists (travel, watch, eat-out, buy, gifts) |
| **Vault** | Documents (bills, warranties, prescriptions, IDs) synced to Google Drive |

Settings (profile, household, members & permissions, currency, theme, notifications) lives behind the avatar.

---

## 3. Category review & refined scope

### 3.1 Login & Family
- **Google Sign-In only** (Android Credential Manager → Firebase Auth). No passwords.
- **Household** = shared space. Creator is *Owner*. Invite via link / QR / 6-digit code (expires in 48h). Invitee signs in with Google → lands in household after Owner approval (or auto-join if the invite was person-specific).
- A person can belong to more than one household later (e.g., parents' home) — design the data model for it now, ship the UI later.

**Permission model (recommended)** — roles + per-module access + private items:

| Role | Default access |
|---|---|
| Owner | Everything, manages members |
| Adult | Everything except member management |
| Teen | Tasks, lists, grocery, recipes; Money hidden; can see only own medicines |
| Kid | Own tasks, shopping list, watch list; no Money, no Medicine, no Vault |
| Helper (e.g., cook/maid) | Shopping list + specific tasks only |

On top of roles:
- **Module toggles per member** (Owner can override any default).
- **Sensitive categories** (e.g., sanitary, sexual wellness, certain medicines, salary) are flagged *restricted*: visible only to chosen members. Restricted items still count in totals for those who can see the totals, and appear as "Personal care (restricted)" in aggregates for others if needed.
- **Private item** toggle on any record: visible only to creator (+ optionally chosen members).
- ⚠️ Enforced in Firestore Security Rules, not just hidden in UI.

### 3.2 Budget & Expenses — *keep, with these refinements*
- **Configurable cycles** — this is the key insight from your notes:
  - *Budget period*: starts on salary day (e.g., 1st, 25th, last working day).
  - *Payment methods* each carry their own cycle: credit card statement date + due date; UPI/cash/debit post immediately.
  - Show **both views**: "Spent this budget period" and "Card bill: ₹X due on 12th (statement closes 28th)".
- **Setup wizard (60 seconds)**: monthly income (optional) → savings target (% or amount) → suggested category split (editable sliders) → done.
- **Pace line**: each budget ring shows spent vs. *expected by today* (linear, or weighted for categories like rent that hit day 1). Inline nudges: "Dining is 18% ahead of pace — ₹2,100 left for 12 days."
- **Tags + categories**: category is required (one), tags are free (many) — e.g., #trip-goa, #diwali.
- **Recurring & subscriptions**: frequency (weekly/monthly/quarterly/yearly/custom), auto-post or ask-to-confirm, renewal reminder 3 days before, "unused subscription?" prompt if marked unused.
- **Savings goals**: target amount + date → monthly contribution suggestion; can be linked to a Plans list item (car, trip) so the wish and the money are one object.
- **Multi-currency**: household base currency; each transaction can be in any currency and is converted at that day's rate (stored, never recalculated). Great for travel.
- **Who paid / for whom** (optional field) — lightweight, not full Splitwise.
- **Automatic addition (phased)**:
  1. Share a UPI/bank notification or receipt image → OCR (ML Kit, on-device) → draft.
  2. Opt-in Notification Listener that reads bank/UPI app notifications → drafts for confirmation.
  3. ❌ Avoid SMS reading — Google Play restricts `READ_SMS` and a finance-category app will likely be rejected.

### 3.3 Medicine & Grocery — *merge into one "Stock" engine*
They share 80% of the fields (name, qty, unit, bought on, expiry, cost, restock threshold, reminder, where bought). One engine, three types (Grocery / Medicine / Household supplies), different extra fields:

| Common | Grocery extra | Medicine extra |
|---|---|---|
| Name, type, quantity (slider for "% left"), unit, bought date, expiry, cost (→ auto-creates expense), store, restock threshold, rating 👍/👎, notes, visibility | Sub-type (dairy, veg, staples, snacks…), flavour tags (tangy, sweet, spicy, crispy, savoury), macros per 100g | Used for (fever, cold, pain… user-entered), for whom (member), dosage note, prescription link (Vault), restricted flag |

Smart behaviours:
- **"Running low"** → one tap adds to shopping list. Shopping list is shared and live (one person shopping, another adding).
- **Restock prediction**: learn consumption rate from purchase history ("You usually buy rice every 18 days — due Thursday").
- **Rating memory**: when reminding to restock, show "Last time: Amul 👍, Brand X 👎".
- **Expiry radar**: expiring in 3 days → Home feed; medicines expiring in 30 days; weekly "waste report" (₹ value expired).
- **Barcode scan** (ML Kit) + Open Food Facts lookup to fill name/macros instantly.
- Medicine disclaimer: "used for" is user-entered info, not medical advice.

### 3.4 Recipes — *keep, linked to Stock*
- Recipe = ingredients (linked to Stock items), steps (optional), source link (Instagram reel / YouTube / blog), flavour tags, prep time, servings, macros per serving.
- **Save from Instagram**: share reel → saved as "To try" with the link. Ingredient extraction from caption via AI later; manual ingredient chips for now.
- **"What can I cook?"** ranking = ingredients available × uses items about to expire × mood (tangy/sweet/spicy/crispy) × macro fit for today's target × past rating.
- **"Cook this"** → missing ingredients go to shopping list in one tap; cooking deducts quantities (optional).
- **Macros**: correction to the plan — HealthifyMe has no public API and MyFitnessPal's API is closed to new partners. Use:
  - **Health Connect** (Android) to read/write nutrition & daily targets — this is how you "integrate" with those apps.
  - **Open Food Facts** + **USDA FoodData Central** (both free) for ingredient macros.
- Daily target: carbs / protein / fat / fibre / kcal per member, simple rings.

### 3.5 Family Lists (Wish/Bucket) — *keep, but unify into one List engine with templates*
You listed ~10 sub-lists. Instead of 10 screens, one **List** object with a **template** that adds the right fields:

| Template | Extra fields | Integrations |
|---|---|---|
| Travel (domestic/intl) | Place, things to try, things to buy, reel links, best season, est. budget | Maps place, links; can create a savings goal |
| Watch together | Movie/series, platform, theatre vs. OTT, release date, IMDb/RT rating | **TMDB** (metadata + where to stream) + **OMDb** (IMDb & Rotten Tomatoes scores) |
| Eat out | Restaurant, area, dishes to try, rating after visit | Maps place |
| Buy (home / personal belongings) | Item, link, price, priority, for whom | Price-drop later |
| Gifts | For whom, occasion (linked to Event), budget, ideas | Event reminders |
| Big goals (car, house) | Target amount/date | **Linked savings goal in Money** |
| Places & shops | Shop, area, what to buy there | Later: Google Maps geofence → "You're near Spar; curd is about to run out" |

- Every list item: added by, voted by (❤️ from members), status (idea → planned → done), photo/link.
- **Weekly goals** move to **Plans → Goals** (they're tasks with a cadence, not wishes).

### 3.6 Tasks & Events — *keep*
- Task: title, assignee, assigned by (auto), due, reminder, recurrence (chores!), checklist, priority, privacy.
- **Chore rotation** (e.g., trash: Rohan → Prachi → weekly).
- **Events**: birthdays, anniversaries, renewals (insurance, car service, passport), with lead-time reminders and a link to the Gift list.
- **Google Calendar** two-way sync for tasks with due time and events (opt-in per member).
- **Weekly goals**: learnings / things to get done, progress slider, Sunday recap.

### 3.7 Vault (Documents) — *keep, Google Drive backed*
- Store files in the **user's own Google Drive** under `HouseAffairs/` (zero storage cost for us, users own their data).
- Auto-folders: `Bills/<Category>/<Year>`, `Warranties`, `Medical/<Member>`, `Home`, `Vehicles`, `IDs`.
- Capture: camera scan (ML Kit Document Scanner) or share a PDF → OCR suggests category, amount, date, merchant, warranty period → confirm.
- **Links**: a bill can create the expense; a warranty creates an expiry reminder; a prescription links to a medicine and a member.
- Use the **`drive.file`** scope only (non-sensitive; app sees only files it created). Broader Drive scopes are "restricted" and require a paid security assessment.
- IDs/medical docs default to restricted visibility.

### 3.8 Overview (Monthly & Yearly)
- **Monthly**: spend vs budget by category, savings rate vs target, subscriptions total, top merchants, waste (expired stock ₹), tasks done per member, goals progress.
- **Yearly outlook**: month-by-month trend, projected annual savings, upcoming big-ticket items (renewals, planned trips, goals), "one insight" per month written in plain language.
- Shareable monthly summary card (image) for the family.

### 3.9 Gaps worth adding (small effort, high value)
1. **Today feed** on Home (the assistant).
2. **Global search** across everything.
3. **Home maintenance & renewals** (AC service, insurance, vehicle PUC) — fits Events + Vault.
4. **Data export (CSV/JSON) & account deletion** — Play Store requires deletion; export builds trust.
5. **Onboarding in < 90 s**: sign in → name household → currency auto-detected from locale → invite later.
6. **Notification policy**:
   - Daily digest (user-chosen time, default 8 pm) bundles expiries, low stock, tomorrow's tasks.
   - Real-time only for: task assigned to you, bill due today, medicine reminder, invite accepted.
   - Per-module mute + quiet hours. Never more than ~3 pushes/day by default.

---

## 4. Assistant & automation roadmap

| Phase | Capability | Tech |
|---|---|---|
| Beta | Quick-add with rule-based parser ("450 groceries", "milk over") | Local regex/grammar |
| Beta | Voice quick-add | Android `SpeechRecognizer` (free, on-device) |
| Beta | Receipt/bill OCR, barcode, document scan | Google ML Kit (free, on-device) |
| v1 | Natural-language parsing & recipe suggestions via LLM | Server-side LLM call (keys never in the app) |
| v1 | Notification-listener auto-drafts for UPI/bank | `NotificationListenerService`, opt-in |
| v2 | Location nudges near saved shops | Geofencing API + Maps |
| v2 | Monthly insight writer | LLM over aggregated numbers |

---

## 5. Recommended tech stack

| Layer | Choice | Why |
|---|---|---|
| App | **Kotlin + Jetpack Compose + Material 3** | Best-in-class Android UX, dynamic colour, dark mode, smallest app; KMP later if iOS needed |
| Architecture | MVVM + Clean-ish modules, Hilt DI, Kotlin Flows | Testable, standard |
| Auth | **Firebase Auth** with Google (Credential Manager) | Free, Google-only login out of the box |
| Database | **Cloud Firestore** with offline persistence | Real-time family sync + offline for free; Security Rules for permissions |
| Local reminders | **WorkManager + AlarmManager** | Expiry/medicine/task reminders computed on device → no server cost |
| Push (cross-member) | **Firebase Cloud Messaging** | Free |
| Server logic | Cloudflare Workers (free) *or* Firebase Cloud Functions on Blaze with $1 budget alert | Sends FCM for "task assigned", calls LLM, fetches FX rates |
| Files | **Google Drive API** (`drive.file`) | Users' own storage |
| Calendar | Google Calendar API | Opt-in |
| Movies | TMDB + OMDb | Free tiers |
| FX rates | Frankfurter (ECB data, free, no key) | Daily cached |
| Analytics/crash | Firebase Analytics + Crashlytics | Free |

### Core data model (Firestore)
```
users/{uid}                          profile, settings, theme, currency override
households/{hid}                     name, baseCurrency, budgetCycleStartDay, createdBy
households/{hid}/members/{uid}       role, moduleAccess{money,stock,medicine,vault,...}, restrictedCategoryAccess[]
households/{hid}/invites/{code}      role, expiresAt, createdBy
households/{hid}/paymentMethods/{id} type, statementDay, dueDay
households/{hid}/budgets/{id}        period, category, limit, savingsTarget
households/{hid}/transactions/{id}   amount, currency, baseAmount, fxRate, category, tags[], paidBy, methodId, visibility, createdBy
households/{hid}/recurring/{id}      frequency, nextDate, autoPost
households/{hid}/goals/{id}          target, deadline, linkedListItemId
households/{hid}/stock/{id}          type(grocery|medicine|supply), qty, pctLeft, expiry, rating, flavourTags[], forMember, visibility
households/{hid}/recipes/{id}        ingredients[{stockRef,qty}], tags[], macros, sourceUrl
households/{hid}/lists/{id}          template, title;  /items/{id} template-specific fields
households/{hid}/tasks/{id}          assignee, assignedBy, due, recurrence, visibility
households/{hid}/events/{id}         type, date, leadDays
households/{hid}/documents/{id}      driveFileId, folder, linkedTxnId, warrantyUntil, visibility
```
Every document carries `createdBy` and `visibleTo` (`["*"]` = whole family, otherwise the allowed uids); Security Rules check `members/{uid}.moduleAccess` + `visibleTo` on every read (see `firestore.rules`).

---

## 6. Free hosting plan for the 1-month beta

| Need | Free option | Free-tier limits (plenty for beta) |
|---|---|---|
| Auth, DB, push, analytics, crash | **Firebase Spark (free) plan** | Firestore: 1 GiB stored, 50k reads/20k writes per day; Auth & FCM free |
| Server functions | **Cloudflare Workers** free | 100k requests/day; no credit card |
| (alt) Cloud Functions | Firebase **Blaze** with a $1 budget alert | 2M invocations/month free; needs a card on file |
| File storage | **User's Google Drive** | $0 to us |
| Distribution to testers | **Firebase App Distribution** (free) → then **Google Play Internal/Closed testing** | Play Console needs a one-time **US$25** developer fee — the only unavoidable cost |
| CI builds | **GitHub Actions** | 2,000 min/month on private repos |
| Landing/privacy page | GitHub Pages / Firebase Hosting | Free (privacy policy URL is required for Google sign-in consent screen and Play) |

Beta gotchas:
- Google OAuth consent screen stays in **"Testing"** mode (max 100 test users) — fine for beta, no verification needed. Calendar scopes need Google verification before public launch; start that in week 3.
- New personal Play developer accounts must run a **closed test with ≥12 testers for 14 days** before going to production — plan your beta around it.
- Move to paid (Blaze / GCP or another cloud) only when you outgrow daily quotas; Firebase → GCP is a zero-migration upgrade.

---

## 7. Release plan

| Milestone | Scope |
|---|---|
| **Week 1** | Project setup, Google login, household + invite + roles, theme, currency, Home feed shell, quick-add sheet |
| **Week 2** | Money: budgets, cycles, transactions, recurring/subscriptions, pace nudges, goals |
| **Week 3** | Stock (grocery + medicine), shopping list, reminders, digest notifications; Tasks & Events |
| **Week 4** | Recipes + "what can I cook", Lists with templates (TMDB/OMDb), Vault (Drive), monthly overview; closed beta |
| **Post-beta** | Calendar sync, LLM assistant, notification-listener auto-add, Health Connect, Maps geofencing, yearly outlook |
