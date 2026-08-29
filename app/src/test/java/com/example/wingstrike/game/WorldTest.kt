package com.example.wingstrike.game

import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WorldTest {
  @Test
  fun startBeginsPlay() {
    val world = World(Random(0))
    assertEquals(Phase.READY, world.phase)
    world.startOrAdvance()
    assertEquals(Phase.PLAYING, world.phase)
  }

  @Test
  fun holdingFireSendsShots() {
    val world = World(Random(1), bossAt = 99f)
    world.startOrAdvance()
    skipTakeoff(world)
    world.movePlayer(0.5f)
    world.setFiring(true)
    repeat(20) { world.step(1f / 60f) }
    assertTrue(world.shots.any { it.fromPlayer && it.alive })
  }

  @Test
  fun shotAndBoomEmitSeparateCues() {
    val world = World(Random(2), bossAt = 99f)
    world.startOrAdvance()
    skipTakeoff(world)
    world.takeCues()
    world.movePlayer(0.5f)
    world.setFiring(true)
    world.step(0.2f)
    val first = world.takeCues()
    assertTrue(first.any { it.kind == SfxKind.SHOT })
    world.takeCues()
    world.setFiring(false)
  }

  @Test
  fun blastScaleMakesBigBoomsLarger() {
    val small = Blast(0.5f, 0.5f, big = false)
    val bomber = Blast(0.5f, 0.5f, big = true, scale = 2.2f)
    assertTrue(bomber.duration > small.duration)
  }

  @Test
  fun bossArrivesAfterTheStageClock() {
    val world = World(Random(0), bossAt = 0.35f)
    world.setViewSize(1080f, 1920f)
    world.startOrAdvance()
    skipTakeoff(world)
    var guard = 0
    while (world.boss == null && world.phase == Phase.PLAYING && guard++ < 800) {
      world.mobs.clear()
      world.step(1f / 60f)
    }
    assertTrue(world.boss != null)
  }

  @Test
  fun groundUnitsSitOnMatchingTerrainAndCanFire() {
    val world = World(Random(3), bossAt = 99f)
    world.setViewSize(1080f, 1920f)
    world.startOrAdvance()
    assertTrue(world.grounds.isNotEmpty())
    world.grounds.filter { it.alive }.forEach { unit ->
      if (unit.kind == GroundKind.DESTROYER ||
        unit.kind == GroundKind.SUB ||
        unit.kind == GroundKind.BATTLESHIP ||
        unit.kind == GroundKind.PATROL ||
        unit.kind == GroundKind.BOAT
      ) {
        return@forEach
      }
      assertEquals(unit.kind.onLand(), LandMask.isLand(unit.mapU, unit.mapV))
    }
    var fired = false
    repeat(400) {
      world.step(1f / 60f)
      if (world.shots.any { !it.fromPlayer }) fired = true
    }
    assertTrue(fired)
  }

  @Test
  fun groundLayoutIsFixedAndOnScreen() {
    val a = World(Random(1), bossAt = 99f)
    val b = World(Random(99), bossAt = 99f)
    a.setViewSize(1080f, 1920f)
    b.setViewSize(1080f, 1920f)
    a.startOrAdvance()
    b.startOrAdvance()
    assertEquals(a.grounds.size, b.grounds.size)
    a.grounds.zip(b.grounds).forEach { (l, r) ->
      assertEquals(l.kind, r.kind)
      assertEquals(l.mapU, r.mapU, 0.0001f)
      assertEquals(l.mapV, r.mapV, 0.0001f)
    }
    val water = a.grounds.count { !it.kind.onLand() }
    assertTrue(water in 18..40)
    a.grounds.forEach { unit ->
      assertTrue(unit.x >= -0.05f)
      assertTrue(unit.x + groundDrawW(unit) <= 1.05f)
    }
    assertTrue(a.grounds.any { it.kind == GroundKind.DESTROYER })
    assertTrue(a.grounds.any { it.kind == GroundKind.SUB })
    assertTrue(a.grounds.count { it.kind == GroundKind.BATTLESHIP } >= 2)
    assertTrue(a.grounds.any { it.kind == GroundKind.PATROL })
    assertTrue(a.grounds.none { it.kind == GroundKind.BOAT })
    assertTrue(a.grounds.none { it.kind.isShoreTile() })
    assertEquals(groundH(GroundKind.BATTLESHIP), groundH(GroundKind.DESTROYER) * 1.5f, 0.001f)
    val anchored = a.grounds.filter { !it.roams() }
    val period = StageMap.periodN(1080f / 1920f)
    anchored.forEach { unit ->
      assertTrue(unit.mapU > 0.25f)
      assertTrue(unit.mapU < 0.75f)
    }
    for (i in anchored.indices) {
      for (j in i + 1 until anchored.size) {
        val p = anchored[i]
        val q = anchored[j]
        val dx = kotlin.math.abs(p.mapU - q.mapU)
        val dy = kotlin.math.abs(p.mapV - q.mapV) * period
        val minX = (groundDrawW(p) + groundDrawW(q)) * 0.5f
        val minY = (groundH(p.kind) + groundH(q.kind)) * 0.5f
        assertTrue(dx >= minX * 0.92f || dy >= minY * 0.92f)
      }
    }
  }

  @Test
  fun fighterFormationsAreFiveAndRepeat() {
    val a = World(Random(1), bossAt = 99f)
    val b = World(Random(99), bossAt = 99f)
    a.startOrAdvance()
    b.startOrAdvance()
    skipTakeoff(a)
    skipTakeoff(b)
    var guard = 0
    while (a.mobs.count { it.kind == MobKind.FIGHTER } < 5 && guard++ < 200) {
      a.step(1f / 60f)
      b.step(1f / 60f)
    }
    val fa = a.mobs.filter { it.kind == MobKind.FIGHTER }
    val fb = b.mobs.filter { it.kind == MobKind.FIGHTER }
    assertEquals(5, fa.size)
    assertEquals(5, fb.size)
    fa.zip(fb).forEach { (l, r) ->
      assertEquals(l.form, r.form)
      assertEquals(l.slot, r.slot)
      assertEquals(l.x, r.x, 0.0001f)
      assertEquals(l.y, r.y, 0.0001f)
    }
    repeat(800) {
      a.step(1f / 60f)
      b.step(1f / 60f)
    }
    val later = a.mobs.filter { it.kind == MobKind.FIGHTER }
    if (later.size == 5) {
      later.zip(b.mobs.filter { it.kind == MobKind.FIGHTER }).forEach { (l, r) ->
        assertEquals(l.form, r.form)
        assertEquals(l.x, r.x, 0.0001f)
        assertEquals(l.y, r.y, 0.0001f)
      }
    }
  }

  @Test
  fun demoPilotHuntsFighters() {
    val world = World(Random(0), bossAt = 0.08f)
    world.setViewSize(1080f, 1920f)
    repeat(300) { world.step(1f / 60f) }
    assertEquals(Phase.DEMO, world.phase)
    var guard = 0
    while (world.boss == null && guard++ < 200) {
      world.step(1f / 60f)
    }
    world.mobs.clear()
    world.shots.removeAll { !it.fromPlayer }
    world.mobs +=
      Mob(
        x = 0.78f - World.FIGHTER_W / 2f,
        y = 0.28f,
        kind = MobKind.FIGHTER,
        hp = 99,
        fire = 99f,
        form = Forms.NONE,
      )
    world.movePlayer(0.22f, World.DEFAULT_PLAYER_Y)
    repeat(100) { world.step(1f / 60f) }
    assertTrue(kotlin.math.abs(world.playerX - 0.78f) < 0.16f)
  }

  @Test
  fun playerStaysFullyOnScreen() {
    val world = World(Random(0), bossAt = 99f)
    world.startOrAdvance()
    skipTakeoff(world)
    world.movePlayer(-1f, -1f)
    assertTrue(world.playerLeft() >= 0f)
    assertTrue(world.playerTop() >= 0f)
    world.movePlayer(2f, 2f)
    assertTrue(world.playerLeft() + World.SHIP_W <= 1.0001f)
    assertTrue(world.playerTop() + World.SHIP_H <= 1.0001f)
  }

  @Test
  fun powerChipNeedsThePlaneOverIt() {
    val world = World(Random(0), bossAt = 99f)
    world.setViewSize(1080f, 1920f)
    world.startOrAdvance()
    skipTakeoff(world)
    world.movePlayer(0.5f, 0.55f)
    world.pickups += Pickup(world.playerLeft() + World.SHIP_W - 0.02f, world.playerTop())
    world.step(1f / 60f)
    assertTrue(world.pickups.any { it.alive })
    assertEquals(0, world.power)
    world.pickups.clear()
    world.pickups += Pickup(world.playerX - World.PICKUP_DRAW * 0.5f, world.playerY - World.PICKUP_DRAW * 0.15f)
    world.step(1f / 60f)
    assertTrue(world.pickups.none { it.alive })
    assertEquals(1, world.power)
  }

  @Test
  fun bombChipAddsABomb() {
    val world = World(Random(0), bossAt = 99f)
    world.setViewSize(1080f, 1920f)
    world.startOrAdvance()
    skipTakeoff(world)
    world.movePlayer(0.5f, 0.55f)
    val bombs = world.bombs
    world.pickups +=
      Pickup(world.playerX - World.PICKUP_DRAW * 0.5f, world.playerY - World.PICKUP_DRAW * 0.15f, PickupKind.BOMB)
    world.step(1f / 60f)
    assertEquals(bombs + 1, world.bombs)
    assertEquals(0, world.power)
  }

  @Test
  fun fiveEnemyBoltsDestroyThePlayer() {
    val world = World(Random(0), bossAt = 99f)
    world.setViewSize(1080f, 1920f)
    world.startOrAdvance()
    skipTakeoff(world)
    world.movePlayer(0.5f, 0.5f)
    repeat(90) {
      world.step(1f / 60f)
      world.mobs.clear()
      world.grounds.clear()
      world.shots.removeAll { !it.fromPlayer }
    }
    world.shots.clear()
    assertEquals(3, world.lives)
    assertEquals(World.PLAYER_HITS, world.playerHp)
    repeat(4) {
      world.shots += Shot(world.playerX, world.playerY, 0f, 0f, fromPlayer = false)
      world.step(1f / 60f)
      assertEquals(3, world.lives)
      repeat(40) { world.step(1f / 60f) }
    }
    world.shots += Shot(world.playerX, world.playerY, 0f, 0f, fromPlayer = false)
    world.step(1f / 60f)
    assertEquals(2, world.lives)
  }

  @Test
  fun insertCoinThenStartBeginsPlay() {
    val world = World(Random(0))
    world.pressStart()
    assertEquals(Phase.READY, world.phase)
    world.insertCoin()
    assertEquals(1, world.credits)
    world.pressStart()
    assertEquals(Phase.PLAYING, world.phase)
    assertEquals(0, world.credits)
  }

  @Test
  fun idleTitleEntersDemoAndTapReturns() {
    val world = World(Random(0), bossAt = 99f)
    world.setViewSize(1080f, 1920f)
    repeat(300) { world.step(1f / 60f) }
    assertEquals(Phase.DEMO, world.phase)
    val lives = world.lives
    repeat(180) { world.step(1f / 60f) }
    assertEquals(Phase.DEMO, world.phase)
    assertEquals(lives, world.lives)
    world.exitDemo()
    assertEquals(Phase.READY, world.phase)
  }

  @Test
  fun takeoffLeavesTheRunway() {
    val world = World(Random(0), bossAt = 99f)
    world.setViewSize(1080f, 1920f)
    world.startOrAdvance()
    assertTrue(world.playerY > 0.88f)
    assertEquals(World.GROUND_SCALE, world.playerScale, 0.01f)
    assertTrue(!world.combat())
    skipTakeoff(world)
    assertEquals(World.DEFAULT_PLAYER_Y, world.playerY, 0.02f)
    assertEquals(1f, world.playerScale, 0.01f)
    assertTrue(world.combat())
  }

  @Test
  fun bossClearLandsOnTheRunway() {
    val world = World(Random(0), bossAt = 0.2f)
    world.setViewSize(1080f, 1920f)
    world.startOrAdvance()
    skipTakeoff(world)
    var guard = 0
    while (world.boss == null && world.phase == Phase.PLAYING && guard++ < 600) {
      world.mobs.clear()
      world.step(1f / 60f)
    }
    assertTrue(world.boss != null)
    guard = 0
    while (world.phase == Phase.PLAYING && guard++ < 2800) {
      val b = world.boss
      if (b != null && !b.dying) {
        world.shots += Shot(b.centerX(), b.centerY(), 0f, 0f, fromPlayer = true)
      }
      world.step(1f / 60f)
    }
    assertEquals(Phase.CLEARED, world.phase)
    assertTrue(kotlin.math.abs(world.playerX - 0.5f) < 0.06f)
    assertTrue(world.playerY > 0.86f)
    assertEquals(World.GROUND_SCALE, world.playerScale, 0.04f)
  }
}

private fun skipTakeoff(world: World) {
  world.setViewSize(1080f, 1920f)
  repeat((World.TAKEOFF_T * 60f).toInt() + 8) { world.step(1f / 60f) }
}
