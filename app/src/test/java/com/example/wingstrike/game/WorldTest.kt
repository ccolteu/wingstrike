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
    val world = World(Random(1))
    world.startOrAdvance()
    world.movePlayer(0.5f)
    world.setFiring(true)
    repeat(20) { world.step(1f / 60f) }
    assertTrue(world.shots.any { it.fromPlayer && it.alive })
  }

  @Test
  fun shotAndBoomEmitSeparateCues() {
    val world = World(Random(2), bossAt = 99f)
    world.startOrAdvance()
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
    world.startOrAdvance()
    var guard = 0
    while (world.boss == null && world.phase == Phase.PLAYING && guard++ < 400) {
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
  fun planeWavesAreScripted() {
    val world = World(Random(7), bossAt = 99f)
    world.startOrAdvance()
    var guard = 0
    while (world.mobs.none { it.kind == MobKind.DIVE } && guard++ < 400) {
      world.step(1f / 60f)
    }
    val dives = world.mobs.filter { it.kind == MobKind.DIVE }
    assertTrue(dives.isNotEmpty())
    assertTrue(dives.all { kotlin.math.abs(it.x - 0.08f) < 0.001f })
  }

  @Test
  fun playerStaysFullyOnScreen() {
    val world = World(Random(0))
    world.startOrAdvance()
    world.movePlayer(-1f, -1f)
    assertTrue(world.playerLeft() >= 0f)
    assertTrue(world.playerTop() >= 0f)
    world.movePlayer(2f, 2f)
    assertTrue(world.playerLeft() + World.SHIP_W <= 1.0001f)
    assertTrue(world.playerTop() + World.SHIP_H <= 1.0001f)
  }

  @Test
  fun fiveEnemyBoltsDestroyThePlayer() {
    val world = World(Random(0), bossAt = 99f)
    world.setViewSize(1080f, 1920f)
    world.startOrAdvance()
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
}
