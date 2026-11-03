# Adding things & the Home page: requirement review

## Why the first quick add wasn't enough

The first version of quick add turned one line of text into one of 4 kinds (expense, shopping, task, list item), each with 2–3 fields. The original brief asks for about 12 kinds of things, many with 6–10 fields. Some of those fields, like "for whom", expiry, frequency and links, can't be captured in one sentence reliably. The brief also needs per-item privacy (sensitive medicines or categories), which had no place to go.

Home showed every section in a fixed order. Once medicines, subscriptions, birthdays and wishlists exist, that becomes a long, flat scroll with no sense of what matters today.

## The new model

### One Add sheet, two ways in, same forms

```
            ┌─────────── Add sheet ────────────┐
 Speak / ──►│  “crocin for Riya expires March” │──► Suggestion card ──► Save (1 tap)
 type        │                                  │                  └─► Edit details ─┐
            │  or pick a tile:                 │                                    ▼
 Tap a  ───►│  Expense · Subscription · Grocery│──────────────────────────► Prefilled form
 tile        │  Medicine · Shopping · Task …    │                           (mic: "fill by voice")
            └──────────────────────────────────┘
```

* **Voice or text** pre-fills a form; it never saves a guess on its own. One tap saves when the required fields are filled; otherwise the form opens.
* **Forms** are built from one schema per kind, so adding a new kind means adding field definitions, not a new screen.
* **Essential fields first** (usually 2–4). Everything else is under **More details**. Sensible defaults: today's date, me, the home's currency, Monthly.
* **Who can see this**: Everyone (the default), Only me, or chosen people. This covers sanitary, sexual-wellness and other sensitive items. Rules still enforce module access on top.

### Field types (the form engine)

| Widget | Used for |
|---|---|
| Text / multi-line | names, notes, things to try, ingredients |
| Money (amount + currency) | expense, subscription, cost, budget |
| Choice chips (single / multi, custom values allowed) | category, frequency, flavour tags, used-for, priority |
| Date with quick chips (Today, Tomorrow, Next week) + calendar | due, expiry, next payment, birthday |
| Quantity (number + unit chips) | 2 kg, 10 tablets, 1 pack |
| Level slider | "how much is left", goal progress |
| Family members (avatars) | for whom, assignee, paid by |
| Rating 👍/👎 | was this brand good? |
| Link | Instagram reel, recipe, maps link |

### Coverage of the original brief

| Brief item | Kind | Essential fields | More details | Voice example |
|---|---|---|---|---|
| Add expenses easily | **Expense** | amount, category, where, date | payment method, paid by, tags, note | "1200 dinner at Toit" |
| Subscriptions, frequency of payments | **Subscription** | name, amount, frequency, next payment | category, payment method, reminder, note | "Netflix 649 monthly" |
| Credit-card vs salary cycle | Payment method on expense and subscription; cycles in the Money tab (week 2) | — | — | — |
| Grocery: type, expiry, quantity, cost, rating, flavour tags | **Grocery** | name, quantity, best before, type | cost, store, how much is left, flavours, rating, restock reminder | "bought 2kg rice expires June" |
| Medicine: used for, bought, expiry, cost, for whom, quantity, reminder | **Medicine** | name, used for, for whom, expiry | quantity, bought on, cost, form, dosage, restock reminder | "crocin for Riya expires March 2027" |
| Sensitive categories hidden from kids | **Who can see** on every kind + Medicine module | — | — | — |
| Remind when to buy | **Shopping** + restock reminders | item, quantity | store, priority, note | "milk over", "add paneer to shopping list" |
| Recipes linked to stock, mood tags, macros, reels | **Recipe** | name, link, mood, ingredients | meal type, prep time, macros, rating | "add paneer tikka reel to recipes" (via share sheet later) |
| Travel / watch / eat out / buy / gifts / big goals | **Wish** (template chooses extra fields) | list, title | where, link, budget, platform (watch), things to try, for whom (gift), target date | "add Dune 2 to watch list" |
| Shops / areas list | **Place** | name, kind, area | what to buy there, map link | — |
| Weekly goals | **Goal** | title, kind, who | progress, by when | — |
| Todo: who, due, who gave it, reminder | **Task** | title, who, due | priority, repeat, reminder, note (assigned-by is automatic) | "pay rent tomorrow" |
| Birthdays, important events, renewals | **Event** | title, kind, date, for whom | repeat (yearly), reminder, note | "Riya birthday 12 Dec" |
| Bills, warranty, prescriptions → Drive | **Document**: needs Google Drive upload (week 4); shown as "soon" | — | — | — |
| Monthly / yearly overview | Money tab (week 2) | — | — | — |

## Home: priority instead of a long scroll

1. **Hero**: greeting and a one-line summary.
2. **Needs attention**: one ranked list built from every module:
   * 🔴 **Urgent**: overdue tasks, expired groceries or medicines.
   * 🟠 **Today**: tasks due today, items expiring within 2 days, payments due within 2 days.
   * 🔵 **Soon**: birthdays and events within 7 days, items expiring within 7 days, renewals within 7 days.

   It shows the top 4, with "Show all".
3. **Filter chips**: All · Money · Kitchen · Health · Plans. These narrow the attention list and sections.
4. **Sections** (Up next, Shopping list, Coming up, Recent) that each person can **reorder or hide** with **Customise Home**. The choice is saved on the device.
5. Members only see what their role allows. Kids never see money or medicine items.

## How everything connects

```
 Budget (₹1,00,000 · salary-day month · ₹20,000 to save · split by category)
   ▲ every expense            ▲ planned spends ("Goa trip")      ▲ subscriptions due this cycle
   │                          │  reserve money before they happen │  count as "Due"
 Expense ◄── grocery / medicine with a cost (added automatically, Undo removes both)

 Shopping list ──tick──► Pantry ──"made paneer sabzi"──► quantities go down
      ▲                    │                                   │
      └──── runs low / out ┴───────────────────────────────────┘   (added with a reason)

 Recipes know quantities (200 g paneer, 100 g peas, serves 2). Cooking scales by servings,
 converts units (g↔kg, ml↔L, tbsp→ml) and uses whatever expires first.

 Home stats strip: budget left · tasks left · next birthday/anniversary · expiring soon (eye button hides amounts)
 Needs attention: over-budget categories, spending pace, expired food, low stock, due tasks, birthdays.
```

### Budget rules
* **Left this month** = budget − spent − planned − subscriptions still due this cycle.
* **Safe to spend** keeps the savings goal aside; the hero shows a per-day figure for the days left.
* **Pace** compares day-to-day spending against a straight line through the month. **Rent and Bills are fixed costs** and count in full from day one, so paying rent on the 1st never reads as "spending fast". A category is only "fast" once it's at least a quarter used.
* **Over with plans** (amber) is a heads-up that planned spends will exceed a category; **over** (red) means real spending already has.

### Kitchen rules
* **Ticking something off the shopping list** tops up the existing pantry item to its usual pack size (50 g → 300 g), or adds a new one.
* **Cooking** lowers each ingredient. Anything used up, at 25% or less, or missing from the pantry goes on the shopping list unless it's already there.
* **"What can I cook?"** puts recipes you can make now first, then ones that use food about to expire. Mood chips filter it (spicy, tangy…).

## Rollover, savings, dreams and investments

```
 Each budget month ends ─► unused category money stays in its envelope (this year)
                        └► unassigned money flows into the Savings pot (earlier years too)

 Savings pot / envelopes ──move──► Dreams (Bali trip, car…)      "save 5000 for Bali trip"
                         ──park──► Investments (FD, mutual fund…) "park 20000 in FD"
 Investments ──withdraw──► pot / envelopes / dreams
 Growth (interest, gains, value edits) ──► investments            "add interest 1200 to HDFC FD"
                                                                   "HDFC FD is now worth 52000"
 Gift cards, points, coupons ──earmarked for──► a category or a dream ("got Amazon gift card 2000 for gifting")
```

* Everything is a **ledger of transfers**, so every number can be traced. Undo deletes a single entry.
* An investment's value is the money put in, minus what's taken out, plus growth. **Edit value** records the gain or loss as a growth entry.
* A dream's progress is the money moved to it, plus earmarked credits, plus the current value of investments marked "Saving for" it. It's **affordable now** when the savings pot covers the rest. With a date set, the dream also shows the monthly amount needed.
* Ask "can we afford Bali trip?", "how much is left for dining?" or "how much is HDFC FD worth?".

## Kitchen extras
* **Cook before it expires:** dishes from the family's recipes and a built-in starter library that use up food expiring within 3 days. Shown on Home, in Stock, and as a hint on the expiring item ("Expires tomorrow · try Paneer sabzi").
* **Supplies** (batteries, bulbs, toiletries…) live in Stock with quantity, expiry, where they're kept and a "Used 1" button. They never mix with cooking.

## Places and where things are kept
* **Plans → Places:** restaurants, cafés, grocers, pharmacies, doctors and more, with rating, things to try and a **Map** button.
* **Vault → Where things are kept** (adults only): "gold necklace is in the bank locker"; later, "where is the gold necklace?" Search by any word.

## Location: "going to Indiranagar"
* Places get coordinates from a pasted Google Maps link (…/@12.97,77.64) or, failing that, the phone's built-in geocoder (name + area). They're saved once and reused.
* **"Going to X" / "heading to X" / "what's near me"** lists saved places within 2 km that you love or want to try, nearest first. It leaves out **the destination itself, places of the same kind** (heading to a grocer: no other grocers; heading to a restaurant: no other restaurants or cafés), and anything rated "Not great". If a grocer is involved, it adds "While you're there, buy: …" from the shopping list.
* **Plans → Places → 📍 Near me** uses the phone's location, with 1 / 2 / 5 km.
* Location is only read while the app is open, and only after the user allows it. Nothing is tracked in the background.

## Reminders: the daily digest
* **One notification a day** at the chosen time (7 am to 8 pm). Settings has a switch for each area and **Send a test now**.
* **It includes** overdue and due tasks; events, birthdays and payments on their "Remind" day (default: the day before) and on the day; food expiring today or tomorrow; expired food; medicines 7 days before expiry and on the day; investments 7 days before maturity; gift cards about to expire; over-budget alerts; and a long shopping list.
* **It sends nothing** when nothing needs you. Each person's digest only covers what their role can see. Scheduled with WorkManager, so it survives restarts.
* **Real-time pushes** to another member (e.g. "Asha assigned you a task") need a small server to send them. That's planned for later, and the digest covers it for now.

## Cooking for the people eating
* Recipe quantities are written for a number of people (default 1). Cooking always asks **who's eating** (family members and guests) and takes that many portions from the pantry. "Made dal for 4" counts 4 people. The cooking log records who ate, ready for per-person nutrition later.

## Trips and outings
* Start one ("start trip Lonavala this sunday", or Money → Trips & outings → New) with dates and an optional budget.
* While it's happening, **every new expense is tagged with it automatically**. One tap on "None" untags it.
* Expenses have **sub-categories** (Dining → Breakfast/Lunch/Dinner/Snacks…, Transport → Cab/Fuel…, Shopping → Clothes/Shoes…), picked from words like "lunch" or "uber". The trip card adds them up by category and sub-category, against the trip's budget.

## "I'm feeling…"
* "I'm feeling feverish", "Riya has loose motions", "burnt my hand": lists medicines at home tagged for it (the person's own first, expired ones left out, with the family's dosage note), general home care, and when to see a doctor.
* No dosages come from the app. Kids get the home-care tips and "tell an adult".

## Home
* **For right now**: breakfast, lunch or dinner ideas from the pantry by time of day; in the evening, something from the watch list; on weekend days, restaurants to try and, only if the Shopping envelope has money, things on the buy list.
* **Up next · Coming up · Recent** in one card with tabs and calendar tiles.
* **To watch together**: posters, ratings and where to watch in India. TVMaze is used for shows (no key needed). Add a free TMDB key (`tmdb.apiKey=…` in local.properties) for movies, ratings and Indian streaming services.

## Taps and editing
* Every row opens what it shows. Tasks, events, expenses, subscriptions, pantry and supply items, medicines, recipes, places, kept items, credits, trips, list items and dreams open their **edit form**, filled in. Save changes; Undo puts it back.
* Stats, at-a-glance cards and "More/All" links open the right tab **and sub-tab** (e.g. "tasks left" → Plans → Tasks, "To buy" → Stock → Shopping).
* Shopping chips: tap = bought (into the pantry), hold = edit. Shopping rows in Stock: circle = bought, row = edit.
* Plans → **Lists**: travel, watch, eat-out, buy and gifts. "Watched / Been there / Bought" removes an item from every list and suggestion.

## Variety
* The cooking log feeds every recommendation. Dishes made in the last 3 days move to the back; ones not made for 2+ weeks are nudged forward and labelled "Not made in N weeks".

## Weekly review: the live-well wheel
* Your avatar on Home has a **story ring** that lights up each week when a new review is ready. Tap it to open **Your week** (settings live behind its gear icon).
* Four pillars of long, healthy lives, three habits each, scored from what the family already logs:
  * **Move naturally**: walking (150 min/week), garden & chores (60 min), workout (90 min). Log with "walked 30 min", "45 minutes yoga".
  * **Eat wisely**: home-cooked meals (7), plant-based share, moderation (dining within the week's share of the Dining budget).
  * **Connect**: family meals (cooked for 2+), together time (shared activities, outings, events), and the check-in's "time with friends and family".
  * **Outlook**: unwind and gratitude (from a private 3-tap weekly check-in), and purpose (tasks done, money saved toward a dream).
* Shows the overall score, one focus tip for the weakest habit, and the week in numbers: spend, meals, active minutes, tasks done, saved, waste.
