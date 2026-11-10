package com.ayushpant.habitquest.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ayushpant.habitquest.data.HabitRepository
import com.ayushpant.habitquest.data.PersonalReward
import com.ayushpant.habitquest.rewards.REWARD_IDEAS
import com.ayushpant.habitquest.rewards.REWARD_NAME_MAX
import com.ayushpant.habitquest.rewards.coinsStillNeeded
import com.ayushpant.habitquest.rewards.rewardCostValid
import com.ayushpant.habitquest.scoring.ScoringConfig
import kotlinx.coroutines.launch
import java.time.format.DateTimeFormatter

private val REDEEMED_DAY = DateTimeFormatter.ofPattern("EEE d MMM")

/**
 * Personal rewards (step 5c): treats the user defines and buys with coins. Redeem asks first,
 * then takes the coins; with too few coins the button is off and shows how many are still needed.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RewardsScreen(repo: HabitRepository, onBack: () -> Unit) {
    val today by repo.observeToday(repo.today()).collectAsStateWithLifecycle(initialValue = null)
    val rewards by repo.observeRewards().collectAsStateWithLifecycle(initialValue = emptyList())
    val redeemed by repo.observeRedemptions().collectAsStateWithLifecycle(initialValue = emptyList())
    val balance = today?.coins ?: 0
    val coinName = today?.rewards?.theme?.text?.coinName ?: "coins"
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var editing by remember { mutableStateOf<PersonalReward?>(null) } // id 0 = new
    var confirming by remember { mutableStateOf<PersonalReward?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Rewards") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") } },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { editing = PersonalReward(name = "", cost = 100) },
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text("New reward") },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { inner ->
        LazyColumn(
            Modifier.fillMaxSize().padding(inner),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Text("🪙 $balance $coinName", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
                        Text(
                            "Every habit and task earns $coinName. Spend them on treats you choose. Spending never slows your egg.",
                            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            if (rewards.isEmpty()) {
                item { Text("Ideas to start", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 6.dp)) }
                items(REWARD_IDEAS, key = { "idea-${it.name}" }) { idea ->
                    Card(Modifier.fillMaxWidth().clickable { editing = PersonalReward(name = idea.name, cost = idea.cost) }) {
                        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(idea.name, style = MaterialTheme.typography.titleMedium)
                                Text("${idea.cost} $coinName", style = MaterialTheme.typography.bodyMedium)
                            }
                            Text("Add", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge)
                        }
                    }
                }
            } else {
                item { Text("Your rewards", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 6.dp)) }
                items(rewards, key = { it.id }) { r ->
                    RewardRow(r, balance, coinName, onRedeem = { confirming = r }, onEdit = { editing = r })
                }
            }
            if (redeemed.isNotEmpty()) {
                item { Text("Redeemed", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 10.dp)) }
                items(redeemed, key = { "r-${it.id}" }) { r ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                        Text("🎁 ${r.name}", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                        Text("−${r.cost} · ${r.day.format(REDEEMED_DAY)}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }

    editing?.let { start ->
        RewardEditor(
            start = start,
            coinName = coinName,
            onDismiss = { editing = null },
            onSave = { r -> scope.launch { if (repo.saveReward(r)) editing = null } },
            onDelete = { id -> scope.launch { repo.deleteReward(id); editing = null } },
        )
    }

    confirming?.let { r ->
        AlertDialog(
            onDismissRequest = { confirming = null },
            title = { Text("Redeem “${r.name}”?") },
            text = { Text("This takes ${r.cost} $coinName. You'll have ${balance - r.cost} left. Your egg keeps all its growth.") },
            confirmButton = {
                Button(onClick = {
                    confirming = null
                    scope.launch {
                        val ok = repo.redeemReward(r.id)
                        snackbar.showSnackbar(if (ok) "Enjoy it! 🎉 ${r.name}" else "Not enough $coinName yet.")
                    }
                }) { Text("Redeem") }
            },
            dismissButton = { TextButton(onClick = { confirming = null }) { Text("Not now") } },
        )
    }
}

/** One reward: its name and cost, and Redeem (off, with the coins still needed, when the balance is too low). */
@Composable
private fun RewardRow(r: PersonalReward, balance: Int, coinName: String, onRedeem: () -> Unit, onEdit: () -> Unit) {
    val needed = coinsStillNeeded(balance, r.cost)
    Card(Modifier.fillMaxWidth().clickable(onClick = onEdit)) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(r.name, style = MaterialTheme.typography.titleMedium)
                Text("${r.cost} $coinName", style = MaterialTheme.typography.bodyMedium)
            }
            Column(horizontalAlignment = Alignment.End) {
                if (needed == 0) {
                    Button(onClick = onRedeem) { Text("Redeem") }
                } else {
                    OutlinedButton(onClick = {}, enabled = false) { Text("Redeem") }
                    Text("$needed more needed", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

/** Create or edit a reward: a name and a cost of 10 to 5000 coins. */
@Composable
private fun RewardEditor(
    start: PersonalReward,
    coinName: String,
    onDismiss: () -> Unit,
    onSave: (PersonalReward) -> Unit,
    onDelete: (Long) -> Unit,
) {
    val cfg = ScoringConfig.DEFAULT
    var name by remember(start) { mutableStateOf(start.name) }
    var costText by remember(start) { mutableStateOf(start.cost.toString()) }
    val cost = costText.toIntOrNull()
    val costOk = cost != null && rewardCostValid(cost, cfg)
    val valid = name.isNotBlank() && costOk
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (start.id == 0L) "New reward" else "Edit reward") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = name, onValueChange = { name = it.take(REWARD_NAME_MAX) },
                    label = { Text("What treat?") }, placeholder = { Text("e.g. A coffee outside") },
                    singleLine = true, modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = costText, onValueChange = { costText = it.filter(Char::isDigit).take(5) },
                    label = { Text("Cost in $coinName") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    isError = costText.isNotEmpty() && !costOk,
                    supportingText = { Text("${cfg.rewardMinCost} to ${cfg.rewardMaxCost}") },
                    modifier = Modifier.fillMaxWidth(),
                )
                if (start.id != 0L) {
                    TextButton(onClick = { onDelete(start.id) }) { Text("Delete this reward", color = MaterialTheme.colorScheme.error) }
                }
            }
        },
        confirmButton = {
            Button(enabled = valid, onClick = { onSave(start.copy(name = name.trim(), cost = cost ?: start.cost)) }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
