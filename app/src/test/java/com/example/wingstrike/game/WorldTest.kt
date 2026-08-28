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
  fun bossArrivesAfterTheStageClock() {
    val world = World(Random(0), bossAt = 0.35f)
    world.startOrAdvance()
    var guard = 0
    while (world.boss == null && world.phase == Phase.PLAYING && guard++ < 400) {
      world.step(1f / 60f)
    }
    assertTrue(world.boss != null)
  }
}
