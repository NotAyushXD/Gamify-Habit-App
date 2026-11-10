package com.ayushpant.habitquest.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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

// A classic handheld look: paper-white screen, black ink, a bold monospace font. Fixed colours,
// like a game screen, in light and dark mode alike.
private val Paper = Color(0xFFF8F8F0)
private val Ink = Color(0xFF202020)
private val Platform = Color(0xFFD8D8C8)
private val Retro = FontFamily.Monospace

private fun hpColor(share: Float) = when {
    share > 0.5f -> Color(0xFF38B048)
    share > 0.2f -> Color(0xFFE8B820)
    else -> Color(0xFFE04030)
}

/**
 * The sparring game (step 5h, piece 1): pick one of your creatures, then a turn-based battle in
 * a classic handheld style against a wild creature. HP drops with each move; at 0 a creature is
 * worn out and bows (R5: nobody faints or is hurt). Win: coins and bond. Loss: nothing lost.
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
    var line by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var flash by remember { mutableStateOf<Boolean?>(null) } // true: my creature blinks, false: the wild one
    var paidResult by remember { mutableStateOf<SparResult?>(null) }
    var finished by remember { mutableStateOf(false) }
    var fx by remember { mutableStateOf<Fx?>(null) } // the attack animation running now

    /** Shows one message with the typewriter, and waits long enough to read it. */
    suspend fun say(text: String) {
        line = text
        delay(20L * text.length + 550)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Spar") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") } },
            )
        },
    ) { inner ->
        val m = match
        Column(Modifier.fillMaxSize().padding(inner).padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
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
                            finished = false
                            busy = true
                            match = Match(
                                id, o, stage, opp,
                                SparState(
                                    fighter(o.def.name, o.def.dimension, power, id = o.def.id),
                                    fighter(opp.name, opp.dimension, oppPower, id = opp.id),
                                ),
                            )
                            say(theme.text.sparIntro(opp.name.uppercase()))
                            say(theme.text.sparGo(o.def.name.uppercase()))
                            line = "What will ${o.def.name.uppercase()} do?"
                            busy = false
                        }
                    },
                )
                !finished -> Battle(
                    m, theme, line, busy, flash, fx,
                    onMove = { slot ->
                        busy = true
                        scope.launch {
                            val turn = playTurn(m.state, slot, rng)
                            var running = m.state
                            for (e in turn.events) {
                                val texts = linesFor(e, running, theme)
                                say(texts.first()) // "CHARIGNIS used EMBER!"
                                val attacker = if (e.byMe) running.me else running.them
                                val style = fxStyleFor(theme.moveNames(attacker.id, attacker.dimension)[e.slot], e.kind)
                                if (!(e.kind == MoveKind.REST && !e.hit)) { // a failed rest shows nothing
                                    val anim = Animatable(0f)
                                    anim.animateTo(1f, tween(if (style == FxStyle.HEAL) 800 else 900, easing = LinearEasing)) {
                                        fx = Fx(style, e.byMe, e.hit, value)
                                    }
                                    fx = null
                                }
                                running = applyEvent(running, e)
                                match = m.copy(state = running) // the HP bar moves now
                                if (e.hit && e.kind != MoveKind.REST) {
                                    repeat(3) { flash = !e.byMe; delay(90); flash = null; delay(90) }
                                }
                                for (text in texts.drop(1)) say(text) // "It's super effective!", "…missed!"
                            }
                            match = m.copy(state = turn.state)
                            val result = when (turn.state.outcome) {
                                SparOutcome.WON -> SparResult.WON
                                SparOutcome.LOST -> SparResult.LOST
                                SparOutcome.DRAW -> SparResult.DRAW
                                SparOutcome.PLAYING -> null
                            }
                            if (result == null) {
                                line = "What will ${m.mine.def.name.uppercase()} do?"
                            } else {
                                if (repo.finishSpar(m.sparId, result)) paidResult = result
                                say(
                                    when (result) {
                                        SparResult.WON -> theme.text.sparWon(m.opponent.name.uppercase())
                                        SparResult.LOST -> theme.text.sparLost(m.mine.def.name.uppercase())
                                        SparResult.DRAW -> theme.text.sparDraw
                                    }
                                )
                                finished = true
                            }
                            busy = false
                        }
                    },
                    onRun = {
                        busy = true
                        scope.launch {
                            say(theme.text.sparRun) // the ticket stays used
                            match = null
                            busy = false
                        }
                    },
                )
                else -> Result(m, paidResult, tickets, onAgain = { match = null }, onDone = onBack)
            }
        }
    }
}

/** Energy after one event, so the HP bar drops while its message shows. */
private fun applyEvent(s: SparState, e: SparEvent): SparState {
    if (!e.hit) return s
    fun Fighter.plus(d: Int) = copy(energy = (energy + d).coerceIn(0, SparConfig.DEFAULT.maxEnergy))
    return when {
        e.kind == MoveKind.REST && e.byMe -> s.copy(me = s.me.plus(e.amount).copy(restsLeft = s.me.restsLeft - 1))
        e.kind == MoveKind.REST -> s.copy(them = s.them.plus(e.amount).copy(restsLeft = s.them.restsLeft - 1))
        e.byMe -> s.copy(them = s.them.plus(-e.amount))
        else -> s.copy(me = s.me.plus(-e.amount))
    }
}

/** The messages for one move, in the theme's words: "CHARIGNIS used EMBER!", "It's super effective!". */
private fun linesFor(e: SparEvent, before: SparState, theme: Theme): List<String> {
    val attacker = if (e.byMe) before.me else before.them
    val who = (if (e.byMe) "" else "Wild ") + attacker.name.uppercase()
    val move = theme.moveNames(attacker.id, attacker.dimension)[e.slot].uppercase()
    val used = "$who used $move!"
    return when {
        e.kind == MoveKind.REST && !e.hit -> listOf(used, theme.text.sparFailed)
        e.kind == MoveKind.REST -> listOf(used, "$who got ${e.amount} HP back.")
        !e.hit -> listOf(used, theme.text.sparMissed(who))
        e.matchup == 1 -> listOf(used, theme.text.sparStrong)
        e.matchup == -1 -> listOf(used, theme.text.sparWeak)
        else -> listOf(used)
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
    Text("Choose your creature!", style = MaterialTheme.typography.titleMedium)
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
                        Text("${o.def.dimension.emoji} ${o.def.dimension.label} · :L${sparPower(stage, o.bond)} · ♥ ${o.bond}", style = MaterialTheme.typography.bodySmall)
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
private fun RetroText(text: String, size: TextUnit = 15.sp, modifier: Modifier = Modifier) =
    Text(text, modifier, color = Ink, fontFamily = Retro, fontWeight = FontWeight.Bold, fontSize = size, lineHeight = size * 1.35f)

/** The classic info-box bracket: a line down one side and along the bottom. */
private fun Modifier.bracket(rightSide: Boolean) = drawBehind {
    val w = 3.dp.toPx()
    val x = if (rightSide) size.width - w / 2 else w / 2
    drawLine(Ink, Offset(x, size.height * 0.45f), Offset(x, size.height - w / 2), w)
    drawLine(Ink, Offset(0f, size.height - w / 2), Offset(size.width, size.height - w / 2), w)
}

/** Name, level and HP bar; [numbers] adds "64/ 100" (the user's own creature). */
@Composable
private fun InfoBox(f: Fighter, numbers: Boolean, rightSide: Boolean, modifier: Modifier = Modifier) {
    val max = SparConfig.DEFAULT.maxEnergy
    val share by animateFloatAsState(f.energy / max.toFloat(), tween(450), label = "hp")
    Column(modifier.bracket(rightSide).padding(start = 10.dp, end = 10.dp, bottom = 8.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
        RetroText(f.name.uppercase(), 16.sp)
        RetroText("  :L${f.power}", 13.sp)
        Row(verticalAlignment = Alignment.CenterVertically) {
            RetroText("HP:", 11.sp)
            Box(
                Modifier.padding(start = 4.dp).weight(1f).height(9.dp)
                    .border(1.5.dp, Ink, RoundedCornerShape(5.dp)).padding(2.dp),
            ) {
                Box(Modifier.fillMaxHeight().fillMaxWidth(share).clip(RoundedCornerShape(3.dp)).background(hpColor(share)))
            }
        }
        if (numbers) RetroText("${f.energy}/ $max", 14.sp, Modifier.align(Alignment.End))
    }
}

/** A creature on its little ground ellipse. [blink] makes it flash when a move lands. */
@Composable
private fun OnPlatform(def: CreatureDef, stage: Int, size: Int, blink: Boolean, modifier: Modifier = Modifier) {
    Box(modifier.size(size.dp), contentAlignment = Alignment.BottomCenter) {
        Canvas(Modifier.fillMaxWidth().height((size * 0.22f).dp)) {
            drawOval(Platform, topLeft = Offset(0f, 0f), size = this.size)
        }
        CreatureArt(def, stage, CompanionState.ACTIVE, Modifier.size((size * 0.86f).dp).padding(bottom = (size * 0.06f).dp).alpha(if (blink) 0.15f else 1f))
    }
}

/** The double-bordered message box at the bottom, with typewriter text. */
@Composable
private fun MessageBox(text: String, modifier: Modifier = Modifier) {
    var shown by remember(text) { mutableIntStateOf(0) }
    LaunchedEffect(text) {
        while (shown < text.length) {
            delay(20)
            shown++
        }
    }
    Box(
        modifier.fillMaxWidth().height(104.dp)
            .border(4.dp, Ink, RoundedCornerShape(10.dp)).padding(4.dp)
            .border(2.dp, Ink, RoundedCornerShape(7.dp)).background(Paper, RoundedCornerShape(7.dp))
            .padding(horizontal = 14.dp, vertical = 10.dp),
    ) {
        RetroText(text.take(shown), 15.sp)
    }
}

@Composable
private fun ColumnScope.Battle(
    m: Match, theme: Theme, line: String, busy: Boolean, flash: Boolean?, fx: Fx?,
    onMove: (Int) -> Unit, onRun: () -> Unit,
) {
    val s = m.state
    // Where the two sprites are, so the attack animation can fly between them.
    var origin by remember { mutableStateOf(Offset.Zero) }
    var mine by remember { mutableStateOf(Offset.Zero) }
    var theirs by remember { mutableStateOf(Offset.Zero) }
    Box(
        Modifier.fillMaxWidth().weight(1f).clip(RoundedCornerShape(12.dp)).background(Paper)
            .border(3.dp, Ink, RoundedCornerShape(12.dp))
            .onGloballyPositioned { origin = it.positionInRoot() },
    ) {
        Column(Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.SpaceBetween) {
            // The wild creature: info box top left, on its ground top right
            Row(verticalAlignment = Alignment.Top) {
                InfoBox(s.them, numbers = false, rightSide = false, modifier = Modifier.weight(1f).padding(top = 6.dp))
                OnPlatform(m.opponent, 1, 118, blink = flash == false, modifier = Modifier.onGloballyPositioned { theirs = it.boundsInRoot().center })
            }
            // Your creature: on its ground bottom left, info box bottom right
            Row(verticalAlignment = Alignment.Bottom) {
                OnPlatform(m.mine.def, m.stage, 136, blink = flash == true, modifier = Modifier.onGloballyPositioned { mine = it.boundsInRoot().center })
                InfoBox(s.me, numbers = true, rightSide = true, modifier = Modifier.weight(1f).padding(bottom = 6.dp))
            }
            MessageBox(line)
        }
        if (fx != null) {
            Canvas(Modifier.matchParentSize()) {
                val me = mine - origin
                val them = theirs - origin
                drawFx(fx, from = if (fx.fromMe) me else them, to = if (fx.fromMe) them else me, unit = 120.dp.toPx())
            }
        }
    }
    // The move menu: three moves and RUN, in boxes
    val names = theme.moveNames(s.me.id, s.me.dimension)
    val cfg = SparConfig.DEFAULT
    val options = cfg.moves.mapIndexed { slot, move ->
        val blocked = move.kind == MoveKind.REST && s.me.restsLeft <= 0
        val hint = when (move.kind) {
            MoveKind.PUSH -> "STEADY"
            MoveKind.BIG -> "STRONG · MAY MISS"
            MoveKind.REST -> "HEAL ${move.power} · ${s.me.restsLeft}/${cfg.maxRests}"
        }
        Triple(names[slot].uppercase(), hint, if (blocked) null else ({ onMove(slot) }))
    } + Triple("RUN", "LEAVE THE SPAR", onRun)
    for (row in options.chunked(2)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for ((label, hint, action) in row) {
                val enabled = !busy && action != null
                Column(
                    Modifier.weight(1f).clip(RoundedCornerShape(8.dp)).background(Paper)
                        .border(2.dp, Ink.copy(alpha = if (enabled) 1f else 0.3f), RoundedCornerShape(8.dp))
                        .clickable(enabled = enabled) { action?.invoke() }
                        .padding(horizontal = 10.dp, vertical = 8.dp)
                        .alpha(if (enabled) 1f else 0.4f),
                ) {
                    RetroText("▶ $label", 14.sp)
                    RetroText(hint, 10.sp)
                }
            }
        }
    }
}

@Composable
private fun ColumnScope.Result(m: Match, paid: SparResult?, tickets: Int, onAgain: () -> Unit, onDone: () -> Unit) {
    val cfg = ScoringConfig.DEFAULT
    val name = m.mine.def.name.uppercase()
    val text = when (m.state.outcome) {
        SparOutcome.WON -> "You won!" + if (paid == SparResult.WON) "\nGot ${cfg.sparWinCoins} coins!\n$name's bond grew by ${cfg.sparWinBond}." else ""
        SparOutcome.LOST -> "$name is taking a breather.\nNo coins lost. Try again with a new ticket!"
        else -> "It's a draw!\nNo coins lost."
    }
    Column(
        Modifier.fillMaxWidth().weight(1f).clip(RoundedCornerShape(12.dp)).background(Paper)
            .border(3.dp, Ink, RoundedCornerShape(12.dp)).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.weight(1f))
        OnPlatform(m.mine.def, m.stage, 160, blink = false)
        Spacer(Modifier.weight(1f))
        MessageBox(text.replace("\n", " "))
    }
    if (tickets > 0) Button(onClick = onAgain, modifier = Modifier.fillMaxWidth().height(52.dp)) { Text("Spar again ($tickets left)") }
    Button(onClick = onDone, modifier = Modifier.fillMaxWidth().height(48.dp)) { Text("Done") }
}
