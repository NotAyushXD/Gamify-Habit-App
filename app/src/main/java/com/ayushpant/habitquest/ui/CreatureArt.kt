package com.ayushpant.habitquest.ui

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import com.ayushpant.habitquest.balance.Dimension
import com.ayushpant.habitquest.rewards.CompanionState
import com.ayushpant.habitquest.rewards.CreatureDef

/*
 * Every creature is drawn in code from simple shapes (circles, ellipses, triangles, arcs), so the
 * app needs no image files and every design is our own (rule R14). The three states all look
 * calm or happy (rule R5): open eyes and a smile, or closed eyes and a smile when resting.
 */

/** Draws [def] at [stage] 1–3 in [state]. [grey] shows a creature the user does not have yet. */
@Composable
fun CreatureArt(def: CreatureDef, stage: Int, state: CompanionState, modifier: Modifier = Modifier, grey: Boolean = false) {
    Canvas(modifier) {
        val p = Palette.of(def, grey)
        if (!grey && stage >= 3) drawCircle(p.accent.copy(alpha = 0.18f), radius = size.minDimension * 0.5f, center = center)
        when (def.id) {
            "lumi" -> lumi(p, state)
            "pondra" -> pondra(p, state)
            "quillo" -> quillo(p, state)
            "orbit" -> orbit(p, state)
            "pebbit" -> pebbit(p, state)
            "trundle" -> trundle(p, state)
            "zippa" -> zippa(p, state)
            "bramb" -> bramb(p, state)
            "mossle" -> mossle(p, state)
            "dewin" -> dewin(p, state)
            "wisper" -> wisper(p, state)
            "solen" -> solen(p, state)
            else -> blob(p, state)
        }
        if (!grey && stage >= 2) star(p.accent, at(0.82f, 0.18f), s(0.07f))
    }
}

/** An egg with a fill that shows [progress] 0–1. */
@Composable
fun EggArt(progress: Float, tint: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val egg = Path().apply {
            moveTo(w * 0.5f, 0f)
            cubicTo(w * 0.95f, h * 0.05f, w, h * 0.65f, w * 0.5f, h)
            cubicTo(0f, h * 0.65f, w * 0.05f, h * 0.05f, w * 0.5f, 0f)
            close()
        }
        drawPath(egg, Color(0xFFF3EBDD))
        androidx.compose.ui.graphics.drawscope.clipPath(egg) {
            drawRect(tint.copy(alpha = 0.75f), topLeft = Offset(0f, h * (1f - progress)), size = Size(w, h * progress))
        }
        drawCircle(Color.White.copy(alpha = 0.6f), radius = w * 0.08f, center = Offset(w * 0.36f, h * 0.3f))
        drawCircle(Color.White.copy(alpha = 0.4f), radius = w * 0.05f, center = Offset(w * 0.62f, h * 0.55f))
        drawPath(egg, Color(0xFFBFA98A), style = Stroke(width = w * 0.04f))
    }
}

private data class Palette(val body: Color, val light: Color, val accent: Color, val ink: Color) {
    companion object {
        fun of(def: CreatureDef, grey: Boolean): Palette {
            if (grey) return Palette(Color(0xFFBDC3C0), Color(0xFFD5DAD7), Color(0xFFBDC3C0), Color(0xFF9AA19D))
            val accent = when (def.dimension) {
                Dimension.MIND -> Color(0xFF5B5BD6)
                Dimension.BODY -> Color(0xFFE5624F)
                Dimension.SOUL -> Color(0xFFF2A922)
            }
            val body = when (def.id) {
                "lumi" -> Color(0xFFCFC8FF); "pondra" -> Color(0xFF8FBF9F); "quillo" -> Color(0xFFF0A868); "orbit" -> Color(0xFF9FB3D9)
                "pebbit" -> Color(0xFFB9B3AE); "trundle" -> Color(0xFFA67C52); "zippa" -> Color(0xFFF2C14E); "bramb" -> Color(0xFF8C6E63)
                "mossle" -> Color(0xFF7FC08A); "dewin" -> Color(0xFFD9B38C); "wisper" -> Color(0xFFCFE3F2); "solen" -> Color(0xFF9EC9E2)
                else -> Color(0xFFCCCCCC)
            }
            return Palette(body, body.copy(alpha = 0.55f).compositeOverWhite(), accent, Color(0xFF2B2F2E))
        }

        private fun Color.compositeOverWhite() = Color(red * alpha + (1 - alpha), green * alpha + (1 - alpha), blue * alpha + (1 - alpha))
    }
}

// ---- Helpers: fractions of the canvas, so every size works ---------------------------------------

private fun DrawScope.s(f: Float) = size.minDimension * f
private fun DrawScope.at(x: Float, y: Float) = Offset(size.width * x, size.height * y)

private fun DrawScope.ellipse(color: Color, cx: Float, cy: Float, rx: Float, ry: Float) =
    drawOval(color, topLeft = Offset(size.width * cx - s(rx), size.height * cy - s(ry)), size = Size(s(rx) * 2, s(ry) * 2))

private fun DrawScope.triangle(color: Color, a: Offset, b: Offset, c: Offset) =
    drawPath(Path().apply { moveTo(a.x, a.y); lineTo(b.x, b.y); lineTo(c.x, c.y); close() }, color)

/** Two eyes and a smile. Resting: closed, happy eyes. Active: bigger, brighter eyes. */
private fun DrawScope.face(p: Palette, state: CompanionState, cx: Float, cy: Float, gap: Float = 0.11f, eye: Float = 0.045f) {
    val r = if (state == CompanionState.ACTIVE) eye * 1.25f else eye
    for (dx in listOf(-gap, gap)) {
        val c = at(cx + dx, cy)
        if (state == CompanionState.RESTING) {
            drawArc(p.ink, 0f, 180f, false, topLeft = Offset(c.x - s(r), c.y - s(r)), size = Size(s(r) * 2, s(r) * 2), style = Stroke(s(0.018f)))
        } else {
            drawCircle(p.ink, s(r), c)
            drawCircle(Color.White, s(r) * 0.35f, Offset(c.x - s(r) * 0.3f, c.y - s(r) * 0.3f))
        }
    }
    val w = if (state == CompanionState.ACTIVE) 0.09f else 0.06f
    drawArc(p.ink, 20f, 140f, false, topLeft = Offset(at(cx, cy).x - s(w), at(cx, cy + 0.04f).y - s(w) * 0.6f), size = Size(s(w) * 2, s(w) * 1.2f), style = Stroke(s(0.018f)))
    if (state != CompanionState.RESTING) {
        drawCircle(p.accent.copy(alpha = 0.35f), s(0.035f), at(cx - gap - 0.06f, cy + 0.06f))
        drawCircle(p.accent.copy(alpha = 0.35f), s(0.035f), at(cx + gap + 0.06f, cy + 0.06f))
    }
}

private fun DrawScope.star(color: Color, c: Offset, r: Float) {
    val path = Path()
    for (i in 0 until 10) {
        val ang = Math.toRadians(-90.0 + i * 36)
        val rr = if (i % 2 == 0) r else r * 0.45f
        val x = (c.x + rr * Math.cos(ang)).toFloat()
        val y = (c.y + rr * Math.sin(ang)).toFloat()
        if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
    }
    path.close()
    drawPath(path, color)
}

// ---- The twelve creatures ------------------------------------------------------------------------

private fun DrawScope.blob(p: Palette, st: CompanionState) {
    ellipse(p.body, 0.5f, 0.58f, 0.34f, 0.32f)
    face(p, st, 0.5f, 0.55f)
}

/** Lumi, the glow-moth: a round body with two wide wings and antennae. */
private fun DrawScope.lumi(p: Palette, st: CompanionState) {
    ellipse(p.light, 0.26f, 0.5f, 0.24f, 0.3f)
    ellipse(p.light, 0.74f, 0.5f, 0.24f, 0.3f)
    ellipse(p.accent.copy(alpha = 0.25f), 0.26f, 0.5f, 0.12f, 0.15f)
    ellipse(p.accent.copy(alpha = 0.25f), 0.74f, 0.5f, 0.12f, 0.15f)
    ellipse(p.body, 0.5f, 0.58f, 0.2f, 0.3f)
    for (dx in listOf(-0.08f, 0.08f)) {
        drawLine(p.ink, at(0.5f + dx, 0.32f), at(0.5f + dx * 2.2f, 0.12f), strokeWidth = s(0.015f))
        drawCircle(p.accent, s(0.03f), at(0.5f + dx * 2.2f, 0.12f))
    }
    face(p, st, 0.5f, 0.52f, gap = 0.07f, eye = 0.04f)
}

/** Pondra, the thinking turtle: a dome shell with a round head and four feet. */
private fun DrawScope.pondra(p: Palette, st: CompanionState) {
    for (x in listOf(0.3f, 0.7f)) ellipse(p.light, x, 0.82f, 0.09f, 0.07f)
    ellipse(p.light, 0.22f, 0.58f, 0.14f, 0.13f)
    drawArc(p.body, 180f, 180f, true, topLeft = at(0.18f, 0.3f), size = Size(size.width * 0.72f, size.height * 0.56f))
    drawArc(p.accent.copy(alpha = 0.3f), 180f, 180f, true, topLeft = at(0.3f, 0.4f), size = Size(size.width * 0.48f, size.height * 0.36f))
    face(p, st, 0.22f, 0.55f, gap = 0.05f, eye = 0.035f)
}

/** Quillo, the feathered fox: pointed ears, a bushy tail and a feather on the head. */
private fun DrawScope.quillo(p: Palette, st: CompanionState) {
    ellipse(p.light, 0.8f, 0.7f, 0.16f, 0.1f)
    triangle(p.body, at(0.3f, 0.42f), at(0.26f, 0.12f), at(0.46f, 0.36f))
    triangle(p.body, at(0.7f, 0.42f), at(0.74f, 0.12f), at(0.54f, 0.36f))
    ellipse(p.body, 0.5f, 0.6f, 0.32f, 0.28f)
    ellipse(p.light, 0.5f, 0.7f, 0.16f, 0.12f)
    ellipse(p.accent, 0.5f, 0.18f, 0.035f, 0.1f)
    face(p, st, 0.5f, 0.55f)
}

/** Orbit, the round owl: big eye discs, a small beak and a ring around it. */
private fun DrawScope.orbit(p: Palette, st: CompanionState) {
    drawCircle(p.accent.copy(alpha = 0.5f), s(0.46f), center, style = Stroke(s(0.02f)))
    ellipse(p.body, 0.5f, 0.55f, 0.32f, 0.32f)
    for (dx in listOf(-0.12f, 0.12f)) ellipse(p.light, 0.5f + dx, 0.5f, 0.12f, 0.12f)
    triangle(p.accent, at(0.47f, 0.6f), at(0.53f, 0.6f), at(0.5f, 0.67f))
    face(p, st, 0.5f, 0.5f, gap = 0.12f, eye = 0.05f)
}

/** Pebbit, the pebble rabbit: a smooth rounded body with two long ears. */
private fun DrawScope.pebbit(p: Palette, st: CompanionState) {
    for (dx in listOf(-0.12f, 0.12f)) {
        ellipse(p.body, 0.5f + dx, 0.22f, 0.08f, 0.2f)
        ellipse(p.accent.copy(alpha = 0.35f), 0.5f + dx, 0.24f, 0.04f, 0.13f)
    }
    drawRoundRect(p.body, topLeft = at(0.16f, 0.36f), size = Size(size.width * 0.68f, size.height * 0.52f), cornerRadius = androidx.compose.ui.geometry.CornerRadius(s(0.22f)))
    face(p, st, 0.5f, 0.6f)
}

/** Trundle, the leaf-bear: a big round body, round ears and a leaf on the head. */
private fun DrawScope.trundle(p: Palette, st: CompanionState) {
    for (dx in listOf(-0.24f, 0.24f)) ellipse(p.body, 0.5f + dx, 0.3f, 0.11f, 0.11f)
    ellipse(p.body, 0.5f, 0.58f, 0.34f, 0.32f)
    ellipse(p.light, 0.5f, 0.68f, 0.14f, 0.1f)
    ellipse(p.accent, 0.56f, 0.2f, 0.09f, 0.045f)
    drawLine(p.ink, at(0.5f, 0.26f), at(0.6f, 0.17f), strokeWidth = s(0.012f))
    face(p, st, 0.5f, 0.54f)
}

/** Zippa, the spark-lizard: a long body, a curled tail and a little spark. */
private fun DrawScope.zippa(p: Palette, st: CompanionState) {
    drawArc(p.body, 20f, 200f, false, topLeft = at(0.5f, 0.55f), size = Size(size.width * 0.42f, size.height * 0.3f), style = Stroke(s(0.09f)))
    ellipse(p.body, 0.42f, 0.6f, 0.3f, 0.22f)
    ellipse(p.light, 0.42f, 0.68f, 0.14f, 0.08f)
    for (x in listOf(0.3f, 0.5f)) ellipse(p.accent.copy(alpha = 0.5f), x, 0.42f, 0.04f, 0.04f)
    star(p.accent, at(0.84f, 0.3f), s(0.06f))
    face(p, st, 0.42f, 0.57f, gap = 0.09f)
}

/** Bramb, the bramble boar: a wide body, a round snout and tiny friendly tusks. */
private fun DrawScope.bramb(p: Palette, st: CompanionState) {
    for (dx in listOf(-0.26f, 0.26f)) triangle(p.body, at(0.5f + dx, 0.4f), at(0.5f + dx * 1.2f, 0.18f), at(0.5f + dx * 0.5f, 0.32f))
    ellipse(p.body, 0.5f, 0.6f, 0.36f, 0.28f)
    ellipse(p.light, 0.5f, 0.7f, 0.12f, 0.08f)
    drawCircle(p.ink, s(0.015f), at(0.46f, 0.7f)); drawCircle(p.ink, s(0.015f), at(0.54f, 0.7f))
    for (x in listOf(0.34f, 0.66f)) drawCircle(Color.White, s(0.02f), at(x, 0.74f))
    for (x in listOf(0.3f, 0.42f, 0.56f, 0.7f)) drawCircle(p.accent.copy(alpha = 0.6f), s(0.02f), at(x, 0.4f))
    face(p, st, 0.5f, 0.55f, gap = 0.12f, eye = 0.04f)
}

/** Mossle, the moss sprite: a soft round body with moss tufts on top. */
private fun DrawScope.mossle(p: Palette, st: CompanionState) {
    for ((i, x) in listOf(0.34f, 0.46f, 0.58f, 0.68f).withIndex()) ellipse(p.accent.copy(alpha = 0.55f), x, 0.26f - (i % 2) * 0.04f, 0.07f, 0.06f)
    ellipse(p.body, 0.5f, 0.6f, 0.32f, 0.3f)
    ellipse(p.light, 0.5f, 0.7f, 0.14f, 0.09f)
    face(p, st, 0.5f, 0.56f)
}

/** Dewin, the dew-drop deer: small antlers and a shining drop on the head. */
private fun DrawScope.dewin(p: Palette, st: CompanionState) {
    for (dx in listOf(-0.16f, 0.16f)) {
        drawLine(p.ink, at(0.5f + dx, 0.34f), at(0.5f + dx * 1.4f, 0.1f), strokeWidth = s(0.02f))
        drawLine(p.ink, at(0.5f + dx * 1.2f, 0.22f), at(0.5f + dx * 1.9f, 0.16f), strokeWidth = s(0.02f))
    }
    ellipse(p.body, 0.5f, 0.6f, 0.3f, 0.3f)
    ellipse(p.light, 0.5f, 0.72f, 0.13f, 0.08f)
    drawCircle(p.accent.copy(alpha = 0.8f), s(0.05f), at(0.5f, 0.3f))
    drawCircle(Color.White.copy(alpha = 0.8f), s(0.015f), at(0.48f, 0.28f))
    face(p, st, 0.5f, 0.56f)
}

/** Wisper, the wind-cat: pointed ears and a curled tail like a gust of wind. */
private fun DrawScope.wisper(p: Palette, st: CompanionState) {
    drawArc(p.accent.copy(alpha = 0.5f), 200f, 250f, false, topLeft = at(0.6f, 0.5f), size = Size(size.width * 0.34f, size.height * 0.34f), style = Stroke(s(0.05f)))
    triangle(p.body, at(0.26f, 0.42f), at(0.28f, 0.14f), at(0.46f, 0.36f))
    triangle(p.body, at(0.74f, 0.42f), at(0.72f, 0.14f), at(0.54f, 0.36f))
    ellipse(p.body, 0.5f, 0.6f, 0.32f, 0.28f)
    for (dx in listOf(-0.16f, 0.16f)) drawLine(p.ink, at(0.5f + dx * 1.1f, 0.64f), at(0.5f + dx * 2.0f, 0.62f), strokeWidth = s(0.012f))
    face(p, st, 0.5f, 0.55f)
}

/** Solen, the sun-flower seal: petals around a round, shiny face. */
private fun DrawScope.solen(p: Palette, st: CompanionState) {
    for (i in 0 until 10) {
        val ang = Math.toRadians(i * 36.0)
        val c = Offset((center.x + s(0.36f) * Math.cos(ang)).toFloat(), (center.y + s(0.36f) * Math.sin(ang)).toFloat())
        drawCircle(p.accent.copy(alpha = 0.7f), s(0.09f), c)
    }
    ellipse(p.body, 0.5f, 0.5f, 0.28f, 0.28f)
    ellipse(p.light, 0.5f, 0.6f, 0.12f, 0.08f)
    face(p, st, 0.5f, 0.47f)
}
