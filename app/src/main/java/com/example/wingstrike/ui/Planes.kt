package com.example.wingstrike.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.example.wingstrike.game.Blast
import com.example.wingstrike.game.MobKind
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

internal class ShipArt(
  val player: ImageBitmap,
  val fighter: ImageBitmap,
  val bomber: ImageBitmap,
  val boss: ImageBitmap,
  val power: ImageBitmap,
)

internal fun DrawScope.drawStageMap(
  water: ImageBitmap,
  land: ImageBitmap,
  scroll: Float,
  w: Float,
  h: Float,
  bombFlash: Float,
) {
  val zoom = 1.72f
  val dstW = (w * zoom).toInt().coerceAtLeast(1)
  val dstH = (w * water.height.toFloat() / water.width.toFloat() * zoom).toInt().coerceAtLeast(1)
  val period = dstH.toFloat()
  val worldY = scroll * h
  val off = ((worldY % period) + period) % period
  val ox = ((w - dstW) / 2f).toInt()
  var sy = off - period
  while (sy < h + period) {
    val dst = IntOffset(ox, sy.toInt())
    val size = IntSize(dstW, dstH)
    drawImage(
      image = water,
      dstOffset = dst,
      dstSize = size,
      filterQuality = FilterQuality.Medium,
    )
    drawImage(
      image = land,
      dstOffset = dst,
      dstSize = size,
      filterQuality = FilterQuality.Medium,
    )
    sy += period
  }
  if (bombFlash > 0f) drawRect(Color.White.copy(alpha = bombFlash * 0.45f))
}

private fun DrawScope.drawFitted(
  img: ImageBitmap,
  left: Float,
  top: Float,
  width: Float,
  height: Float,
  alignTop: Boolean,
  flash: Boolean = false,
) {
  val scale = min(width / img.width.toFloat(), height / img.height.toFloat())
  val dw = (img.width * scale).toInt().coerceAtLeast(1)
  val dh = (img.height * scale).toInt().coerceAtLeast(1)
  val ox = left + (width - dw) / 2f
  val oy = if (alignTop) top else top + (height - dh) / 2f
  val dropX = (dw * 0.08f).toInt().coerceAtLeast(2)
  val dropY = (dh * 0.14f).toInt().coerceAtLeast(3)
  drawImage(
    image = img,
    dstOffset = IntOffset(ox.toInt() + dropX, oy.toInt() + dropY),
    dstSize = IntSize(dw, dh),
    alpha = 0.45f,
    colorFilter = ColorFilter.tint(Color.Black, BlendMode.SrcIn),
    filterQuality = FilterQuality.Medium,
  )
  drawImage(
    image = img,
    dstOffset = IntOffset(ox.toInt(), oy.toInt()),
    dstSize = IntSize(dw, dh),
    filterQuality = FilterQuality.Medium,
  )
  if (flash) {
    drawImage(
      image = img,
      dstOffset = IntOffset(ox.toInt(), oy.toInt()),
      dstSize = IntSize(dw, dh),
      alpha = 0.34f,
      colorFilter = ColorFilter.tint(Color(0xFFFFC060), BlendMode.SrcAtop),
      filterQuality = FilterQuality.Medium,
    )
  }
}

internal fun DrawScope.drawPlayerPlane(art: ShipArt, left: Float, top: Float, width: Float, height: Float) {
  drawFitted(art.player, left, top, width, height, alignTop = true)
}

internal fun DrawScope.drawMob(
  art: ShipArt,
  kind: MobKind,
  left: Float,
  top: Float,
  width: Float,
  height: Float,
  flash: Boolean = false,
) {
  val sprite = if (kind == MobKind.BOMBER) art.bomber else art.fighter
  drawFitted(sprite, left, top, width, height, alignTop = false, flash = flash)
}

internal fun DrawScope.drawBoss(art: ShipArt, left: Float, top: Float, width: Float, height: Float, flash: Boolean = false) {
  drawFitted(art.boss, left, top, width, height, alignTop = false, flash = flash)
}

private fun DrawScope.oval(color: Color, cx: Float, cy: Float, w: Float, h: Float) {
  drawOval(color, Offset(cx - w / 2f, cy - h / 2f), Size(w, h))
}

internal fun DrawScope.drawAllyShot(x: Float, y: Float, w: Float, h: Float, missile: Boolean) {
  val cx = x * w
  val cy = y * h
  if (missile) {
    val bw = (w / 90f).coerceAtLeast(2.2f)
    val bh = bw * 3.4f
    drawOval(Color(0xFFFF6810), Offset(cx - bw * 0.45f, cy + bh * 0.15f), Size(bw * 0.9f, bh * 0.7f))
    drawOval(Color(0xFFFFF0C0), Offset(cx - bw * 0.25f, cy + bh * 0.45f), Size(bw * 0.5f, bh * 0.35f))
    drawOval(Color(0xFF6A40A8), Offset(cx - bw * 0.55f, cy - bh), Size(bw * 1.1f, bh))
    drawOval(Color(0xFFC8B0E8), Offset(cx - bw * 0.28f, cy - bh * 0.85f), Size(bw * 0.55f, bh * 0.45f))
  } else {
    val px = (w / 130f).coerceAtLeast(1.8f)
    val len = px * 11f
    drawOval(Color(0xAA40FF40), Offset(cx - px * 1.6f, cy - len), Size(px * 3.2f, len))
    drawOval(Color(0xFF80FF50), Offset(cx - px * 0.9f, cy - len * 0.92f), Size(px * 1.8f, len * 0.92f))
    drawOval(Color(0xFFE8FFC0), Offset(cx - px * 0.35f, cy - len * 0.7f), Size(px * 0.7f, len * 0.45f))
  }
}

internal fun DrawScope.drawFoeShot(x: Float, y: Float, w: Float, h: Float) {
  val px = (w / 110f).coerceAtLeast(2.2f)
  val cx = x * w
  val cy = y * h
  drawOval(Color(0xAAFFC060), Offset(cx - px * 0.9f, cy - px * 2.2f), Size(px * 1.8f, px * 4.4f))
  drawOval(Color(0xFFFF8030), Offset(cx - px * 0.5f, cy - px * 1.6f), Size(px, px * 3.2f))
}

internal fun DrawScope.drawPowerChip(art: ShipArt, x: Float, y: Float, w: Float, h: Float) {
  val s = min(w, h) * 0.092f
  drawFitted(art.power, x * w, y * h, s, s, alignTop = false)
}

internal fun DrawScope.drawBlast(blast: Blast, w: Float, h: Float) {
  val p = blast.progress
  val cx = blast.x * w
  val cy = blast.y * h
  val r = min(w, h) * (if (blast.big) 0.18f else 0.08f) * blast.scale * (0.25f + p * 1.5f)
  val fade = 1f - p
  oval(Color(0x88FF2808).copy(alpha = 0.4f * fade), cx, cy, r * 2.1f, r * 2.1f)
  oval(Color(0xFFFF6010).copy(alpha = 0.75f * fade), cx, cy, r * 1.4f, r * 1.4f)
  oval(Color(0xFFFFC020).copy(alpha = 0.9f * fade), cx, cy, r * 0.8f, r * 0.8f)
  oval(Color(0xFFFFF8E0).copy(alpha = fade), cx, cy, r * 0.36f, r * 0.36f)
  drawCircle(Color(0xFFFFB040).copy(alpha = 0.8f * fade), r, Offset(cx, cy), style = Stroke((4f * fade).coerceAtLeast(1f)))
  for (i in 0 until 12) {
    val a = i * (Math.PI * 2.0 / 12) + p * 1.6
    oval(Color(0xFFFFE8A0).copy(alpha = fade), cx + cos(a).toFloat() * r, cy + sin(a).toFloat() * r, 6f * fade, 6f * fade)
  }
}
