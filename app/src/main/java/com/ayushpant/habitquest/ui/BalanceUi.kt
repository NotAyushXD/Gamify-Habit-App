package com.ayushpant.habitquest.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ayushpant.habitquest.balance.Dimension
import com.ayushpant.habitquest.balance.DimensionScore
import kotlin.math.roundToInt

/** Fixed colours, so a dimension always looks the same everywhere. */
fun Dimension.color(): Color = when (this) {
    Dimension.MIND -> Color(0xFF5E5CE6)
    Dimension.BODY -> Color(0xFFFF453A)
    Dimension.SOUL -> Color(0xFFFF9F0A)
}

fun DimensionScore.percentText(): String = rate?.let { "${(it * 100).roundToInt()}%" } ?: "–"

/** Three small meters side by side: 🧠 Mind 60% with a bar. Used on Today. */
@Composable
fun BalanceRow(scores: List<DimensionScore>, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        for (score in scores) {
            val d = score.dimension
            Column(Modifier.weight(1f)) {
                Text("${d.emoji} ${d.label}", style = MaterialTheme.typography.labelMedium)
                Text(score.percentText(), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = d.color())
                LinearProgressIndicator(
                    progress = { (score.rate ?: 0.0).toFloat() },
                    color = d.color(),
                    trackColor = d.color().copy(alpha = 0.18f),
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                )
                Text("+${score.points} pts", style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(top = 2.dp))
            }
        }
    }
}
