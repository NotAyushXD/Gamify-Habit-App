package com.ayushpant.habitquest.ui

import android.os.Build
import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ayushpant.habitquest.data.PromiseStatus
import com.ayushpant.habitquest.data.TodayData
import com.ayushpant.habitquest.stats.streakMilestone
import kotlinx.coroutines.launch
import kotlin.math.sin
import kotlin.random.Random

/** What to celebrate. [big] = a milestone, a perfect day, a kept promise or a new level. */
data class Celebration(val id: Long, val title: String, val subtitle: String, val big: Boolean)

private val CHEERS = listOf("Nice!", "Well done!", "That's the way!", "Keep going!", "Yes!", "Another win!")

/**
 * Compares Today before and after a change, and returns what to celebrate when something was
 * just finished (null otherwise). The most special moment wins.
 */
fun celebrationFor(before: TodayData, after: TodayData): Celebration? {
    if (before.day != after.day) return null
    val wasDone = before.habits.filter { it.isDone }.map { it.habit.id }.toSet()
    val habit = after.habits.firstOrNull { it.isDone && it.habit.id !in wasDone }
    val taskWasDone = before.tasks.filter { it.isDone }.map { it.task.id }.toSet()
    val task = after.tasks.firstOrNull { it.isDone && it.task.id !in taskWasDone }
    if (habit == null && task == null) return null

    val points = habit?.points ?: task!!.points
    val coins = after.coins - before.coins
    val gain = "+$points points" + if (coins > 0) " · 🪙 +$coins" else ""
    val id = System.nanoTime()
    val level = after.level
    val milestone = habit?.let { streakMilestone(it.streakDays) }
    val hatched = after.rewards.owned.drop(before.rewards.owned.size).firstOrNull()
    // An egg hatched with no new creature: the collection was already complete, coins instead.
    // (No egg before = the very first egg just appeared; that is not a hatch.)
    val eggBefore = before.rewards.egg?.number
    val eggDone = eggBefore != null && (after.rewards.egg?.number ?: 0) > eggBefore
    return when {
        hatched != null -> Celebration(id, "🥚 It hatched!", after.rewards.theme.text.hatched(hatched.def.name, hatched.def.dimension) + " $gain", big = true)
        eggDone -> Celebration(id, "🎉 It hatched!", "${after.rewards.theme.text.fullCollection} $gain", big = true)
        level.number > before.level.number -> Celebration(id, "Level ${level.number}!", "You are now: ${level.title}", big = true)
        milestone != null -> Celebration(id, "🔥 $milestone-day streak!", "${habit.habit.name}, $milestone days in a row. Incredible.", big = true)
        after.dailyBonus > 0 && before.dailyBonus == 0 -> Celebration(id, "🌟 Perfect day!", "Everything planned is done. +${after.dailyBonus} bonus", big = true)
        habit?.promise?.status == PromiseStatus.KEPT -> Celebration(id, "🤝 Promise kept!", "You did what you said you would. $gain", big = true)
        task != null && task.daysLate <= 0 -> Celebration(id, "On time! ✅", "${task.task.name} · $gain", big = false)
        else -> Celebration(id, CHEERS[Random.nextInt(CHEERS.size)], gain, big = false)
    }
}

private data class Particle(val x: Float, val speed: Float, val drift: Float, val size: Float, val spin: Float, val color: Color, val delay: Float)

private val CONFETTI_COLORS = listOf(
    Color(0xFF30D158), Color(0xFF0A84FF), Color(0xFFFF9F0A), Color(0xFFFF453A),
    Color(0xFFBF5AF2), Color(0xFFFFD60A), Color(0xFF40C8E0),
)

/** Confetti falling over the screen and a card with the message. It never blocks taps. */
@Composable
fun CelebrationOverlay(c: Celebration, onDone: () -> Unit) {
    val view = LocalView.current
    val particles = remember(c.id) {
        List(if (c.big) 120 else 50) {
            Particle(
                x = Random.nextFloat(), speed = 0.7f + Random.nextFloat() * 0.8f, drift = (Random.nextFloat() - 0.5f) * 0.15f,
                size = 6f + Random.nextFloat() * 10f, spin = Random.nextFloat() * 720f,
                color = CONFETTI_COLORS[Random.nextInt(CONFETTI_COLORS.size)], delay = Random.nextFloat() * 0.25f,
            )
        }
    }
    val fall = remember(c.id) { Animatable(0f) }
    val pop = remember(c.id) { Animatable(0.5f) }
    LaunchedEffect(c.id) {
        view.performHapticFeedback(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) HapticFeedbackConstants.CONFIRM else HapticFeedbackConstants.LONG_PRESS
        )
        launch { pop.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy)) }
        fall.animateTo(1f, tween(durationMillis = if (c.big) 2800 else 1800, easing = LinearEasing))
        onDone()
    }
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            for (p in particles) {
                val t = ((fall.value - p.delay) / (1f - p.delay)).coerceIn(0f, 1f)
                if (t <= 0f) continue
                val y = -0.05f * size.height + t * p.speed * size.height * 1.1f
                val x = (p.x + p.drift * sin(t * 12f)) * size.width
                rotate(p.spin * t, pivot = Offset(x, y)) {
                    drawRect(p.color.copy(alpha = 1f - t * 0.6f), topLeft = Offset(x, y), size = Size(p.size, p.size * 0.6f))
                }
            }
        }
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.primaryContainer,
            shadowElevation = 12.dp,
            modifier = Modifier.scale(pop.value).alpha((1.6f - fall.value * 1.2f).coerceIn(0f, 1f)),
        ) {
            Column(Modifier.padding(horizontal = 28.dp, vertical = 20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(c.title, fontSize = if (c.big) 30.sp else 24.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                Text(c.subtitle, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 4.dp))
            }
        }
    }
}
