package com.ayushpant.habitquest.reminders

import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.os.Build
import android.util.Log
import android.view.Gravity
import android.view.WindowManager
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.ComposeView
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.ayushpant.habitquest.HabitQuestApp
import com.ayushpant.habitquest.MainActivity
import com.ayushpant.habitquest.R
import com.ayushpant.habitquest.data.LOG_TAG
import com.ayushpant.habitquest.ui.HabitQuestTheme
import kotlinx.coroutines.launch

/**
 * Draws popups over other apps ("Display over other apps", spec 6). It is a foreground service so
 * Android keeps it alive until the user answers. If the screen is locked, the popup appears after
 * unlock (spec 6.2). Popups queue up and show one at a time.
 *
 * Compose normally lives in an Activity; here the service provides the lifecycle and saved state
 * that Compose needs.
 */
class PopupService : LifecycleService(), SavedStateRegistryOwner {
    private val savedState = SavedStateRegistryController.create(this)
    override val savedStateRegistry: SavedStateRegistry get() = savedState.savedStateRegistry

    private val queue = ArrayDeque<PopupModel>()
    private val current = mutableStateOf<PopupModel?>(null)
    private var view: ComposeView? = null

    override fun onCreate() {
        savedState.performAttach()
        savedState.performRestore(null)
        super.onCreate()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        val notification = NotificationCompat.Builder(this, PopupPresenter.CHANNEL_SERVICE)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("Reminder on screen")
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .build()
        ServiceCompat.startForeground(
            this, NOTIFICATION_ID, notification,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0,
        )
        val ids = intent?.getLongArrayExtra(EXTRA_LOG_IDS) ?: longArrayOf()
        lifecycleScope.launch {
            val reminders = (application as HabitQuestApp).reminders
            for (id in ids) reminders.popupFor(id)?.let { queue.addLast(it) }
            showNext()
        }
        return START_NOT_STICKY
    }

    private fun showNext() {
        if (current.value != null) return
        val next = queue.removeFirstOrNull()
        if (next == null) {
            removeView()
            stopSelf()
            return
        }
        current.value = next
        if (view == null) addView()
    }

    private fun addView() {
        val reminders = (application as HabitQuestApp).reminders
        val compose = ComposeView(this).apply {
            setViewTreeLifecycleOwner(this@PopupService)
            setViewTreeSavedStateRegistryOwner(this@PopupService)
            setContent {
                HabitQuestTheme {
                    current.value?.let { model ->
                        PopupCard(
                            model = model,
                            reminders = reminders,
                            scope = lifecycleScope,
                            openApp = ::openApp,
                            onFinished = {
                                current.value = null
                                showNext()
                            },
                        )
                    }
                }
            }
        }
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            // The rest of the screen keeps working; the popup does not take the keyboard.
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT,
        ).apply { gravity = Gravity.CENTER }
        try {
            getSystemService(WindowManager::class.java).addView(compose, params)
            view = compose
        } catch (e: Exception) {
            Log.e(LOG_TAG, "Could not show popup over other apps", e)
            stopSelf()
        }
    }

    private fun removeView() {
        view?.let { runCatching { getSystemService(WindowManager::class.java).removeView(it) } }
        view = null
    }

    private fun openApp() {
        startActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    override fun onDestroy() {
        removeView()
        super.onDestroy()
    }

    companion object {
        private const val EXTRA_LOG_IDS = "log_ids"
        private const val NOTIFICATION_ID = 1001

        fun start(context: Context, logIds: LongArray) {
            context.startForegroundService(Intent(context, PopupService::class.java).putExtra(EXTRA_LOG_IDS, logIds))
        }
    }
}
