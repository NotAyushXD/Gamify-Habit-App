# Habit Quest v1 Specification (ASD-STE100)

Nov 4, 2026 · @Ayush Pant

## 1. About this document

This document is the specification for version 1 (v1) of Habit Quest. "Habit Quest" is a working name. The developer uses this document with Claude Code to build the app.

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
| Source app | An app on the phone that the user links to a habit, for example Medito. |
| Zone | A location with a radius of 150 m or more, for example the gym. |
| Session | One time that the user does a habit. A session has a start time and an end time. |
| Window | The time period in which the user plans to do a habit. |
| Detection | The app finds signals that show a possible session. |
| Confirmation | The user tells the app that a session is correct. |
| Points | The score that the user gets for a confirmed session. |
| Creature | An original character that grows and evolves when the user gets points. |

## 2. Product summary

Habit Quest helps the user use good apps and do good activities regularly. It does not replace these apps. It starts them, finds signals of use, and gives points.

The app has three functions:

1. It links habits to source apps (for example Medito), to zones (for example the gym), or to manual check-ins.
2. It finds possible sessions automatically. The user confirms each session with one tap.
3. It gives more points when the user does a habit in the planned window. Points grow creatures and unlock rewards.

### 2.1 Principle: a flaw must not cost points

Automatic detection is not 100% reliable. Thus, detection only suggests a session. The user always confirms, edits, or adds the session. The app must not remove points because of a detection fault.

### 2.2 v1 success test

The user uses v1 for 3 weeks. Then the user compares the results with the period before v1.

- Target: the user meditates on 5 or more days each week.
- Open item: record the current number of meditation days each week before v1 starts.

If v1 does not meet the target, change the game design before you add new functions.

## 3. Scope

v1 is for one user on one Android phone. Keep v1 small. Add a function only when the v1 success test shows a need.

| Release | Users | Functions |
| --- | --- | --- |
| v1 | The developer only | One app habit (Medito), one zone habit (gym), one manual habit (home). Detection, confirmation, time-window scoring, streaks, user rewards, creatures, popups, 11 PM checklist, debug screen. All data stays on the phone. |
| v2 | The developer and friends | Accounts, cloud sync of points only (Firebase or Supabase), friend leaderboard with a weekly reset. Distribution as an APK or through Play Store internal testing. |
| Later | To be decided | More source apps, Health Connect data, chained habits, better creature art and animation. |

### 3.1 Parked ideas

Do not build these items in v1 or v2. Examine them again after v2.

- **Friends' stake pool.** Each friend puts in money each month. The friends who hit the target share the pool. The app only records results. Get legal advice before a public release.
- **Optional rewarded ads.** Show ads only inside the app, for example "watch an ad to protect your streak". Do not give cash for ads. Do not show ads in popups or notifications.
- **iPhone version.** iOS does not give app usage data in the same way. An iPhone user can use manual check-ins and Apple Health data only.

## 4. Platform and technology

v1 is a native Android app. Native code gives the best control of background work, usage data, and location.

| Item | Selection | Reason |
| --- | --- | --- |
| Language | Kotlin | Standard language for Android. |
| User interface | Jetpack Compose | Standard modern Android interface toolkit. |
| Local database | Room | Keeps all v1 data on the phone. |
| Background tasks | WorkManager | Android keeps these tasks after the app stops. |
| Location | Google Play Services Geofencing API | Google Play Services monitors zones, not the app. |
| App usage | UsageStatsManager (usage events) | Gives the time of each foreground and background event. |
| Audio signal | NotificationListenerService + MediaSessionManager | Shows when the source app plays audio. |
| Popups | Overlay window ("Display over other apps") | Shows a popup over other apps. |
| Minimum Android version | Android 10 (API 29) | Covers most phones in use. Change it if a test phone needs an older version. |
| Source control | Git + GitHub | Keeps each change safe. Claude Code can use it. |

Do all tests on a real phone. The emulator does not show real behaviour for location, usage data, audio, and battery limits.

## 5. Habit types and detection

Each habit has one type. The type sets the signals that the app uses to find a session. Every type also accepts a manual entry.

| Type | v1 example | Start signal | End signal | Evidence for bonus |
| --- | --- | --- | --- | --- |
| App | Meditate with Medito | User taps the habit tile in Habit Quest | Audio stops, or the user leaves the source app | Audio played for the target duration |
| Zone | Workout at the gym | User stays in the zone for 5 minutes (dwell) | User leaves the zone | Time in the zone is 30 minutes or more |
| Manual | Read at home | User taps "Start" or ticks the habit | User taps "Done" | None |

&#91;embedded content: session detection flow · 4 starts, 2 outcomes\]

A tick in the 11 PM checklist is a confirmation. All other signals need a tap on the popup.

### 5.1 App habit procedure

1. The user taps the habit tile.
2. The app records the start time.
3. The app starts a foreground service with a "Session in progress" notification.
4. The app opens the source app.
5. The app reads the media session state of the source app.
6. The app reads the usage events of the source app.
7. When audio stops, or the user leaves the source app for 2 minutes, the app ends the session.
8. The app stops the foreground service.
9. The app shows the confirmation popup.

The confirmation popup shows the habit, the start time, the end time, and the points. It has three buttons: **Confirm**, **Edit**, and **Not done**.

### 5.2 Use without Habit Quest

The user can open the source app directly. Android keeps usage events for some days. The app reads these events during each catch-up task (section 6.3). If the app finds a possible session, it asks: "Did you meditate at 07:05?"

### 5.3 Zone habit procedure

1. The user saves a zone with a radius of 150 m or more.
2. The app registers the zone with the Geofencing API.
3. When the user stays in the zone for 5 minutes, Android starts the app.
4. The app shows the popup: "At the gym. Are you working out now?"
5. If the user taps **Yes**, the app starts the session.
6. When the user leaves the zone, the app ends the session.
7. The app shows the confirmation popup with the duration.

### 5.4 Manual habit procedure

Home habits are manual. Location at home does not show that the user did a habit. The user ticks the habit in the app, or in the 11 PM checklist.

### 5.5 Detection limits

- Usage data counts screen-on time only. Thus, the audio signal is the main signal for meditation.
- Each source app needs its own detection rule. v1 has a rule for Medito only.
- A geofence event can come some minutes late. The session times are approximate. The user can edit them.

## 6. Popups, reminders, and the 11 PM checklist

The app uses popups, not notifications, for reminders and confirmations. A popup shows over other apps. It uses the "Display over other apps" permission.

### 6.1 Popup types

| Popup | When it shows | Buttons |
| --- | --- | --- |
| Reminder | At the start of the habit window, if the habit is not done | Start now, Snooze 15 min, Skip today |
| Zone check | User stays in a zone for 5 minutes | Yes, No |
| Confirmation | A session ends | Confirm, Edit, Not done |
| Catch-up | The app finds a session that it did not see live | Confirm, Edit, Not done |
| 11 PM checklist | At 23:00, if the user selects it in Settings | One tick box for each open habit, Save |

### 6.2 Popup rules

- Every popup must have a button that closes it.
- Do not show a popup during a phone call, a video call, or navigation. Show it when the activity ends.
- If the screen is off, show the popup at the next unlock.
- Do not show a popup in quiet hours. The user sets quiet hours in Settings.
- Show a maximum of 3 reminders for each habit each day.

### 6.3 Scheduled tasks

| Task | Frequency | Function |
| --- | --- | --- |
| Catch-up | Every 1 hour | Reads usage events. Finds sessions that the app did not see live. |
| Window check | At the start of each window | Shows the reminder popup if the habit is not done. |
| 11 PM checklist | Daily at 23:00 | Shows the checklist popup. |
| Day close | Daily at 00:05 | Closes the previous day. Calculates streaks. Sets misses. |

The app does not run all the time. Android starts it for each event or task. Thus, if Android stops the app, the next event or task recovers the data.

## 7. Scoring rules

The app gives the most points for a habit done in its window. All values below are v1 defaults. Keep them in one configuration file, so that you can change them after tests.

### 7.1 Points for one session

```latex
\text{Points} = \text{Base} \times \text{Timing factor} \times \text{Streak multiplier} \times \text{Evidence factor}
```

Round the result to the nearest whole number.

| Factor | Condition | Value |
| --- | --- | --- |
| Base | Each habit | 10 (the user can set 5 to 20) |
| Timing factor | Session starts in the window | 1.0 |
| Timing factor | Session starts up to 1 hour before or after the window | 0.6 |
| Timing factor | Session starts at a different time on the same day | 0.3 |
| Evidence factor | Detection found the signals in section 5 | 1.2 |
| Evidence factor | Manual entry or no evidence | 1.0 |

### 7.2 Streak multiplier

A streak is the number of days in sequence on which the user did the habit.

| Streak (days) | Multiplier |
| --- | --- |
| 1 to 6 | 1.0 |
| 7 to 13 | 1.2 |
| 14 to 29 | 1.5 |
| 30 or more | 2.0 |

### 7.3 Missed days

- **Freeze:** Each habit gets 1 freeze each week. The app uses it automatically for the first miss. The streak does not change.
- **Decay:** After the freeze, a miss divides the streak by 2. The streak does not go to 0.
- **Comeback bonus:** The first session after a miss gets 5 extra points.

### 7.4 Daily bonus

If the user does all habits on one day, the app gives 10 extra points.

### 7.5 Example

The user meditates at 07:10. The window is 06:30 to 07:30. The streak is 8 days. The audio signal was found. The result is 10 × 1.0 × 1.2 × 1.2 = 14.4, thus 14 points.

## 8. Rewards and creatures

Each confirmed session adds its points to two counters:

- **XP (experience points):** XP grows creatures. XP never decreases.
- **Coins:** The user spends coins on rewards. Coins decrease when the user claims a reward.

### 8.1 User rewards

1. The user opens the Rewards screen.
2. The user adds a reward name and a coin cost, for example "Dinner out, 500 coins".
3. When the coin balance is equal to or more than the cost, the reward unlocks.
4. The user taps **Claim**.
5. The app removes the coins and records the claim with the date.

### 8.2 Creatures

Each habit has one companion creature. The creature gets the XP of its habit only. Thus, the collection shows the habits that the user does most.

| Stage | XP of the habit | Change |
| --- | --- | --- |
| Egg | 0 | The user selects a creature line when the habit is created. |
| Stage 1 | 50 | The egg hatches. |
| Stage 2 | 300 | First evolution. |
| Stage 3 | 1,000 | Final evolution. |

- A 30-day streak unlocks a rare colour variant of the creature.
- The Collection screen shows all creatures. Locked creatures show as shadows.
- v1 has 3 creature lines: calm (mind habits), strong (body habits), and wise (learning habits).

### 8.3 Creature art

All creatures, names, and art must be original. Do not copy or modify characters from Pokémon or other franchises. v1 can use simple shapes and colours. Replace them with better original art later.

## 9. Screens

v1 has 8 screens and the popups in section 6.

| Screen | Contents | Main actions |
| --- | --- | --- |
| Onboarding | One step for each permission (section 11), with the reason for each | Grant, Skip, Open phone settings |
| Today (home) | Habit tiles with window, status, and streak. Total XP and coins for today. | Tap tile to start a session. Tick a manual habit. |
| Habit editor | Name, type, source app or zone, window, base points, creature line | Save, Delete |
| Zones | Map with saved zones and radius | Add zone, Edit radius (150 m minimum) |
| History | Sessions by day, with times, points, and evidence | Edit session, Add missed session |
| Rewards | Reward list with cost and lock status. Coin balance. | Add reward, Claim |
| Collection | All creatures, stage, XP to next stage | Open creature details |
| Settings | Quiet hours, 11 PM checklist on or off, reminder limits | Open debug screen |

The debug screen (section 12) is hidden. Open it with 5 taps on the version number in Settings.

## 10. Data model

v1 keeps all data in a local Room database. In v2, only the points and the creature stages go to the cloud. Raw usage and location data stay on the phone.

| Table | Fields | Notes |
| --- | --- | --- |
| habit | id, name, type (app / zone / manual), source\_package, zone\_id, window\_start, window\_end, base\_points, creature\_line, active | One row for each habit. |
| zone | id, name, latitude, longitude, radius\_m | radius\_m is 150 or more. |
| session | id, habit\_id, start\_time, end\_time, status (suggested / confirmed / rejected), source (live / catch\_up / manual / checklist), evidence\_found, points | Only confirmed sessions give points. |
| signal\_log | id, time, kind (launch / media / usage / geofence / task), payload | Raw signals for the debug screen. Delete rows older than 14 days. |
| streak | habit\_id, current\_days, best\_days, last\_done\_date, freeze\_used\_week | Updated by the day-close task. |
| wallet | xp\_total, coins | One row. |
| reward | id, name, cost, claimed\_at | claimed\_at is empty until the user claims. |
| creature | habit\_id, line, stage, xp, rare\_variant | One creature for each habit. |
| settings | quiet\_start, quiet\_end, checklist\_enabled, max\_reminders | One row. |

The app must never change a session to "confirmed" without a user action.

## 11. Permissions and onboarding

The app needs 7 permissions. Ask for each permission on its own onboarding step. Tell the user why the app needs it. If the user skips a permission, the related function uses manual entry.

| Permission | Function | If the user skips it |
| --- | --- | --- |
| Usage access | Reads usage events of source apps | No catch-up detection. Manual entry only. |
| Notification access | Reads the audio state of source apps | No audio signal. Usage events only. |
| Location, "Allow all the time" | Zone detection in the background | No zone habits. |
| Display over other apps | Shows popups | The app uses notifications. |
| Notifications | "Session in progress" notification | The session can stop early. |
| Ignore battery optimisation | Keeps tasks on time | Tasks can come late. |
| Background pop-ups (Xiaomi, Redmi, Poco only) | Lets popups show from the background | Popups do not show on these phones. |

### 11.1 Onboarding procedure

1. Show the purpose of the app on one screen.
2. Show one step for each permission in the table above.
3. Find the phone brand.
4. Show the battery settings steps for that brand.
5. Ask the user to create the first habit.
6. Run a test popup.
7. Ask the user to confirm that the popup showed.

The Settings screen must show the status of each permission. It must let the user open the related phone setting again.

## 12. Reliability, debug screen, and tests

The app must work when Android stops it. Each event and each scheduled task must recover the data that the app missed.

### 12.1 Reliability rules

- Every automatic detection is a suggestion. The user confirms it.
- Every habit has a manual entry.
- The 11 PM checklist catches sessions that detection did not find.
- The day-close task must run again if it did not run at 00:05.
- Write each signal to signal\_log before you use it.

### 12.2 Debug screen

The debug screen shows:

- The last 200 rows of signal\_log, newest first.
- The status of each permission.
- The time of the last run of each scheduled task.
- A button that exports signal\_log as a text file.

When a fault occurs, send the export file to Claude Code with a description of what you did on the phone.

### 12.3 Test procedure

Do each test on a real phone. Record the result in a test log.

- [ ] Start a Medito session from the tile. Lock the screen for 10 minutes. Make sure that the confirmation popup shows the correct duration.
- [ ] Open Medito directly, not from the tile. Make sure that the catch-up popup shows within 1 hour.
- [ ] Go to the gym. Stay for 5 minutes. Make sure that the zone popup shows.
- [ ] Drive past the gym without a stop. Make sure that no popup shows.
- [ ] Stop the app from the phone settings. Make sure that the next task recovers the data.
- [ ] Restart the phone. Make sure that zones and tasks work again.
- [ ] Make a phone call at a reminder time. Make sure that the popup shows after the call.
- [ ] Miss a habit for 2 days. Make sure that the freeze and the decay rules apply.
- [ ] Before you share v1 with friends, do these tests on 2 more phone brands. Include one Xiaomi, Vivo, Oppo, or Realme phone.

## 13. Build plan with Claude Code

Build v1 in 6 steps. Each step gives a version that works on the phone. Do not start a step before the previous step works on the phone.

1. **Base app.** Create the project, the Room database, the Today screen, and manual habits. Add the scoring rules and the day-close task.
2. **Popups.** Add the overlay permission, the popup types, the popup rules, and the 11 PM checklist.
3. **App habits.** Add the habit tile launch, the foreground service, the audio signal, and the usage events for Medito. Add the confirmation popup.
4. **Zone habits.** Add the Zones screen, the Geofencing API, the dwell trigger, and the zone popups.
5. **Rewards and creatures.** Add XP, coins, the Rewards screen, and the Collection screen.
6. **Reliability.** Add the catch-up task, the debug screen, onboarding, and the battery settings steps. Do the tests in section 12.3.

### 13.1 How to work with Claude Code

1. Give Claude Code this document at the start of each step.
2. Tell Claude Code which step to build.
3. Install the app on your phone after each step.
4. Use the app for 1 or 2 days.
5. Tell Claude Code what you did and what occurred.
6. Send the debug export file when a fault occurs.
7. Commit the code to GitHub when the step works.

### 13.2 After v1

Use v1 for 3 weeks. Do the success test in section 2.2. Then select the v2 functions.
