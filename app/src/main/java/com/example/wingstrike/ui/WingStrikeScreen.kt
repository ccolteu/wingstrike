package com.example.wingstrike.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.wingstrike.HighScoreStore
import com.example.wingstrike.game.Boss
import com.example.wingstrike.game.Phase
import com.example.wingstrike.game.World

@Composable
fun WingStrikeScreen() {
  val context = LocalContext.current
  val scores = remember { HighScoreStore(context) }
  val world = remember { World() }
  var high by remember { mutableIntStateOf(scores.load()) }
  var frame by remember { mutableIntStateOf(0) }

  LaunchedEffect(Unit) {
    var last = 0L
    while (true) {
      withFrameNanos { now ->
        val dt = if (last == 0L) 0f else ((now - last) / 1_000_000_000f).coerceAtMost(0.05f)
        last = now
        world.step(dt)
        if (world.score > high) {
          high = world.score
          scores.save(high)
        }
        frame++
      }
    }
  }

  val tick = frame
  Column(
    modifier =
      Modifier
        .fillMaxSize()
        .background(Brush.verticalGradient(listOf(Color(0xFF2A3A18), Ink)))
        .statusBarsPadding()
        .navigationBarsPadding(),
  ) {
    Row(
      modifier =
        Modifier
          .fillMaxWidth()
          .padding(horizontal = 12.dp, vertical = 8.dp)
          .shadow(8.dp, RoundedCornerShape(6.dp))
          .background(Color(0xFF1A2410), RoundedCornerShape(6.dp))
          .border(1.dp, Gold.copy(alpha = 0.7f), RoundedCornerShape(6.dp))
          .padding(horizontal = 12.dp, vertical = 8.dp),
      horizontalArrangement = Arrangement.SpaceBetween,
    ) {
      Hud("SCORE", world.score.toString().padStart(6, '0'))
      Hud("BOMB", world.bombs.toString())
      Hud("POW", (world.power + 1).toString())
      Hud("HI", high.toString().padStart(6, '0'))
    }
    Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
      Canvas(
        modifier =
          Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
              awaitEachGesture {
                val down = awaitFirstDown()
                if (world.phase == Phase.CLEARED || world.phase == Phase.READY || world.phase == Phase.GAME_OVER) {
                  if (world.phase != Phase.READY && world.phase != Phase.GAME_OVER) {
                    world.startOrAdvance()
                  }
                  do {
                    val event = awaitPointerEvent()
                  } while (event.changes.any { it.pressed })
                  return@awaitEachGesture
                }
                world.movePlayer((down.position.x / size.width).coerceIn(0f, 1f))
                world.setFiring(true)
                while (true) {
                  val event = awaitPointerEvent()
                  val change = event.changes.first()
                  world.movePlayer((change.position.x / size.width).coerceIn(0f, 1f))
                  change.consume()
                  if (event.changes.none { it.pressed }) break
                }
                world.setFiring(false)
              }
            },
      ) {
        tick
        val w = size.width
        val h = size.height
        drawStage(world.scroll, w, h, world.bombFlash)
        world.pickups.filter { it.alive }.forEach { drawPowerChip(it.x, it.y, w, h) }
        world.mobs.filter { it.alive }.forEach { mob ->
          drawMob(mob.kind, mob.x * w, mob.y * h, World.FIGHTER_W * w, World.FIGHTER_H * h)
        }
        world.boss?.let { b ->
          drawBoss(b.x * w, b.y * h, Boss.BOSS_W * w, Boss.BOSS_H * h)
        }
        world.shots.filter { it.alive && !it.fromPlayer }.forEach { drawFoeShot(it.x, it.y, w, h) }
        world.blasts.forEach { drawBlast(it, w, h) }
        val show = world.playerOnField() && (!world.playerFlashing() || tick % 8 < 5)
        if (show) {
          drawPlayerPlane(world.playerLeft() * w, World.PLAYER_Y * h, World.SHIP_W * w, World.SHIP_H * h)
        }
        world.shots.filter { it.alive && it.fromPlayer }.forEach { drawAllyShot(it.x, it.y, w, h) }
        world.boss?.let { b ->
          val barW = w * 0.7f
          val bx = (w - barW) / 2f
          drawRect(Color(0xAA000000), Offset(bx, h * 0.04f), Size(barW, 10f))
          val frac = (b.hp / Boss.MAX_HP.toFloat()).coerceIn(0f, 1f)
          drawRect(Danger, Offset(bx, h * 0.04f), Size(barW * frac, 10f))
        }
        val extras = if (world.playerOnField()) (world.lives - 1).coerceAtLeast(0) else world.lives.coerceAtLeast(0)
        repeat(extras) { drawLife(it, h - 30f, w) }
      }
      if (world.warning > 0f && world.phase == Phase.PLAYING) {
        Text(
          "WARNING\nBOSS APPROACHING",
          color = Danger,
          fontWeight = FontWeight.Black,
          fontSize = 22.sp,
          textAlign = TextAlign.Center,
          modifier = Modifier.align(Alignment.Center),
        )
      }
      when (world.phase) {
        Phase.READY, Phase.GAME_OVER -> {
          Column(
            modifier =
              Modifier
                .align(Alignment.Center)
                .background(Color(0xCC1A2410), RoundedCornerShape(8.dp))
                .border(2.dp, Gold, RoundedCornerShape(8.dp))
                .padding(horizontal = 18.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
          ) {
            Text(if (world.phase == Phase.GAME_OVER) "GAME OVER" else "WING STRIKE", color = Gold, fontWeight = FontWeight.Black, fontSize = 24.sp)
            Text("1945  •  HOLD TO FLY & FIRE", color = Cream, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp, bottom = 12.dp))
            Text(
              if (world.phase == Phase.GAME_OVER) "TAP TO RESTART" else "TAP TO START",
              color = Gold,
              fontWeight = FontWeight.Black,
              fontSize = 16.sp,
              modifier =
                Modifier
                  .border(1.dp, Gold, RoundedCornerShape(6.dp))
                  .clickable { world.startOrAdvance() }
                  .padding(horizontal = 16.dp, vertical = 8.dp),
            )
          }
        }
        Phase.CLEARED -> {
          Text(
            "STAGE CLEAR\nTAP FOR ANOTHER RUN",
            color = Gold,
            fontWeight = FontWeight.Black,
            fontSize = 22.sp,
            textAlign = TextAlign.Center,
            modifier =
              Modifier
                .align(Alignment.Center)
                .background(Color(0xCC1A2410), RoundedCornerShape(8.dp))
                .border(2.dp, Gold, RoundedCornerShape(8.dp))
                .clickable { world.startOrAdvance() }
                .padding(18.dp),
          )
        }
        Phase.PLAYING -> {
          Text(
            "BOMB",
            color = Gold,
            fontWeight = FontWeight.Black,
            fontSize = 13.sp,
            modifier =
              Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp)
                .background(Color(0xAA1A2410), RoundedCornerShape(20.dp))
                .border(2.dp, Gold, RoundedCornerShape(20.dp))
                .clickable { world.useBomb() }
                .padding(horizontal = 16.dp, vertical = 12.dp),
          )
        }
      }
    }
  }
}

@Composable
private fun Hud(label: String, value: String) {
  Column(horizontalAlignment = Alignment.CenterHorizontally) {
    Text(label, color = Gold, fontWeight = FontWeight.Bold, fontSize = 10.sp, letterSpacing = 1.2.sp)
    Text(value, color = Cream, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 18.sp)
  }
}
