package com.ayushpant.habitquest.detect

import android.app.Notification
import android.content.ComponentName
import android.content.Context
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Handler
import android.os.Looper
import android.service.notification.NotificationListenerService
import android.util.Log
import androidx.core.app.NotificationManagerCompat
import com.ayushpant.habitquest.HabitQuestApp
import com.ayushpant.habitquest.data.LOG_TAG
import com.ayushpant.habitquest.data.SessionSource
import com.ayushpant.habitquest.data.SignalKind
import com.ayushpant.habitquest.reminders.PopupPresenter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Audio signal for app habits (spec 4, 5.1): Android keeps this service connected once the user
 * allows "Notification access". It never reads notification content; it only uses the access to
 * watch the media sessions (play / pause) of the apps linked to habits, e.g. Medito.
 *
 * When a linked app stops playing for a minute, the session is suggested and a confirmation popup
 * asks the user. Nothing is confirmed without the user's tap.
 */
class HabitListenerService : NotificationListenerService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val handler = Handler(Looper.getMainLooper())
    private val tracker = AudioTracker()
    private val order = Mutex()
    private val callbacks = mutableMapOf<String, Pair<MediaController, MediaController.Callback>>()
    private var watched: Set<String> = emptySet()
    private var sessions: MediaSessionManager? = null

    private val app get() = application as HabitQuestApp
    private val component get() = ComponentName(this, HabitListenerService::class.java)

    private val sessionsChanged = MediaSessionManager.OnActiveSessionsChangedListener { controllers ->
        onControllers(controllers.orEmpty())
    }

    private val check = Runnable { finishSessions() }

    override fun onListenerConnected() {
        instance = this
        val msm = getSystemService(MediaSessionManager::class.java)
        sessions = msm
        scope.launch {
            watched = app.repository.watchedPackages()
            try {
                msm.addOnActiveSessionsChangedListener(sessionsChanged, component)
                onControllers(msm.getActiveSessions(component))
            } catch (e: SecurityException) {
                Log.w(LOG_TAG, "No access to media sessions", e)
            }
        }
    }

    override fun onListenerDisconnected() {
        instance = null
        sessions?.removeOnActiveSessionsChangedListener(sessionsChanged)
        callbacks.values.forEach { (controller, cb) -> controller.unregisterCallback(cb) }
        callbacks.clear()
        // Ask Android to connect us again (it can disconnect listeners, e.g. after an update).
        requestRebind(component)
    }

    override fun onDestroy() {
        instance = null
        handler.removeCallbacks(check)
        scope.cancel()
        super.onDestroy()
    }

    /** The set of playing apps changed: follow the linked ones. */
    private fun onControllers(controllers: List<MediaController>) {
        scope.launch {
            watched = app.repository.watchedPackages() // a habit may have been added or changed
            val current = controllers.filter { it.packageName in watched }.associateBy { it.packageName }
            // Sessions that disappeared count as stopped.
            for (pkg in callbacks.keys - current.keys) {
                callbacks.remove(pkg)?.let { (c, cb) -> c.unregisterCallback(cb) }
                signal(pkg, playing = false, why = "session ended")
            }
            for ((pkg, controller) in current) {
                if (pkg in callbacks) continue
                val cb = object : MediaController.Callback() {
                    override fun onPlaybackStateChanged(state: PlaybackState?) {
                        signal(pkg, state?.state == PlaybackState.STATE_PLAYING, why = "state ${state?.state}")
                    }
                }
                controller.registerCallback(cb, handler)
                callbacks[pkg] = controller to cb
                signal(pkg, controller.playbackState?.state == PlaybackState.STATE_PLAYING, why = "session found")
            }
        }
    }

    private fun signal(pkg: String, playing: Boolean, why: String) {
        val now = System.currentTimeMillis()
        scope.launch {
            // One at a time and in order (the lock is first come, first served).
            order.withLock {
                // Spec 12.1: write each signal to signal_log before using it.
                app.repository.logSignal(SignalKind.MEDIA, "$pkg playing=$playing ($why)")
                tracker.onState(pkg, playing, now)
            }
            scheduleCheck()
        }
    }

    private fun scheduleCheck() {
        handler.removeCallbacks(check)
        val at = tracker.nextCheck() ?: return
        handler.postDelayed(check, (at - System.currentTimeMillis()).coerceAtLeast(1_000))
    }

    private fun finishSessions() {
        scope.launch {
            val done = order.withLock { tracker.finished(System.currentTimeMillis()) }
            for (d in done) {
                val id = app.repository.suggestDetected(d.packageName, d.start, d.end, d.evidence, SessionSource.LIVE)
                if (id != null) PopupPresenter.showConfirmation(this@HabitListenerService, id)
            }
            scheduleCheck()
        }
    }

    /** Spec 6.2: no popup during navigation. A navigation app shows an ongoing "navigation" notification. */
    private fun navigating(): Boolean =
        runCatching { activeNotifications.orEmpty().any { it.notification.category == Notification.CATEGORY_NAVIGATION } }.getOrDefault(false)

    companion object {
        @Volatile private var instance: HabitListenerService? = null

        /** True when the user has allowed "Notification access" for Habit Quest. */
        fun isAllowed(context: Context): Boolean =
            context.packageName in NotificationManagerCompat.getEnabledListenerPackages(context)

        /** True while a navigation app is guiding the user (only known with notification access). */
        fun isNavigating(): Boolean = instance?.navigating() ?: false
    }
}
