package com.ayushpant.habitquest.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import com.ayushpant.habitquest.game.MoveKind
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/*
 * Attack animations for the spar screen (step 5h), drawn in code like the creatures. A shot flies
 * from the attacker to the target; on a hit it bursts on the target, on a miss it lands beside the
 * target and splashes on the ground there. Rest moves sparkle on the creature itself.
 */

/** The look of a move. [main] and [light] are its two colours. */
enum class FxStyle(val main: Color, val light: Color) {
    WATER(Color(0xFF3D8FD6), Color(0xFFB9E1FA)),
    FIRE(Color(0xFFEF6C2A), Color(0xFFFFD45C)),
    ELECTRIC(Color(0xFFF5C518), Color(0xFFFFFBE0)),
    LEAF(Color(0xFF3FA34D), Color(0xFFA8E08A)),
    ROCK(Color(0xFF8A7F72), Color(0xFFC9C0B4)),
    GHOST(Color(0xFF7A4FC2), Color(0xFFD9C8F5)),
    WIND(Color(0xFF9FC6E8), Color(0xFFFFFFFF)),
    BUG(Color(0xFF8DA03A), Color(0xFFD6E39B)),
    PSYCHIC(Color(0xFFE05FA8), Color(0xFFF9C5E3)),
    DRAGON(Color(0xFF5B6FE0), Color(0xFFC3CBFA)),
    NORMAL(Color(0xFF9A9A9A), Color(0xFFFFFFFF)),
    HEAL(Color(0xFF38B048), Color(0xFFC8F0C0)),
}

/** One running animation. [t] runs 0 → 1: the shot flies (0–0.7), then bursts or splashes (0.7–1). */
data class Fx(val style: FxStyle, val fromMe: Boolean, val hit: Boolean, val t: Float)

private const val FLY = 0.7f

/** The look of a move from its (theme 1, classic) name. Anything not listed is a plain hit. */
fun fxStyleFor(moveName: String, kind: MoveKind): FxStyle {
    if (kind == MoveKind.REST) return FxStyle.HEAL
    val n = moveName.lowercase()
    fun has(vararg words: String) = words.any { it in n }
    return when {
        has("water", "bubble", "hydro") -> FxStyle.WATER
        has("ember", "flame", "fire") -> FxStyle.FIRE
        has("thunder", "spark", "volt") -> FxStyle.ELECTRIC
        has("vine", "leaf", "solar") -> FxStyle.LEAF
        has("rock", "stone") -> FxStyle.ROCK
        has("shadow", "lick") -> FxStyle.GHOST
        has("gust", "twister", "wing") -> FxStyle.WIND
        has("bug") -> FxStyle.BUG
        has("psy") -> FxStyle.PSYCHIC
        has("dragon") -> FxStyle.DRAGON
        else -> FxStyle.NORMAL
    }
}

/** Where a missed shot lands: beside and a little past the target, on the ground. */
fun missPoint(from: Offset, to: Offset, unit: Float): Offset {
    val d = to - from
    val len = sqrt(d.x * d.x + d.y * d.y).coerceAtLeast(1f)
    val dir = Offset(d.x / len, d.y / len)
    val side = Offset(-dir.y, dir.x)
    return to + side * (unit * 0.75f) + dir * (unit * 0.25f)
}

/**
 * Draws [fx] from the attacker at [from] to the target at [to] (sprite centres, in this canvas).
 * [unit] is about one sprite's size in pixels, so every effect scales with the screen.
 */
fun DrawScope.drawFx(fx: Fx, from: Offset, to: Offset, unit: Float) {
    if (fx.style == FxStyle.HEAL) {
        drawHeal(from, unit, fx.t)
        return
    }
    val end = if (fx.hit) to else missPoint(from, to, unit)
    if (fx.t < FLY) {
        drawShot(fx.style, from, end, (fx.t / FLY).coerceIn(0f, 1f), unit)
    } else {
        val k = ((fx.t - FLY) / (1f - FLY)).coerceIn(0f, 1f)
        if (fx.hit) drawBurst(fx.style, end, k, unit) else drawSplash(fx.style, end, k, unit)
    }
}

// ---- Paths --------------------------------------------------------------------------------------

private fun lerp(a: Offset, b: Offset, t: Float) = a + (b - a) * t

/** Thrown things (rocks, leaves) fly on an arc; beams go straight. */
private fun pathPoint(style: FxStyle, from: Offset, to: Offset, t: Float, unit: Float): Offset {
    val base = lerp(from, to, t)
    val arc = when (style) {
        FxStyle.ROCK -> 0.7f
        FxStyle.LEAF -> 0.35f
        FxStyle.FIRE -> 0.15f
        else -> 0f
    }
    return base - Offset(0f, sin(PI.toFloat() * t) * unit * arc)
}

private fun perpendicular(from: Offset, to: Offset): Offset {
    val d = to - from
    val len = sqrt(d.x * d.x + d.y * d.y).coerceAtLeast(1f)
    return Offset(-d.y / len, d.x / len)
}

// ---- The shot -----------------------------------------------------------------------------------

private fun DrawScope.drawShot(style: FxStyle, from: Offset, to: Offset, t: Float, unit: Float) {
    val side = perpendicular(from, to)
    fun at(tt: Float) = pathPoint(style, from, to, tt.coerceIn(0f, 1f), unit)
    when (style) {
        FxStyle.WATER -> for (i in 0 until 10) {
            val tt = t - i * 0.035f
            if (tt < 0f) continue
            val r = unit * 0.055f * (1f - i * 0.06f)
            drawCircle(style.main.copy(alpha = 1f - i * 0.07f), r, at(tt))
            drawCircle(style.light, r * 0.35f, at(tt) - Offset(r * 0.3f, r * 0.3f))
        }
        FxStyle.FIRE -> for (i in 0 until 7) {
            val tt = t - i * 0.05f
            if (tt < 0f) continue
            val flicker = 1f + 0.18f * sin((t * 40f + i).toDouble()).toFloat()
            val r = unit * (0.085f - i * 0.009f) * flicker
            drawCircle(if (i < 2) style.light else style.main.copy(alpha = 1f - i * 0.11f), r, at(tt))
        }
        FxStyle.ELECTRIC -> {
            val head = at(t)
            val bolt = Path().apply {
                moveTo(from.x, from.y)
                for (j in 1..7) {
                    val p = lerp(from, head, j / 7f) + side * (unit * 0.12f * if (j % 2 == 0) 1f else -1f) * (if (j == 7) 0f else 1f)
                    lineTo(p.x, p.y)
                }
            }
            drawPath(bolt, style.main, style = Stroke(unit * 0.06f, cap = StrokeCap.Round))
            drawPath(bolt, style.light, style = Stroke(unit * 0.022f, cap = StrokeCap.Round))
        }
        FxStyle.LEAF -> for (k in 0 until 4) {
            val tt = t - k * 0.08f
            if (tt < 0f) continue
            val c = at(tt) + side * (unit * 0.12f * (k - 1.5f))
            rotate(tt * 900f + k * 45f, pivot = c) {
                drawOval(if (k % 2 == 0) style.main else style.light, topLeft = c - Offset(unit * 0.07f, unit * 0.035f), size = Size(unit * 0.14f, unit * 0.07f))
            }
        }
        FxStyle.ROCK -> for (k in 0 until 3) {
            val tt = t - k * 0.1f
            if (tt < 0f) continue
            drawRock(at(tt) + side * (unit * 0.1f * (k - 1)), unit * (0.08f - k * 0.012f), tt * 360f, style)
        }
        FxStyle.GHOST -> {
            val c = at(t) + side * (sin(t * 4f * PI.toFloat()) * unit * 0.18f)
            drawCircle(style.light.copy(alpha = 0.45f), unit * 0.17f, c)
            drawCircle(style.main, unit * 0.09f, c)
            drawCircle(Color.White.copy(alpha = 0.8f), unit * 0.025f, c - Offset(unit * 0.03f, unit * 0.03f))
        }
        FxStyle.WIND -> for (k in 0 until 3) {
            val tt = t - k * 0.09f
            if (tt < 0f) continue
            val c = at(tt) + side * (unit * 0.1f * (k - 1))
            val r = unit * (0.12f + k * 0.03f)
            drawArc(style.main.copy(alpha = 0.9f - k * 0.2f), (tt * 720f) % 360f, 220f, false, topLeft = c - Offset(r, r), size = Size(r * 2, r * 2), style = Stroke(unit * 0.03f, cap = StrokeCap.Round))
        }
        FxStyle.BUG -> for (i in 0 until 9) {
            val tt = t - i * 0.03f
            if (tt < 0f) continue
            val c = at(tt) + side * (sin((tt * 30f + i).toDouble()).toFloat() * unit * 0.05f)
            drawCircle(if (i % 2 == 0) style.main else style.light, unit * 0.035f, c)
        }
        FxStyle.PSYCHIC -> for (k in 0 until 3) {
            val tt = t - k * 0.1f
            if (tt < 0f) continue
            drawCircle(style.main.copy(alpha = 0.9f - k * 0.25f), unit * (0.07f + k * 0.04f), at(tt), style = Stroke(unit * 0.025f))
        }
        FxStyle.DRAGON -> {
            val rainbow = listOf(Color(0xFFFF8A80), Color(0xFFFFCC80), Color(0xFFFFF59D), Color(0xFFA5D6A7), Color(0xFF90CAF9), style.main)
            for ((i, col) in rainbow.withIndex()) {
                val tt = t - i * 0.04f
                if (tt < 0f) continue
                drawCircle(col, unit * 0.06f, at(tt) + side * (sin(tt * 6f * PI.toFloat()) * unit * 0.15f))
            }
        }
        FxStyle.NORMAL, FxStyle.HEAL -> {
            val head = at(t)
            val back = lerp(from, to, (t - 0.18f).coerceAtLeast(0f))
            for (k in -1..1) drawLine(style.main.copy(alpha = 0.6f), back + side * (unit * 0.06f * k), head + side * (unit * 0.06f * k), unit * 0.02f, StrokeCap.Round)
            drawCircle(style.light, unit * 0.08f, head)
            drawCircle(style.main, unit * 0.08f, head, style = Stroke(unit * 0.02f))
        }
    }
}

private fun DrawScope.drawRock(c: Offset, r: Float, spin: Float, style: FxStyle) {
    val p = Path()
    for (i in 0 until 6) {
        val ang = Math.toRadians(spin + i * 60.0 + if (i % 2 == 0) 10.0 else -8.0)
        val rr = if (i % 2 == 0) r else r * 0.8f
        val x = (c.x + rr * cos(ang)).toFloat()
        val y = (c.y + rr * sin(ang)).toFloat()
        if (i == 0) p.moveTo(x, y) else p.lineTo(x, y)
    }
    p.close()
    drawPath(p, style.light)
    drawPath(p, style.main, style = Stroke(r * 0.25f))
}

// ---- Hit and miss -------------------------------------------------------------------------------

/** On the target: rays and a ring, plus the move's own touch (droplets, sparks, leaves, dust). */
private fun DrawScope.drawBurst(style: FxStyle, c: Offset, k: Float, unit: Float) {
    val fade = 1f - k
    val reach = unit * (0.15f + 0.3f * k)
    for (i in 0 until 8) {
        val ang = i * PI / 4
        val dir = Offset(cos(ang).toFloat(), sin(ang).toFloat())
        drawLine(style.main.copy(alpha = fade), c + dir * (reach * 0.45f), c + dir * reach, unit * 0.03f, StrokeCap.Round)
    }
    drawCircle(style.light.copy(alpha = fade * 0.8f), unit * (0.1f + 0.25f * k), c, style = Stroke(unit * 0.03f))
    when (style) {
        FxStyle.WATER, FxStyle.BUG -> for (i in 0 until 6) {
            val ang = -PI / 2 + (i - 2.5) * 0.45
            val p = c + Offset(cos(ang).toFloat(), sin(ang).toFloat()) * (unit * 0.35f * k) + Offset(0f, unit * 0.4f * k * k)
            drawCircle(style.main.copy(alpha = fade), unit * 0.035f, p)
        }
        FxStyle.FIRE, FxStyle.ELECTRIC -> drawCircle(style.light.copy(alpha = fade * 0.6f), unit * 0.22f * (0.5f + k), c)
        FxStyle.LEAF -> for (i in 0 until 4) {
            val p = c + Offset((i - 1.5f) * unit * 0.14f, unit * 0.3f * k)
            rotate(k * 400f + i * 70f, pivot = p) { drawOval(style.main.copy(alpha = fade), topLeft = p - Offset(unit * 0.05f, unit * 0.025f), size = Size(unit * 0.1f, unit * 0.05f)) }
        }
        FxStyle.ROCK -> drawOval(style.light.copy(alpha = fade * 0.7f), topLeft = c + Offset(-unit * 0.3f * (0.6f + k), unit * 0.1f), size = Size(unit * 0.6f * (0.6f + k), unit * 0.18f))
        FxStyle.NORMAL -> drawStar(c, unit * 0.16f * (0.6f + k), Color.White.copy(alpha = fade), style.main.copy(alpha = fade))
        else -> Unit
    }
}

/** A miss: the shot hits the ground beside the target and splashes up, with a little dust. */
private fun DrawScope.drawSplash(style: FxStyle, c: Offset, k: Float, unit: Float) {
    val fade = 1f - k
    drawOval(Color(0xFFBDB6A8).copy(alpha = fade * 0.7f), topLeft = c + Offset(-unit * 0.25f * (0.5f + k), unit * 0.02f), size = Size(unit * 0.5f * (0.5f + k), unit * 0.12f))
    for (i in 0 until 7) {
        val ang = -PI / 2 + (i - 3) * 0.35
        val p = c + Offset(cos(ang).toFloat(), sin(ang).toFloat()) * (unit * 0.3f * k) + Offset(0f, unit * 0.35f * k * k)
        drawCircle((if (i % 2 == 0) style.main else style.light).copy(alpha = fade), unit * 0.03f, p)
    }
}

private fun DrawScope.drawStar(c: Offset, r: Float, fill: Color, edge: Color) {
    val p = Path()
    for (i in 0 until 10) {
        val ang = Math.toRadians(-90.0 + i * 36)
        val rr = if (i % 2 == 0) r else r * 0.45f
        val x = (c.x + rr * cos(ang)).toFloat()
        val y = (c.y + rr * sin(ang)).toFloat()
        if (i == 0) p.moveTo(x, y) else p.lineTo(x, y)
    }
    p.close()
    drawPath(p, fill)
    drawPath(p, edge, style = Stroke(r * 0.12f))
}

/** Rest: green plus signs and sparkles rise around the creature. */
private fun DrawScope.drawHeal(c: Offset, unit: Float, t: Float) {
    val fade = sin(PI.toFloat() * t)
    for (i in 0 until 6) {
        val x = c.x + (i - 2.5f) * unit * 0.16f
        val y = c.y + unit * 0.35f - t * unit * 0.7f - (i % 2) * unit * 0.12f
        val s = unit * 0.05f
        val col = (if (i % 2 == 0) FxStyle.HEAL.main else FxStyle.HEAL.light).copy(alpha = fade)
        drawLine(col, Offset(x - s, y), Offset(x + s, y), unit * 0.025f, StrokeCap.Round)
        drawLine(col, Offset(x, y - s), Offset(x, y + s), unit * 0.025f, StrokeCap.Round)
    }
    drawCircle(FxStyle.HEAL.light.copy(alpha = fade * 0.35f), unit * 0.5f, c)
}
