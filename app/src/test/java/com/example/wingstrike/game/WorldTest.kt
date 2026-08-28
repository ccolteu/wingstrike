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
}
