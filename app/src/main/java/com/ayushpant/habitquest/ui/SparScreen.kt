package com.ayushpant.habitquest.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ayushpant.habitquest.data.HabitRepository
import com.ayushpant.habitquest.data.OwnedView
import com.ayushpant.habitquest.data.RewardsView
import com.ayushpant.habitquest.data.SparResult
import com.ayushpant.habitquest.game.Fighter
import com.ayushpant.habitquest.game.MoveKind
import com.ayushpant.habitquest.game.SparConfig
import com.ayushpant.habitquest.game.SparEvent
import com.ayushpant.habitquest.game.SparOutcome
import com.ayushpant.habitquest.game.SparState
import com.ayushpant.habitquest.game.fighter
import com.ayushpant.habitquest.game.pickOpponent
import com.ayushpant.habitquest.game.playTurn
import com.ayushpant.habitquest.game.sparPower
import com.ayushpant.habitquest.rewards.CompanionState
import com.ayushpant.habitquest.rewards.CreatureDef
import com.ayushpant.habitquest.rewards.Theme
import com.ayushpant.habitquest.rewards.companionStage
import com.ayushpant.habitquest.scoring.ScoringConfig
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.random.Random

/** One running match: the user's creature, the app's creature, and where the match is. */
private data class Match(val sparId: Long, val mine: OwnedView, val stage: Int, val opponent: CreatureDef, val state: SparState)

/**
 * The sparring game (step 5h, piece 1): pick one of your creatures, then a friendly turn-based
 * match against a creature the app controls. Energy, not health; at 0 a creature takes a breather.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SparScreen(repo: HabitRepository, onBack: () -> Unit) {
    val today by repo.observeToday(repo.today()).collectAsStateWithLifecycle(initialValue = null)
    val tickets by repo.observeSparTickets().collectAsStateWithLifecycle(initialValue = 0)
    val rewards = today?.rewards ?: RewardsView()
    val theme = rewards.theme
    val scope = rememberCoroutineScope()
    val rng = remember { Random(System.nanoTime()) }
    var picked by remember { mutableStateOf<Long?>(null) }
    var match by remember { mutableStateOf<Match?>(null) }
    var lines by remember { mutableStateOf(listOf<String>()) }
    var busy by remember { mutableStateOf(false) }
    var paidResult by remember { mutableStateOf<SparResult?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Spar") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") } },
            )
        },
    ) { inner ->
        val m = match
        Column(Modifier.fillMaxSize().padding(inner).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            when {
                m == null -> PickCreature(
                    owned = rewards.owned.sortedByDescending { it.companion },
                    tickets = tickets,
                    picked = picked ?: rewards.owned.firstOrNull { it.companion }?.ownedId,
                    onPick = { picked = it },
                    onStart = { o ->
                        scope.launch {
                            val stage = companionStage(o.bond)
                            val power = sparPower(stage, o.bond)
                            val (opp, oppPower) = pickOpponent(theme.creatures, power, rng)
                            val id = repo.startSpar(o.ownedId, opp.id, oppPower) ?: return@launch
                            paidResult = null
                            lines = listOf(theme.text.sparIntro(opp.name))
                            match = Match(id, o, stage, opp, SparState(fighter(o.def.name, o.def.dimension, power), fighter(opp.name, opp.dimension, oppPower)))
                        }
                    },
                )
                m.state.outcome == SparOutcome.PLAYING -> Battle(m, theme, lines, busy) { slot ->
                    busy = true
                    scope.launch {
                        val turn = playTurn(m.state, slot, rng)
                        for (e in turn.events) {
                            lines = (lines + lineFor(e, m.state, theme)).takeLast(4)
                            delay(650)
                        }
                        match = m.copy(state = turn.state)
                        val result = when (turn.state.outcome) {
                            SparOutcome.WON -> SparResult.WON
                            SparOutcome.LOST -> SparResult.LOST
                            SparOutcome.DRAW -> SparResult.DRAW
                            SparOutcome.PLAYING -> null
                        }
                        if (result != null && repo.finishSpar(m.sparId, result)) paidResult = result
                        busy = false
                    }
                }
                else -> Result(m, theme, paidResult, tickets, onAgain = { match = null }, onDone = onBack)
            }
        }
    }
}

@Composable
private fun ColumnScope.PickCreature(owned: List<OwnedView>, tickets: Int, picked: Long?, onPick: (Long) -> Unit, onStart: (OwnedView) -> Unit) {
    if (owned.isEmpty()) {
        Text("Hatch your first egg to spar. Every coin you earn helps it grow.", style = MaterialTheme.typography.bodyLarge)
        return
    }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text("🎟 $tickets ${if (tickets == 1) "ticket" else "tickets"} today", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            Text(
                "Each confirmed session today gives a ticket. Win: +${ScoringConfig.DEFAULT.sparWinCoins} coins and +${ScoringConfig.DEFAULT.sparWinBond} bond. A loss costs nothing.",
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text("💪 Body beats 🧠 Mind · 🧠 Mind beats ✨ Soul · ✨ Soul beats 💪 Body", style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = 6.dp))
        }
    }
    Text("Who spars?", style = MaterialTheme.typography.titleMedium)
    LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(bottom = 8.dp)) {
        items(owned, key = { it.ownedId }) { o ->
            val stage = companionStage(o.bond)
            val selected = o.ownedId == picked
            Card(
                Modifier.fillMaxWidth().clickable { onPick(o.ownedId) },
                border = if (selected) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
                colors = if (selected) CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer) else CardDefaults.cardColors(),
            ) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    CreatureArt(o.def, stage, CompanionState.ACTIVE, Modifier.size(56.dp))
                    Column(Modifier.padding(start = 12.dp).weight(1f)) {
                        Text(o.def.name + if (o.companion) " · with you" else "", style = MaterialTheme.typography.titleMedium)
                        Text("${o.def.dimension.emoji} ${o.def.dimension.label} · power ${sparPower(stage, o.bond)} · ♥ ${o.bond}", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
    val chosen = owned.firstOrNull { it.ownedId == picked }
    Button(
        enabled = tickets > 0 && chosen != null,
        onClick = { chosen?.let(onStart) },
        modifier = Modifier.fillMaxWidth().height(52.dp),
    ) { Text(if (tickets > 0) "Start a spar (uses 1 ticket)" else "No tickets left today") }
}

@Composable
private fun ColumnScope.Battle(m: Match, theme: Theme, lines: List<String>, busy: Boolean, onMove: (Int) -> Unit) {
    val s = m.state
    // The app's creature, top right
    Row(verticalAlignment = Alignment.CenterVertically) {
        FighterPanel(s.them, Modifier.weight(1f))
        CreatureArt(m.opponent, 1, CompanionState.ACTIVE, Modifier.size(112.dp))
    }
    // The user's creature, bottom left
    Row(verticalAlignment = Alignment.CenterVertically) {
        CreatureArt(m.mine.def, m.stage, CompanionState.ACTIVE, Modifier.size(128.dp))
        FighterPanel(s.me, Modifier.weight(1f))
    }
    Card(Modifier.fillMaxWidth().heightIn(min = 96.dp)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            lines.forEachIndexed { i, line ->
                Text(line, style = if (i == lines.lastIndex) MaterialTheme.typography.bodyLarge else MaterialTheme.typography.bodySmall)
            }
        }
    }
    val names = theme.text.sparMoves.getValue(s.me.dimension)
    val cfg = SparConfig.DEFAULT
    cfg.moves.forEachIndexed { slot, move ->
        val restBlocked = move.kind == MoveKind.REST && s.me.restsLeft <= 0
        val hint = when (move.kind) {
            MoveKind.PUSH -> "steady"
            MoveKind.BIG -> "strong · can miss"
            MoveKind.REST -> "+${move.power} energy · ${s.me.restsLeft} left"
        }
        OutlinedButton(onClick = { onMove(slot) }, enabled = !busy && !restBlocked, modifier = Modifier.fillMaxWidth()) {
            Text(names[slot], fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.weight(1f))
            Text(hint, style = MaterialTheme.typography.labelSmall)
        }
    }
}

/** Name, power and an energy bar that moves smoothly. */
@Composable
private fun FighterPanel(f: Fighter, modifier: Modifier = Modifier) {
    val share by animateFloatAsState(f.energy / SparConfig.DEFAULT.maxEnergy.toFloat(), label = "energy")
    Column(modifier.padding(horizontal = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(f.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Text("${f.dimension.emoji} power ${f.power}", style = MaterialTheme.typography.labelMedium)
        LinearProgressIndicator(progress = { share }, modifier = Modifier.fillMaxWidth().height(10.dp))
        Text("Energy ${f.energy}/${SparConfig.DEFAULT.maxEnergy}", style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun ColumnScope.Result(m: Match, theme: Theme, paid: SparResult?, tickets: Int, onAgain: () -> Unit, onDone: () -> Unit) {
    val cfg = ScoringConfig.DEFAULT
    val (title, text) = when (m.state.outcome) {
        SparOutcome.WON -> "🎉 You won!" to theme.text.sparWon(m.opponent.name) +
            if (paid == SparResult.WON) "\n+${cfg.sparWinCoins} coins · +${cfg.sparWinBond} bond for ${m.mine.def.name}" else ""
        SparOutcome.LOST -> "Good spar" to theme.text.sparLost(m.mine.def.name)
        else -> "A draw" to theme.text.sparDraw
    }
    Spacer(Modifier.height(24.dp))
    CreatureArt(m.mine.def, m.stage, if (m.state.outcome == SparOutcome.WON) CompanionState.ACTIVE else CompanionState.RESTING, Modifier.size(140.dp).align(Alignment.CenterHorizontally))
    Text(title, style = MaterialTheme.typography.headlineMedium, modifier = Modifier.align(Alignment.CenterHorizontally))
    Text(text, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
    Spacer(Modifier.weight(1f))
    if (tickets > 0) Button(onClick = onAgain, modifier = Modifier.fillMaxWidth().height(52.dp)) { Text("Spar again ($tickets left)") }
    TextButton(onClick = onDone, modifier = Modifier.fillMaxWidth()) { Text("Done") }
}

/** The message-box line for one move, in the theme's words. */
private fun lineFor(e: SparEvent, before: SparState, theme: Theme): String {
    val attacker = if (e.byMe) before.me else before.them
    val defender = if (e.byMe) before.them else before.me
    val move = theme.text.sparMoves.getValue(attacker.dimension)[e.slot]
    return when {
        e.kind == MoveKind.REST && !e.hit -> "${attacker.name} has no rests left."
        e.kind == MoveKind.REST -> "${attacker.name} used $move: +${e.amount} energy."
        !e.hit -> "${attacker.name} tried $move, but it went wide."
        else -> "${attacker.name} used $move! ${defender.name} −${e.amount} energy." + when (e.matchup) {
            1 -> " Extra strong!"
            -1 -> " Not so strong."
            else -> ""
        }
    }
}
