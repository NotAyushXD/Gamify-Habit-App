package com.ayushpant.habitquest.promise

import android.app.Activity
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Intent
import android.speech.RecognizerIntent
import android.text.format.DateFormat
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateIntOffsetAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.ayushpant.habitquest.data.OathMethod
import com.ayushpant.habitquest.reminders.PromiseContext
import com.ayushpant.habitquest.reminders.ReminderConfig
import com.ayushpant.habitquest.scoring.ScoringConfig
import kotlinx.coroutines.delay
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.random.Random

private val HHMM = DateTimeFormatter.ofPattern("HH:mm")
private val DAY = DateTimeFormatter.ofPattern("EEE d MMM")

/** How far ahead "another day" can be. A promise for next month is not a promise. */
private const val MAX_DAYS_AHEAD = 14L

/**
 * "Can't today": no guilt, then a real promise. Pick when (tomorrow, or another day), then
 * promise. Missed before today too? The promise needs the oath, typed or spoken.
 * [onPromise] gets the promised time, how the oath was given, and what was typed or said.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PromiseScreen(
    ctx: PromiseContext,
    today: LocalDate,
    onPromise: (LocalDateTime, OathMethod, String?) -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    var otherDay by remember { mutableStateOf(false) }
    var day by remember { mutableStateOf(today.plusDays(1)) }
    var time by remember { mutableStateOf(ctx.suggestedTime) }
    var typed by remember { mutableStateOf("") }
    var spokenOk by remember { mutableStateOf<String?>(null) }
    var speechMessage by remember { mutableStateOf<String?>(null) }
    var secondsLeft by remember { mutableIntStateOf(ReminderConfig.DEFAULT.promiseWaitSeconds) }
    LaunchedEffect(Unit) {
        while (secondsLeft > 0) {
            delay(1_000)
            secondsLeft--
        }
    }

    val dueDay = if (otherDay) day else today.plusDays(1)
    val sentence = oathSentence(ctx.habitName, dueDay, time, today)
    val typedOk = typedOathMatches(typed, sentence)

    // Speech: Android's own "speak now" screen (Google's recogniser on most phones).
    val speechIntent = remember {
        Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
    }
    val canSpeak = remember { speechIntent.resolveActivity(context.packageManager) != null }
    val speak = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val heard = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS).orEmpty()
        val match = heard.firstOrNull { spokenOathMatches(it, ctx.habitName) }
        if (result.resultCode == Activity.RESULT_OK && match != null) {
            spokenOk = match
            speechMessage = "✓ Heard: \"$match\""
        } else {
            spokenOk = null
            speechMessage = if (heard.isEmpty()) "Didn't catch that. Try again, or type it." else "Heard \"${heard.first()}\". Say \"I promise\" and \"${ctx.habitName}\"."
        }
    }

    val canConfirm = if (ctx.oathRequired) typedOk || spokenOk != null else secondsLeft == 0

    Card(
        modifier = Modifier.padding(16.dp).fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
    ) {
        Column(
            Modifier.padding(start = 20.dp, end = 8.dp, top = 8.dp, bottom = 16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("That's okay", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                IconButton(onClick = onBack) { Icon(Icons.Default.Close, contentDescription = "Close") }
            }
            Column(Modifier.padding(end = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Don't feel guilty if it's not possible today. Setting realistic goals is better than forcing it.")
                if (ctx.priorMisses > 0) {
                    Text(
                        "${ctx.habitName} was also missed on the ${ctx.priorMisses} planned day${if (ctx.priorMisses == 1) "" else "s"} " +
                            "before today. This time, make it a real promise.",
                        color = MaterialTheme.colorScheme.error,
                    )
                }

                Text("When will you do ${ctx.habitName}?", style = MaterialTheme.typography.titleSmall)
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    SegmentedButton(selected = !otherDay, onClick = { otherDay = false }, shape = SegmentedButtonDefaults.itemShape(0, 2)) {
                        Text("Tomorrow")
                    }
                    SegmentedButton(selected = otherDay, onClick = { otherDay = true }, shape = SegmentedButtonDefaults.itemShape(1, 2)) {
                        Text("Another day")
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (otherDay) {
                        OutlinedButton(onClick = {
                            DatePickerDialog(context, { _, y, m, d -> day = LocalDate.of(y, m + 1, d) }, day.year, day.monthValue - 1, day.dayOfMonth).apply {
                                val zone = ZoneId.systemDefault()
                                datePicker.minDate = today.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
                                datePicker.maxDate = today.plusDays(MAX_DAYS_AHEAD).atStartOfDay(zone).toInstant().toEpochMilli()
                            }.show()
                        }) { Text(day.format(DAY)) }
                    }
                    OutlinedButton(onClick = {
                        TimePickerDialog(context, { _, h, m -> time = LocalTime.of(h, m) }, time.hour, time.minute, DateFormat.is24HourFormat(context)).show()
                    }) { Text("at ${time.format(HHMM)}") }
                }
                Text(
                    "A reminder comes at exactly that time. Keep it: +${ScoringConfig.DEFAULT.promiseKeptBonus} bonus.",
                    style = MaterialTheme.typography.bodySmall,
                )

                Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.secondaryContainer) {
                    Text(
                        "“$sentence”",
                        style = MaterialTheme.typography.titleMedium,
                        fontStyle = FontStyle.Italic,
                        modifier = Modifier.padding(12.dp),
                    )
                }

                if (ctx.oathRequired) {
                    Text("Type it word for word${if (canSpeak) ", or say it out loud" else ""}:", style = MaterialTheme.typography.bodyMedium)
                    OutlinedTextField(
                        value = typed,
                        onValueChange = { typed = it },
                        label = { Text("Your oath") },
                        modifier = Modifier.fillMaxWidth(),
                        isError = typed.isNotBlank() && !typedOk,
                        supportingText = {
                            Text(
                                when {
                                    typedOk -> "✓ Oath accepted"
                                    typed.isBlank() -> "It must match the words above"
                                    else -> "Not yet: it must match word for word"
                                }
                            )
                        },
                    )
                    if (canSpeak) {
                        OutlinedButton(onClick = {
                            speak.launch(Intent(speechIntent).putExtra(RecognizerIntent.EXTRA_PROMPT, sentence))
                        }) { Text("🎤 Say it") }
                        speechMessage?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                    }
                }

                Button(
                    onClick = {
                        val method = when {
                            !ctx.oathRequired -> OathMethod.TAP
                            typedOk -> OathMethod.TYPED
                            else -> OathMethod.SPOKEN
                        }
                        val text = when (method) {
                            OathMethod.TYPED -> typed.trim()
                            OathMethod.SPOKEN -> spokenOk
                            OathMethod.TAP -> null
                        }
                        onPromise(dueDay.atTime(time), method, text)
                    },
                    enabled = canConfirm,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(if (!ctx.oathRequired && secondsLeft > 0) "I promise 🤝 ($secondsLeft)" else "I promise 🤝")
                }

                RunawayButton()
                TextButton(onClick = onBack) { Text("Back") }
            }
        }
    }
}

private val RUNAWAY_TEXTS = listOf(
    "Not possible tomorrow either",
    "Nice try 😄",
    "That's not an option",
    "You can do this",
    "Tomorrow is a fresh start",
    "Still no 🙂",
)

/**
 * A button that can never be pressed: as soon as a finger touches it, it jumps somewhere else.
 * "Not possible tomorrow either" is not an answer this app accepts.
 */
@Composable
private fun RunawayButton() {
    var target by remember { mutableStateOf(IntOffset.Zero) }
    var tries by remember { mutableIntStateOf(0) }
    var size by remember { mutableStateOf(IntSize.Zero) }
    val offset by animateIntOffsetAsState(target, animationSpec = spring(), label = "runaway")
    BoxWithConstraints(Modifier.fillMaxWidth().height(96.dp)) {
        val areaW = constraints.maxWidth
        val areaH = constraints.maxHeight
        Surface(
            shape = RoundedCornerShape(20.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier
                .offset { offset }
                .onSizeChanged { size = it }
                .pointerInput(Unit) {
                    awaitEachGesture {
                        awaitFirstDown().consume()
                        tries++
                        val maxX = (areaW - size.width).coerceAtLeast(1)
                        val maxY = (areaH - size.height).coerceAtLeast(1)
                        var next: IntOffset
                        do {
                            next = IntOffset(Random.nextInt(maxX), Random.nextInt(maxY))
                        } while (kotlin.math.abs(next.x - target.x) < size.width / 2 && maxX > size.width)
                        target = next
                    }
                },
        ) {
            Text(
                RUNAWAY_TEXTS[tries % RUNAWAY_TEXTS.size],
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            )
        }
    }
}
