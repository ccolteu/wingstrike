package com.example.wingstrike.game

import kotlin.math.PI
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

enum class Phase {
  READY,
  DEMO,
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
  var missile: Boolean = false,
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
  var hitFlash: Float = 0f,
  var form: Int = Forms.NONE,
  var slot: Int = 0,
)

data class Pickup(var x: Float, var y: Float, var alive: Boolean = true)

enum class SfxKind {
  SHOT,
  BOOM,
  BOOM_BIG,
}

data class SfxCue(val kind: SfxKind, val x: Float)

data class Blast(
  var x: Float,
  var y: Float,
  var t: Float = 0f,
  val big: Boolean = false,
  val scale: Float = 1f,
  var wait: Float = 0f,
) {
  val duration: Float
    get() = if (big) 0.62f else 0.28f
  val progress: Float
    get() = (t / duration).coerceIn(0f, 1f)
  val visible: Boolean
    get() = wait <= 0f
}

class Boss {
  var x: Float = 0.5f - BOSS_W / 2f
  var y: Float = -0.22f
  var hp: Int = MAX_HP
  var t: Float = 0f
  var pattern: Int = 0
  var fire: Float = 0.4f
  var entered: Boolean = false
  var hitFlash: Float = 0f
  var dying: Boolean = false

  fun centerX(): Float = x + BOSS_W / 2f

  fun centerY(): Float = y + BOSS_H / 2f

  companion object {
    const val BOSS_W = 0.56f
    const val BOSS_H = 0.26f
    const val MAX_HP = 90
  }
}

class World(
  private val random: Random = Random.Default,
  private val bossAt: Float = 42f,
) {
  var phase: Phase = Phase.READY
    private set
  var score: Int = 0
    private set
  var lives: Int = 3
    private set
  var playerHp: Int = PLAYER_HITS
    private set
  var bombs: Int = 3
    private set
  var power: Int = 0
    private set
  var playerX: Float = 0.5f
    private set
  var playerY: Float = DEFAULT_PLAYER_Y
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
  private var viewAspect = 9f / 16f

  internal fun viewAspect(): Float = viewAspect

  fun setViewSize(widthPx: Float, heightPx: Float) {
    if (heightPx > 1f) viewAspect = (widthPx / heightPx).coerceIn(0.35f, 0.85f)
  }
  val mobs: MutableList<Mob> = mutableListOf()
  val grounds: MutableList<GroundUnit> = mutableListOf()
  val shots: MutableList<Shot> = mutableListOf()
  val pickups: MutableList<Pickup> = mutableListOf()
  val blasts: MutableList<Blast> = mutableListOf()
  private val cues: ArrayDeque<SfxCue> = ArrayDeque()

  fun takeCues(): List<SfxCue> {
    if (cues.isEmpty()) return emptyList()
    val out = cues.toList()
    cues.clear()
    return out
  }

  private var wantFire = false
  var credits: Int = 0
    private set
  private var idleT = 0f
  private var demoAimX = 0.5f
  private var demoAimY = DEFAULT_PLAYER_Y
  private var demoVx = 0f
  private var demoVy = 0f
  private var demoPrey: Mob? = null
  private var demoGroundPrey: GroundUnit? = null
  private var fireCool = 0f
  private var spawnCool = 0.6f
  private var wave = 0
  private var bossDeath = 0f
  private var deathBoomIn = 0f

  fun movePlayer(nx: Float, ny: Float = playerY) {
    playerX = nx.coerceIn(SHIP_W / 2f, 1f - SHIP_W / 2f)
    playerY = ny.coerceIn(SHIP_H / 2f, 1f - SHIP_H / 2f)
  }

  fun setFiring(on: Boolean) {
    wantFire = on
  }

  fun pingAttract() {
    if (phase == Phase.READY) idleT = 0f
  }

  fun insertCoin() {
    if (phase == Phase.DEMO) {
      exitDemo()
      return
    }
    if (phase == Phase.READY || phase == Phase.GAME_OVER) {
      credits++
      idleT = 0f
    }
  }

  fun pressStart() {
    if (phase == Phase.DEMO) {
      exitDemo()
      return
    }
    if ((phase == Phase.READY || phase == Phase.GAME_OVER) && credits > 0) {
      credits--
      beginPlay()
    }
  }

  fun exitDemo() {
    if (phase != Phase.DEMO) return
    exitToTitle()
  }

  fun exitToTitle() {
    if (phase != Phase.GAME_OVER && phase != Phase.DEMO) return
    phase = Phase.READY
    idleT = 0f
    wantFire = false
    resetStage()
  }

  fun playerLeft(): Float = playerX - SHIP_W / 2f

  fun playerTop(): Float = playerY - SHIP_H / 2f

  fun playerOnField(): Boolean = respawnIn <= 0f && lives > 0

  fun playerFlashing(): Boolean = invuln > 0f

  fun startOrAdvance() {
    when (phase) {
      Phase.READY, Phase.GAME_OVER -> beginPlay()
      Phase.CLEARED -> {
        resetStage()
        phase = Phase.PLAYING
      }
      Phase.PLAYING, Phase.DEMO -> {}
    }
  }

  private fun beginPlay() {
    score = 0
    lives = 3
    playerHp = PLAYER_HITS
    bombs = 3
    power = 0
    playerX = 0.5f
    playerY = DEFAULT_PLAYER_Y
    resetStage()
    phase = Phase.PLAYING
  }

  private fun beginDemo() {
    score = 0
    lives = 3
    playerHp = PLAYER_HITS
    bombs = 3
    power = 0
    playerX = 0.5f
    playerY = DEFAULT_PLAYER_Y
    demoAimX = 0.5f
    demoAimY = DEFAULT_PLAYER_Y
    demoVx = 0f
    demoVy = 0f
    demoPrey = null
    demoGroundPrey = null
    resetStage()
    invuln = 0f
    wantFire = true
    phase = Phase.DEMO
  }

  fun useBomb() {
    if (phase != Phase.PLAYING || bombs <= 0 || !playerOnField()) return
    bombs -= 1
    bombFlash = 0.35f
    cues += SfxCue(SfxKind.BOOM_BIG, playerX)
    shots.removeAll { !it.fromPlayer }
    mobs.filter { it.alive }.forEach { mob ->
      hurtMob(mob, 4)
    }
    grounds.filter { it.alive }.forEach { unit ->
      hurtGround(unit, 4)
    }
    boss?.let { b ->
      if (!b.dying) hurtBoss(12)
    }
  }

  fun step(dt: Float) {
    val clamped = dt.coerceAtMost(0.05f)
    tickFx(clamped)
    if (phase == Phase.READY) {
      idleT += clamped
      if (idleT >= 5f) beginDemo()
      return
    }
    if (phase != Phase.PLAYING && phase != Phase.DEMO) return
    var left = clamped
    val slice = 1f / 120f
    while (left > 0f && (phase == Phase.PLAYING || phase == Phase.DEMO)) {
      val s = min(slice, left)
      if (phase == Phase.DEMO) {
        demoPilot(s)
        wantFire = true
      }
      advance(s)
      left -= s
    }
    if (wantFire && playerOnField()) tryShot()
  }

  private fun demoPilot(dt: Float) {
    refreshDemoPrey()
    var bestX = demoHuntX()
    var bestY = DEFAULT_PLAYER_Y
    val danger = demoShotDanger(bestX, bestY)
    val panic = danger < -1.2f
    if (panic) {
      val left = (bestX - 0.12f).coerceIn(SHIP_W / 2f, 1f - SHIP_W / 2f)
      val right = (bestX + 0.12f).coerceIn(SHIP_W / 2f, 1f - SHIP_W / 2f)
      val dl = demoShotDanger(left, bestY)
      val dr = demoShotDanger(right, bestY)
      bestX =
        when {
          dl >= dr && dl > danger -> left
          dr > danger -> right
          else -> bestX
        }
    }
    val follow = if (panic) 16f else 11f
    val blend = 1f - kotlin.math.exp(-follow * dt.toDouble()).toFloat()
    demoAimX += (bestX - demoAimX) * blend
    demoAimY += (bestY - demoAimY) * blend
    val spring = if (panic) 30f else 24f
    val damp = if (panic) 6.2f else 5.2f
    demoVx += ((demoAimX - playerX) * spring - demoVx * damp) * dt
    demoVy += ((demoAimY - playerY) * spring - demoVy * damp) * dt
    val sp = hypot(demoVx, demoVy)
    val maxSp = if (panic) 1.18f else 0.92f
    if (sp > maxSp) {
      demoVx *= maxSp / sp
      demoVy *= maxSp / sp
    }
    movePlayer(playerX + demoVx * dt, playerY + demoVy * dt)
  }

  private fun demoHuntX(): Float {
    val prey = demoPrey
    if (prey != null && prey.alive) {
      return (prey.x + mobW(prey.kind) / 2f).coerceIn(SHIP_W / 2f, 1f - SHIP_W / 2f)
    }
    val ground = demoGroundPrey
    if (ground != null && ground.alive) {
      val box = groundHurt(ground)
      return (box.x + box.w / 2f).coerceIn(SHIP_W / 2f, 1f - SHIP_W / 2f)
    }
    boss?.let { b ->
      if (!b.dying) return b.centerX().coerceIn(SHIP_W / 2f, 1f - SHIP_W / 2f)
    }
    return 0.5f
  }

  private fun refreshDemoPrey() {
    val locked = demoPrey
    if (locked != null && locked.alive) {
      val my = locked.y + mobH(locked.kind) / 2f
      if (my >= -0.06f && my <= 0.78f) return
    }
    demoPrey = null
    var best: Mob? = null
    var bestS = Float.NEGATIVE_INFINITY
    mobs.forEach { mob ->
      if (!mob.alive) return@forEach
      val mx = mob.x + mobW(mob.kind) / 2f
      val my = mob.y + mobH(mob.kind) / 2f
      if (my < -0.06f || my > 0.78f) return@forEach
      var s = 12f - abs(mx - playerX) * 2.2f
      s += (0.52f - my).coerceIn(-0.25f, 0.52f) * 7f
      if (mob.kind == MobKind.BOMBER) s += 7f
      if (s > bestS) {
        bestS = s
        best = mob
      }
    }
    demoPrey = best
    if (best != null) {
      demoGroundPrey = null
      return
    }
    val gLocked = demoGroundPrey
    if (gLocked != null && gLocked.alive) {
      val gy = gLocked.y + groundDrawH(gLocked, viewAspect) * 0.5f
      if (gy >= 0.12f && gy <= 0.76f) return
    }
    demoGroundPrey = null
    var gBest: GroundUnit? = null
    var gScore = Float.NEGATIVE_INFINITY
    grounds.forEach { unit ->
      if (!unit.alive) return@forEach
      val box = groundHurt(unit)
      val cx = box.x + box.w / 2f
      val cy = box.y + box.h / 2f
      if (cy < 0.12f || cy > 0.76f) return@forEach
      var s = 6f - abs(cx - playerX) * 3f
      if (unit.kind == GroundKind.BATTLESHIP) s += 5f
      if (unit.kind == GroundKind.DESTROYER) s += 3f
      if (s > gScore) {
        gScore = s
        gBest = unit
      }
    }
    demoGroundPrey = gBest
  }

  private fun demoShotDanger(x: Float, y: Float): Float {
    var s = 0f
    val hx = x - SHIP_W * 0.22f
    val hy = y - SHIP_H * 0.22f
    val hw = SHIP_W * 0.56f
    val hh = SHIP_H * 0.58f
    shots.forEach { shot ->
      if (!shot.alive || shot.fromPlayer) return@forEach
      var t = 0f
      while (t <= 0.38f) {
        val sx = shot.x + shot.vx * t
        val sy = shot.y + shot.vy * t
        val cx = hx + hw * 0.5f
        val cy = hy + hh * 0.5f
        val px = abs(sx - cx) - hw * 0.5f - 0.025f
        val py = abs(sy - cy) - hh * 0.5f - 0.025f
        if (px < 0f && py < 0f) {
          s -= (0.5f - t) * 14f
          break
        }
        t += 0.035f
      }
    }
    mobs.forEach { mob ->
      if (!mob.alive) return@forEach
      val mw = mobW(mob.kind)
      val mh = mobH(mob.kind)
      if (boxes(hx, hy, hw, hh, mob.x, mob.y, mw, mh)) s -= 8f
    }
    return s
  }

  private fun resetStage() {
    mobs.clear()
    grounds.clear()
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
    bossDeath = 0f
    deathBoomIn = 0f
    demoPrey = null
    demoGroundPrey = null
    cues.clear()
    seedGround()
    playerHp = PLAYER_HITS
    invuln = 1.2f
  }

  private fun tickFx(dt: Float) {
    bombFlash = (bombFlash - dt).coerceAtLeast(0f)
    warning = (warning - dt).coerceAtLeast(0f)
    blasts.forEach { blast ->
      if (blast.wait > 0f) {
        blast.wait -= dt
        if (blast.wait <= 0f) {
          blast.wait = 0f
          cueBoom(blast)
        }
      } else {
        blast.t += dt
      }
    }
    blasts.removeAll { it.wait <= 0f && it.t >= it.duration }
    mobs.forEach { it.hitFlash = (it.hitFlash - dt).coerceAtLeast(0f) }
    grounds.forEach { it.hitFlash = (it.hitFlash - dt).coerceAtLeast(0f) }
    boss?.let { it.hitFlash = (it.hitFlash - dt).coerceAtLeast(0f) }
    tickBossDeath(dt)
  }

  private fun advance(dt: Float) {
    if (respawnIn > 0f) {
      respawnIn = (respawnIn - dt).coerceAtLeast(0f)
      if (respawnIn == 0f && lives > 0) {
        playerX = 0.5f
        playerY = DEFAULT_PLAYER_Y
        playerHp = PLAYER_HITS
        invuln = 2.4f
        shots.removeAll { !it.fromPlayer }
      }
    }
    invuln = (invuln - dt).coerceAtLeast(0f)
    fireCool = (fireCool - dt).coerceAtLeast(0f)
    stageT += dt
    val scrolling = boss == null || boss?.entered == false
    scroll += (if (scrolling) 0.055f else 0.014f) * dt
    moveShots(dt)
    moveMobs(dt)
    moveGround(dt)
    movePickups(dt)
    maybeSpawn(dt)
    maybeBoss()
    stepBoss(dt)
    collide()
  }

  private fun tryShot() {
    if (fireCool > 0f) return
    val y = playerTop() + SHIP_H * 0.06f
    shots += Shot(playerX, y, 0f, -0.62f, true)
    if (power >= 1) {
      shots += Shot(playerX + 0.042f, y + 0.012f, 0f, -0.48f, true, missile = true)
    }
    if (power >= 2) {
      shots += Shot(playerX - 0.055f, y, -0.07f, -0.58f, true)
    }
    fireCool = 0.16f
    cues += SfxCue(SfxKind.SHOT, playerX)
  }

  private fun maybeSpawn(dt: Float) {
    if (boss != null) return
    if (stageT > bossAt - 3f) return
    if (mobs.any { it.alive }) {
      spawnCool = 0.55f
      return
    }
    spawnCool -= dt
    if (spawnCool > 0f) return
    wave += 1
    when (PLANE_WAVES[(wave - 1) % PLANE_WAVES.size]) {
      PlaneBeat.V_DOWN -> spawnFighters(Forms.V_DOWN)
      PlaneBeat.CHAIN_L -> spawnFighters(Forms.CHAIN_L)
      PlaneBeat.CHAIN_R -> spawnFighters(Forms.CHAIN_R)
      PlaneBeat.RANK_ZIG -> spawnFighters(Forms.RANK_ZIG)
      PlaneBeat.RING -> spawnFighters(Forms.RING)
      PlaneBeat.BOMBERS -> spawnBombers()
    }
    spawnCool = 0.55f
  }

  private fun spawnFighters(form: Int) {
    repeat(5) { slot ->
      val (cx, cy) = formCenter(form, slot, 0f)
      mobs +=
        Mob(
          x = cx - FIGHTER_W / 2f,
          y = cy - FIGHTER_H / 2f,
          kind = MobKind.FIGHTER,
          hp = 2,
          fire = 0.42f + slot * 0.11f,
          drop = slot == 2,
          form = form,
          slot = slot,
        )
    }
  }

  private fun spawnBombers() {
    mobs +=
      Mob(
        x = 0.28f,
        y = -0.16f,
        kind = MobKind.BOMBER,
        hp = 12,
        fire = 0.6f,
        drop = true,
      )
  }

  private fun seedGround() {
    STAGE_GROUNDS.forEach { addGround(it, it.u, it.v) }
    syncGround()
  }

  private fun addGround(anchor: GroundAnchor, u: Float, v: Float) {
    grounds +=
      GroundUnit(
        mapU = u,
        mapV = v,
        kind = anchor.kind,
        hp = groundHp(anchor.kind),
        fire = anchor.fire,
        mapH = anchor.mapH,
        leftShore = anchor.leftShore,
        roamA = anchor.roamA,
        roamB = anchor.roamB,
        roamSpeed = anchor.roamSpeed,
      )
  }

  private fun syncGround() {
    grounds.forEach { unit ->
      val gw = groundDrawW(unit)
      val gh = groundDrawH(unit, viewAspect)
      val cx = StageMap.screenX(unit.mapU)
      val cy = StageMap.screenY(unit.mapV, scroll, viewAspect)
      unit.x =
        when {
          unit.kind.isShoreTile() -> if (unit.leftShore) 0f else 1f - gw
          unit.kind.onLand() -> cx - gw / 2f
          else -> (cx - gw / 2f).coerceIn(0.02f, 1f - gw - 0.02f)
        }
      unit.y = cy - gh / 2f
    }
  }

  private fun moveGround(dt: Float) {
    grounds.forEach { unit ->
      if (!unit.roams()) return@forEach
      unit.roamT += dt
      val mid = (unit.roamA + unit.roamB) * 0.5f
      val span = (unit.roamB - unit.roamA) * 0.5f
      unit.mapU = mid + span * sin(unit.roamT * unit.roamSpeed)
    }
    syncGround()
    grounds.filter { it.alive }.forEach { unit ->
      if (!unit.kind.shoots()) return@forEach
      val onScreen = unit.y > -0.12f && unit.y < 0.88f && unit.x > -0.12f && unit.x < 1.02f
      if (!onScreen) return@forEach
      unit.fire -= dt
      if (unit.fire > 0f) return@forEach
      val gun = groundMuzzle(unit)
      val dx = playerX - gun.x
      val dy = playerY - gun.y
      val len = kotlin.math.sqrt(dx * dx + dy * dy).coerceAtLeast(0.05f)
      val sp = groundShotSpeed(unit.kind)
      shots += Shot(gun.x, gun.y, dx / len * sp, dy / len * sp, false)
      unit.fire = groundFireReload(unit.kind)
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
    if (b.dying) return
    b.t += dt
    if (!b.entered) {
      b.y += 0.07f * dt
      if (b.y >= 0.08f) {
        b.y = 0.08f
        b.entered = true
        b.t = 0f
      }
      return
    }
    b.x = 0.5f - Boss.BOSS_W / 2f + sin(b.t * 0.4f) * 0.22f
    b.fire -= dt
    if (b.fire > 0f) return
    b.pattern = ((b.t / 3.2f).toInt()) % 3
    when (b.pattern) {
      0 -> {
        val gun = bossMuzzle(b)
        for (i in -1..1) {
          shots += Shot(gun.x + i * 0.04f, gun.y, i * 0.05f, 0.18f, false)
        }
        b.fire = 0.9f
      }
      1 -> {
        val gun = bossMuzzle(b)
        val hull = bossSprite(b)
        val dx = playerX - gun.x
        val dy = playerY - gun.y
        val len = kotlin.math.sqrt(dx * dx + dy * dy).coerceAtLeast(0.05f)
        shots += Shot(gun.x, gun.y, dx / len * 0.16f, dy / len * 0.16f, false)
        if (random.nextBoolean()) {
          shots += Shot(hull.x + hull.w * 0.18f, hull.y + hull.h * 0.62f, -0.03f, 0.19f, false)
        } else {
          shots += Shot(hull.x + hull.w * 0.82f, hull.y + hull.h * 0.62f, 0.03f, 0.19f, false)
        }
        b.fire = 0.48f
      }
      else -> {
        val gun = bossMuzzle(b)
        for (i in 0..2) {
          val a = (i / 2f - 0.5f) * 1.2f
          shots += Shot(gun.x, gun.y, sin(a) * 0.14f, 0.15f + abs(cos(a)) * 0.05f, false)
        }
        b.fire = 1.1f
      }
    }
  }

  private fun moveMobs(dt: Float) {
    mobs.filter { it.alive }.forEach { mob ->
      mob.t += dt
      mob.fire -= dt
      when {
        mob.kind == MobKind.BOMBER -> {
          mob.y += 0.11f * dt
          mob.x += sin(mob.t * 0.85f) * 0.12f * dt
          mob.x = mob.x.coerceIn(0.06f, 1f - mobW(MobKind.BOMBER) - 0.06f)
        }
        mob.form >= 0 -> followForm(mob)
        mob.kind == MobKind.DIVE -> {
          mob.y += 0.12f * dt
          val mw = mobW(mob.kind)
          mob.x += (0.5f - (mob.x + mw / 2f)) * 0.18f * dt
        }
        else -> {
          mob.y += 0.03f * dt
        }
      }
      if (mob.y > 1.12f) mob.alive = false
      if (mob.fire <= 0f && mob.y > 0.02f && mob.y < 0.72f) {
        val gun = mobMuzzle(mob)
        when (mob.kind) {
          MobKind.FIGHTER, MobKind.DIVE -> shots += Shot(gun.x, gun.y, 0f, 0.19f, false)
          MobKind.BOMBER -> {
            shots += Shot(gun.x, gun.y, 0f, 0.17f, false)
          }
        }
        mob.fire = if (mob.kind == MobKind.BOMBER) 2.1f else 3.6f
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
      p.y += 0.07f * dt
      if (p.y > 1.1f) p.alive = false
    }
    pickups.removeAll { !it.alive }
  }

  private fun collide() {
    shots.filter { it.alive && it.fromPlayer }.forEach { shot ->
      val hit = mobs.firstOrNull { it.alive && hitsMob(shot.x, shot.y, it) }
      if (hit != null) {
        shot.alive = false
        hurtMob(hit, 1)
      } else {
        val gHit = grounds.firstOrNull { it.alive && hitsGround(shot.x, shot.y, it) }
        if (gHit != null) {
          shot.alive = false
          hurtGround(gHit, 1)
        } else {
          val b = boss
          if (b != null && !b.dying && hitsBoss(shot.x, shot.y, b)) {
            shot.alive = false
            hurtBoss(1)
          }
        }
      }
    }
    pickups.filter { it.alive }.forEach { p ->
      if (playerOnField() && boxes(pickupBody(), pickupBox(p))) {
        p.alive = false
        power = (power + 1).coerceAtMost(2)
      }
    }
    if (!playerOnField() || invuln > 0f || phase == Phase.DEMO) return
    val px = playerLeft()
    val py = playerTop()
    val bolt = shots.firstOrNull { it.alive && !it.fromPlayer && pointIn(it.x, it.y, px + SHIP_W * 0.28f, py + SHIP_H * 0.12f, SHIP_W * 0.44f, SHIP_H * 0.55f) }
    val ram =
      mobs.firstOrNull {
        it.alive && ramMob(px + 0.03f, py + 0.02f, SHIP_W - 0.06f, SHIP_H - 0.04f, it)
      }
    val ramGround =
      grounds.firstOrNull {
        it.alive && ramGround(px + 0.03f, py + 0.02f, SHIP_W - 0.06f, SHIP_H - 0.04f, it)
      }
    val bossHit =
      boss?.let { b ->
        !b.dying && ramBoss(px + 0.03f, py + 0.02f, SHIP_W - 0.06f, SHIP_H - 0.04f, b)
      } == true
    if (bolt != null || ram != null || ramGround != null || bossHit) {
      bolt?.alive = false
      ram?.let { killMob(it) }
      ramGround?.let { killGround(it) }
      if (bolt != null && ram == null && ramGround == null && !bossHit) {
        chipPlayer()
      } else {
        hitPlayer()
      }
    }
  }

  private fun hurtGround(unit: GroundUnit, dmg: Int) {
    if (!unit.alive) return
    unit.hp -= dmg
    unit.hitFlash = 0.14f
    if (unit.hp <= 0) killGround(unit)
  }

  private fun killGround(unit: GroundUnit) {
    unit.alive = false
    score += groundScore(unit.kind)
    val sprite = groundSprite(unit)
    if (unit.kind == GroundKind.BATTLESHIP) {
      val cx = sprite.x + sprite.w / 2f
      val cy = sprite.y + sprite.h / 2f
      boom(cx, cy, true, 2.5f)
      boom(cx, cy - sprite.h * 0.34f, true, 1.9f, 0.07f)
      boom(cx, cy + sprite.h * 0.32f, true, 2.0f, 0.14f)
      boom(cx - sprite.w * 0.30f, cy - sprite.h * 0.08f, true, 1.7f, 0.20f)
      boom(cx + sprite.w * 0.30f, cy + sprite.h * 0.10f, true, 1.8f, 0.28f)
      boom(cx, cy, true, 2.8f, 0.40f)
    } else {
      boom(
        sprite.x + sprite.w / 2f,
        sprite.y + sprite.h / 2f,
        unit.kind.isShoreTile(),
      )
    }
  }

  private fun hurtMob(mob: Mob, dmg: Int) {
    if (!mob.alive) return
    mob.hp -= dmg
    mob.hitFlash = 0.14f
    if (mob.hp <= 0) killMob(mob)
  }

  private fun hurtBoss(dmg: Int) {
    val b = boss ?: return
    if (b.dying) return
    b.hp -= dmg
    b.hitFlash = 0.12f
    if (b.hp <= 0) startBossDeath()
  }

  private fun startBossDeath() {
    val b = boss ?: return
    b.dying = true
    b.hitFlash = 1.6f
    b.hp = 0
    bossDeath = 1.55f
    deathBoomIn = 0f
    shots.removeAll { !it.fromPlayer }
    boom(b.centerX(), b.centerY(), true, 2.1f)
  }

  private fun tickBossDeath(dt: Float) {
    val b = boss ?: return
    if (!b.dying) return
    b.hitFlash = if (((bossDeath * 22f).toInt() % 2) == 0) 0.2f else 0f
    deathBoomIn -= dt
    if (deathBoomIn <= 0f) {
      val ox = (random.nextFloat() - 0.5f) * Boss.BOSS_W * 0.85f
      val oy = (random.nextFloat() - 0.5f) * Boss.BOSS_H * 0.9f
      boom(b.centerX() + ox, b.centerY() + oy, true, 1.5f + random.nextFloat() * 0.8f)
      deathBoomIn = 0.11f
    }
    bossDeath -= dt
    if (bossDeath <= 0f) finishBossDeath()
  }

  private fun finishBossDeath() {
    val b = boss ?: return
    boom(b.centerX(), b.centerY(), true, 2.4f)
    boom(b.centerX() - 0.12f, b.centerY() + 0.05f, true, 1.8f)
    boom(b.centerX() + 0.14f, b.centerY() - 0.04f, true, 1.9f)
    boom(b.x + 0.08f, b.y + Boss.BOSS_H * 0.4f, true, 1.6f)
    boom(b.x + Boss.BOSS_W - 0.08f, b.y + Boss.BOSS_H * 0.55f, true, 1.7f)
    score += 5000
    boss = null
    bossDeath = 0f
    if (phase == Phase.DEMO) {
      beginDemo()
    } else {
      phase = Phase.CLEARED
    }
  }

  private fun killMob(mob: Mob) {
    mob.alive = false
    score += when (mob.kind) {
      MobKind.FIGHTER -> 100
      MobKind.DIVE -> 150
      MobKind.BOMBER -> 300
    }
    val cx = mob.x + mobW(mob.kind) / 2f
    val cy = mob.y + mobH(mob.kind) / 2f
    if (mob.kind == MobKind.BOMBER) {
      val wing = mobW(MobKind.BOMBER) * 0.38f
      boom(cx - wing, cy + 0.012f, false, 1.05f)
      boom(cx + wing, cy + 0.012f, false, 1.05f, wait = 0.07f)
    } else {
      boom(cx, cy, false)
    }
    if (mob.drop) pickups += Pickup(mob.x + 0.02f, mob.y)
  }

  private fun chipPlayer() {
    if (phase == Phase.DEMO) return
    playerHp -= 1
    invuln = 0.45f
    if (playerHp <= 0) hitPlayer()
  }

  private fun hitPlayer() {
    if (phase == Phase.DEMO) return
    playerHp = 0
    lives -= 1
    boom(playerX, playerY, true)
    shots.removeAll { !it.fromPlayer }
    power = (power - 1).coerceAtLeast(0)
    if (lives <= 0) {
      respawnIn = 0f
      phase = Phase.GAME_OVER
    } else {
      respawnIn = 0.55f
    }
  }

  private fun boom(x: Float, y: Float, big: Boolean, scale: Float = 1f, wait: Float = 0f) {
    val blast = Blast(x, y, big = big, scale = scale, wait = wait)
    blasts += blast
    if (wait <= 0f) cueBoom(blast)
  }

  private fun cueBoom(blast: Blast) {
    cues += SfxCue(if (blast.big) SfxKind.BOOM_BIG else SfxKind.BOOM, blast.x)
  }

  private fun hitsGround(px: Float, py: Float, unit: GroundUnit): Boolean = pointIn(px, py, groundHurt(unit))

  private fun ramGround(px: Float, py: Float, pw: Float, ph: Float, unit: GroundUnit): Boolean = boxes(px, py, pw, ph, groundHurt(unit))

  private fun groundMuzzle(unit: GroundUnit): Vec {
    val sprite = groundSprite(unit)
    return Vec(sprite.x + sprite.w * 0.5f, sprite.y + sprite.h * 0.92f)
  }

  private fun groundSprite(unit: GroundUnit): Box {
    val gw = groundDrawW(unit)
    val gh = groundDrawH(unit, viewAspect)
    return if (unit.kind.isShoreTile() || unit.kind == GroundKind.PATROL) {
      Box(unit.x, unit.y, gw, gh)
    } else {
      fitSprite(unit.x, unit.y, gw, gh, groundArtW(unit.kind), groundArtH(unit.kind))
    }
  }

  private fun groundHurt(unit: GroundUnit): Box {
    val hull = groundSprite(unit)
    val ix = hull.w * 0.12f
    val iy = hull.h * 0.10f
    return Box(hull.x + ix, hull.y + iy, hull.w - ix * 2f, hull.h - iy * 2f)
  }

  private fun hitsMob(px: Float, py: Float, mob: Mob): Boolean {
    val sprite = mobSprite(mob)
    return pointIn(px, py, fuseBox(sprite, mob.kind)) || pointIn(px, py, wingBox(sprite, mob.kind))
  }

  private fun ramMob(px: Float, py: Float, pw: Float, ph: Float, mob: Mob): Boolean {
    val sprite = mobSprite(mob)
    return boxes(px, py, pw, ph, fuseBox(sprite, mob.kind)) || boxes(px, py, pw, ph, wingBox(sprite, mob.kind))
  }

  private fun hitsBoss(px: Float, py: Float, b: Boss): Boolean = pointIn(px, py, bossHurt(b))

  private fun ramBoss(px: Float, py: Float, pw: Float, ph: Float, b: Boss): Boolean = boxes(px, py, pw, ph, bossHurt(b))

  private fun mobMuzzle(mob: Mob): Vec {
    val sprite = mobSprite(mob)
    return Vec(sprite.x + sprite.w * 0.5f, sprite.y + sprite.h * 0.97f)
  }

  private fun bossMuzzle(b: Boss): Vec {
    val hull = bossSprite(b)
    return Vec(hull.x + hull.w * 0.5f, hull.y + hull.h * 0.96f)
  }

  private fun mobSprite(mob: Mob): Box {
    val artW = if (mob.kind == MobKind.BOMBER) BOMBER_ART_W else FIGHTER_ART_W
    val artH = if (mob.kind == MobKind.BOMBER) BOMBER_ART_H else FIGHTER_ART_H
    return fitSprite(mob.x, mob.y, mobW(mob.kind), mobH(mob.kind), artW, artH)
  }

  private fun bossSprite(b: Boss): Box = fitSprite(b.x, b.y, Boss.BOSS_W, Boss.BOSS_H, BOSS_ART_W, BOSS_ART_H)

  private fun fitSprite(left: Float, top: Float, boxW: Float, boxH: Float, artW: Float, artH: Float): Box {
    val aspect = viewAspect
    val drawnW = min(boxW, boxH * (artW / artH) / aspect)
    val drawnH = min(boxH, boxW * (artH / artW) * aspect)
    return Box(left + (boxW - drawnW) * 0.5f, top + (boxH - drawnH) * 0.5f, drawnW, drawnH)
  }

  private fun bossHurt(b: Boss): Box {
    val hull = bossSprite(b)
    val ix = hull.w * 0.10f
    val iy = hull.h * 0.08f
    return Box(hull.x + ix, hull.y + iy, hull.w - ix * 2f, hull.h - iy * 2f)
  }

  private fun pickupBody(): Box {
    val insetX = SHIP_W * 0.22f
    val insetY = SHIP_H * 0.18f
    return Box(
      playerLeft() + insetX,
      playerTop() + insetY,
      SHIP_W - insetX * 2f,
      SHIP_H - insetY * 2f,
    )
  }

  private fun pickupBox(p: Pickup): Box {
    val w = PICKUP_DRAW
    val h = PICKUP_DRAW * viewAspect
    val solid = 0.48f
    return Box(p.x + w * (1f - solid) * 0.5f, p.y + h * (1f - solid) * 0.5f, w * solid, h * solid)
  }

  private fun fuseBox(sprite: Box, kind: MobKind): Box {
    val frac = if (kind == MobKind.BOMBER) 0.17f else 0.22f
    val w = sprite.w * frac
    return Box(sprite.x + (sprite.w - w) * 0.5f, sprite.y, w, sprite.h)
  }

  private fun wingBox(sprite: Box, kind: MobKind): Box {
    val top = sprite.y + sprite.h * if (kind == MobKind.BOMBER) 0.27f else 0.30f
    val h = sprite.h * if (kind == MobKind.BOMBER) 0.36f else 0.30f
    val inset = sprite.w * 0.03f
    return Box(sprite.x + inset, top, sprite.w - inset * 2f, h)
  }

  private fun pointIn(px: Float, py: Float, box: Box): Boolean = pointIn(px, py, box.x, box.y, box.w, box.h)

  private fun pointIn(px: Float, py: Float, x: Float, y: Float, w: Float, h: Float): Boolean =
    px >= x && px <= x + w && py >= y && py <= y + h

  private fun boxes(a: Box, b: Box): Boolean = boxes(a.x, a.y, a.w, a.h, b.x, b.y, b.w, b.h)

  private fun boxes(ax: Float, ay: Float, aw: Float, ah: Float, box: Box): Boolean =
    boxes(ax, ay, aw, ah, box.x, box.y, box.w, box.h)

  private fun boxes(ax: Float, ay: Float, aw: Float, ah: Float, bx: Float, by: Float, bw: Float, bh: Float): Boolean =
    ax < bx + bw && ax + aw > bx && ay < by + bh && ay + ah > by

  companion object {
    const val PLAYER_HITS = 5
    const val SHIP_W = 0.168f
    const val SHIP_H = 0.138f
    const val DEFAULT_PLAYER_Y = 0.849f
    const val FIGHTER_W = 0.118f
    const val FIGHTER_H = 0.100f
    const val PICKUP_DRAW = 0.092f
    const val BOMBER_SCALE = 2.5f
    private const val FIGHTER_ART_W = 616f
    private const val FIGHTER_ART_H = 604f
    private const val BOMBER_ART_W = 1005f
    private const val BOMBER_ART_H = 768f
    private const val BOSS_ART_W = 1412f
    private const val BOSS_ART_H = 964f

    fun mobW(kind: MobKind): Float = if (kind == MobKind.BOMBER) FIGHTER_W * BOMBER_SCALE else FIGHTER_W

    fun mobH(kind: MobKind): Float = if (kind == MobKind.BOMBER) FIGHTER_H * BOMBER_SCALE else FIGHTER_H
  }

  private fun followForm(mob: Mob) {
    val (cx, cy) = formCenter(mob.form, mob.slot, mob.t)
    mob.x = cx - FIGHTER_W / 2f
    mob.y = cy - FIGHTER_H / 2f
  }

  private fun formCenter(form: Int, slot: Int, t: Float): Pair<Float, Float> {
    return when (form) {
      Forms.V_DOWN -> {
        val vx = (slot - 2) * 0.082f
        val vy = abs((slot - 2).toFloat()) * 0.048f
        val cx = 0.50f + sin(t * 1.25f) * 0.14f + vx
        val cy = -0.12f + t * 0.125f + vy
        cx to cy
      }
      Forms.RANK_ZIG -> {
        val cx = 0.30f + slot * 0.10f + sin(t * 2.35f) * 0.055f
        val cy = -0.10f + t * 0.118f
        cx to cy
      }
      Forms.CHAIN_L -> alongChain(left = true, t - slot * 0.20f)
      Forms.CHAIN_R -> alongChain(left = false, t - slot * 0.20f)
      Forms.RING -> {
        val ang = t * 1.55f + slot * (2f * PI.toFloat() / 5f)
        var rad = 0.22f
        var cy0 = -0.04f + (t * 0.38f).coerceAtMost(0.32f)
        if (t > 3.35f) {
          val peel = t - 3.35f
          cy0 += peel * 0.16f
          rad *= (1f - peel * 0.22f).coerceAtLeast(0.18f)
        }
        val cx = 0.50f + cos(ang) * rad
        val cy = cy0 + sin(ang) * rad * 0.70f
        cx to cy
      }
      else -> 0.5f to -0.2f
    }
  }
}

internal object Forms {
  const val NONE = -1
  const val V_DOWN = 0
  const val CHAIN_L = 1
  const val CHAIN_R = 2
  const val RANK_ZIG = 3
  const val RING = 4
}

private enum class PlaneBeat {
  V_DOWN,
  CHAIN_L,
  CHAIN_R,
  RANK_ZIG,
  RING,
  BOMBERS,
}

private val PLANE_WAVES =
  listOf(
    PlaneBeat.V_DOWN,
    PlaneBeat.CHAIN_L,
    PlaneBeat.RANK_ZIG,
    PlaneBeat.BOMBERS,
    PlaneBeat.CHAIN_R,
    PlaneBeat.RING,
    PlaneBeat.V_DOWN,
    PlaneBeat.BOMBERS,
  )

private val CHAIN_L_PATH =
  floatArrayOf(
    0.10f, -0.16f,
    0.32f, 0.05f,
    0.58f, 0.12f,
    0.72f, 0.26f,
    0.64f, 0.42f,
    0.40f, 0.50f,
    0.28f, 0.68f,
    0.38f, 1.20f,
  )

private fun alongChain(left: Boolean, u: Float): Pair<Float, Float> {
  val p = alongPath(CHAIN_L_PATH, u.coerceAtLeast(0f) * 0.46f)
  return if (left) p else (1f - p.first) to p.second
}

private fun alongPath(pts: FloatArray, dist: Float): Pair<Float, Float> {
  val n = pts.size / 2
  if (n == 0) return 0.5f to -0.2f
  if (dist <= 0f) return pts[0] to pts[1]
  var remain = dist
  var i = 0
  while (i < n - 1) {
    val x0 = pts[i * 2]
    val y0 = pts[i * 2 + 1]
    val x1 = pts[i * 2 + 2]
    val y1 = pts[i * 2 + 3]
    val len = hypot(x1 - x0, y1 - y0)
    if (remain <= len) {
      val k = if (len < 1e-6f) 0f else remain / len
      return x0 + (x1 - x0) * k to y0 + (y1 - y0) * k
    }
    remain -= len
    i++
  }
  return pts[pts.size - 2] to pts[pts.size - 1]
}

private data class Box(val x: Float, val y: Float, val w: Float, val h: Float)

private data class Vec(val x: Float, val y: Float)

private fun cos(a: Float): Float = kotlin.math.cos(a.toDouble()).toFloat()
private fun abs(v: Float): Float = kotlin.math.abs(v)
