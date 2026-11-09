package com.ayushpant.habitquest.reminders

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.ayushpant.habitquest.HabitQuestApp
import com.ayushpant.habitquest.MainActivity
import com.ayushpant.habitquest.promise.PromiseScreen
import com.ayushpant.habitquest.ui.HabitQuestTheme
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * Shows a popup inside the app:
 * - a reminder that arrived as a notification (because "Display over other apps" is off), or
 * - the promise screen ("Can't today"), which needs the keyboard and the microphone.
 */
class PopupActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val logId = intent.getLongExtra(EXTRA_LOG_ID, Long.MIN_VALUE)
        val promiseHabit = intent.getLongExtra(EXTRA_PROMISE_HABIT, -1L)
        val sessionId = intent.getLongExtra(EXTRA_SESSION_ID, -1L)
        val app = application as HabitQuestApp
        val reminders = app.reminders
        setContent {
            HabitQuestTheme {
                var model by remember { mutableStateOf<PopupModel?>(null) }
                var promise by remember { mutableStateOf<PromiseContext?>(null) }
                var promising by remember { mutableStateOf(false) } // set at once on "Can't today", before loading
                val scope = rememberCoroutineScope()
                LaunchedEffect(logId, promiseHabit) {
                    if (promiseHabit >= 0) {
                        promise = reminders.promiseContext(logId, promiseHabit)
                        if (promise == null) finish()
                    } else if (sessionId >= 0) {
                        model = reminders.confirmationFor(sessionId)
                        if (model == null) finish()
                    } else {
                        model = reminders.popupFor(logId)
                        if (model == null) finish()
                    }
                }
                Box(
                    Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.4f)).systemBarsPadding().imePadding(),
                    contentAlignment = Alignment.Center,
                ) {
                    val p = promise
                    val m = model
                    when {
                        p != null -> PromiseScreen(
                            ctx = p,
                            today = app.repository.today(),
                            onPromise = { dueAt, method, text ->
                                scope.launch {
                                    reminders.promise(p.logId, p.habitId, dueAt, method, text)
                                    val day = if (dueAt.toLocalDate() == LocalDate.now().plusDays(1)) "tomorrow" else dueAt.format(DateTimeFormatter.ofPattern("EEE d MMM"))
                                    Toast.makeText(
                                        this@PopupActivity,
                                        "Promise saved 🤝 See you $day at ${dueAt.format(DateTimeFormatter.ofPattern("HH:mm"))}",
                                        Toast.LENGTH_LONG,
                                    ).show()
                                    finish()
                                }
                            },
                            onBack = {
                                scope.launch {
                                    if (p.logId >= 0) reminders.close(p.logId)
                                    finish()
                                }
                            },
                        )
                        m != null -> PopupCard(
                            model = m,
                            reminders = reminders,
                            scope = scope,
                            openApp = { startActivity(Intent(this@PopupActivity, MainActivity::class.java)) },
                            // Same window: switch to the promise screen.
                            openPromise = { id, habitId ->
                                promising = true
                                scope.launch { promise = reminders.promiseContext(id, habitId) ?: run { finish(); null } }
                            },
                            onFinished = { if (!promising) finish() },
                        )
                    }
                }
            }
        }
    }

    companion object {
        const val EXTRA_LOG_ID = "log_id"
        const val EXTRA_SESSION_ID = "session_id"
        private const val EXTRA_PROMISE_HABIT = "promise_habit"

        fun promiseIntent(context: Context, logId: Long, habitId: Long): Intent =
            Intent(context, PopupActivity::class.java)
                .putExtra(EXTRA_LOG_ID, logId)
                .putExtra(EXTRA_PROMISE_HABIT, habitId)
    }
}
