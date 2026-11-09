package com.ayushpant.habitquest.ui

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.edit
import com.ayushpant.habitquest.scoring.ScoringConfig
import kotlinx.coroutines.launch

/** Whether the welcome tour was seen. A per-phone convenience, so it lives in SharedPreferences. */
object Welcome {
    private const val PREFS = "welcome"
    private const val KEY_SEEN = "seen"

    fun seen(context: Context): Boolean = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_SEEN, false)

    fun markSeen(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit { putBoolean(KEY_SEEN, true) }
}

private data class Page(val emoji: String, val title: String, val text: String, val points: List<Pair<String, String>> = emptyList())

private val PAGES = ScoringConfig.DEFAULT.let { cfg ->
    listOf(
        Page(
            "🌱", "Welcome to Habit Quest",
            "Build good habits one small win at a time. Every habit you do gives points, grows your streak and levels you up.",
        ),
        Page(
            "⭐", "How points work", "Simple and fair: every habit is worth the same.",
            listOf(
                "🕖" to "Do it in its planned time for full points",
                "🔥" to "Days in a row grow your streak: points × up to 2",
                "🌟" to "Do all of today's habits: +${cfg.dailyBonus} bonus",
                "❄️" to "Missed a day? One free miss a week keeps your streak",
            ),
        ),
        Page(
            "🧠", "Mind · Body · Soul", "Each habit feeds one or more. Meditating feeds Mind and Soul, the gym feeds Body and Mind.",
            listOf("🧠" to "Mind: learning, focus, calm", "💪" to "Body: moving, sleep, food", "✨" to "Soul: gratitude, people, joy"),
        ),
        Page(
            "🔔", "Friendly reminders", "Habit Quest reminds you at your time, and it can notice when you use your meditation app or go to the gym.",
            listOf(
                "✓" to "One tap to mark a habit done",
                "🤝" to "Can't today? Promise a time for tomorrow",
                "⚙️" to "Set up reminders and detection later in Settings",
            ),
        ),
        Page("🚀", "Ready?", "Start with one habit you really want. You can add more any time."),
    )
}

/** The first-run tour: five short pages, swipe or tap Next. [onFinish] gets true to add a habit now. */
@Composable
fun WelcomeScreen(onFinish: (addHabit: Boolean) -> Unit) {
    val pager = rememberPagerState { PAGES.size }
    val scope = rememberCoroutineScope()
    val last = pager.currentPage == PAGES.lastIndex
    Column(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).statusBarsPadding().navigationBarsPadding().padding(24.dp),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            if (!last) TextButton(onClick = { onFinish(false) }) { Text("Skip") }
        }
        HorizontalPager(state = pager, modifier = Modifier.weight(1f)) { i -> PageContent(PAGES[i]) }
        // Dots: where you are in the tour.
        Row(Modifier.fillMaxWidth().padding(vertical = 16.dp), horizontalArrangement = Arrangement.Center) {
            repeat(PAGES.size) { i ->
                Box(
                    Modifier.padding(horizontal = 4.dp).height(8.dp).width(if (i == pager.currentPage) 24.dp else 8.dp).clip(CircleShape)
                        .background(if (i == pager.currentPage) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant)
                )
            }
        }
        if (last) {
            Button(onClick = { onFinish(true) }, modifier = Modifier.fillMaxWidth().height(56.dp)) {
                Text("Add my first habit", style = MaterialTheme.typography.titleMedium)
            }
            OutlinedButton(onClick = { onFinish(false) }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp).height(52.dp)) { Text("Look around first") }
        } else {
            Button(onClick = { scope.launch { pager.animateScrollToPage(pager.currentPage + 1) } }, modifier = Modifier.fillMaxWidth().height(56.dp)) {
                Text("Next", style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}

@Composable
private fun PageContent(page: Page) {
    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Box(
            Modifier.size(120.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) { Text(page.emoji, fontSize = 56.sp) }
        Text(page.title, style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 24.dp))
        Text(
            page.text, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp),
        )
        if (page.points.isNotEmpty()) {
            Card(Modifier.fillMaxWidth().padding(top = 24.dp), shape = RoundedCornerShape(20.dp)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    for ((icon, line) in page.points) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(icon, fontSize = 22.sp, modifier = Modifier.width(36.dp))
                            Text(line, style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
            }
        }
    }
}
