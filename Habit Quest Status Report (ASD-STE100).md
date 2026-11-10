# Habit Quest Status Report (ASD-STE100)

Nov 10, 2026 · @Ayush Pant · App version 0.11.2

## 1. About this document

This document describes two things: the original plan for Habit Quest, and the app as it is now built. The original plan is in "Habit Quest v1 Specification (ASD-STE100).md". This document shows what is done, what is changed from the plan, and what is not yet built.

This document uses the rules of ASD-STE100 Simplified Technical English:

- A procedural sentence has a maximum of 20 words.
- A descriptive sentence has a maximum of 25 words.
- A procedural sentence gives one instruction.
- Sentences use the active voice.
- "Must" shows a mandatory requirement. "Can" shows an optional function.

### 1.1 Terms

| Term | Meaning |
| --- | --- |
| Habit | An activity that the user wants to do regularly. |
| Task | A one-off item with a due date. A task is not a habit. |
| Source app | An app on the phone that the user links to a habit, for example Medito. |
| Zone | A location with a radius of 150 m or more, for example the gym. |
| Session | One time that the user does a habit. A session has a start time and an end time. |
| Window | The time period in which the user plans to do a habit. |
| Detection | The app finds signals that show a possible session. |
| Confirmation | The user tells the app that a session is correct. |
| Points | The score that the user gets for a confirmed session or a completed task. |
| Streak | The number of days in a row that the user does a scheduled habit. |
| Freeze | A free miss each week. A freeze keeps the streak. |
| Promise | A pledge to do a missed habit on a later day, made from a reminder. |
| Reminder | A popup or notification that asks the user to do a habit. |
| Quiet hours | A time period in which the app shows no reminders. |
| Dimension | One of Mind, Body, or Soul. Each habit feeds one or more dimensions. |

## 2. Product summary: the plan versus the build

The original plan set three functions for Habit Quest: link habits to source apps, zones, or manual check-ins; find possible sessions with automatic detection; and score the user higher for habits done inside the planned window. The app now has all three functions, plus reminders, tasks, promises, a reward and motivation layer, and a user guide. The plan's core principle still holds in every part of the app.

### 2.1 Principle: a flaw must not cost points

This principle is unchanged from the plan. Automatic detection only suggests a session. The user always confirms, edits, or adds the session. The app never sets a session to "confirmed" without a user action, and it never removes points because of a detection fault.

## 3. Build plan: steps done and steps open

The developer and Claude Code agreed a 6-step build plan. Each step must work on the developer's phone before the next step starts.

| Step | Content | Status |
| --- | --- | --- |
| 1. Base app | Project, database, Today screen, manual habits, scoring, day-close task | Done |
| 2. Popups | Overlay permission, popup types, popup rules, 11 PM checklist | Done |
| 3. App habits | Tile launch, audio signal, usage events, confirmation popup | Done |
| 4. Zone habits | Zones screen, geofencing, dwell trigger, zone popups | Done |
| 5. Rewards and creatures | XP, coins, Rewards screen, Collection screen | Not started |
| 6. Reliability | Debug screen, onboarding, battery steps, spec 12.3 tests | Partly done |

Step 6 items already done: a reminder check in Settings, a 5-page welcome tour, a 13-topic in-app guide, and most of the test types in spec 12.3. Open items: a dedicated debug screen, and formal battery-optimization steps for the user.

## 4. Changes from the v1 plan

The plan named v1 as single-user only, with v2 adding accounts and a friend leaderboard. The build took a different, smaller path to sharing: the developer signs release files with a private key and sends the file directly to a friend. No account system, cloud sync, or leaderboard is built. These remain parked ideas.

Several rules in the plan were replaced or extended after testing on the phone. The table in section 5 lists each one.

## 5. Scoring and streak rules, as built

| Topic | Rule as built |
| --- | --- |
| Base points | Fixed at 10 points for every habit and every task. Not adjustable by the user. |
| Habit schedule | Each habit has its own days of the week. A day with no schedule is not a miss. |
| Streak | Counts today once the habit is confirmed. The streak keeps the state after the last closed day. |
| Daily bonus | 10 points, given once all of today's scheduled habits are confirmed. |
| Freeze | One free miss each week (Monday to Sunday). A later miss in the same week resets the streak to 0. |
| Comeback bonus | 5 points, added after the first scored session that follows any missed day. |
| Several sessions in one day | Only the first confirmed session of a habit scores. Later ones score 0. |
| Rounding | Half up. 13.5 points round to 14. |
| Creation day | A habit not done on the day it is created is not counted as a miss. |
| Windows | A window must not cross midnight. The start time and the end time are both inside the window. |
| Delete | A soft delete. The habit stops showing, but its history and points stay. |
| Task points | Base points × 1.2 if done by the due date. Late completion scores less, down to a minimum of × 0.2. |
| Evidence bonus | × 1.2 when detection finds strong evidence, for example 5 minutes of audio or 30 minutes in a zone. |

## 6. Detection, as built

### 6.1 App habits

The user links a habit to an app on the phone, for example a meditation app. The habit tile opens that app. A background listener follows the app's audio. The app ends the session after 60 seconds without audio. A session under 2 minutes is dropped. A session of 5 minutes or more counts as evidence.

The app also finds sessions from app use outside Habit Quest. Each hour, and when the user opens Habit Quest, the app reads usage data from the phone. It builds sessions from app-open and app-close events, 3 minutes or more on screen. This function is named catch-up.

### 6.2 Zone habits

The user links a habit to a place, for example the gym. The user adds the place with the current location or a typed address. The phone's location service watches the place. After 5 minutes in the place, the app asks "Are you doing [habit] now?". When the user leaves the place, the app asks for confirmation. 30 minutes or more in the place counts as evidence.

### 6.3 Manual habits

The user starts and ends the session by hand, with no automatic signal. This type always exists for every habit, as a fallback.

### 6.4 Shared rule for all types

Detection never confirms a session on its own. It only suggests one. The user must tap Confirm, or the app must not add points for it.

## 7. Reminders, as built

The app sends reminders at a habit's window start, and again 15 minutes before the window ends ("last chance"). The user can snooze a reminder for 15 minutes. Each habit gets a maximum number of reminders per day, set in Settings (1 to 5, default 3).

During a phone call, or while a navigation app is on screen, a reminder does not interrupt. Instead the app sends a quiet notification with a short vibration, with no sound. The full popup shows again after the call ends.

In quiet hours (default 23:30 to 06:00), the app shows no reminders at all. It does not delay them to a later time.

A missed habit can be forgiven with a promise: the user picks a day and a time to do it instead. After more than one earlier miss, the user must type or speak an oath to make the promise. The app never shames the user for a missed habit.

At 11 PM, a checklist popup lists open habits and open tasks. The user can tick items directly from this popup.

## 8. Tasks, as built

A task is a one-off item with a due date. It is not part of a habit's streak or window. A task stays on the Today screen, marked overdue if needed, with no point penalty for staying open. It scores full points with a 20% bonus if finished on time, and a reduced score if finished late.

## 9. Mind, Body, and Soul, as built

Every habit and task has one or more dimensions: Mind, Body, or Soul. The app suggests dimensions from the habit's name, for example "meditate" suggests Mind and Soul. The user can change the suggestion with on-screen chips. The Today screen and the Summary screen each show a meter for every dimension.

## 10. Motivation and rewards, as built

The app shows a short celebration (confetti, vibration, and the points gained) when the user finishes a habit or a task. A bigger celebration shows for a new level, a streak milestone, a perfect day, or a kept promise. Levels grow from lifetime points. The Today screen shows a daily nudge and a quote of the day.

The plan's reward step (XP, coins, a Rewards screen, and a Collection screen with creatures) is not yet built. Today's celebrations and levels give some of this function in a simpler form.

## 11. Summary screen, as built

The Summary screen has three sections, in this order: a Mind/Body/Soul section with a ring for each dimension; a Highlights row with streaks, perfect days, best days, and badges; and an Overview section with a calendar. The user can switch the Summary screen between Day, Week, and Month views.

## 12. History screen, as built

The History screen lets the user edit a session's time, mark a session as not done, or add a missed session, for any day since the habit was created. An edit on a closed day replays that habit's streak and points from the edited day onward. Earlier points do not change.

## 13. Settings and the user guide, as built

The Settings screen lists each permission the app needs, with a button that opens the matching phone setting directly. It shows quiet hours, the reminder limit, and a reminder check: the next planned reminder time, and the last 20 reminder events.

A "How to use Habit Quest" guide is the first item in Settings. It has 13 topics, for example points, streaks, tasks, and places. Each topic has numbered steps and a "try it" button that opens the matching screen. A search box finds topics by keyword.

A 5-page welcome tour shows the first time the app opens. The user can replay it from Settings at any time.

## 14. Technology, as built

The technology matches the original plan, with newer tool versions.

| Item | As built |
| --- | --- |
| Language | Kotlin 2.4 |
| User interface | Jetpack Compose |
| Local database | Room, with 5 schema versions, each added without data loss |
| Background tasks | WorkManager (day-close task, hourly catch-up task) |
| Alarms | AlarmManager, one exact alarm for the next reminder |
| Location | Google Play Services Geofencing API |
| App usage | UsageStatsManager |
| Audio signal | NotificationListenerService + MediaSessionManager |
| Popups | An overlay window, with a notification as a fallback |
| Minimum Android version | Android 10 (API 29) |
| Target Android version | Android 16 (API 36) |
| Source control | Git, with a commit after each working piece |

## 15. Testing, as built

The app has 137 automated tests. They run on the developer's computer, not on the phone, in under one minute. The tests cover: scoring and streak rules, the Mind/Body/Soul balance, promise rules, levels, reminder planning, reminder behaviour, statistics, audio detection, usage-based detection, and each database schema change. One test renders key screens to an image file, to check the on-screen layout without a phone.

Every build is also installed and checked on the developer's own phone, as the build plan requires.

## 16. Known limitations

- The Rewards screen, the Collection screen, and creatures (step 5) are not built.
- A dedicated debug screen (step 6) is not built.
- Formal battery-optimization steps for the user (step 6) are not written.
- Zone habits need the "Allow all the time" location permission, which some users may decline.
- A friend who installs the shared file can see a Play Protect warning, because of the notification-listener permission the app uses for audio detection.

## 17. Distribution, as built

The developer's own phone keeps the debug build, installed directly from the computer. A signed release file is built for sharing with other people. The developer sends this file directly; the app is not yet on the Google Play Store.
