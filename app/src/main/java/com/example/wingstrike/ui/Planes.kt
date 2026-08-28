package com.example.wingstrike.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import com.example.wingstrike.game.Blast
import com.example.wingstrike.game.MobKind
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

private val PalAlly =
  mapOf(
    '1' to Color(0xFF1A3810),
    '2' to Color(0xFF3A6820),
    '3' to Color(0xFF6A9A38),
    '4' to Color(0xFFA8D060),
    '5' to Color(0xFFE8F8C8),
    '6' to Color(0xFF245070),
    '7' to Color(0xFF58C8E8),
    '8' to Color(0xFFFF6810),
    '9' to Color(0xFFFFF0A0),
    'A' to Color(0xFFC02828),
  )

private val PalFoe =
  mapOf(
    '1' to Color(0xFF401010),
    '2' to Color(0xFF782018),
    '3' to Color(0xFFB03828),
    '4' to Color(0xFFE07058),
    '5' to Color(0xFFFFD0C0),
    '6' to Color(0xFF303040),
    '7' to Color(0xFF80D0FF),
    '8' to Color(0xFFFF7018),
    '9' to Color(0xFFFFF2B0),
    'A' to Color(0xFFD0A030),
  )

private val PalBoss =
  mapOf(
    '1' to Color(0xFF2A2418),
    '2' to Color(0xFF4A4030),
    '3' to Color(0xFF7A6A48),
    '4' to Color(0xFFB0A070),
    '5' to Color(0xFFE8D8A8),
    '6' to Color(0xFF203040),
    '7' to Color(0xFF50A0D0),
    '8' to Color(0xFFFF5010),
    'A' to Color(0xFFC04030),
  )

internal object Planes {
  val player =
    listOf(
      ".........55.........",
      "........5445........",
      ".......544445.......",
      "......15477451......",
      ".....1154774511.....",
      "....111547745111....",
      "...11115433451111...",
      "..1111154334511111..",
      ".111112543345211111.",
      "11112225433452221111",
      "111222A154451A222111",
      "11221A.154451.A12211",
      ".121.....88.....121.",
      "..1......99......1..",
    )

  val fighter =
    listOf(
      "........55........",
      ".......5445.......",
      "......544445......",
      ".....15433451.....",
      "....1154334511....",
      "...111543345111...",
      "..11125433452111..",
      ".11122A1441A22111.",
      "11121..18881..1211",
      ".121....9999....12",
    )

  val bomber =
    listOf(
      "......11111111......",
      "....122233332221....",
      "...12234444443221...",
      "..12234455555443221.",
      ".122344A43334A443221",
      "12234444422224444321",
      "B22344A188881A44432B".replace('B', '1'),
      ".12234..19991..4321.",
      "...111...888...111..",
    )

  val fortress =
    listOf(
      "....1111111111111111....",
      "...122223333333322221...",
      "..12233444444444433221..",
      ".1223444555555555443221.",
      "1223444A433333334A443221",
      "122344444222222224444321",
      "1122344A188888881A443211",
      ".12234..19999991..43221.",
      "..1221...188881...1221..",
      "....11....A..A....11....",
    )
}

internal fun DrawScope.drawStage(scroll: Float, w: Float, h: Float, bombFlash: Float) {
  drawRect(Color(0xFF3A5A28))
  val band = h * 0.22f
  val off = (scroll * h) % band
  var y = -band + off
  var i = 0
  while (y < h + band) {
    val green = if (i % 2 == 0) Color(0xFF4A6E30) else Color(0xFF355824)
    drawRect(green, Offset(0f, y), Size(w, band))
    val riverX = w * (0.35f + 0.12f * sin((scroll * 4f + i).toDouble()).toFloat())
    drawRect(Color(0xFF2A6A88), Offset(riverX, y), Size(w * 0.16f, band))
    drawRect(Color(0xFF8A7038), Offset(w * 0.08f, y + band * 0.3f), Size(w * 0.12f, band * 0.18f))
    drawRect(Color(0xFF6A5030), Offset(w * 0.78f, y + band * 0.55f), Size(w * 0.14f, band * 0.2f))
    y += band
    i++
  }
  if (bombFlash > 0f) {
    drawRect(Color.White.copy(alpha = bombFlash * 0.45f))
  }
}

private fun padCenter(row: String, cols: Int): String {
  if (row.length >= cols) return row.take(cols)
  val pad = cols - row.length
  val left = pad / 2
  return ".".repeat(left) + row + ".".repeat(pad - left)
}

internal fun DrawScope.drawSprite(
  pixels: List<String>,
  left: Float,
  top: Float,
  width: Float,
  height: Float,
  palette: Map<Char, Color>,
  alignTop: Boolean = false,
) {
  val cols = pixels.maxOf { it.length }
  val rows = pixels.size
  val lines = pixels.map { padCenter(it, cols) }
  val px = min(width / cols, height / rows)
  val spriteW = px * cols
  val spriteH = px * rows
  val ox = left + (width - spriteW) / 2f
  val oy = if (alignTop) top else top + (height - spriteH) / 2f
  val overlap = px * 0.12f
  for (r in lines.indices) {
    val line = lines[r]
    for (c in line.indices) {
      val color = palette[line[c]] ?: continue
      drawRect(color, Offset(ox + c * px, oy + r * px), Size(px + overlap, px + overlap))
    }
  }
}

internal fun DrawScope.drawPlayerPlane(left: Float, top: Float, width: Float, height: Float) {
  drawSprite(Planes.player, left, top, width, height, PalAlly, alignTop = true)
}

internal fun DrawScope.drawMob(kind: MobKind, left: Float, top: Float, width: Float, height: Float) {
  val sprite = if (kind == MobKind.BOMBER) Planes.bomber else Planes.fighter
  drawSprite(sprite, left, top, width, height, PalFoe)
}

internal fun DrawScope.drawBoss(left: Float, top: Float, width: Float, height: Float) {
  drawSprite(Planes.fortress, left, top, width, height, PalBoss)
}

internal fun DrawScope.drawAllyShot(x: Float, y: Float, w: Float, h: Float) {
  val px = (w / 150f).coerceAtLeast(1.2f)
  val cx = x * w
  val cy = y * h
  val len = px * 8f
  drawRect(Color(0xFF40E0FF).copy(alpha = 0.35f), Offset(cx - px * 1.4f, cy - len), Size(px * 2.8f, len))
  drawRect(Color.White, Offset(cx - px * 0.4f, cy - len), Size(px * 0.8f, len))
}

internal fun DrawScope.drawFoeShot(x: Float, y: Float, w: Float, h: Float) {
  val px = (w / 150f).coerceAtLeast(1.2f)
  val cx = x * w
  val cy = y * h
  drawCircle(Color(0xFFFFD080), px * 1.6f, Offset(cx, cy))
  drawCircle(Color(0xFFFF4028), px, Offset(cx, cy))
}

internal fun DrawScope.drawPowerChip(x: Float, y: Float, w: Float, h: Float) {
  val s = min(w, h) * 0.045f
  val cx = x * w
  val cy = y * h
  drawRect(Color(0xFF1858A8), Offset(cx, cy), Size(s, s))
  drawRect(Color(0xFFFFE060), Offset(cx + s * 0.22f, cy + s * 0.18f), Size(s * 0.56f, s * 0.64f))
}

internal fun DrawScope.drawBlast(blast: Blast, w: Float, h: Float) {
  val p = blast.progress
  val cx = blast.x * w
  val cy = blast.y * h
  val r = min(w, h) * (if (blast.big) 0.12f else 0.07f) * (0.3f + p * 1.4f)
  val fade = 1f - p
  drawCircle(Color(0xFFFF8020).copy(alpha = 0.5f * fade), r * 0.8f, Offset(cx, cy))
  drawCircle(Color(0xFFFFF090).copy(alpha = 0.8f * fade), r * 0.4f, Offset(cx, cy))
  drawCircle(Color.White.copy(alpha = fade), r * 0.16f, Offset(cx, cy))
  drawCircle(Color(0xFFFFB040).copy(alpha = 0.65f * fade), r, Offset(cx, cy), style = Stroke((3.5f * fade).coerceAtLeast(1f)))
  for (i in 0 until 8) {
    val a = i * (Math.PI * 2.0 / 8) + p
    drawCircle(Color(0xFFFFE8A0).copy(alpha = fade), 2.8f * fade, Offset(cx + cos(a).toFloat() * r, cy + sin(a).toFloat() * r))
  }
}

internal fun DrawScope.drawLife(index: Int, top: Float, width: Float) {
  drawSprite(Planes.player, 8f + index * width * 0.08f, top, width * 0.07f, width * 0.06f, PalAlly)
}
