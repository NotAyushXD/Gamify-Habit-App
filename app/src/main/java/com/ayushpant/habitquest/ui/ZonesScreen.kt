package com.ayushpant.habitquest.ui

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.location.Geocoder
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ayushpant.habitquest.data.HabitRepository
import com.ayushpant.habitquest.data.Zone
import com.ayushpant.habitquest.detect.Geofences
import com.ayushpant.habitquest.scoring.ScoringConfig
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

/** Zones (spec 9): the places for zone habits, e.g. the gym. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ZonesScreen(repo: HabitRepository, onBack: () -> Unit) {
    val zones by repo.observeZones().collectAsStateWithLifecycle(initialValue = emptyList())
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var editing by remember { mutableStateOf<Zone?>(null) }
    var adding by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<Zone?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Zones") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") } },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { adding = true }) { Icon(Icons.Default.Add, contentDescription = "Add zone") }
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 88.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Text(
                    "A zone is a place like your gym. Stay there 5 minutes and Habit Quest asks if you're doing the habit; " +
                        "leave and it asks you to confirm. Driving past does nothing.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            if (zones.isEmpty()) item { Text("No zones yet. Tap + to add one, ideally while you are there.", style = MaterialTheme.typography.bodyLarge) }
            items(zones, key = { it.id }) { z ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Text("📍 ${z.name}", style = MaterialTheme.typography.titleMedium)
                        Text("Radius ${z.radiusM} m · %.5f, %.5f".format(z.latitude, z.longitude), style = MaterialTheme.typography.bodySmall)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                            OutlinedButton(onClick = { openMap(context, z.latitude, z.longitude, z.name) }) { Text("Map") }
                            OutlinedButton(onClick = { editing = z }) { Text("Edit") }
                            TextButton(onClick = { deleting = z }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
                        }
                    }
                }
            }
        }
    }

    if (adding || editing != null) {
        ZoneEditorDialog(
            initial = editing,
            onSave = { z -> scope.launch { repo.saveZone(z) }; adding = false; editing = null },
            onDismiss = { adding = false; editing = null },
        )
    }
    deleting?.let { z ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("Delete \"${z.name}\"?") },
            text = { Text("Habits at this place become manual. Their history and points stay.") },
            confirmButton = { TextButton(onClick = { scope.launch { repo.deleteZone(z.id) }; deleting = null }) { Text("Delete") } },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Cancel") } },
        )
    }
}

fun openMap(context: Context, lat: Double, lon: Double, label: String) {
    val uri = "geo:$lat,$lon?q=$lat,$lon(${android.net.Uri.encode(label)})".toUri()
    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, uri)) }
}

/**
 * Add or change a zone: a name, the place (where you are now, or an address), and the radius.
 * The radius is 150 m or more: geofences are not precise enough below that (spec 1.1).
 */
@SuppressLint("MissingPermission") // asked right before
@Composable
fun ZoneEditorDialog(initial: Zone?, onSave: (Zone) -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val minRadius = ScoringConfig.DEFAULT.minZoneRadiusMeters
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var lat by remember { mutableStateOf(initial?.latitude) }
    var lon by remember { mutableStateOf(initial?.longitude) }
    var radius by remember { mutableStateOf((initial?.radiusM ?: 200).toFloat()) }
    var address by remember { mutableStateOf("") }
    var message by remember { mutableStateOf<String?>(null) }

    fun useCurrentLocation() {
        message = "Finding where you are…"
        LocationServices.getFusedLocationProviderClient(context)
            .getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
            .addOnSuccessListener { loc ->
                if (loc == null) {
                    message = "Could not get your location. Is location switched on?"
                } else {
                    lat = loc.latitude
                    lon = loc.longitude
                    message = "✓ Set to where you are (±${loc.accuracy.roundToInt()} m)"
                }
            }
            .addOnFailureListener { message = "Could not get your location: ${it.message}" }
    }

    val askLocation = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { granted ->
        if (granted[Manifest.permission.ACCESS_FINE_LOCATION] == true) useCurrentLocation() else message = "Location permission is needed for this."
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "New zone" else "Edit zone") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Name, e.g. Gym") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Button(
                    onClick = {
                        if (Geofences.hasForegroundLocation(context)) useCurrentLocation()
                        else askLocation.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("📍 Use where I am now") }
                Text("or search an address:", style = MaterialTheme.typography.bodySmall)
                Row {
                    OutlinedTextField(value = address, onValueChange = { address = it }, label = { Text("Address") }, singleLine = true, modifier = Modifier.weight(1f))
                    TextButton(onClick = {
                        scope.launch {
                            message = "Searching…"
                            val found = withContext(Dispatchers.IO) {
                                @Suppress("DEPRECATION")
                                runCatching { Geocoder(context).getFromLocationName(address, 1)?.firstOrNull() }.getOrNull()
                            }
                            if (found == null) {
                                message = "No place found for that address."
                            } else {
                                lat = found.latitude
                                lon = found.longitude
                                message = "✓ ${found.getAddressLine(0) ?: "Found"}"
                            }
                        }
                    }, enabled = address.isNotBlank()) { Text("Search") }
                }
                message?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                if (lat != null && lon != null) {
                    OutlinedButton(onClick = { openMap(context, lat!!, lon!!, name.ifBlank { "Zone" }) }) { Text("Check on map") }
                }
                Text("Radius: ${radius.roundToInt()} m", style = MaterialTheme.typography.titleSmall)
                Slider(value = radius, onValueChange = { radius = (it / 25).roundToInt() * 25f }, valueRange = minRadius.toFloat()..500f)
                Text("Bigger is more reliable, smaller is more exact. $minRadius m is the minimum.", style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = {
            TextButton(
                enabled = name.isNotBlank() && lat != null && lon != null,
                onClick = {
                    onSave((initial ?: Zone(name = "", latitude = 0.0, longitude = 0.0, radiusM = minRadius)).copy(
                        name = name.trim(), latitude = lat!!, longitude = lon!!, radiusM = radius.roundToInt(),
                    ))
                },
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
