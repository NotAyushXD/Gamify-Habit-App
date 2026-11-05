package com.ayushpant.habitquest.reminders

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
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
import com.ayushpant.habitquest.ui.HabitQuestTheme

/**
 * Shows a popup inside the app. Used when a reminder arrives as a notification (because
 * "Display over other apps" is off) and the user taps it.
 */
class PopupActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val logId = intent.getLongExtra(EXTRA_LOG_ID, Long.MIN_VALUE)
        val reminders = (application as HabitQuestApp).reminders
        setContent {
            HabitQuestTheme {
                var model by remember { mutableStateOf<PopupModel?>(null) }
                val scope = rememberCoroutineScope()
                LaunchedEffect(logId) {
                    model = reminders.popupFor(logId)
                    if (model == null) finish()
                }
                Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.4f)), contentAlignment = Alignment.Center) {
                    model?.let {
                        PopupCard(
                            model = it,
                            reminders = reminders,
                            scope = scope,
                            openApp = { startActivity(Intent(this@PopupActivity, MainActivity::class.java)) },
                            onFinished = { finish() },
                        )
                    }
                }
            }
        }
    }

    companion object {
        const val EXTRA_LOG_ID = "log_id"
    }
}
