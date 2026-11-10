package com.ayushpant.habitquest.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ayushpant.habitquest.balance.Dimension
import com.ayushpant.habitquest.data.HabitRepository
import com.ayushpant.habitquest.data.OwnedView
import com.ayushpant.habitquest.data.RewardsView
import com.ayushpant.habitquest.rewards.CompanionState
import com.ayushpant.habitquest.rewards.CreatureDef
import com.ayushpant.habitquest.rewards.companionStage
import kotlinx.coroutines.launch
import java.time.format.DateTimeFormatter

private val HATCH_DAY = DateTimeFormatter.ofPattern("d MMM")

/** All creatures of the theme (step 5b, 18 in the first theme): the ones hatched, grey shapes for the rest, and the egg. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CollectionScreen(repo: HabitRepository, onBack: () -> Unit, onSpar: () -> Unit = {}) {
    val data by repo.observeToday(repo.today()).collectAsStateWithLifecycle(initialValue = null)
    val scope = rememberCoroutineScope()
    val rewards = data?.rewards ?: RewardsView()
    val theme = rewards.theme
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(theme.text.collectionName) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") } },
            )
        },
    ) { inner ->
        LazyColumn(Modifier.fillMaxSize().padding(inner), contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (rewards.starterChoices.isNotEmpty()) item { StarterCard(rewards, onChoose = { def -> scope.launch { repo.chooseStarter(def.id) } }) }
            item { EggCard(rewards) }
            if (rewards.owned.isNotEmpty()) item {
                Button(onClick = onSpar, modifier = Modifier.fillMaxWidth()) { Text("⚔️ Spar with your creatures") }
            }
            item {
                Text(
                    "${rewards.owned.size} of ${theme.creatures.size} hatched. Tap a creature to make it your ${theme.text.companionName}.",
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            for (dim in Dimension.entries) {
                item {
                    Text("${dim.emoji} ${dim.label}", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 4.dp))
                }
                // Rows of 3, so the cells keep a readable size whatever the theme's count.
                for (row in theme.creatures.filter { it.dimension == dim }.chunked(3)) item {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        for (def in row) {
                            val owned = rewards.owned.firstOrNull { it.def.id == def.id }
                            CreatureCell(def, owned, theme.text.notYet, theme.text.chooseCompanion) {
                                owned?.let { o -> scope.launch { repo.setCompanion(o.ownedId) } }
                            }
                        }
                        repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
        }
    }
}

@Composable
private fun EggCard(rewards: RewardsView) {
    val egg = rewards.egg ?: return
    val text = rewards.theme.text
    Card(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            EggArt(egg.progress, MaterialTheme.colorScheme.primary, Modifier.width(56.dp).height(72.dp))
            Column(Modifier.padding(start = 16.dp)) {
                Text("${text.eggName.replaceFirstChar { it.uppercase() }} ${egg.number} · ${egg.total}/${egg.needed}", style = MaterialTheme.typography.titleMedium)
                Text(if (egg.number == 1) text.firstEgg else text.eggGrowing, style = MaterialTheme.typography.bodyMedium)
                EggGrowthLine(egg.mind, egg.body, egg.soul, Modifier.padding(top = 6.dp))
                Text("The creature follows the side that grew it most.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun RowScope.CreatureCell(def: CreatureDef, owned: OwnedView?, notYet: String, choose: String, onChoose: () -> Unit) {
    Card(Modifier.weight(1f)) {
        Column(Modifier.fillMaxWidth().padding(vertical = 10.dp, horizontal = 4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            CreatureArt(def, owned?.let { companionStage(it.bond) } ?: 1, CompanionState.WAITING, Modifier.size(56.dp), grey = owned == null)
            Text(if (owned != null) def.name else "?", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 6.dp))
            Text(
                owned?.let { "${it.hatchDay.format(HATCH_DAY)} · ♥ ${it.bond}" } ?: notYet,
                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center,
            )
            when {
                owned == null -> Unit
                owned.companion -> Text("✓ with you", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 4.dp))
                else -> TextButton(onClick = onChoose, contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 6.dp, vertical = 0.dp)) { Text(choose, style = MaterialTheme.typography.labelSmall) }
            }
        }
    }
}
