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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.wingstrike.audio.GameAudio
import com.example.wingstrike.HighScoreStore
import com.example.wingstrike.R
import com.example.wingstrike.game.Boss
import com.example.wingstrike.game.MobKind
import com.example.wingstrike.game.Phase
import com.example.wingstrike.game.World
import com.example.wingstrike.game.baseLayer
import com.example.wingstrike.game.flipDrawX
import com.example.wingstrike.game.groundDrawH
import com.example.wingstrike.game.groundDrawW
import com.example.wingstrike.game.onLand

@Composable
fun WingStrikeScreen() {
  val context = LocalContext.current
  val scores = remember { HighScoreStore(context) }
  val world = remember { World() }
  val audio = remember { GameAudio(context) }
  val lifecycleOwner = LocalLifecycleOwner.current
  DisposableEffect(lifecycleOwner, audio) {
    val observer =
      LifecycleEventObserver { _, event ->
        when (event) {
          Lifecycle.Event.ON_PAUSE -> audio.pause()
          Lifecycle.Event.ON_RESUME -> audio.resume()
          else -> Unit
        }
      }
    lifecycleOwner.lifecycle.addObserver(observer)
    onDispose {
      lifecycleOwner.lifecycle.removeObserver(observer)
      audio.release()
    }
  }
  val stageWater = ImageBitmap.imageResource(R.drawable.stage_water)
  val stageLand =
    listOf(
      ImageBitmap.imageResource(R.drawable.stage_land_0),
      ImageBitmap.imageResource(R.drawable.stage_land_1),
      ImageBitmap.imageResource(R.drawable.stage_land_2),
      ImageBitmap.imageResource(R.drawable.stage_land_3),
      ImageBitmap.imageResource(R.drawable.stage_land_4),
      ImageBitmap.imageResource(R.drawable.stage_land_5),
    )
  val shipArt =
    ShipArt(
      player = ImageBitmap.imageResource(R.drawable.spr_player),
      fighter = ImageBitmap.imageResource(R.drawable.spr_fighter),
      bomber = ImageBitmap.imageResource(R.drawable.spr_bomber),
      boss = ImageBitmap.imageResource(R.drawable.spr_boss),
      power = ImageBitmap.imageResource(R.drawable.spr_power),
      patrol = ImageBitmap.imageResource(R.drawable.spr_patrol),
      destroyer = ImageBitmap.imageResource(R.drawable.spr_destroyer),
      battleship = ImageBitmap.imageResource(R.drawable.spr_battleship),
      sub = ImageBitmap.imageResource(R.drawable.spr_sub),
      tank = ImageBitmap.imageResource(R.drawable.spr_tank),
      cannon = ImageBitmap.imageResource(R.drawable.spr_cannon),
      barracks = ImageBitmap.imageResource(R.drawable.spr_barracks),
      bunker = ImageBitmap.imageResource(R.drawable.spr_bunker),
      dock = ImageBitmap.imageResource(R.drawable.spr_dock),
      dockSlip = ImageBitmap.imageResource(R.drawable.spr_dock_slip),
      hangar = ImageBitmap.imageResource(R.drawable.spr_hangar),
      runway = ImageBitmap.imageResource(R.drawable.spr_runway),
      yard = ImageBitmap.imageResource(R.drawable.spr_yard),
      boat = ImageBitmap.imageResource(R.drawable.spr_boat),
    )
  var high by remember { mutableIntStateOf(scores.load()) }
  var frame by remember { mutableIntStateOf(0) }

  LaunchedEffect(Unit) {
    var last = 0L
    while (true) {
      withFrameNanos { now ->
        val dt = if (last == 0L) 0f else ((now - last) / 1_000_000_000f).coerceAtMost(0.05f)
        last = now
        world.step(dt)
        audio.playCues(world.takeCues())
        audio.setStageMusic(world.phase == Phase.PLAYING || world.phase == Phase.CLEARED)
        if (world.score > high) {
          high = world.score
          scores.save(high)
        }
        frame++
      }
    }
  }

  val tick = frame
  Box(
    modifier =
      Modifier
        .fillMaxSize()
        .background(Ink)
        .statusBarsPadding()
        .navigationBarsPadding(),
  ) {
    Canvas(
      modifier =
        Modifier
          .fillMaxSize()
          .onSizeChanged { world.setViewSize(it.width.toFloat(), it.height.toFloat()) }
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
              world.movePlayer((down.position.x / size.width), (down.position.y / size.height))
              world.setFiring(true)
              while (true) {
                val event = awaitPointerEvent()
                val change = event.changes.first()
                world.movePlayer((change.position.x / size.width), (change.position.y / size.height))
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
      world.setViewSize(w, h)
      drawStageMap(stageWater, stageLand, world.scroll, w, h, world.bombFlash)
      world.grounds.filter { it.alive }.sortedBy { if (it.kind.baseLayer()) 0 else if (it.kind.onLand()) 2 else 1 }.forEach { unit ->
        drawGround(
          shipArt,
          unit.kind,
          unit.x * w,
          unit.y * h,
          groundDrawW(unit) * w,
          groundDrawH(unit, world.viewAspect()) * h,
          flash = unit.hitFlash > 0f,
          flipX = unit.flipDrawX(),
        )
      }
      world.pickups.filter { it.alive }.forEach { drawPowerChip(shipArt, it.x, it.y, w, h) }
      world.mobs.filter { it.alive }.forEach { mob ->
        val bw = World.mobW(mob.kind)
        val bh = World.mobH(mob.kind)
        drawMob(shipArt, mob.kind, mob.x * w, mob.y * h, bw * w, bh * h, flash = mob.hitFlash > 0f)
      }
      world.boss?.let { b ->
        drawBoss(shipArt, b.x * w, b.y * h, Boss.BOSS_W * w, Boss.BOSS_H * h, flash = b.hitFlash > 0f)
      }
      world.shots.filter { it.alive && !it.fromPlayer }.forEach { drawFoeShot(it.x, it.y, w, h) }
      world.blasts.filter { it.visible }.forEach { drawBlast(it, w, h) }
      val show = world.playerOnField() && (!world.playerFlashing() || tick % 8 < 5)
      if (show) {
        drawPlayerPlane(shipArt, world.playerLeft() * w, world.playerTop() * h, World.SHIP_W * w, World.SHIP_H * h)
      }
      world.shots.filter { it.alive && it.fromPlayer }.forEach { drawAllyShot(it.x, it.y, w, h, it.missile) }
    }

    Row(
      modifier =
        Modifier
          .align(Alignment.TopCenter)
          .fillMaxWidth()
          .padding(horizontal = 8.dp, vertical = 4.dp),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        Arcade("1P", Gold, 16)
        Text(" ", fontSize = 10.sp)
        Arcade("${world.lives.coerceAtLeast(0)}", Color.White, 16)
      }
      Arcade(world.score.toString(), Color.White, 18)
      Arcade("1-1", Color.White, 16)
    }
    world.boss?.let { b ->
      val frac = (b.hp / Boss.MAX_HP.toFloat()).coerceIn(0f, 1f)
      Box(
        modifier =
          Modifier
            .align(Alignment.TopCenter)
            .padding(top = 28.dp)
            .fillMaxWidth(0.72f)
            .height(6.dp)
            .background(Color(0xAA101010), RoundedCornerShape(2.dp)),
      ) {
        Box(
          modifier =
            Modifier
              .fillMaxWidth(frac)
              .height(6.dp)
              .background(Danger, RoundedCornerShape(2.dp)),
        )
      }
    }

    Row(
      modifier =
        Modifier
          .align(Alignment.BottomStart)
          .padding(start = 8.dp, bottom = 10.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
      Arcade("X", Danger, 14)
      val filled = (world.power + 1) * 4
      repeat(12) { i ->
        Box(
          modifier =
            Modifier
              .width(6.dp)
              .height(10.dp)
              .background(if (i < filled) Color(0xFFC8C8C8) else Color(0xFF3A3A3A), RoundedCornerShape(2.dp)),
        )
      }
    }
    Arcade(
      "CREDIT 0",
      Color.White,
      14,
      modifier = Modifier.align(Alignment.BottomEnd).padding(end = 88.dp, bottom = 12.dp),
    )

    if (world.warning > 0f && world.phase == Phase.PLAYING) {
      Text(
        "WARNING\nBOSS APPROACHING",
        color = Danger,
        fontWeight = FontWeight.Black,
        fontSize = 22.sp,
        textAlign = TextAlign.Center,
        modifier =
          Modifier
            .align(Alignment.Center)
            .background(Color(0xCC0A1020))
            .padding(horizontal = 16.dp, vertical = 10.dp),
      )
    }
    when (world.phase) {
      Phase.READY, Phase.GAME_OVER -> {
        Column(
          modifier =
            Modifier
              .align(Alignment.Center)
              .background(Color(0xF2080A14), RoundedCornerShape(8.dp))
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
              .background(Color(0xF2080A14), RoundedCornerShape(8.dp))
              .border(2.dp, Gold, RoundedCornerShape(8.dp))
              .clickable { world.startOrAdvance() }
              .padding(18.dp),
        )
      }
      Phase.PLAYING -> {
        Column(
          modifier =
            Modifier
              .align(Alignment.BottomEnd)
              .padding(12.dp)
              .clickable { world.useBomb() }
              .padding(6.dp),
          horizontalAlignment = Alignment.CenterHorizontally,
        ) {
          Arcade(world.bombs.toString(), Color.White, 18)
          Box(
            modifier =
              Modifier
                .size(22.dp, 32.dp)
                .background(Color(0xFF1A3060), RoundedCornerShape(5.dp))
                .border(1.dp, Color(0xFF80A0D0), RoundedCornerShape(5.dp)),
          )
        }
      }
    }
  }
}

@Composable
private fun Arcade(text: String, color: Color, size: Int, modifier: Modifier = Modifier) {
  Text(
    text,
    color = color,
    fontWeight = FontWeight.Black,
    fontSize = size.sp,
    fontFamily = FontFamily.SansSerif,
    modifier = modifier,
    style = TextStyle(shadow = Shadow(Color.Black, Offset(2f, 2f), 1.2f)),
  )
}
