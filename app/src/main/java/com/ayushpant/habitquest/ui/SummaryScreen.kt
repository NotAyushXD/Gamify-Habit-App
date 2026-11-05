package com.ayushpant.habitquest.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.ayushpant.habitquest.data.HabitRepository
import com.ayushpant.habitquest.stats.DayCell
import com.ayushpant.habitquest.stats.Period
import com.ayushpant.habitquest.stats.Summary
import com.ayushpant.habitquest.stats.buildSummary
import com.ayushpant.habitquest.stats.periodRange
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

/** Summary: how consistent habits and tasks were in a day, a week or a month. */
@OptIn(ExperimentalCoroutinesApi::class)
class SummaryViewModel(private val repo: HabitRepository) : ViewModel() {
    val period = MutableStateFlow(Period.WEEK)
    /** Any day inside the period that is shown. */
    val anchor = MutableStateFlow(repo.today())

    val summary: StateFlow<Summary?> = combine(period, anchor) { p, a -> periodRange(p, a) }
        .flatMapLatest { (from, to) -> repo.observeRange(from, to).map { buildSummary(it, repo.today()) } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun today(): LocalDate = repo.today()
    fun select(p: Period) { period.value = p; anchor.value = repo.today() }
    fun move(steps: Long) { anchor.value = shiftPeriod(period.value, anchor.value, steps) }
}

private val DAY_LONG = DateTimeFormatter.ofPattern("EEEE d MMMM")
private val DAY_SHORT = DateTimeFormatter.ofPattern("d MMM")
private val MONTH = DateTimeFormatter.ofPattern("MMMM yyyy")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SummaryScreen(vm: SummaryViewModel) {
    val period by vm.period.collectAsStateWithLifecycle()
    val summary by vm.summary.collectAsStateWithLifecycle()

    Scaffold(topBar = { TopAppBar(title = { Text("Summary") }) }) { padding ->
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
            val s = summary ?: return@LazyColumn
            item {
                val isCurrent = vm.today() <= s.to
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { vm.move(-1) }) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Earlier") }
                    Text(rangeLabel(period, s.from, s.to), style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
                    IconButton(onClick = { vm.move(1) }, enabled = !isCurrent) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Later") }
                }
            }
            item { PointsCard(s) }
            item { HabitsCard(s, period) }
            item { TasksCard(s) }
        }
    }
}

private fun rangeLabel(period: Period, from: LocalDate, to: LocalDate) = when (period) {
    Period.DAY -> from.format(DAY_LONG)
    Period.WEEK -> "${from.format(DAY_SHORT)} – ${to.format(DAY_SHORT)}"
    Period.MONTH -> from.format(MONTH)
}

private fun percent(rate: Double?) = rate?.let { "${(it * 100).roundToInt()}%" } ?: "–"

@Composable
private fun PointsCard(s: Summary) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text("${s.points} points", style = MaterialTheme.typography.headlineSmall)
            Text("Habits, daily bonuses and tasks together", style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun HabitsCard(s: Summary, period: Period) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Habits", style = MaterialTheme.typography.titleMedium)
            Text("${percent(s.habitRate)} consistent", style = MaterialTheme.typography.headlineSmall)
            Text("${s.done} of ${s.scheduled} planned habit-days done", style = MaterialTheme.typography.bodySmall)
            when (period) {
                Period.WEEK -> WeekBars(s.days)
                Period.MONTH -> MonthGrid(s.days)
                Period.DAY -> Unit
            }
            if (s.habits.isEmpty()) Text("No habits yet.")
            for (h in s.habits) {
                Column {
                    Row {
                        Text(h.habit.name, modifier = Modifier.weight(1f))
                        Text("${h.done}/${h.scheduled}  ${percent(h.rate)}")
                    }
                    LinearProgressIndicator(progress = { (h.rate ?: 0.0).toFloat() }, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp))
                    val extra = if (h.extra > 0) "  ·  +${h.extra} extra day${if (h.extra == 1) "" else "s"}" else ""
                    Text("🔥 ${h.currentStreak} now  ·  best ${h.bestStreak}$extra", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

/** One bar per day: the share of planned habits done. */
@Composable
private fun WeekBars(days: List<DayCell>) {
    Row(Modifier.fillMaxWidth().height(110.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        for (cell in days) {
            Column(Modifier.weight(1f).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.BottomCenter) {
                    val rate = cell.rate
                    Box(
                        Modifier.fillMaxWidth()
                            .fillMaxHeight(if (rate == null) 0.04f else maxOf(rate.toFloat(), 0.04f))
                            .clip(RoundedCornerShape(4.dp))
                            .background(if (rate == null) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.primary)
                    )
                }
                Text(cell.day.dayOfWeek.name.take(1), style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

/** A calendar: Monday first. Darker = more of the planned habits done that day. */
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
                                Modifier.fillMaxSize().clip(RoundedCornerShape(4.dp)).background(
                                    if (rate == null) MaterialTheme.colorScheme.surfaceVariant
                                    else MaterialTheme.colorScheme.primary.copy(alpha = 0.15f + 0.85f * rate.toFloat())
                                )
                            )
                            Text("${cell.day.dayOfMonth}", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(4.dp))
    }
}

@Composable
private fun TasksCard(s: Summary) {
    val t = s.tasks
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Tasks", style = MaterialTheme.typography.titleMedium)
            val onTime = if (t.dueInRange == 0) null else t.dueInRangeOnTime.toDouble() / t.dueInRange
            Text("${percent(onTime)} on time", style = MaterialTheme.typography.headlineSmall)
            Text("${t.dueInRangeOnTime} of ${t.dueInRange} tasks due in this period were done by their due date", style = MaterialTheme.typography.bodySmall)
            Text("Completed: ${t.completed}  (${t.completedOnTime} on time)")
            t.averageDaysLate?.let { Text("Late ones were %.1f days late on average".format(it)) }
            Text("Points from tasks: ${t.points}")
            Text("Open now: ${t.openNow}" + if (t.overdueNow > 0) "  (${t.overdueNow} overdue)" else "")
        }
    }
}
