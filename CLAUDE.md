# Habit Quest — working rules for Claude Code

The full spec is `Habit Quest v1 Specification (ASD-STE100).md` (the "spec"). Read it at the start of each step. This file summarises the rules that must never be broken, plus decisions made after the spec was written.

## The developer

Strong in Python, new to Kotlin and Android. Explain Android/Kotlin concepts simply when they matter (compare to Python where it helps). Do not assume Android Studio knowledge.

## Core principle: a flaw must not cost points (spec 2.1, 10, 12.1)

- Automatic detection only **suggests** a session. The user confirms, edits, or adds it.
- The app must **never** set a session to `confirmed` without a user action.
- The app must never remove points because of a detection fault.
- Every habit always has a manual entry path.
- When in doubt, choose the behaviour that is generous to the user.

## Build plan (spec 13) — one step at a time

1. **Base app** — project, Room database, Today screen, manual habits, scoring rules (spec 7), day-close task.
2. **Popups** — overlay permission, popup types, popup rules, 11 PM checklist.
3. **App habits** — tile launch, foreground service, audio signal, usage events for Medito, confirmation popup.
4. **Zone habits** — Zones screen, Geofencing API, dwell trigger, zone popups.
5. **Rewards and creatures** — XP, coins, Rewards screen, Collection screen.
6. **Reliability** — catch-up task, debug screen, onboarding, battery steps, tests in spec 12.3.

**Gate rule:** do not start a step until the previous step has been installed and works on the developer's real phone. Test on a real phone, not the emulator. Build only the step that was asked for.

## Decisions made after the spec (Step 1)

| Topic | Decision |
| --- | --- |
| Habit schedule | Each habit has days of the week (default: every day). Unscheduled days are not misses and do not count toward the daily bonus. |
| Streak for the multiplier | Counts today's session. Stored streak = state after the last closed day; today's effective streak = stored + 1 once done. |
| Daily bonus | Given as soon as the last scheduled habit of the day is confirmed. Recomputed if today's sessions change. Day close re-checks it. |
| Several sessions in one day | Only the first confirmed session of a habit per day scores. Later ones are recorded with 0 points. |
| Comeback bonus | +5, added after rounding, to the first scored session after any scheduled miss (frozen misses included). |
| Freeze week | Monday–Sunday, local time. A miss while the streak is 0 does not use the freeze. |
| Missed day (v0.4) | The first miss of a week uses the freeze (streak kept, tile shows ❄️ freeze used). Any other miss resets the streak to 0. (Replaced the spec's halving.) Stored streaks were re-walked once (`streak_rules_version`). |
| Creation day | A habit that is not done on the day it was created is not a miss. |
| Windows | Must not cross midnight (start < end). Boundaries are inclusive. |
| Rounding | Half up, done with BigDecimal so 13.5 always becomes 14. |
| Delete habit | Soft delete (`active = false`) so history and points stay. |
| Today's points | XP and coins are added in Step 5. Until then, the Today screen shows "points today". |
| Tasks (added after Step 1) | One-off item with a due date, own `task` table (DB v2). Chosen with a Habit/Task switch when creating. Stays on Today until done; marked overdue, no penalty for being open. Points = base × 1.2 if done by the due date; late: 1+ days ×0.8, 3+ ×0.6, 7+ ×0.4, 14+ ×0.2 (never 0). No streak, no window, not part of the daily bonus. Values in `ScoringConfig`. |
| Fixed points (v0.4) | Base points are 10 for every habit and task (`ScoringConfig.basePoints`); not user-adjustable, to keep the score fair. The `base_points` column is ignored. |

## Decisions made after the spec (Step 2)

| Topic | Decision |
| --- | --- |
| Reminders | Window start, plus a "last chance" reminder 15 min before the window ends. Snooze 15 min. All count toward the per-habit daily limit (default 3, Settings 1–5); one slot is kept for the last chance, so snoozes cannot remove it. The receiver always sets the next alarm (finally), and falls back to notifications if the popup service cannot start. If several are due together (phone was off), only the most urgent shows; expired ones are suppressed. Timings in `reminders/ReminderConfig.kt`. |
| "Can't today" / promise (v0.4) | On every reminder. Opens `PopupActivity` (promise mode): pick tomorrow or another day (≤ 14 days) and a time. No earlier misses: a tap (after 5 s). Missed before today too: the oath ("I promise I will X tomorrow at 07:00") must be typed word for word or spoken (contains "promise" + the habit name). A "Not possible tomorrow either" button runs away when touched. `promise` table (DB v4): OPEN → KEPT when done on the due day (+5 `promiseKeptBonus`, also via History) → BROKEN at day close. A PROMISE reminder comes at the promised time, outside the daily limit, also on unplanned days. Today shows the promise. The day of the promise is still a miss for streaks. |
| Tasks on popups | The first habit reminder popup of the day lists the top 3 open tasks (soonest due). The 11 PM checklist lists open habits and open tasks due today or earlier. |
| Checklist tick | Creates a CHECKLIST session timed at the window start (generous: full timing points). No checklist popup if nothing is open. |
| Quiet hours | Default 23:30–06:00, can cross midnight. Popups due in quiet hours are suppressed, not delayed. |
| Popup rules | Calls: AudioManager mode; navigation: an ongoing CATEGORY_NAVIGATION notification (needs Notification access). Both retry every 60 s. |
| History | Edit time, mark not done, or add a missed session on any day since the habit was created. A closed-day edit replays that habit's streak from its creation; only days from the edited day on are rescored (earlier points stay). Daily bonuses from that day on are rechecked. |
| Summary | Day/Week/Month. Habit consistency = done scheduled habit-days ÷ scheduled habit-days (today counts only once done; creation day not a miss). Unscheduled done days shown as "extra". Tasks: on-time share of tasks due in the period, completed, average days late, open/overdue now. |
| Popup host | Overlay `ComposeView` in a `specialUse` foreground service (`PopupService`), started from an exact-alarm receiver. Without the overlay permission: a notification that opens `PopupActivity`. The overlay has a grab bar: drag up/down, the position is kept (`popup_y`). |
| Summary page (v0.8) | One page, three sections in this order (developer's choice): Mind · Body · Soul (a ring each + balance tip); Highlights (swipeable row: growth lines only when ≥ last period, streak, perfect days, best day, wins, tasks, comebacks, promises, badges card → badge sheet); Overview (stacked rings Habits / Tasks on time / Perfect days with points + praise, then the calendar with rings per day and an Overview/Habit rings switch). Per-habit progress only via the calendar's Habit rings (developer's choice). |
| Sharing | Release APKs are signed with a private key: `~/.habitquest/habitquest-release.jks`, passwords in `~/.gradle/gradle.properties` (`HABITQUEST_*`), never in the repo. The developer's own phone keeps the debug build (`installDebug`); a release APK cannot install over it without uninstalling (data loss). Shared APKs go to `dist/` (ignored). |

## Decisions made after the spec (v0.4)

| Topic | Decision |
| --- | --- |
| Mind / Body / Soul | Every habit and task has dimensions (`mbs_mask`, DB v4: Mind 1, Body 2, Soul 4). Suggested from the name (`balance/Balance.kt` keyword stems: meditate → Mind+Soul, gym/run → Body+Mind, read → Mind, journal → Mind+Soul…), changeable with chips. The creature line follows the main dimension. Meters: each item counts 1, shared equally between its dimensions; % = done ÷ planned; points shared the same way. Shown on Today (today) and Summary (period). |
| Motivation | Celebration (confetti, vibration, points) when something is finished; big for a new level, a streak milestone, a perfect day or a kept promise. Daily nudge + quote of the day on Today. Levels from lifetime points (level n starts at 25·n·(n−1); titles in `ScoringConfig`). Reminders show the current streak. Badge "True to my word" (3 kept promises). |
| Zone habits (Step 4, v0.9) | Habit type "At a place" + a `zone` (DB v5, radius ≥ 150 m, `minZoneRadiusMeters`). Zones are added in the Zones screen (Settings → Track a habit at a place → Places, or + New zone in the editor) with the current location (fused location) or an address (Geocoder); no map (needs an API key), "Check on map" opens the maps app. `detect/Geofences` registers DWELL (5 min loitering) + EXIT with Play Services; `sync` runs on app start, every change, boot and permission changes. Dwell → "At Gym. Are you doing Gym now?" (Yes starts a SUGGESTED session at arrival = dwell − 5 min; skipped in quiet hours/calls/navigation). Exit → session ends, confirmation popup; ≥ 30 min = evidence. Needs location "Allow all the time". |
| UI/UX (v0.10) | Own brand theme (`ui/Theme.kt`, teal primary, white cards, light + dark) instead of wallpaper colours. Today: greeting + level pill, one card (day ring, points, level bar, meters), Habits/Tasks switch, rows with one big round done button + chips + one status line; tapping a row opens an action sheet. Editor: What / When (presets) / How / Good for cards, quick-pick habits, sticky Add/Save. First run: 5-page welcome tour (`ui/WelcomeScreen.kt`, seen-flag in SharedPreferences), replay from Settings. Plain, short wording for non-technical users. |
| Reminder check | Settings shows the next reminder time and the last 20 reminder_log rows, to diagnose missing reminders. |
| App habits (Step 3, v0.5) | Habit type "With an app" + an app picked from the launcher list (Medito first). The tile opens the app (LAUNCH signal). `HabitListenerService` (Notification access) follows the linked apps' media sessions; `detect/AudioTracker` ends a session after 60 s without audio, drops < 2 min, evidence at ≥ 5 min (values in `ScoringConfig`). The result is a SUGGESTED session (start/end set) shown on the tile and as a confirmation popup (Confirm / Not done → REJECTED). Never auto-confirmed. No foreground service: the listener stays bound by the system. Catch-up (v0.6, spec 5.2, brought forward from Step 6): with Usage access, `detect/CatchUp` reads `UsageStatsManager` events every hour (`CatchUpWorker`) and on app resume; `usageSessions` joins gaps < 2 min, keeps ≥ 3 min on screen; results are SUGGESTED with source CATCH_UP, no evidence. Not re-asked if overlapping a SUGGESTED or REJECTED session, or the day is done. Popups wait (stay on the tile) in quiet hours, calls and navigation. With Notification access, popups also wait while a navigation app is active. |

## Decisions made after the spec (Step 5)

The full plan is `Habit Quest Step 5 Plan (ASD-STE100).md`; the build prompt's rules R1–R14 apply (no reward without a user action, coins only go down when spent, no shaming, original creatures only, no accounts).

| Topic | Decision |
| --- | --- |
| Coins (5a, v0.12) | 1 coin per point, paid when points are settled (`settleCoins`, after every change and at day close). `coin_ledger` holds every movement; `coin_credit` holds the most points each source (habit-day, day bonus, task) was ever paid, so points that fall after a History edit take no coins back and the same points are never paid twice. Points from before coins existed (`coins_started` in app_state) give no coins. DB v6 also adds `habit.cue_line` for 5f. |
| Egg and creatures (5b, DB v7) | Pure rules in `rewards/EggRules.kt`, theme data in `rewards/Theme.kt` (rules never read a theme). Tables `egg`, `creature_owned`, `bond_event`, all keyed by `theme_id`. 1 coin earned = 1 growth, split by the source's dimensions (day bonus: all three). First egg 100 (starts 20), later 300 (starts 30); growth above the target carries into the next egg's head start (developer: keep, generous). Full-collection coins (100) are not growth. Resting and the welcome back use the same gap (≥ 2 days). |
| Undo takes coins back (Nov 10, 2026) | Replaces R6 for one case only: when the user reopens a task or removes a session, `takeBack` removes the coins that source paid (and the day bonus the removal ended), its growth in the open egg, and the session's bond. Never more than the source really paid, never below a 0 balance; `coin_credit` drops by what was taken, so ticking again pays it back. Time edits, streak replays and detection still never take coins. Each egg's start is marked in app_state (`egg_mark:<id>` = last ledger id) so growth that went into a hatched egg stays, and so does the creature. |
| Due times and early reminders (v0.13, DB v8) | Developer request after 5b. Tasks: optional `due_time` → a TASK_DUE popup then (Done ✓ / OK, later); optional `remind_before` (15 min, 1 h, 3 h, 1 day) → TASK_EARLY, planned on the day it falls on, expiring at the due time. Habits: optional `remind_before` (15 min–3 h) → EARLY before the window, expiring at the window start; none if it would fall before midnight. Both are the user's choice, so outside the per-habit daily limit (like PROMISE); quiet hours still suppress them. `reminder_log.task_id` links task popups. Points still count by the due date only (section 5 rules unchanged). Choices in `ReminderConfig`. |
| Personal rewards (5c, v0.14, DB v9) | Rewards screen (tap the 🪙 pill on Today). `personal_reward` (name, cost 10–5000 in `ScoringConfig`, soft delete) and `reward_redemption` (name and cost copied at redeem time, so edits and deletes never change history). Redeem asks first, then `redeemReward` writes a REWARD ledger row and the redemption in one transaction; refused with no change when the balance is too low (the button is off and shows "N more needed"). Growth never changes. Three examples (`rewards/RewardRules.kt`) show while the list is empty and fill in a new reward when tapped. |
| Sparring, piece 1 (5h, v0.15, DB v10) | Built before 5d–5g at the developer's request (Nov 10, 2026). Pure rules in `game/SparRules.kt` (`SparConfig`): energy not health (100), 3 move slots per creature (push 15; big 24, 75 % hit; rest +18, max 2 a match), move effect = power × creature power ÷ 12 × 1.5 with the advantage / 0.67 against it, ±10 % variance; Body > Mind > Soul > Body; the stronger creature moves first (user on a tie); draw after 30 turns. Creature power = 10 + 3 per stage above 1 + bond/15 (bond capped at 150): stage and bond only. Opponent: any theme creature, power from 2 below to 1 above yours (`boost` for the later hatch battle); rests when ≤ 30 % energy, big move 30 % of turns. Tickets: confirmed sessions today − spars started today; the `spar` row is written at start (leaving keeps the ticket used). `finishSpar` sets the result once; a win pays `sparWinCoins` 5 (SPAR ledger row, grows the egg, no dimension) and `sparWinBond` 1 (bond_event with a negative id, as bond rows are keyed by session id); a loss or draw changes nothing. Words (move names per dimension, intro, win, loss, draw) are theme data. Entry: Collection → Spar. Pieces 2 (map, regions by level) and 3 (hatch battle, a little stronger opponent) to come. |
| Creature names (theme 1) | Exception to R14 and spec 8.3, decided by the developer on Nov 10, 2026, who reports written approval from The Pokémon Company for these names (keep that approval with the project). 18 creatures, 6 per dimension; each evolution line stays in one dimension (Mind: water + star-bug lines; Body: fire line, spark pair, stone; Soul: leaf line, spirit, sleeper, rainbow). Art stays our own simple vector shapes (`ui/CreatureArt.kt`), no official artwork; blurbs stay calm and kind (R4, R5). The Collection shows rows of 3. First-draft ids (lumi, pebbit…) are renamed once on the phone (`Themes.RENAMED_IDS`, `renameOldCreatures`), so a hatched creature is never lost. R14 still applies to theme 2 and anything else. |

## Code rules

- Kotlin + Jetpack Compose, minSdk 29 (Android 10). Package `com.ayushpant.habitquest`.
- **All scoring values live in one file:** `app/src/main/java/com/ayushpant/habitquest/scoring/ScoringConfig.kt`. Never hard-code a scoring number anywhere else.
- Scoring and streak logic are pure Kotlin functions (no Android imports), so they can be unit tested.
- Room schema is exported to `app/schemas/`. Never use destructive migration: the developer's real data lives on the phone. Add tables with `@AutoMigration` or write a `Migration`.
- Write each signal to `signal_log` before you use it (spec 12.1). Delete rows older than 14 days.
- The day-close task must catch up on all unclosed days whenever it runs (worker, app start, before scoring).
- Commit after each working piece.

## Toolchain

- Android Studio 2026.2. Its bundled JDK is **Java 25**, so tools must support it: Gradle 9.8, AGP 9.4 (built-in Kotlin: do not apply `org.jetbrains.kotlin.android`), Kotlin 2.4, KSP 2.3.
- Versions live in `gradle/libs.versions.toml`. Prefer releases at least two weeks old.
- compileSdk 37 (installed), targetSdk 36.
- Tests: `app/src/test` — `ScoringTest`, `BalanceTest`, `PromiseRulesTest`, `LevelsTest`, `StatsTest`, `MotivationTest`, `ReminderPlannerTest`, `EggRulesTest` (pure rules), `HabitRepositoryTest` (Robolectric + in-memory Room, with a fake clock that moves through days), `MigrationTest` (builds an old-version database from `app/schemas` and opens it with current code). Add a migration test case for every schema version.
- Robolectric runs on Android 15 (`app/src/test/resources/robolectric.properties`, `sdk=35`) with a plain `Application`: the Android 16 runtime fails on Java 25, and `HabitQuestApp` schedules WorkManager on start.
- `SummaryRenderTest` renders a screen to PNG in `app/build/render/` (Robolectric native graphics). Use it to check UI changes without a phone.
- Do not chain `git commit` after a build in one shell line unless the commit is guarded by the build's exit status (`if ./gradlew …; then git commit …; fi`).

## Commands

The shell needs `JAVA_HOME` and the SDK tools on `PATH` (set in `~/.zshrc`):

```bash
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
export ANDROID_HOME="$HOME/Library/Android/sdk"
export PATH="$JAVA_HOME/bin:$ANDROID_HOME/platform-tools:$PATH"
```

Give the developer commands with no `#` comments: interactive zsh does not treat `#` as a comment and passes it as an argument.

| Command | What it does |
| --- | --- |
| `./gradlew testDebugUnitTest` | Run unit tests (like pytest) |
| `./gradlew installDebug` | Build and install on the connected phone (keeps app data) |
| `adb devices` | Check the phone is connected |
| `adb logcat -s HabitQuest` | App logs |
| `./gradlew assembleRelease` | Signed APK for friends: `app/build/outputs/apk/release/app-release.apk` (copy to `dist/`) |

If `adb devices` lists the phone more than once (USB + wireless), `installDebug` can fail. Install the built APK on one connection: `adb -s <serial> install -r app/build/outputs/apk/debug/app-debug.apk`.
