package com.ayushpant.habitquest.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.ayushpant.habitquest.data.HabitRepository
import com.ayushpant.habitquest.stats.Badge
import com.ayushpant.habitquest.stats.DayCell
import com.ayushpant.habitquest.stats.Period
import com.ayushpant.habitquest.stats.Summary
import com.ayushpant.habitquest.stats.badges
import com.ayushpant.habitquest.stats.buildSummary
import com.ayushpant.habitquest.stats.compare
import com.ayushpant.habitquest.stats.highlights
import com.ayushpant.habitquest.stats.periodRange
import com.ayushpant.habitquest.stats.praise
import com.ayushpant.habitquest.stats.shiftPeriod
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

/** This period's summary and the one before it (for "better than last week"). */
data class SummaryState(val current: Summary, val previous: Summary)

/** Summary: how consistent habits and tasks were in a day, a week or a month. */
@OptIn(ExperimentalCoroutinesApi::class)
class SummaryViewModel(private val repo: HabitRepository) : ViewModel() {
    val period = MutableStateFlow(Period.WEEK)
    /** Any day inside the period that is shown. */
    val anchor = MutableStateFlow(repo.today())

    val state: StateFlow<SummaryState?> = combine(period, anchor) { p, a -> p to a }
        .flatMapLatest { (p, a) ->
            val (from, to) = periodRange(p, a)
            val (prevFrom, prevTo) = periodRange(p, shiftPeriod(p, a, -1))
            combine(repo.observeRange(from, to), repo.observeRange(prevFrom, prevTo)) { now, before ->
                val today = repo.today()
                SummaryState(buildSummary(now, today), buildSummary(before, today))
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun today(): LocalDate = repo.today()
    fun select(p: Period) { period.value = p; anchor.value = repo.today() }
    fun move(steps: Long) { anchor.value = shiftPeriod(period.value, anchor.value, steps) }

    /** Tapping a day in the calendar shows that day. */
    fun openDay(day: LocalDate) { period.value = Period.DAY; anchor.value = day }
}

// Ring colours: fixed (not from the wallpaper theme) so each ring always means the same thing.
private val HABITS_COLOR = Color(0xFF30D158)
private val TASKS_COLOR = Color(0xFF0A84FF)
private val PERFECT_COLOR = Color(0xFFFF9F0A)
private val HABIT_PALETTE = listOf(
    Color(0xFFBF5AF2), Color(0xFFFF453A), Color(0xFF40C8E0), Color(0xFFFFD60A),
    Color(0xFFFF375F), Color(0xFF5E5CE6), Color(0xFF66D4CF), Color(0xFFAC8E68),
)
/** Per-habit rings in a calendar day: more would be too thin to read. */
private const val MAX_DAY_HABIT_RINGS = 4

private val DAY_LONG = DateTimeFormatter.ofPattern("EEEE d MMMM")
private val DAY_SHORT = DateTimeFormatter.ofPattern("d MMM")
private val MONTH = DateTimeFormatter.ofPattern("MMMM yyyy")

/**
 * Three sections, most personal first: Mind · Body · Soul, the highlights (swipe), and the
 * overview (stacked rings with points, and the calendar with rings per day).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SummaryScreen(vm: SummaryViewModel) {
    val period by vm.period.collectAsStateWithLifecycle()
    val state by vm.state.collectAsStateWithLifecycle()
    var showBadges by remember { mutableStateOf(false) }

    Scaffold(topBar = { TopAppBar(title = { Text("Your progress") }) }) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            val st = state
            item {
                PeriodBar(
                    period = period,
                    label = st?.let { rangeLabel(period, it.current.from, it.current.to) } ?: "",
                    canGoLater = st != null && vm.today() > st.current.to,
                    onSelect = vm::select,
                    onMove = vm::move,
                )
            }
            if (st == null) return@LazyColumn
            val s = st.current
            val isCurrent = vm.today() <= s.to
            item { Section("Mind · Body · Soul") { BalanceCard(s) } }
            item {
                HighlightsRow(s, compare(s, st.previous, thanText(period, isCurrent)), onBadges = { showBadges = true })
            }
            item {
                Section("Overview") {
                    HeroCard(s, whenText(period, isCurrent))
                    if (period != Period.DAY) CalendarSection(s, period, vm::openDay)
                }
            }
        }
    }

    val st = state
    if (showBadges && st != null) {
        ModalBottomSheet(onDismissRequest = { showBadges = false }) {
            Column(Modifier.padding(start = 20.dp, end = 20.dp, bottom = 32.dp).verticalScroll(rememberScrollState())) {
                BadgesCard(badges(st.current))
            }
        }
    }
}

/** A titled block of the page, with the side margins. */
@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        content()
    }
}

/** The highlights as small cards in one swipeable row: what went well, growth, and badges. */
@Composable
private fun HighlightsRow(s: Summary, growth: List<String>, onBadges: () -> Unit) {
    val list = highlights(s)
    val earned = badges(s)
    val next = earned.firstOrNull { !it.earned }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Highlights", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 16.dp))
        LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            items(growth) { line -> HighlightCard("📈", "You're growing", line, accent = HABITS_COLOR) }
            items(list) { h -> HighlightCard(h.emoji, h.title, h.value) }
            item {
                HighlightCard(
                    "🏅", "Badges · ${earned.count { it.earned }} of ${earned.size}",
                    next?.let { "Next: ${it.emoji} ${it.title} (${it.progress.coerceAtMost(it.target)}/${it.target})" } ?: "All earned!",
                    onClick = onBadges,
                )
            }
            if (list.isEmpty() && growth.isEmpty()) {
                item { HighlightCard("🌱", "Just starting", "Tick a habit to see your highlights here") }
            }
        }
    }
}

@Composable
private fun HighlightCard(emoji: String, title: String, value: String, accent: Color? = null, onClick: (() -> Unit)? = null) {
    Card(
        modifier = Modifier.width(156.dp).height(118.dp).then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        colors = if (accent != null) CardDefaults.cardColors(containerColor = accent.copy(alpha = 0.16f)) else CardDefaults.cardColors(),
    ) {
        Column(Modifier.padding(12.dp)) {
            Text(emoji, fontSize = 24.sp)
            Text(title, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = 4.dp), maxLines = 1)
            Text(value, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, maxLines = 3)
        }
    }
}

/** Day | Week | Month, and the arrows to go back and forth, in one compact bar. */
@Composable
private fun PeriodBar(period: Period, label: String, canGoLater: Boolean, onSelect: (Period) -> Unit, onMove: (Long) -> Unit) {
    Column(Modifier.padding(horizontal = 16.dp)) {
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            Period.entries.forEachIndexed { i, p ->
                SegmentedButton(
                    selected = period == p,
                    onClick = { onSelect(p) },
                    shape = SegmentedButtonDefaults.itemShape(i, Period.entries.size),
                ) { Text(p.name.lowercase().replaceFirstChar { it.uppercase() }) }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { onMove(-1) }) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Earlier") }
            Text(label, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
            IconButton(onClick = { onMove(1) }, enabled = canGoLater) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Later") }
        }
    }
}

private fun rangeLabel(period: Period, from: LocalDate, to: LocalDate) = when (period) {
    Period.DAY -> from.format(DAY_LONG)
    Period.WEEK -> "${from.format(DAY_SHORT)} – ${to.format(DAY_SHORT)}"
    Period.MONTH -> from.format(MONTH)
}

private fun whenText(period: Period, isCurrent: Boolean) = when (period) {
    Period.DAY -> if (isCurrent) "today" else "that day"
    Period.WEEK -> if (isCurrent) "this week" else "that week"
    Period.MONTH -> if (isCurrent) "this month" else "that month"
}

private fun thanText(period: Period, isCurrent: Boolean) = when (period) {
    Period.DAY -> if (isCurrent) "yesterday" else "the day before"
    Period.WEEK -> if (isCurrent) "last week" else "the week before"
    Period.MONTH -> if (isCurrent) "last month" else "the month before"
}

private fun percent(rate: Double?) = rate?.let { "${(it * 100).roundToInt()}%" } ?: "–"

// ---- Rings --------------------------------------------------------------------------------------

/** One ring: [progress] 0..1, or null when there is nothing to measure (only the faint track shows). */
private data class Ring(val progress: Double?, val color: Color)

/**
 * Rings inside each other, outer ring first. Each fills clockwise from the top, with a short
 * animation when the numbers change.
 */
@Composable
private fun StackedRings(
    rings: List<Ring>,
    size: Dp,
    hole: Dp = 36.dp,
    gap: Dp = 4.dp,
    center: @Composable () -> Unit = {},
) {
    val stroke = minOf(18.dp, (size / 2 - hole) / rings.size.coerceAtLeast(1) - gap)
    val animated = rings.mapIndexed { i, r ->
        key(i) {
            animateFloatAsState(
                targetValue = (r.progress ?: 0.0).toFloat().coerceIn(0f, 1f),
                animationSpec = tween(durationMillis = 900, delayMillis = i * 120),
                label = "ring$i",
            ).value
        }
    }
    Box(Modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val strokePx = stroke.toPx()
            val gapPx = gap.toPx()
            rings.forEachIndexed { i, ring ->
                val inset = strokePx / 2 + i * (strokePx + gapPx)
                val arcSize = Size(this.size.width - 2 * inset, this.size.height - 2 * inset)
                val topLeft = Offset(inset, inset)
                drawArc(ring.color.copy(alpha = 0.18f), 0f, 360f, false, topLeft, arcSize, style = Stroke(strokePx))
                if (animated[i] > 0f) {
                    drawArc(ring.color, -90f, 360f * animated[i], false, topLeft, arcSize, style = Stroke(strokePx, cap = StrokeCap.Round))
                }
            }
        }
        center()
    }
}

// ---- Overview -----------------------------------------------------------------------------------

@Composable
private fun HeroCard(s: Summary, whenText: String) {
    val p = praise(s, whenText)
    // A neutral background, so all three ring colours stand out.
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
        Column(Modifier.padding(16.dp)) {
            Text(p.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            Text(p.message, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 2.dp, bottom = 12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                StackedRings(
                    rings = listOf(Ring(s.habitRate, HABITS_COLOR), Ring(s.taskRate, TASKS_COLOR), Ring(s.perfectRate, PERFECT_COLOR)),
                    size = 150.dp,
                    hole = 26.dp,
                    gap = 3.dp,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("${s.points}", fontSize = 26.sp, fontWeight = FontWeight.Bold)
                        Text("points", style = MaterialTheme.typography.labelSmall)
                    }
                }
                Column(Modifier.padding(start = 16.dp).weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    RingStat(HABITS_COLOR, "Habits", percent(s.habitRate), "${s.done}/${s.scheduled}")
                    RingStat(TASKS_COLOR, "Tasks on time", percent(s.taskRate), "${s.tasks.dueInRangeOnTime}/${s.tasks.dueInRange}")
                    RingStat(PERFECT_COLOR, "Perfect days", percent(s.perfectRate), "${s.perfectDays}/${s.plannedDays}")
                }
            }
        }
    }
}

/** One line next to the hero rings: a big coloured value, a label and the count. */
@Composable
private fun RingStat(color: Color, label: String, value: String, count: String) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(10.dp).clip(CircleShape).background(color))
            Text(label, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(start = 6.dp))
        }
        Row(verticalAlignment = Alignment.Bottom) {
            Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = color)
            Text("  $count", style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(bottom = 3.dp))
        }
    }
}

/** The calendar with rings per day; switch between the category rings and one ring per habit. */
@Composable
private fun CalendarSection(s: Summary, period: Period, onDay: (LocalDate) -> Unit) {
    var byHabit by rememberSaveable { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            SegmentedButton(selected = !byHabit, onClick = { byHabit = false }, shape = SegmentedButtonDefaults.itemShape(0, 2)) { Text("Overview rings") }
            SegmentedButton(selected = byHabit, onClick = { byHabit = true }, shape = SegmentedButtonDefaults.itemShape(1, 2), enabled = s.habits.isNotEmpty()) {
                Text("Habit rings")
            }
        }
        if (byHabit) HabitCalendarCard(s, period, onDay) else CategoryCalendarCard(s, period, onDay)
    }
}

/** Mind / Body / Soul for the period: one ring each, with the share done and the points. */
@Composable
private fun BalanceCard(s: Summary) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(horizontalArrangement = Arrangement.SpaceEvenly, modifier = Modifier.fillMaxWidth()) {
                for (score in s.balance) {
                    val d = score.dimension
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        StackedRings(rings = listOf(Ring(score.rate, d.color())), size = 84.dp, hole = 28.dp) {
                            Text(d.emoji, fontSize = 22.sp)
                        }
                        Text(d.label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 6.dp))
                        Text(score.percentText(), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = d.color())
                        Text("+${score.points} pts", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
            val weakest = s.balance.filter { it.rate != null }.minByOrNull { it.rate!! }
            val strongest = s.balance.filter { it.rate != null }.maxByOrNull { it.rate!! }
            if (weakest != null && strongest != null && weakest != strongest) {
                Text(
                    "Your ${strongest.dimension.label.lowercase()} is thriving. A little more for your ${weakest.dimension.label.lowercase()} would balance you out.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

@Composable
private fun BadgesCard(list: List<Badge>) {
    val earned = list.count { it.earned }
    val next = list.firstOrNull { !it.earned }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Badges", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                Text("$earned of ${list.size} earned", style = MaterialTheme.typography.bodyMedium)
            }
            if (next != null) {
                Column {
                    Text("Next up: ${next.emoji} ${next.title}", style = MaterialTheme.typography.bodyLarge)
                    Text("${next.description} · ${next.progress.coerceAtMost(next.target)} of ${next.target}", style = MaterialTheme.typography.bodySmall)
                    LinearProgressIndicator(progress = { next.ratio }, modifier = Modifier.fillMaxWidth().padding(top = 6.dp))
                }
            }
            for (row in list.chunked(3)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (b in row) BadgeTile(b, Modifier.weight(1f))
                    repeat(3 - row.size) { Box(Modifier.weight(1f)) }
                }
            }
        }
    }
}

@Composable
private fun BadgeTile(b: Badge, modifier: Modifier) {
    Column(modifier.alpha(if (b.earned) 1f else 0.35f), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier.size(56.dp).clip(CircleShape).background(
                if (b.earned) PERFECT_COLOR.copy(alpha = 0.25f) else MaterialTheme.colorScheme.surfaceVariant
            ),
            contentAlignment = Alignment.Center,
        ) { Text(b.emoji, fontSize = 26.sp) }
        Text(b.title, style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 4.dp))
        if (!b.earned) Text("${b.progress.coerceAtMost(b.target)}/${b.target}", style = MaterialTheme.typography.labelSmall)
    }
}

// ---- Habits view --------------------------------------------------------------------------------

@Composable
private fun CategoryCalendarCard(s: Summary, period: Period, onDay: (LocalDate) -> Unit) {
    CalendarCard(
        title = if (period == Period.WEEK) "Your week" else "Your month",
        s = s,
        period = period,
        onDay = onDay,
        rings = { cell ->
            listOf(
                Ring(cell.rate, HABITS_COLOR),
                Ring(cell.taskRate, TASKS_COLOR),
                Ring(if (cell.perfect) 1.0 else if (cell.scheduled > 0 && cell.day < s.today) 0.0 else null, PERFECT_COLOR),
            )
        },
        legend = listOf(HABITS_COLOR to "Habits", TASKS_COLOR to "Tasks", PERFECT_COLOR to "Perfect day"),
    )
}

@Composable
private fun HabitCalendarCard(s: Summary, period: Period, onDay: (LocalDate) -> Unit) {
    if (s.habits.isEmpty()) return
    val shown = s.habits.take(MAX_DAY_HABIT_RINGS)
    CalendarCard(
        title = "Day by day",
        s = s,
        period = period,
        onDay = onDay,
        rings = { cell ->
            shown.mapIndexed { i, h ->
                Ring(cell.habitDone[h.habit.id]?.let { if (it) 1.0 else 0.0 }, HABIT_PALETTE[i % HABIT_PALETTE.size])
            }
        },
        legend = shown.mapIndexed { i, h -> HABIT_PALETTE[i % HABIT_PALETTE.size] to h.habit.name },
        note = if (s.habits.size > MAX_DAY_HABIT_RINGS) "Days show your first $MAX_DAY_HABIT_RINGS habits." else null,
    )
}

/**
 * A calendar, Monday first: one row for a week, a grid for a month. Every day shows its date and
 * small stacked rings. Tap a day to see it in the Day view.
 */
@Composable
private fun CalendarCard(
    title: String,
    s: Summary,
    period: Period,
    onDay: (LocalDate) -> Unit,
    rings: (DayCell) -> List<Ring>,
    legend: List<Pair<Color, String>>,
    note: String? = null,
) {
    val leading = if (period == Period.MONTH) s.days.first().day.dayOfWeek.value - 1 else 0
    val cells: List<DayCell?> = List(leading) { null } + s.days
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Row {
                for (letter in listOf("M", "T", "W", "T", "F", "S", "S")) {
                    Text(letter, style = MaterialTheme.typography.labelMedium, textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
                }
            }
            for (week in cells.chunked(7)) {
                Row {
                    for (i in 0 until 7) {
                        val cell = week.getOrNull(i)
                        // The rings fill the day's column (about 48 dp on a phone), up to 56 dp.
                        BoxWithConstraints(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                            if (cell != null) DayRings(cell, s.today, rings(cell), minOf(maxWidth - 4.dp, 56.dp), onDay)
                        }
                    }
                }
            }
            FlowLegend(legend)
            note?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
            Text("Tap a day to see it.", style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun DayRings(cell: DayCell, today: LocalDate, rings: List<Ring>, size: Dp, onDay: (LocalDate) -> Unit) {
    val future = cell.day > today
    Column(
        Modifier
            .clip(RoundedCornerShape(10.dp))
            .clickable(enabled = !future) { onDay(cell.day) }
            .padding(vertical = 4.dp)
            .alpha(if (future) 0.4f else 1f),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        val isToday = cell.day == today
        Box(
            Modifier.size(22.dp).clip(CircleShape)
                .background(if (isToday) MaterialTheme.colorScheme.primary else Color.Transparent),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                "${cell.day.dayOfMonth}",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = if (isToday || cell.perfect) FontWeight.Bold else FontWeight.Normal,
                color = when {
                    isToday -> MaterialTheme.colorScheme.onPrimary
                    cell.perfect -> PERFECT_COLOR
                    else -> MaterialTheme.colorScheme.onSurface
                },
            )
        }
        StackedRings(rings = rings, size = size, hole = size * 0.1f, gap = 1.5.dp)
    }
}

@Composable
private fun FlowLegend(items: List<Pair<Color, String>>) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(top = 4.dp)) {
        for ((color, label) in items) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(10.dp).clip(CircleShape).background(color))
                Text(label, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(start = 4.dp), maxLines = 1)
            }
        }
    }
}
