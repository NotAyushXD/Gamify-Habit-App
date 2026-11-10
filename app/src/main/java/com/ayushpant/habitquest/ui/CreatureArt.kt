package com.ayushpant.habitquest.ui

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import com.ayushpant.habitquest.balance.Dimension
import com.ayushpant.habitquest.rewards.CompanionState
import com.ayushpant.habitquest.rewards.CreatureDef

/*
 * Every creature is drawn in code from simple shapes (circles, ellipses, triangles, arcs), so the
 * app needs no image files and copies no artwork. The three states all look calm or happy
 * (rule R5): open eyes and a smile, or closed eyes and a smile when resting.
 */

/** Draws [def] at [stage] 1–3 in [state]. [grey] shows a creature the user does not have yet. */
@Composable
fun CreatureArt(def: CreatureDef, stage: Int, state: CompanionState, modifier: Modifier = Modifier, grey: Boolean = false) {
    Canvas(modifier) {
        val p = Palette.of(def, grey)
        if (!grey && stage >= 3) drawCircle(p.accent.copy(alpha = 0.18f), radius = size.minDimension * 0.5f, center = center)
        when (def.id) {
            "squirtgorgon" -> squirtgorgon(p, state)
            "wartorion" -> wartorion(p, state)
            "blastochimera" -> blastochimera(p, state)
            "caterstellar" -> caterstellar(p, state)
            "metapodas" -> metapodas(p, state)
            "butterfreeas" -> butterfreeas(p, state)
            "charignis" -> charignis(p, state)
            "charmelion" -> charmelion(p, state)
            "charititan" -> charititan(p, state)
            "pikastellar" -> pikastellar(p, state)
            "raichura" -> raichura(p, state)
            "geodudonis" -> geodudonis(p, state)
            "bulbavulpis" -> bulbavulpis(p, state)
            "iviscar" -> iviscar(p, state)
            "venusauris" -> venusauris(p, state)
            "gengachimera" -> gengachimera(p, state)
            "snorlaura" -> snorlaura(p, state)
            "dragonairis" -> dragonairis(p, state)
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
        clipPath(egg) {
            drawRect(tint.copy(alpha = 0.75f), topLeft = Offset(0f, h * (1f - progress)), size = Size(w, h * progress))
        }
        drawCircle(Color.White.copy(alpha = 0.6f), radius = w * 0.08f, center = Offset(w * 0.36f, h * 0.3f))
        drawCircle(Color.White.copy(alpha = 0.4f), radius = w * 0.05f, center = Offset(w * 0.62f, h * 0.55f))
        drawPath(egg, Color(0xFFBFA98A), style = Stroke(width = w * 0.04f))
    }
}

private data class Palette(val body: Color, val light: Color, val accent: Color, val ink: Color, val grey: Boolean) {
    /** A fixed extra colour (a rainbow band, a flower), grey for a creature not hatched yet. */
    fun tint(c: Color): Color = if (grey) light else c

    companion object {
        fun of(def: CreatureDef, grey: Boolean): Palette {
            if (grey) return Palette(Color(0xFFBDC3C0), Color(0xFFD5DAD7), Color(0xFFBDC3C0), Color(0xFF9AA19D), true)
            val accent = when (def.dimension) {
                Dimension.MIND -> Color(0xFF5B5BD6)
                Dimension.BODY -> Color(0xFFE5624F)
                Dimension.SOUL -> Color(0xFFF2A922)
            }
            val body = when (def.id) {
                "squirtgorgon" -> Color(0xFF8CC8E8); "wartorion" -> Color(0xFF7FB2E0); "blastochimera" -> Color(0xFF6E9FD4)
                "caterstellar" -> Color(0xFFB7E08C); "metapodas" -> Color(0xFFE8C15A); "butterfreeas" -> Color(0xFFCFC8FF)
                "charignis" -> Color(0xFFF4A261); "charmelion" -> Color(0xFFE9845A); "charititan" -> Color(0xFFF08A4B)
                "pikastellar" -> Color(0xFFF7D154); "raichura" -> Color(0xFFF0B44C); "geodudonis" -> Color(0xFFB9B3AE)
                "bulbavulpis" -> Color(0xFF8FCB9B); "iviscar" -> Color(0xFF6FAF8A); "venusauris" -> Color(0xFF5E9E7A)
                "gengachimera" -> Color(0xFFC9B8F0); "snorlaura" -> Color(0xFF9DB7C9); "dragonairis" -> Color(0xFF9EC9E2)
                else -> Color(0xFFCCCCCC)
            }
            return Palette(body, body.copy(alpha = 0.55f).compositeOverWhite(), accent, Color(0xFF2B2F2E), false)
        }

        private fun Color.compositeOverWhite() = Color(red * alpha + (1 - alpha), green * alpha + (1 - alpha), blue * alpha + (1 - alpha))
    }
}

private val SKY = Color(0xFF8FD3F4)
private val LEAF = Color(0xFF6DBE6D)
private val PETAL = Color(0xFFF49AC1)
private val RAINBOW = listOf(Color(0xFFFF8A80), Color(0xFFFFCC80), Color(0xFFFFF59D), Color(0xFFA5D6A7), Color(0xFF90CAF9))
private val AURORA = listOf(Color(0xFF80CBC4), Color(0xFFB39DDB), Color(0xFFA5D6A7))

// ---- Helpers: fractions of the canvas, so every size works ---------------------------------------

private fun DrawScope.s(f: Float) = size.minDimension * f
private fun DrawScope.at(x: Float, y: Float) = Offset(size.width * x, size.height * y)

private fun DrawScope.ellipse(color: Color, cx: Float, cy: Float, rx: Float, ry: Float) =
    drawOval(color, topLeft = Offset(size.width * cx - s(rx), size.height * cy - s(ry)), size = Size(s(rx) * 2, s(ry) * 2))

private fun DrawScope.triangle(color: Color, a: Offset, b: Offset, c: Offset) =
    drawPath(Path().apply { moveTo(a.x, a.y); lineTo(b.x, b.y); lineTo(c.x, c.y); close() }, color)

/** A smooth line through canvas fractions with round ends: a start point, then 3 points per cubic curve. */
private fun DrawScope.curve(color: Color, width: Float, vararg pts: Pair<Float, Float>) {
    val o = pts.map { (x, y) -> at(x, y) }
    val path = Path().apply {
        moveTo(o[0].x, o[0].y)
        for (i in 1 until o.size - 2 step 3) cubicTo(o[i].x, o[i].y, o[i + 1].x, o[i + 1].y, o[i + 2].x, o[i + 2].y)
    }
    drawPath(path, color, style = Stroke(s(width), cap = StrokeCap.Round))
}

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

/** A flower: [n] round petals around a centre. */
private fun DrawScope.flower(petal: Color, middle: Color, c: Offset, r: Float, n: Int = 5) {
    for (i in 0 until n) {
        val ang = Math.toRadians(-90.0 + i * 360.0 / n)
        drawCircle(petal, r * 0.55f, Offset((c.x + r * Math.cos(ang)).toFloat(), (c.y + r * Math.sin(ang)).toFloat()))
    }
    drawCircle(middle, r * 0.5f, c)
}

// ---- The creatures of theme 1 --------------------------------------------------------------------

private fun DrawScope.blob(p: Palette, st: CompanionState) {
    ellipse(p.body, 0.5f, 0.58f, 0.34f, 0.32f)
    face(p, st, 0.5f, 0.55f)
}

// Mind

/** Squirtgorgon: a small swimmer with a round head, a marked shell and a curled tail. */
private fun DrawScope.squirtgorgon(p: Palette, st: CompanionState) {
    curve(p.body, 0.05f, 0.76f to 0.7f, 0.92f to 0.66f, 0.92f to 0.52f, 0.82f to 0.5f)
    for (x in listOf(0.38f, 0.7f)) ellipse(p.body, x, 0.84f, 0.07f, 0.05f)
    ellipse(p.light, 0.56f, 0.64f, 0.26f, 0.21f)
    for (dx in listOf(-0.1f, 0f, 0.1f)) drawArc(p.accent.copy(alpha = 0.5f), 200f, 140f, false, topLeft = at(0.5f + dx, 0.56f), size = Size(s(0.12f), s(0.1f)), style = Stroke(s(0.014f)))
    ellipse(p.body, 0.32f, 0.42f, 0.2f, 0.19f)
    face(p, st, 0.32f, 0.42f, gap = 0.07f, eye = 0.035f)
}

/** Wartorion: a wave-riding turtle with curly wave ears and a wave for a tail. */
private fun DrawScope.wartorion(p: Palette, st: CompanionState) {
    curve(p.tint(SKY), 0.04f, 0.1f to 0.9f, 0.3f to 0.8f, 0.5f to 1.0f, 0.9f to 0.88f)
    for (x in listOf(0.34f, 0.7f)) ellipse(p.light, x, 0.8f, 0.08f, 0.06f)
    drawArc(p.body, 180f, 180f, true, topLeft = at(0.28f, 0.42f), size = Size(size.width * 0.62f, size.height * 0.46f))
    drawArc(p.accent.copy(alpha = 0.3f), 180f, 180f, true, topLeft = at(0.4f, 0.52f), size = Size(size.width * 0.38f, size.height * 0.26f))
    ellipse(p.light, 0.26f, 0.48f, 0.17f, 0.16f)
    for (dx in listOf(-0.1f, 0.1f)) drawArc(p.light, 120f, 270f, false, topLeft = at(0.2f + dx, 0.22f), size = Size(s(0.13f), s(0.13f)), style = Stroke(s(0.04f), cap = StrokeCap.Round))
    face(p, st, 0.26f, 0.48f, gap = 0.06f, eye = 0.035f)
}

/** Blastochimera: a great round shell-fortress with two little shell towers and a calm face. */
private fun DrawScope.blastochimera(p: Palette, st: CompanionState) {
    for (x in listOf(0.24f, 0.76f)) {
        drawRoundRect(p.accent.copy(alpha = 0.55f), topLeft = at(x - 0.06f, 0.22f), size = Size(s(0.12f), s(0.2f)), cornerRadius = androidx.compose.ui.geometry.CornerRadius(s(0.05f)))
        drawCircle(p.light, s(0.04f), at(x, 0.24f))
    }
    drawRoundRect(p.body, topLeft = at(0.14f, 0.34f), size = Size(size.width * 0.72f, size.height * 0.56f), cornerRadius = androidx.compose.ui.geometry.CornerRadius(s(0.26f)))
    for (y in listOf(0.5f, 0.62f, 0.74f)) drawLine(p.accent.copy(alpha = 0.3f), at(0.22f, y), at(0.78f, y), strokeWidth = s(0.016f))
    ellipse(p.light, 0.5f, 0.5f, 0.17f, 0.14f)
    face(p, st, 0.5f, 0.5f, gap = 0.07f, eye = 0.035f)
}

/** Caterstellar: a tiny caterpillar of round segments, each with a little star. */
private fun DrawScope.caterstellar(p: Palette, st: CompanionState) {
    val segs = listOf(0.82f to 0.7f, 0.66f to 0.66f, 0.5f to 0.64f)
    for ((x, y) in segs) {
        ellipse(p.body, x, y, 0.12f, 0.12f)
        star(p.accent.copy(alpha = 0.7f), at(x, y - 0.02f), s(0.04f))
    }
    ellipse(p.body, 0.3f, 0.56f, 0.19f, 0.19f)
    for (dx in listOf(-0.06f, 0.06f)) {
        drawLine(p.ink, at(0.3f + dx, 0.4f), at(0.3f + dx * 2f, 0.26f), strokeWidth = s(0.014f))
        star(p.accent, at(0.3f + dx * 2f, 0.26f), s(0.035f))
    }
    face(p, st, 0.3f, 0.56f, gap = 0.07f, eye = 0.04f)
}

/** Metapodas: a golden chrysalis hanging from a little stem, with a shine. */
private fun DrawScope.metapodas(p: Palette, st: CompanionState) {
    drawLine(p.ink, at(0.5f, 0.04f), at(0.5f, 0.16f), strokeWidth = s(0.02f))
    val w = size.width; val h = size.height
    val body = Path().apply {
        moveTo(w * 0.5f, h * 0.14f)
        cubicTo(w * 0.82f, h * 0.22f, w * 0.78f, h * 0.78f, w * 0.5f, h * 0.94f)
        cubicTo(w * 0.22f, h * 0.78f, w * 0.18f, h * 0.22f, w * 0.5f, h * 0.14f)
        close()
    }
    drawPath(body, p.body)
    for (y in listOf(0.62f, 0.74f)) drawArc(p.accent.copy(alpha = 0.3f), 20f, 140f, false, topLeft = at(0.34f, y - 0.06f), size = Size(s(0.32f), s(0.1f)), style = Stroke(s(0.014f)))
    ellipse(Color.White.copy(alpha = if (p.grey) 0.2f else 0.6f), 0.4f, 0.32f, 0.04f, 0.08f)
    face(p, st, 0.5f, 0.44f, gap = 0.08f, eye = 0.035f)
}

/** Butterfreeas: a butterfly with four soft wings dotted with cool sky colour. */
private fun DrawScope.butterfreeas(p: Palette, st: CompanionState) {
    for (dx in listOf(-1f, 1f)) {
        ellipse(p.light, 0.5f + dx * 0.24f, 0.38f, 0.22f, 0.2f)
        ellipse(p.light, 0.5f + dx * 0.2f, 0.72f, 0.15f, 0.14f)
        drawCircle(p.tint(SKY).copy(alpha = 0.7f), s(0.05f), at(0.5f + dx * 0.28f, 0.36f))
        drawCircle(p.tint(SKY).copy(alpha = 0.5f), s(0.03f), at(0.5f + dx * 0.2f, 0.74f))
    }
    ellipse(p.body, 0.5f, 0.56f, 0.13f, 0.28f)
    for (dx in listOf(-0.05f, 0.05f)) {
        drawLine(p.ink, at(0.5f + dx, 0.32f), at(0.5f + dx * 2.6f, 0.12f), strokeWidth = s(0.014f))
        drawCircle(p.accent, s(0.025f), at(0.5f + dx * 2.6f, 0.12f))
    }
    face(p, st, 0.5f, 0.46f, gap = 0.05f, eye = 0.03f)
}

// Body

/** Charignis: a small lizard with a warm ember glow at the end of its tail. */
private fun DrawScope.charignis(p: Palette, st: CompanionState) {
    curve(p.body, 0.07f, 0.6f to 0.78f, 0.8f to 0.82f, 0.92f to 0.66f, 0.86f to 0.5f)
    drawCircle(p.accent.copy(alpha = 0.35f), s(0.08f), at(0.86f, 0.46f))
    drawCircle(p.tint(Color(0xFFFFD166)), s(0.04f), at(0.86f, 0.46f))
    ellipse(p.body, 0.46f, 0.68f, 0.2f, 0.18f)
    ellipse(p.light, 0.46f, 0.74f, 0.1f, 0.09f)
    ellipse(p.body, 0.4f, 0.4f, 0.2f, 0.19f)
    face(p, st, 0.4f, 0.4f, gap = 0.07f, eye = 0.038f)
}

/** Charmelion: a proud flame beast with a crest of sun rays and a sun behind it. */
private fun DrawScope.charmelion(p: Palette, st: CompanionState) {
    drawCircle(p.tint(Color(0xFFFFD166)).copy(alpha = 0.45f), s(0.2f), at(0.74f, 0.26f))
    curve(p.body, 0.07f, 0.62f to 0.82f, 0.84f to 0.86f, 0.94f to 0.7f, 0.88f to 0.56f)
    ellipse(p.body, 0.46f, 0.66f, 0.24f, 0.22f)
    ellipse(p.light, 0.46f, 0.72f, 0.12f, 0.1f)
    for (dx in listOf(-0.08f, 0f, 0.08f)) triangle(p.accent, at(0.42f + dx - 0.035f, 0.24f), at(0.42f + dx + 0.035f, 0.24f), at(0.42f + dx, 0.12f - if (dx == 0f) 0.04f else 0f))
    ellipse(p.body, 0.42f, 0.36f, 0.2f, 0.17f)
    face(p, st, 0.42f, 0.37f, gap = 0.07f, eye = 0.035f)
}

/** Charititan: a big, kind flying titan with wide wings and two small horns. */
private fun DrawScope.charititan(p: Palette, st: CompanionState) {
    for (dx in listOf(-1f, 1f)) {
        triangle(p.accent.copy(alpha = 0.55f), at(0.5f + dx * 0.12f, 0.46f), at(0.5f + dx * 0.48f, 0.14f), at(0.5f + dx * 0.4f, 0.62f))
        triangle(p.body, at(0.5f + dx * 0.08f, 0.24f), at(0.5f + dx * 0.14f, 0.08f), at(0.5f + dx * 0.16f, 0.26f))
    }
    ellipse(p.body, 0.5f, 0.66f, 0.26f, 0.24f)
    ellipse(p.light, 0.5f, 0.72f, 0.14f, 0.13f)
    ellipse(p.body, 0.5f, 0.36f, 0.19f, 0.16f)
    face(p, st, 0.5f, 0.37f, gap = 0.07f, eye = 0.035f)
}

/** Pikastellar: a small spark creature with tall ears, star cheeks and a zigzag tail. */
private fun DrawScope.pikastellar(p: Palette, st: CompanionState) {
    val w = size.width; val h = size.height
    val tail = Path().apply {
        moveTo(w * 0.7f, h * 0.72f); lineTo(w * 0.84f, h * 0.6f); lineTo(w * 0.78f, h * 0.52f); lineTo(w * 0.94f, h * 0.38f)
    }
    drawPath(tail, p.accent, style = Stroke(s(0.05f), cap = StrokeCap.Round))
    for (dx in listOf(-1f, 1f)) {
        triangle(p.body, at(0.5f + dx * 0.08f, 0.36f), at(0.5f + dx * 0.24f, 0.06f), at(0.5f + dx * 0.24f, 0.36f))
        triangle(p.ink.copy(alpha = 0.7f), at(0.5f + dx * 0.2f, 0.14f), at(0.5f + dx * 0.24f, 0.06f), at(0.5f + dx * 0.24f, 0.16f))
    }
    ellipse(p.body, 0.5f, 0.6f, 0.28f, 0.27f)
    ellipse(p.light, 0.5f, 0.72f, 0.12f, 0.09f)
    face(p, st, 0.5f, 0.55f, gap = 0.1f, eye = 0.04f)
    if (st != CompanionState.RESTING) for (dx in listOf(-0.18f, 0.18f)) star(p.accent, at(0.5f + dx, 0.62f), s(0.035f))
}

/** Raichura: a taller spark beast with round ears and a tail that shimmers like northern lights. */
private fun DrawScope.raichura(p: Palette, st: CompanionState) {
    for ((i, c) in AURORA.withIndex()) curve(p.tint(c), 0.035f, 0.66f to 0.78f, 0.86f to 0.7f - i * 0.05f, 0.8f to 0.36f - i * 0.04f, 0.96f to 0.2f - i * 0.03f)
    for (dx in listOf(-0.16f, 0.16f)) ellipse(p.body, 0.46f + dx, 0.22f, 0.08f, 0.13f)
    ellipse(p.body, 0.46f, 0.58f, 0.27f, 0.3f)
    ellipse(p.light, 0.46f, 0.7f, 0.13f, 0.1f)
    face(p, st, 0.46f, 0.5f, gap = 0.09f, eye = 0.04f)
}

/** Geodudonis: a rough little boulder with a shining crystal heart. */
private fun DrawScope.geodudonis(p: Palette, st: CompanionState) {
    val pts = listOf(0.2f to 0.5f, 0.3f to 0.26f, 0.52f to 0.18f, 0.74f to 0.28f, 0.84f to 0.52f, 0.76f to 0.82f, 0.5f to 0.9f, 0.26f to 0.8f)
    val rock = Path().apply {
        pts.forEachIndexed { i, (x, y) -> if (i == 0) moveTo(size.width * x, size.height * y) else lineTo(size.width * x, size.height * y) }
        close()
    }
    drawPath(rock, p.body)
    drawLine(p.ink.copy(alpha = 0.25f), at(0.3f, 0.3f), at(0.38f, 0.42f), strokeWidth = s(0.014f))
    drawLine(p.ink.copy(alpha = 0.25f), at(0.72f, 0.7f), at(0.64f, 0.78f), strokeWidth = s(0.014f))
    val gem = p.tint(Color(0xFF7FDBDA))
    triangle(gem, at(0.5f, 0.6f), at(0.42f, 0.7f), at(0.58f, 0.7f))
    triangle(gem.copy(alpha = 0.8f), at(0.42f, 0.7f), at(0.58f, 0.7f), at(0.5f, 0.82f))
    face(p, st, 0.5f, 0.44f, gap = 0.1f, eye = 0.04f)
}

// Soul

/** Bulbavulpis: a gentle plant-fox with leafy ears, a bushy tail and a bud on its head. */
private fun DrawScope.bulbavulpis(p: Palette, st: CompanionState) {
    ellipse(p.light, 0.8f, 0.72f, 0.16f, 0.1f)
    triangle(p.body, at(0.3f, 0.44f), at(0.24f, 0.16f), at(0.46f, 0.38f))
    triangle(p.body, at(0.7f, 0.44f), at(0.76f, 0.16f), at(0.54f, 0.38f))
    ellipse(p.body, 0.5f, 0.62f, 0.3f, 0.27f)
    ellipse(p.light, 0.5f, 0.72f, 0.15f, 0.11f)
    ellipse(p.tint(LEAF), 0.56f, 0.24f, 0.07f, 0.035f)
    ellipse(p.tint(PETAL), 0.5f, 0.24f, 0.045f, 0.07f)
    face(p, st, 0.5f, 0.57f)
}

/** Iviscar: a sturdy beetle with a split shell that keeps a flower safe on top. */
private fun DrawScope.iviscar(p: Palette, st: CompanionState) {
    for (y in listOf(0.58f, 0.68f, 0.78f)) for (dx in listOf(-1f, 1f)) drawLine(p.ink.copy(alpha = 0.6f), at(0.5f + dx * 0.26f, y), at(0.5f + dx * 0.36f, y + 0.04f), strokeWidth = s(0.014f))
    ellipse(p.body, 0.5f, 0.64f, 0.28f, 0.24f)
    drawLine(p.ink.copy(alpha = 0.3f), at(0.5f, 0.42f), at(0.5f, 0.88f), strokeWidth = s(0.014f))
    flower(p.tint(PETAL), p.accent, at(0.5f, 0.32f), s(0.09f))
    ellipse(p.light, 0.5f, 0.66f, 0.15f, 0.11f)
    face(p, st, 0.5f, 0.66f, gap = 0.07f, eye = 0.035f)
}

/** Venusauris: a great jungle friend with leaves at its sides and a big open bloom. */
private fun DrawScope.venusauris(p: Palette, st: CompanionState) {
    for (dx in listOf(-1f, 1f)) ellipse(p.tint(LEAF), 0.5f + dx * 0.32f, 0.5f, 0.16f, 0.07f)
    flower(p.tint(PETAL), p.accent, at(0.5f, 0.26f), s(0.15f), n = 6)
    ellipse(p.body, 0.5f, 0.64f, 0.34f, 0.26f)
    ellipse(p.light, 0.5f, 0.74f, 0.16f, 0.1f)
    face(p, st, 0.5f, 0.6f)
}

/** Gengachimera: a playful spirit with a wavy hem and friendly lights floating around it. */
private fun DrawScope.gengachimera(p: Palette, st: CompanionState) {
    for ((x, y) in listOf(0.14f to 0.3f, 0.86f to 0.36f, 0.2f to 0.8f, 0.82f to 0.78f)) {
        drawCircle(p.accent.copy(alpha = 0.25f), s(0.06f), at(x, y))
        drawCircle(p.accent.copy(alpha = 0.6f), s(0.025f), at(x, y))
    }
    val w = size.width; val h = size.height
    val ghost = Path().apply {
        moveTo(w * 0.24f, h * 0.52f)
        cubicTo(w * 0.24f, h * 0.12f, w * 0.76f, h * 0.12f, w * 0.76f, h * 0.52f)
        lineTo(w * 0.76f, h * 0.84f)
        cubicTo(w * 0.72f, h * 0.74f, w * 0.64f, h * 0.74f, w * 0.6f, h * 0.84f)
        cubicTo(w * 0.56f, h * 0.74f, w * 0.44f, h * 0.74f, w * 0.4f, h * 0.84f)
        cubicTo(w * 0.36f, h * 0.74f, w * 0.28f, h * 0.74f, w * 0.24f, h * 0.84f)
        close()
    }
    drawPath(ghost, p.body)
    face(p, st, 0.5f, 0.46f, gap = 0.1f, eye = 0.045f)
}

/** Snorlaura: a big sleepy friend that glows softly; its eyes stay happily closed unless it is cheering. */
private fun DrawScope.snorlaura(p: Palette, st: CompanionState) {
    for (r in listOf(0.48f, 0.42f)) drawCircle(p.accent.copy(alpha = 0.12f), s(r), at(0.5f, 0.58f))
    for (dx in listOf(-0.2f, 0.2f)) ellipse(p.body, 0.5f + dx, 0.3f, 0.07f, 0.07f)
    ellipse(p.body, 0.5f, 0.62f, 0.38f, 0.32f)
    ellipse(p.light, 0.5f, 0.7f, 0.24f, 0.2f)
    face(p, if (st == CompanionState.ACTIVE) st else CompanionState.RESTING, 0.5f, 0.46f, gap = 0.1f)
}

/** Dragonairis: a graceful serpent in an S-curve, with a rainbow trail behind it. */
private fun DrawScope.dragonairis(p: Palette, st: CompanionState) {
    for ((i, c) in RAINBOW.withIndex()) curve(p.tint(c).copy(alpha = 0.7f), 0.03f, 0.06f to 0.92f - i * 0.035f, 0.3f to 0.98f - i * 0.035f, 0.5f to 0.8f - i * 0.035f, 0.66f to 0.86f - i * 0.035f)
    curve(p.body, 0.11f, 0.7f to 0.84f, 0.96f to 0.74f, 0.3f to 0.56f, 0.5f to 0.36f)
    for (dx in listOf(-1f, 1f)) triangle(p.light, at(0.5f + dx * 0.1f, 0.26f), at(0.5f + dx * 0.24f, 0.18f), at(0.5f + dx * 0.14f, 0.34f))
    ellipse(p.body, 0.5f, 0.3f, 0.16f, 0.14f)
    drawCircle(p.accent, s(0.025f), at(0.5f, 0.17f))
    face(p, st, 0.5f, 0.3f, gap = 0.06f, eye = 0.032f)
}
