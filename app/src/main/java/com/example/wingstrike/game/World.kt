package com.example.wingstrike.game

import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

enum class Phase {
  READY,
  PLAYING,
  CLEARED,
  GAME_OVER,
}

enum class MobKind {
  FIGHTER,
  DIVE,
  BOMBER,
}

data class Shot(
  var x: Float,
  var y: Float,
  var vx: Float,
  var vy: Float,
  var fromPlayer: Boolean,
  var alive: Boolean = true,
)

data class Mob(
  var x: Float,
  var y: Float,
  val kind: MobKind,
  var hp: Int,
  var t: Float = 0f,
  var fire: Float = 0.4f,
  var alive: Boolean = true,
  var drop: Boolean = false,
)

data class Pickup(var x: Float, var y: Float, var alive: Boolean = true)

data class Blast(var x: Float, var y: Float, var t: Float = 0f, val big: Boolean = false) {
  val duration: Float
    get() = if (big) 0.5f else 0.28f
  val progress: Float
    get() = (t / duration).coerceIn(0f, 1f)
}

class Boss {
  var x: Float = 0.5f - BOSS_W / 2f
  var y: Float = -0.22f
  var hp: Int = MAX_HP
  var t: Float = 0f
  var pattern: Int = 0
  var fire: Float = 0.4f
  var entered: Boolean = false

  fun centerX(): Float = x + BOSS_W / 2f

  fun centerY(): Float = y + BOSS_H / 2f

  companion object {
    const val BOSS_W = 0.46f
    const val BOSS_H = 0.20f
    const val MAX_HP = 90
  }
}

class World(
  private val random: Random = Random.Default,
  private val bossAt: Float = 26f,
) {
  var phase: Phase = Phase.READY
    private set
  var score: Int = 0
    private set
  var lives: Int = 3
    private set
  var bombs: Int = 3
    private set
  var power: Int = 0
    private set
  var playerX: Float = 0.5f
    private set
  var invuln: Float = 0f
    private set
  var respawnIn: Float = 0f
    private set
  var scroll: Float = 0f
    private set
  var stageT: Float = 0f
    private set
  var bombFlash: Float = 0f
    private set
  var warning: Float = 0f
    private set
  var boss: Boss? = null
    private set
  val mobs: MutableList<Mob> = mutableListOf()
  val shots: MutableList<Shot> = mutableListOf()
  val pickups: MutableList<Pickup> = mutableListOf()
  val blasts: MutableList<Blast> = mutableListOf()

  private var wantFire = false
  private var fireCool = 0f
  private var spawnCool = 0.6f
  private var wave = 0

  fun movePlayer(nx: Float) {
    playerX = nx.coerceIn(SHIP_W / 2f, 1f - SHIP_W / 2f)
  }

  fun setFiring(on: Boolean) {
    wantFire = on
  }

  fun playerLeft(): Float = playerX - SHIP_W / 2f

  fun playerOnField(): Boolean = respawnIn <= 0f && lives > 0

  fun playerFlashing(): Boolean = invuln > 0f

  fun startOrAdvance() {
    when (phase) {
      Phase.READY, Phase.GAME_OVER -> {
        score = 0
        lives = 3
        bombs = 3
        power = 0
        playerX = 0.5f
        resetStage()
        phase = Phase.PLAYING
      }
      Phase.CLEARED -> {
        resetStage()
        phase = Phase.PLAYING
      }
      Phase.PLAYING -> {}
    }
  }

  fun useBomb() {
    if (phase != Phase.PLAYING || bombs <= 0 || !playerOnField()) return
    bombs -= 1
    bombFlash = 0.35f
    shots.removeAll { !it.fromPlayer }
    mobs.filter { it.alive }.forEach { mob ->
      mob.hp -= 4
      if (mob.hp <= 0) killMob(mob)
    }
    boss?.let { b ->
      b.hp -= 12
      boom(b.centerX(), b.centerY(), true)
      if (b.hp <= 0) beatBoss()
    }
  }

  fun step(dt: Float) {
    val clamped = dt.coerceAtMost(0.05f)
    tickFx(clamped)
    if (phase != Phase.PLAYING) return
    var left = clamped
    val slice = 1f / 120f
    while (left > 0f && phase == Phase.PLAYING) {
      val s = min(slice, left)
      advance(s)
      left -= s
    }
    if (wantFire && playerOnField()) tryShot()
  }

  private fun resetStage() {
    mobs.clear()
    shots.clear()
    pickups.clear()
    blasts.clear()
    boss = null
    scroll = 0f
    stageT = 0f
    fireCool = 0f
    spawnCool = 0.4f
    wave = 0
    invuln = 0f
    respawnIn = 0f
    bombFlash = 0f
    warning = 0f
    wantFire = false
  }

  private fun tickFx(dt: Float) {
    bombFlash = (bombFlash - dt).coerceAtLeast(0f)
    warning = (warning - dt).coerceAtLeast(0f)
    blasts.forEach { it.t += dt }
    blasts.removeAll { it.t >= it.duration }
  }

  private fun advance(dt: Float) {
    if (respawnIn > 0f) {
      respawnIn = (respawnIn - dt).coerceAtLeast(0f)
      if (respawnIn == 0f && lives > 0) {
        playerX = 0.5f
        invuln = 2.4f
        shots.removeAll { !it.fromPlayer }
      }
    }
    invuln = (invuln - dt).coerceAtLeast(0f)
    fireCool = (fireCool - dt).coerceAtLeast(0f)
    stageT += dt
    val scrolling = boss == null || boss?.entered == false
    scroll += (if (scrolling) 0.11f else 0.025f) * dt
    moveShots(dt)
    moveMobs(dt)
    movePickups(dt)
    maybeSpawn(dt)
    maybeBoss()
    stepBoss(dt)
    collide()
  }

  private fun tryShot() {
    if (fireCool > 0f) return
    val y = PLAYER_Y + SHIP_H * 0.06f
    val n = 1 + power.coerceIn(0, 2)
    val spread = 0.08f + power * 0.03f
    if (n == 1) {
      shots += Shot(playerX, y, 0f, -0.95f, true)
    } else {
      for (i in 0 until n) {
        val t = i / (n - 1f) - 0.5f
        shots += Shot(playerX + t * 0.03f, y, t * spread * 0.4f, -0.95f, true)
      }
    }
    fireCool = 0.11f
  }

  private fun maybeSpawn(dt: Float) {
    if (boss != null) return
    if (stageT > bossAt - 3f) return
    spawnCool -= dt
    if (spawnCool > 0f) return
    wave += 1
    when (wave % 4) {
      1 -> spawnLine()
      2 -> spawnDive()
      3 -> spawnBombers()
      else -> {
        spawnLine()
        spawnDive()
      }
    }
    spawnCool = (1.7f - wave * 0.04f).coerceAtLeast(1.05f)
  }

  private fun spawnLine() {
    val n = 4
    val gap = 0.16f
    val start = 0.5f - (n - 1) * gap / 2f
    repeat(n) { i ->
      mobs +=
        Mob(
          x = start + i * gap - FIGHTER_W / 2f,
          y = -0.08f - i * 0.05f,
          kind = MobKind.FIGHTER,
          hp = 2,
          fire = 0.5f + i * 0.15f,
          drop = i == n / 2 && random.nextFloat() < 0.45f,
        )
    }
  }

  private fun spawnDive() {
    val side = if (random.nextBoolean()) 0.08f else 0.82f
    repeat(3) { i ->
      mobs +=
        Mob(
          x = side,
          y = -0.1f - i * 0.07f,
          kind = MobKind.DIVE,
          hp = 2,
          fire = 0.8f,
        )
    }
  }

  private fun spawnBombers() {
    repeat(2) { i ->
      mobs +=
        Mob(
          x = 0.22f + i * 0.42f,
          y = -0.12f,
          kind = MobKind.BOMBER,
          hp = 5,
          fire = 0.6f,
          drop = true,
        )
    }
  }

  private fun maybeBoss() {
    if (boss != null) return
    if (stageT < bossAt) return
    if (mobs.any { it.alive }) return
    warning = 2.2f
    boss = Boss()
  }

  private fun stepBoss(dt: Float) {
    val b = boss ?: return
    b.t += dt
    if (!b.entered) {
      b.y += 0.12f * dt
      if (b.y >= 0.08f) {
        b.y = 0.08f
        b.entered = true
      }
      return
    }
    b.x = 0.5f - Boss.BOSS_W / 2f + sin(b.t * 0.7f).toFloat() * 0.22f
    b.fire -= dt
    if (b.fire > 0f) return
    b.pattern = ((b.t / 3.2f).toInt()) % 3
    when (b.pattern) {
      0 -> {
        for (i in -2..2) {
          shots += Shot(b.centerX() + i * 0.05f, b.y + Boss.BOSS_H, i * 0.08f, 0.32f, false)
        }
        b.fire = 0.55f
      }
      1 -> {
        val dx = playerX - b.centerX()
        val dy = PLAYER_Y - b.centerY()
        val len = kotlin.math.sqrt(dx * dx + dy * dy).coerceAtLeast(0.05f)
        shots += Shot(b.centerX(), b.y + Boss.BOSS_H * 0.6f, dx / len * 0.28f, dy / len * 0.28f, false)
        shots += Shot(b.x + 0.06f, b.y + Boss.BOSS_H * 0.5f, -0.05f, 0.34f, false)
        shots += Shot(b.x + Boss.BOSS_W - 0.06f, b.y + Boss.BOSS_H * 0.5f, 0.05f, 0.34f, false)
        b.fire = 0.28f
      }
      else -> {
        for (i in 0..5) {
          val a = (i / 5f - 0.5f) * 1.2f
          shots += Shot(b.centerX(), b.y + Boss.BOSS_H, sin(a) * 0.22f, 0.26f + abs(cos(a)) * 0.08f, false)
        }
        b.fire = 0.7f
      }
    }
  }

  private fun moveMobs(dt: Float) {
    mobs.filter { it.alive }.forEach { mob ->
      mob.t += dt
      mob.fire -= dt
      when (mob.kind) {
        MobKind.FIGHTER -> {
          mob.y += 0.16f * dt
          mob.x += sin(mob.t * 2.2f).toFloat() * 0.04f * dt
        }
        MobKind.DIVE -> {
          mob.y += 0.22f * dt
          val pull = (playerX - (mob.x + FIGHTER_W / 2f)) * 0.55f * dt
          mob.x += pull
        }
        MobKind.BOMBER -> {
          mob.y += 0.09f * dt
        }
      }
      if (mob.y > 1.12f) mob.alive = false
      if (mob.fire <= 0f && mob.y > 0.02f && mob.y < 0.72f) {
        val cx = mob.x + FIGHTER_W / 2f
        val cy = mob.y + FIGHTER_H
        when (mob.kind) {
          MobKind.FIGHTER, MobKind.DIVE -> shots += Shot(cx, cy, 0f, 0.34f, false)
          MobKind.BOMBER -> {
            shots += Shot(cx, cy, -0.08f, 0.28f, false)
            shots += Shot(cx, cy, 0f, 0.30f, false)
            shots += Shot(cx, cy, 0.08f, 0.28f, false)
          }
        }
        mob.fire = if (mob.kind == MobKind.BOMBER) 1.3f else 1.1f
      }
    }
    mobs.removeAll { !it.alive }
  }

  private fun moveShots(dt: Float) {
    shots.forEach { s ->
      if (!s.alive) return@forEach
      s.x += s.vx * dt
      s.y += s.vy * dt
      if (s.y < -0.08f || s.y > 1.1f || s.x < -0.05f || s.x > 1.05f) s.alive = false
    }
    shots.removeAll { !it.alive }
  }

  private fun movePickups(dt: Float) {
    pickups.forEach { p ->
      if (!p.alive) return@forEach
      p.y += 0.12f * dt
      if (p.y > 1.1f) p.alive = false
    }
    pickups.removeAll { !it.alive }
  }

  private fun collide() {
    shots.filter { it.alive && it.fromPlayer }.forEach { shot ->
      val hit = mobs.firstOrNull { it.alive && pointIn(shot.x, shot.y, it.x, it.y, FIGHTER_W, FIGHTER_H) }
      if (hit != null) {
        shot.alive = false
        hit.hp -= 1
        if (hit.hp <= 0) killMob(hit)
      } else {
        val b = boss
        if (b != null && pointIn(shot.x, shot.y, b.x, b.y, Boss.BOSS_W, Boss.BOSS_H)) {
          shot.alive = false
          b.hp -= 1
          if (b.hp <= 0) beatBoss()
        }
      }
    }
    pickups.filter { it.alive }.forEach { p ->
      if (playerOnField() && boxes(playerLeft(), PLAYER_Y, SHIP_W, SHIP_H, p.x, p.y, 0.06f, 0.06f)) {
        p.alive = false
        power = (power + 1).coerceAtMost(2)
      }
    }
    if (!playerOnField() || invuln > 0f) return
    val px = playerLeft()
    val bolt = shots.firstOrNull { it.alive && !it.fromPlayer && pointIn(it.x, it.y, px + SHIP_W * 0.28f, PLAYER_Y + SHIP_H * 0.12f, SHIP_W * 0.44f, SHIP_H * 0.55f) }
    val ram = mobs.firstOrNull { it.alive && boxes(px + 0.03f, PLAYER_Y + 0.02f, SHIP_W - 0.06f, SHIP_H - 0.04f, it.x, it.y, FIGHTER_W, FIGHTER_H) }
    val bossHit =
      boss?.let { b ->
        boxes(px + 0.03f, PLAYER_Y + 0.02f, SHIP_W - 0.06f, SHIP_H - 0.04f, b.x, b.y, Boss.BOSS_W, Boss.BOSS_H)
      } == true
    if (bolt != null || ram != null || bossHit) {
      bolt?.alive = false
      ram?.let { killMob(it) }
      hitPlayer()
    }
  }

  private fun killMob(mob: Mob) {
    mob.alive = false
    score += when (mob.kind) {
      MobKind.FIGHTER -> 100
      MobKind.DIVE -> 150
      MobKind.BOMBER -> 300
    }
    boom(mob.x + FIGHTER_W / 2f, mob.y + FIGHTER_H / 2f, false)
    if (mob.drop) pickups += Pickup(mob.x + 0.02f, mob.y)
  }

  private fun beatBoss() {
    val b = boss ?: return
    boom(b.centerX(), b.centerY(), true)
    boom(b.centerX() - 0.1f, b.centerY() + 0.04f, true)
    boom(b.centerX() + 0.1f, b.centerY() - 0.03f, true)
    score += 5000
    boss = null
    shots.removeAll { !it.fromPlayer }
    phase = Phase.CLEARED
  }

  private fun hitPlayer() {
    lives -= 1
    boom(playerX, PLAYER_Y + SHIP_H * 0.4f, true)
    shots.removeAll { !it.fromPlayer }
    power = (power - 1).coerceAtLeast(0)
    if (lives <= 0) {
      respawnIn = 0f
      phase = Phase.GAME_OVER
    } else {
      respawnIn = 0.55f
    }
  }

  private fun boom(x: Float, y: Float, big: Boolean) {
    blasts += Blast(x, y, big = big)
  }

  private fun pointIn(px: Float, py: Float, x: Float, y: Float, w: Float, h: Float): Boolean =
    px >= x && px <= x + w && py >= y && py <= y + h

  private fun boxes(ax: Float, ay: Float, aw: Float, ah: Float, bx: Float, by: Float, bw: Float, bh: Float): Boolean =
    ax < bx + bw && ax + aw > bx && ay < by + bh && ay + ah > by

  companion object {
    const val SHIP_W = 0.122f
    const val SHIP_H = 0.108f
    const val PLAYER_Y = 0.80f
    const val FIGHTER_W = 0.090f
    const val FIGHTER_H = 0.078f
  }
}

private fun cos(a: Float): Float = kotlin.math.cos(a.toDouble()).toFloat()
private fun abs(v: Float): Float = kotlin.math.abs(v)
