package com.ayushpant.habitquest.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
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
import kotlinx.coroutines.flow.map
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
}

// Ring colours: fixed (not from the wallpaper theme) so each ring always means the same thing.
private val HABITS_COLOR = Color(0xFF30D158)
private val TASKS_COLOR = Color(0xFF0A84FF)
private val PERFECT_COLOR = Color(0xFFFF9F0A)
private val HABIT_PALETTE = listOf(
    Color(0xFFBF5AF2), Color(0xFFFF453A), Color(0xFF40C8E0), Color(0xFFFFD60A),
    Color(0xFFFF375F), Color(0xFF5E5CE6), Color(0xFF66D4CF), Color(0xFFAC8E68),
)
private const val MAX_HABIT_RINGS = 6

private val DAY_LONG = DateTimeFormatter.ofPattern("EEEE d MMMM")
private val DAY_SHORT = DateTimeFormatter.ofPattern("d MMM")
private val MONTH = DateTimeFormatter.ofPattern("MMMM yyyy")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SummaryScreen(vm: SummaryViewModel) {
    val period by vm.period.collectAsStateWithLifecycle()
    val state by vm.state.collectAsStateWithLifecycle()
    var view by rememberSaveable { mutableIntStateOf(0) } // 0 = Overview, 1 = Habits

    Scaffold(topBar = { TopAppBar(title = { Text("Your progress") }) }) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    Period.entries.forEachIndexed { i, p ->
                        SegmentedButton(
                            selected = period == p,
                            onClick = { vm.select(p) },
                            shape = SegmentedButtonDefaults.itemShape(i, Period.entries.size),
                        ) { Text(p.name.lowercase().replaceFirstChar { it.uppercase() }) }
                    }
                }
            }
            val st = state ?: return@LazyColumn
            val s = st.current
            val isCurrent = vm.today() <= s.to
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { vm.move(-1) }) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Earlier") }
                    Text(rangeLabel(period, s.from, s.to), style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
                    IconButton(onClick = { vm.move(1) }, enabled = !isCurrent) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Later") }
                }
            }
            item {
                PrimaryTabRow(selectedTabIndex = view) {
                    Tab(selected = view == 0, onClick = { view = 0 }, text = { Text("Overview") })
                    Tab(selected = view == 1, onClick = { view = 1 }, text = { Text("Habits") })
                }
            }
            if (view == 0) {
                item { HeroCard(s, whenText(period, isCurrent)) }
                val lines = compare(s, st.previous, thanText(period, isCurrent))
                if (lines.isNotEmpty()) item { ComparisonCard(lines) }
                val hl = highlights(s)
                if (hl.isNotEmpty()) {
                    item { Text("Highlights", style = MaterialTheme.typography.titleMedium) }
                    items(hl.chunked(2).size) { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            for (h in hl.chunked(2)[row]) HighlightCard(h.emoji, h.title, h.value, Modifier.weight(1f))
                            if (hl.chunked(2)[row].size == 1) Box(Modifier.weight(1f))
                        }
                    }
                }
                item { BadgesCard(badges(s)) }
            } else {
                item { HabitRingsCard(s) }
                if (period != Period.DAY) item { CalendarCard(s, period) }
            }
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
private fun StackedRings(rings: List<Ring>, size: Dp, center: @Composable () -> Unit) {
    val gap = 4.dp
    val stroke = minOf(18.dp, (size / 2 - 36.dp) / rings.size.coerceAtLeast(1) - gap)
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

@Composable
private fun LegendRow(color: Color, label: String, value: String, detail: String) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Box(Modifier.size(12.dp).clip(CircleShape).background(color))
        Column(Modifier.weight(1f).padding(start = 10.dp)) {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            Text(detail, style = MaterialTheme.typography.bodySmall)
        }
        Text(value, style = MaterialTheme.typography.titleMedium, color = color, fontWeight = FontWeight.Bold)
    }
}

// ---- Overview -----------------------------------------------------------------------------------

@Composable
private fun HeroCard(s: Summary, whenText: String) {
    val p = praise(s, whenText)
    // A neutral background, so all three ring colours stand out.
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
        Column(Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(p.title, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.primary)
            Text(p.message, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 6.dp, bottom = 16.dp))
            StackedRings(
                rings = listOf(Ring(s.habitRate, HABITS_COLOR), Ring(s.taskRate, TASKS_COLOR), Ring(s.perfectRate, PERFECT_COLOR)),
                size = 220.dp,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("${s.points}", fontSize = 34.sp, fontWeight = FontWeight.Bold)
                    Text("points", style = MaterialTheme.typography.bodyMedium)
                }
            }
            Column(Modifier.padding(top = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                LegendRow(HABITS_COLOR, "Habits", percent(s.habitRate), "${s.done} of ${s.scheduled} planned habit-days")
                LegendRow(TASKS_COLOR, "Tasks on time", percent(s.taskRate), "${s.tasks.dueInRangeOnTime} of ${s.tasks.dueInRange} tasks due")
                LegendRow(PERFECT_COLOR, "Perfect days", percent(s.perfectRate), "${s.perfectDays} of ${s.plannedDays} days with every habit done")
            }
        }
    }
}

@Composable
private fun ComparisonCard(lines: List<String>) {
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = HABITS_COLOR.copy(alpha = 0.15f))) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("📈 You're growing", style = MaterialTheme.typography.titleMedium)
            for (line in lines) Text(line, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun HighlightCard(emoji: String, title: String, value: String, modifier: Modifier) {
    Card(modifier) {
        Column(Modifier.padding(14.dp)) {
            Text(emoji, fontSize = 26.sp)
            Text(title, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp))
            Text(value, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
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
private fun HabitRingsCard(s: Summary) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            if (s.habits.isEmpty()) {
                Text("No habits yet. Add one on the Today screen.")
                return@Column
            }
            val ringed = s.habits.take(MAX_HABIT_RINGS)
            StackedRings(
                rings = ringed.mapIndexed { i, h -> Ring(h.rate, HABIT_PALETTE[i % HABIT_PALETTE.size]) },
                size = 240.dp,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(percent(s.habitRate), fontSize = 30.sp, fontWeight = FontWeight.Bold)
                    Text("consistent", style = MaterialTheme.typography.bodySmall)
                }
            }
            Column(Modifier.padding(top = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                s.habits.forEachIndexed { i, h ->
                    val streak = if (h.currentStreak > 0) "🔥 ${h.currentStreak}  ·  " else ""
                    val extra = if (h.extra > 0) "  ·  +${h.extra} extra" else ""
                    LegendRow(
                        color = if (i < MAX_HABIT_RINGS) HABIT_PALETTE[i % HABIT_PALETTE.size] else MaterialTheme.colorScheme.outline,
                        label = h.habit.name,
                        value = percent(h.rate),
                        detail = "$streak${h.done} of ${h.scheduled} days  ·  best ${h.bestStreak}$extra",
                    )
                }
            }
            if (s.habits.size > MAX_HABIT_RINGS) {
                Text("The circle shows your first $MAX_HABIT_RINGS habits.", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 8.dp))
            }
        }
    }
}

@Composable
private fun CalendarCard(s: Summary, period: Period) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(if (period == Period.WEEK) "Day by day" else "Your month", style = MaterialTheme.typography.titleMedium)
            if (period == Period.WEEK) WeekBars(s.days) else MonthGrid(s.days)
            Text("⭐ = every planned habit done that day", style = MaterialTheme.typography.bodySmall)
        }
    }
}

/** One bar per day: the share of planned habits done. A star marks a perfect day. */
@Composable
private fun WeekBars(days: List<DayCell>) {
    Row(Modifier.fillMaxWidth().height(130.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        for (cell in days) {
            Column(Modifier.weight(1f).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(if (cell.perfect) "⭐" else " ", fontSize = 12.sp)
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.BottomCenter) {
                    val rate = cell.rate
                    Box(
                        Modifier.fillMaxWidth()
                            .fillMaxHeight(if (rate == null) 0.04f else maxOf(rate.toFloat(), 0.04f))
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (rate == null) MaterialTheme.colorScheme.surfaceVariant else HABITS_COLOR)
                    )
                }
                Text(cell.day.dayOfWeek.name.take(1), style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

/** A calendar, Monday first. Brighter = more of the planned habits done; a star = perfect day. */
@Composable
private fun MonthGrid(days: List<DayCell>) {
    val leading = days.first().day.dayOfWeek.value - 1
    val cells: List<DayCell?> = List(leading) { null } + days
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            for (letter in listOf("M", "T", "W", "T", "F", "S", "S")) {
                Text(letter, style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
            }
        }
        for (week in cells.chunked(7)) {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                for (i in 0 until 7) {
                    val cell = week.getOrNull(i)
                    Box(Modifier.weight(1f).aspectRatio(1f), contentAlignment = Alignment.Center) {
                        if (cell != null) {
                            val rate = cell.rate
                            Box(
                                Modifier.fillMaxSize().clip(RoundedCornerShape(6.dp)).background(
                                    if (rate == null) MaterialTheme.colorScheme.surfaceVariant
                                    else HABITS_COLOR.copy(alpha = 0.2f + 0.8f * rate.toFloat())
                                )
                            )
                            Text(if (cell.perfect) "⭐" else "${cell.day.dayOfMonth}", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }
    }
}
