# Habit Quest Step 5 Plan (ASD-STE100)

Nov 10, 2026 · Draft for the developer's approval · Base: app 0.11.2, database version 5, 137 tests

## 1. What the code shows now

- Points are stored per session (`session.points`), per day bonus (`day_bonus`), and per task (`task.points`). Lifetime points are the sum of the three. Levels come from lifetime points.
- Every change rescores the day. A History edit on a closed day replays one habit's streak from its creation day. Points can go up or down.
- The streak table has `current_days`, `best_days`, `freeze_week`, `freezes_used`, and `comeback_pending`. The best streak already exists.
- A kept promise gives +5 points. It does not change the streak. The promised day stays a miss.
- The `habit.creature_line` column (CALM, STRONG, WISE) comes from the v1 spec. No screen uses it.
- A celebration comes from a compare of Today before and after a change (`celebrationFor`).
- Streak milestones are 3, 7, 14, 21, 30, 50, 75, 100, 150, 200, 365. There is no 66.

## 2. Data design

Database version 6 holds all step 5 data. Every new table has a `theme_id` column from the start, so sub-step 5g needs no second schema change.

| Table | Content |
| --- | --- |
| `coin_ledger` | amount, reason, time, and a source key (kind, reference id, day). The balance is the sum. |
| `coin_credit` | For each source key: the points already paid as coins. Makes a credit idempotent. |
| `egg` | theme_id, needed growth, growth from Mind, from Body, from Soul, hatched date, creature id. |
| `creature_owned` | theme_id, creature id, hatch date, bond. One row is the companion. |
| `personal_reward` | name, cost, active flag. Plus `reward_redemption` rows with the date. |
| `quest` | week start, type, target N, habit or dimension, selected flag, done date. |
| `freeze_use` | habit_id, day: the stored freeze protected this habit on this day. |
| `habit.cue_line` | New column, optional text. |

The stored-freeze count, the theme choice, and the theme-change dates go in `app_state`.

## 3. Sub-steps

### 5a. Coins
- After each rescore, for each source (a session day, a day bonus, a task): credit = points − points already credited. Credit only when the result is above 0. Write a ledger row and update `coin_credit`.
- The daily bonus also gives coins. It exists only because of confirmed sessions.
- Show the balance on the Today card. Show "+N coins" in each celebration.
- Tests: coins = points; an unconfirmed session gives 0; a decrease in points removes no coins; a second rescore of the same day adds nothing.

### 5b. Egg and creatures
- One `Theme` data class: names, colours, message texts, 12 creature definitions (name, dimension, draw function). Rules never read it.
- Growth: each credited coin adds 1 growth to the open egg, split by the source's dimensions. The day bonus has no dimension: split it equally in three.
- Hatch at full growth. Dimension = the largest share; tie → the dimension with fewer owned creatures. Creature = one not owned in that dimension; all owned → a different dimension; all 12 owned → +100 coins instead. A new egg starts at once.
- Companion: bond +1 for each confirmed session. Stages at 30 and 100 bond. States active, waiting, resting. A welcome message after a resting period.
- Screens: egg and companion on Today; a Collection screen with 12 positions.
- Tests: hatch and head start; dimension rules; spending does not change growth; big celebration on hatch.

### 5c. Personal rewards
- Rewards screen. Create, edit, delete. Cost 10 to 5000. Three examples when empty.
- Redeem: confirm, subtract, ledger row. Too few coins: button off plus "N more coins".
- A list of redeemed rewards with dates.

### 5d. Weekly quests
- A pure quest generator: input = habits, schedules, tasks, last 4 weeks of results; output = 5 possible quests, N at 50 to 80 % of the maximum.
- Offer on Monday on the Today screen. Select up to 3. One prompt a week.
- Progress is computed from sessions, bonuses, and tasks. Done → +30 coins and a celebration.
- Day close of Sunday removes open quests silently.
- Tests: each offered quest is possible; an open quest ends with no penalty.

### 5e. Streak care
- Show best streak and total days done (count of distinct days with a confirmed session) next to the current streak.
- "Start again" message after a reset. No popup or notification for a broken streak.
- Add 66 to the milestones in `ScoringConfig`.
- Stored freeze: 150 coins, hold 1. At day close: weekly freeze first; then the stored freeze for the habit with the longest streak; write a `freeze_use` row. A replay treats a `freeze_use` day as kept, so a History edit never takes a freeze back.
- Tests: weekly before stored; longest streak first; reset keeps best and total; replay keeps `freeze_use`.

### 5f. Cue line
- Optional field in the editor: "After I [cue], I will [habit]." Shown in the window-start reminder.

### 5g. Themes
- A second theme as a data set only. Welcome tour page with a preview and a mandatory choice. Current users keep theme 1.
- Change in Settings: free for 7 days, then once per 30 days. Show the next possible date. Explain what pauses and what stays.
- Per theme: egg, growth, creatures, companion, bond. Shared: points, levels, coins, streaks, rewards, quests.
- Tests: a change keeps points, levels, coins, streaks; a paused collection returns complete; a refused change inside 30 days.

### Finish
- Guide topics, a companion page in the tour, status report update, render images of each new screen.

## 4. Decisions on Nov 10, 2026

- Questions 1, 2, 5, 6, 7, 8, 9: the developer approved the defaults.
- Question 3: creature names must be original (rule R14). No name may resemble a character from another work. Claude proposes 12 original cute names per theme; the developer can reject any before 5b is committed.
- Question 4: the values are approved. The developer also wants a small creature-battle game with a map. It is added as sub-step 5h.

### 5h. Sparring game (added)
- An original turn-based game: the companion against a map creature that the app controls. No online play (R8).
- Each creature has an energy bar, not health. At 0 energy it takes a breather and bows. No creature is hurt, sad, or defeated in words or pictures (R5).
- Each creature has 3 moves from its dimension. Advantage triangle: Body > Mind > Soul > Body.
- Strength comes from bond and stage only. There is no separate training.
- One spar ticket for each confirmed session that day. Tickets end at day close.
- A win gives 5 coins and 1 bond. A loss costs nothing and gives a kind message (R1, R4).
- An original map: a home and three regions (a grove for Mind, peaks for Body, a shore for Soul). Regions unlock by level. The second theme re-skins the map as data only.
- Tests: the advantage triangle; a loss changes no coins, growth, bond, or streak; a ticket is used once; no spar without a ticket.
