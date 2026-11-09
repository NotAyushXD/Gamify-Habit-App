package com.ayushpant.habitquest.detect

import android.Manifest
import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.util.Log
import androidx.core.content.ContextCompat
import com.ayushpant.habitquest.HabitQuestApp
import com.ayushpant.habitquest.data.LOG_TAG
import com.ayushpant.habitquest.reminders.PopupPresenter
import com.ayushpant.habitquest.reminders.ReminderScheduler
import com.ayushpant.habitquest.scoring.ScoringConfig
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingEvent
import com.google.android.gms.location.GeofencingRequest
import com.google.android.gms.location.LocationServices
import com.google.android.gms.tasks.Task
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Zone habits (spec 4, 5.3): the zones are registered with Google Play Services' Geofencing API,
 * so Play Services watches them, not this app. It wakes [GeofenceReceiver] when the user has stayed
 * in a zone for 5 minutes (dwell) and when they leave it.
 *
 * Geofences are lost after a restart, an app update or when location is switched off, so [sync]
 * runs on app start, after every change, and from the BootReceiver.
 */
object Geofences {
    fun hasForegroundLocation(context: Context) =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

    /** "Allow all the time": needed for zones while the app is closed (spec 11). */
    fun hasBackgroundLocation(context: Context) =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_BACKGROUND_LOCATION) == PackageManager.PERMISSION_GRANTED

    private fun pendingIntent(context: Context): PendingIntent = PendingIntent.getBroadcast(
        context, 1, Intent(context, GeofenceReceiver::class.java),
        // Play Services fills in the event, so the intent must be mutable.
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
    )

    /** Registers the zones of the active zone habits again (and removes old ones). */
    @SuppressLint("MissingPermission") // checked just below
    suspend fun sync(context: Context, cfg: ScoringConfig = ScoringConfig.DEFAULT) {
        val app = context.applicationContext as HabitQuestApp
        val client = LocationServices.getGeofencingClient(context)
        val zones = app.repository.watchedZones()
        runCatching { client.removeGeofences(pendingIntent(context)).await() }
        if (zones.isEmpty()) return
        if (!hasForegroundLocation(context) || !hasBackgroundLocation(context)) {
            Log.i(LOG_TAG, "Zones not watched: location \"all the time\" is not allowed")
            return
        }
        val fences = zones.map {
            Geofence.Builder()
                .setRequestId(it.id.toString())
                .setCircularRegion(it.latitude, it.longitude, it.radiusM.toFloat())
                .setExpirationDuration(Geofence.NEVER_EXPIRE)
                .setTransitionTypes(Geofence.GEOFENCE_TRANSITION_DWELL or Geofence.GEOFENCE_TRANSITION_EXIT)
                .setLoiteringDelay((cfg.zoneDwellMinutes * 60_000).toInt())
                .setNotificationResponsiveness(60_000) // a minute late is fine, and saves battery
                .build()
        }
        val request = GeofencingRequest.Builder()
            .setInitialTrigger(GeofencingRequest.INITIAL_TRIGGER_DWELL) // already there when registering
            .addGeofences(fences)
            .build()
        try {
            client.addGeofences(request, pendingIntent(context)).await()
            Log.i(LOG_TAG, "Watching ${fences.size} zone(s)")
        } catch (e: Exception) {
            // e.g. location switched off on the phone: next sync tries again.
            Log.w(LOG_TAG, "Could not register zones", e)
        }
    }

    private suspend fun <T> Task<T>.await(): T = suspendCancellableCoroutine { cont ->
        addOnSuccessListener { cont.resume(it) }
        addOnFailureListener { cont.resumeWithException(it) }
    }
}

/** Play Services: the user stayed in a zone (dwell) or left it. */
class GeofenceReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val event = GeofencingEvent.fromIntent(intent) ?: return
        if (event.hasError()) {
            Log.w(LOG_TAG, "Geofence error ${event.errorCode}")
            return
        }
        val zoneIds = event.triggeringGeofences.orEmpty().mapNotNull { it.requestId.toLongOrNull() }
        val transition = event.geofenceTransition
        val now = System.currentTimeMillis()
        val pending = goAsync()
        CoroutineScope(Dispatchers.Default).launch {
            try {
                val app = context.applicationContext as HabitQuestApp
                for (zoneId in zoneIds) {
                    when (transition) {
                        Geofence.GEOFENCE_TRANSITION_DWELL -> {
                            val habits = app.repository.onZoneDwell(zoneId)
                            // Spec 6.2: not in quiet hours, during a call or navigation.
                            val wait = app.reminders.inQuietHoursNow() || ReminderScheduler.inCall(context) || HabitListenerService.isNavigating()
                            if (habits.isNotEmpty() && !wait) PopupPresenter.showZoneCheck(context, habits.map { it.id }, now)
                        }
                        Geofence.GEOFENCE_TRANSITION_EXIT -> {
                            for (id in app.repository.onZoneExit(zoneId, now)) PopupPresenter.showConfirmation(context, id)
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(LOG_TAG, "Geofence handling failed", e)
            } finally {
                pending.finish()
            }
        }
    }
}
