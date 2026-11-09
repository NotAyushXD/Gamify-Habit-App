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
| Popup rules not yet done | Navigation detection needs the notification listener (Step 3). Calls: AudioManager mode, retry every 60 s. |
| History | Edit time, mark not done, or add a missed session on any day since the habit was created. A closed-day edit replays that habit's streak from its creation; only days from the edited day on are rescored (earlier points stay). Daily bonuses from that day on are rechecked. |
| Summary | Day/Week/Month. Habit consistency = done scheduled habit-days ÷ scheduled habit-days (today counts only once done; creation day not a miss). Unscheduled done days shown as "extra". Tasks: on-time share of tasks due in the period, completed, average days late, open/overdue now. |
| Popup host | Overlay `ComposeView` in a `specialUse` foreground service (`PopupService`), started from an exact-alarm receiver. Without the overlay permission: a notification that opens `PopupActivity`. The overlay has a grab bar: drag up/down, the position is kept (`popup_y`). |
| Summary page (v0.3) | Two views. Overview: praise headline (never negative), stacked rings Habits (green) / Tasks on time (blue) / Perfect days (orange) with points in the centre, "You're growing" card only when this period ≥ the last, highlights, badges with "next up" progress. Habits: one ring per habit (max 6) + week bars / month calendar. Logic in `stats/Motivation.kt`. |
| Sharing | Release APKs are signed with a private key: `~/.habitquest/habitquest-release.jks`, passwords in `~/.gradle/gradle.properties` (`HABITQUEST_*`), never in the repo. The developer's own phone keeps the debug build (`installDebug`); a release APK cannot install over it without uninstalling (data loss). Shared APKs go to `dist/` (ignored). |

## Decisions made after the spec (v0.4)

| Topic | Decision |
| --- | --- |
| Mind / Body / Soul | Every habit and task has dimensions (`mbs_mask`, DB v4: Mind 1, Body 2, Soul 4). Suggested from the name (`balance/Balance.kt` keyword stems: meditate → Mind+Soul, gym/run → Body+Mind, read → Mind, journal → Mind+Soul…), changeable with chips. The creature line follows the main dimension. Meters: each item counts 1, shared equally between its dimensions; % = done ÷ planned; points shared the same way. Shown on Today (today) and Summary (period). |
| Motivation | Celebration (confetti, vibration, points) when something is finished; big for a new level, a streak milestone, a perfect day or a kept promise. Daily nudge + quote of the day on Today. Levels from lifetime points (level n starts at 25·n·(n−1); titles in `ScoringConfig`). Reminders show the current streak. Badge "True to my word" (3 kept promises). |
| Reminder check | Settings shows the next reminder time and the last 20 reminder_log rows, to diagnose missing reminders. |
| Medito detection | Step 3, next. Detection only suggests; one tap confirms (developer chose this). |

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
- Tests: `app/src/test` — `ScoringTest`, `BalanceTest`, `PromiseRulesTest`, `LevelsTest`, `StatsTest`, `MotivationTest`, `ReminderPlannerTest` (pure rules), `HabitRepositoryTest` (Robolectric + in-memory Room, with a fake clock that moves through days), `MigrationTest` (builds an old-version database from `app/schemas` and opens it with current code). Add a migration test case for every schema version.
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
