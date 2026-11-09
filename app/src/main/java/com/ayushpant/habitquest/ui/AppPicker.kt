package com.ayushpant.habitquest.ui

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** An app on the phone that can be opened. */
data class InstalledApp(val label: String, val packageName: String)

private val MEDITATION_WORDS = listOf(
    "medit", "mindful", "headspace", "calm", "insight timer", "balance", "waking up", "breath", "breethe",
    "smiling mind", "petit bambou", "oak", "aura", "ten percent", "prana", "sattva", "simple habit",
)

/** True for apps that look like meditation apps (by name or package), e.g. Medito, Headspace, Calm. */
fun isMeditationApp(app: InstalledApp): Boolean {
    val text = (app.label + " " + app.packageName).lowercase()
    return MEDITATION_WORDS.any { it in text }
}

/** Every app with a launcher icon, sorted by name. Meditation apps come first, since they are the usual app habit. */
suspend fun loadInstalledApps(context: Context): List<InstalledApp> = withContext(Dispatchers.IO) {
    val pm = context.packageManager
    pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0)
        .map { InstalledApp(it.loadLabel(pm).toString(), it.activityInfo.packageName) }
        .filter { it.packageName != context.packageName }
        .distinctBy { it.packageName }
        .sortedWith(compareByDescending<InstalledApp> { isMeditationApp(it) }.thenBy { it.label.lowercase() })
}

fun appLabel(context: Context, packageName: String): String? = runCatching {
    val pm = context.packageManager
    pm.getApplicationLabel(pm.getApplicationInfo(packageName, 0)).toString()
}.getOrNull()

/** Choose the app that an app habit opens and watches. */
@Composable
fun AppPickerDialog(onPick: (InstalledApp) -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    var apps by remember { mutableStateOf<List<InstalledApp>?>(null) }
    var query by remember { mutableStateOf("") }
    LaunchedEffect(Unit) { apps = loadInstalledApps(context) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Choose the app") },
        text = {
            Column {
                OutlinedTextField(value = query, onValueChange = { query = it }, label = { Text("Search") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                val shown = apps.orEmpty().filter { query.isBlank() || it.label.contains(query, ignoreCase = true) }
                if (apps == null) Text("Loading…", modifier = Modifier.padding(top = 12.dp))
                LazyColumn(Modifier.heightIn(max = 360.dp).padding(top = 8.dp)) {
                    items(shown, key = { it.packageName }) { app ->
                        Column(Modifier.fillMaxWidth().clickable { onPick(app) }.padding(vertical = 10.dp)) {
                            Text(app.label, style = MaterialTheme.typography.bodyLarge)
                            Text(app.packageName, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
