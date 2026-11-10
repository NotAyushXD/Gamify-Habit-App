package com.ayushpant.habitquest.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.TextButton
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ayushpant.habitquest.data.RewardsView
import com.ayushpant.habitquest.rewards.CompanionState
import com.ayushpant.habitquest.rewards.CreatureDef

/**
 * The companion and the egg on the Today screen (step 5b). Before the first hatch it shows the
 * egg alone. Tapping opens the Collection.
 */
@Composable
fun CompanionCard(rewards: RewardsView, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val text = rewards.theme.text
    val egg = rewards.egg ?: return
    val companion = rewards.companion
    Card(modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            if (companion != null) {
                CreatureArt(companion.owned.def, companion.stage, companion.state, Modifier.size(72.dp))
            } else {
                EggArt(egg.progress, MaterialTheme.colorScheme.primary, Modifier.width(56.dp).height(70.dp).padding(horizontal = 4.dp))
            }
            Column(Modifier.padding(start = 14.dp).weight(1f)) {
                if (companion != null) {
                    val name = companion.owned.def.name
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(name, style = MaterialTheme.typography.titleMedium)
                        Text(
                            "  ♥ ${companion.owned.bond} · Stage ${companion.stage}", style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    val line = when {
                        companion.welcomeBack -> text.welcomeBack(name)
                        companion.state == CompanionState.ACTIVE -> text.active(name)
                        companion.state == CompanionState.RESTING -> text.resting(name)
                        else -> text.waiting(name)
                    }
                    Text(line, style = MaterialTheme.typography.bodyMedium)
                    Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        EggArt(egg.progress, MaterialTheme.colorScheme.primary, Modifier.width(18.dp).height(22.dp))
                        EggBar(egg.progress, "${text.eggName.replaceFirstChar { it.uppercase() }} ${egg.number} · ${egg.total}/${egg.needed}", Modifier.padding(start = 8.dp))
                    }
                } else {
                    Text("${text.eggName.replaceFirstChar { it.uppercase() }} ${egg.number}", style = MaterialTheme.typography.titleMedium)
                    Text(if (egg.number == 1) text.firstEgg else text.eggGrowing, style = MaterialTheme.typography.bodyMedium)
                    EggBar(egg.progress, "${egg.total}/${egg.needed}", Modifier.padding(top = 8.dp))
                }
            }
        }
    }
}

@Composable
private fun EggBar(progress: Float, label: String, modifier: Modifier = Modifier) {
    Column(modifier) {
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
            trackColor = MaterialTheme.colorScheme.surfaceVariant,
        )
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Medium, modifier = Modifier.padding(top = 2.dp))
    }
}

/** Tiny "Mind 12 · Body 4 · Soul 9" line for the egg. */
@Composable
fun EggGrowthLine(mind: Int, body: Int, soul: Int, modifier: Modifier = Modifier) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("🧠 $mind", style = MaterialTheme.typography.labelSmall)
        Text("💪 $body", style = MaterialTheme.typography.labelSmall)
        Text("✨ $soul", style = MaterialTheme.typography.labelSmall)
    }
}

/**
 * "Your egg hatched! Choose your starter": the three starters, tap one, confirm. Shown on Today and
 * in the Collection while a choice is open; nothing otherwise.
 */
@Composable
fun StarterCard(rewards: RewardsView, onChoose: (CreatureDef) -> Unit, modifier: Modifier = Modifier) {
    if (rewards.starterChoices.isEmpty()) return
    val text = rewards.theme.text
    var confirm by remember { mutableStateOf<CreatureDef?>(null) }
    Card(modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(text.starterTitle, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (def in rewards.starterChoices) {
                    Column(
                        Modifier.weight(1f).clip(RoundedCornerShape(12.dp)).clickable { confirm = def }.padding(vertical = 6.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        CreatureArt(def, 1, CompanionState.ACTIVE, Modifier.size(72.dp))
                        Text(def.name, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                        Text("${def.dimension.emoji} ${def.dimension.label}", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
            Text(text.starterHint, style = MaterialTheme.typography.bodySmall)
        }
    }
    confirm?.let { def ->
        AlertDialog(
            onDismissRequest = { confirm = null },
            title = { Text("Choose ${def.name}?") },
            text = { Text("${def.blurb} It becomes your companion.") },
            confirmButton = { Button(onClick = { confirm = null; onChoose(def) }) { Text("Choose ${def.name}") } },
            dismissButton = { TextButton(onClick = { confirm = null }) { Text("Look again") } },
        )
    }
}
