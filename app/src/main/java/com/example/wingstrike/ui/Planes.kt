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
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.example.wingstrike.game.Blast
import com.example.wingstrike.game.GroundKind
import com.example.wingstrike.game.MobKind
import com.example.wingstrike.game.StageMap
import com.example.wingstrike.game.isShoreTile
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

internal class ShipArt(
  val player: ImageBitmap,
  val fighter: ImageBitmap,
  val bomber: ImageBitmap,
  val boss: ImageBitmap,
  val power: ImageBitmap,
  val patrol: ImageBitmap,
  val destroyer: ImageBitmap,
  val battleship: ImageBitmap,
  val sub: ImageBitmap,
  val tank: ImageBitmap,
  val cannon: ImageBitmap,
  val barracks: ImageBitmap,
  val bunker: ImageBitmap,
  val dock: ImageBitmap,
  val dockSlip: ImageBitmap,
  val hangar: ImageBitmap,
  val runway: ImageBitmap,
  val yard: ImageBitmap,
  val boat: ImageBitmap,
)

internal fun DrawScope.drawStageMap(
  water: ImageBitmap,
  landPanels: List<ImageBitmap>,
  scroll: Float,
  w: Float,
  h: Float,
  bombFlash: Float,
) {
  val zoom = StageMap.ZOOM
  val dstW = (w * zoom).toInt().coerceAtLeast(1)
  val ox = ((w - dstW) / 2f).toInt()
  val waterH = (w * water.height.toFloat() / water.width.toFloat() * zoom).coerceAtLeast(1f)
  val panelH = (w * StageMap.PANEL_H_OVER_W * zoom).coerceAtLeast(1f)
  val worldY = scroll * h
  tileLayer(water, worldY, waterH, ox, dstW, h)
  tileLandPanels(landPanels, worldY, panelH, ox, dstW, h)
  if (bombFlash > 0f) drawRect(Color.White.copy(alpha = bombFlash * 0.45f))
}

private fun DrawScope.tileLandPanels(
  panels: List<ImageBitmap>,
  worldY: Float,
  panelH: Float,
  ox: Int,
  dstW: Int,
  viewH: Float,
) {
  if (panels.isEmpty()) return
  val total = panelH * panels.size
  val off = ((worldY % total) + total) % total
  for (i in panels.indices) {
    var sy = off + i * panelH
    if (sy >= total) sy -= total
    drawImage(
      image = panels[i],
      dstOffset = IntOffset(ox, sy.toInt()),
      dstSize = IntSize(dstW, panelH.toInt().coerceAtLeast(1)),
      filterQuality = FilterQuality.Medium,
    )
    drawImage(
      image = panels[i],
      dstOffset = IntOffset(ox, (sy - total).toInt()),
      dstSize = IntSize(dstW, panelH.toInt().coerceAtLeast(1)),
      filterQuality = FilterQuality.Medium,
    )
  }
}

private fun DrawScope.tileLayer(
  image: ImageBitmap,
  worldY: Float,
  period: Float,
  ox: Int,
  dstW: Int,
  viewH: Float,
) {
  val off = ((worldY % period) + period) % period
  var sy = off - period
  while (sy < viewH + period) {
    drawImage(
      image = image,
      dstOffset = IntOffset(ox, sy.toInt()),
      dstSize = IntSize(dstW, period.toInt().coerceAtLeast(1)),
      filterQuality = FilterQuality.Medium,
    )
    sy += period
  }
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

internal fun DrawScope.drawGround(
  art: ShipArt,
  kind: GroundKind,
  left: Float,
  top: Float,
  width: Float,
  height: Float,
  flash: Boolean = false,
  flipX: Boolean = false,
) {
  val sprite =
    when (kind) {
      GroundKind.PATROL -> art.patrol
      GroundKind.DESTROYER -> art.destroyer
      GroundKind.BATTLESHIP -> art.battleship
      GroundKind.SUB -> art.sub
      GroundKind.TANK -> art.tank
      GroundKind.CANNON -> art.cannon
      GroundKind.BARRACKS -> art.barracks
      GroundKind.BUNKER -> art.bunker
      GroundKind.DOCK -> art.dock
      GroundKind.DOCK_SLIP -> art.dockSlip
      GroundKind.HANGAR -> art.hangar
      GroundKind.RUNWAY -> art.runway
      GroundKind.YARD -> art.yard
      GroundKind.BOAT -> art.boat
    }
  val stretch = kind.isShoreTile()
  if (!flipX) {
    if (stretch) drawStretched(sprite, left, top, width, height, flash) else drawFitted(sprite, left, top, width, height, alignTop = false, flash = flash)
    return
  }
  withTransform({
    val cx = left + width / 2f
    val cy = top + height / 2f
    translate(cx, cy)
    scale(-1f, 1f)
    translate(-cx, -cy)
  }) {
    if (stretch) drawStretched(sprite, left, top, width, height, flash) else drawFitted(sprite, left, top, width, height, alignTop = false, flash = flash)
  }
}

private fun DrawScope.drawStretched(
  img: ImageBitmap,
  left: Float,
  top: Float,
  width: Float,
  height: Float,
  flash: Boolean,
) {
  val dw = width.toInt().coerceAtLeast(1)
  val dh = height.toInt().coerceAtLeast(1)
  val dst = IntOffset(left.toInt(), top.toInt())
  val size = IntSize(dw, dh)
  drawImage(image = img, dstOffset = dst, dstSize = size, filterQuality = FilterQuality.Medium)
  if (flash) {
    drawImage(
      image = img,
      dstOffset = dst,
      dstSize = size,
      alpha = 0.34f,
      colorFilter = ColorFilter.tint(Color(0xFFFFC060), BlendMode.SrcAtop),
      filterQuality = FilterQuality.Medium,
    )
  }
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
  val r = (w / 70f).coerceAtLeast(3.4f)
  val cx = x * w
  val cy = y * h
  oval(Color(0xAAFFC060), cx, cy, r * 2.2f, r * 2.2f)
  oval(Color(0xFFFF8030), cx, cy, r * 1.45f, r * 1.45f)
  oval(Color(0xFFFFF0C0), cx, cy, r * 0.7f, r * 0.7f)
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
