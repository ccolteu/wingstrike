package com.example.wingstrike.game

enum class GroundKind {
  PATROL,
  DESTROYER,
  BATTLESHIP,
  SUB,
  TANK,
  CANNON,
  BARRACKS,
  BUNKER,
  DOCK,
  DOCK_SLIP,
  HANGAR,
  RUNWAY,
  YARD,
  BOAT,
}

data class GroundUnit(
  var mapU: Float,
  var mapV: Float,
  val kind: GroundKind,
  var hp: Int,
  var fire: Float = 0.8f,
  var alive: Boolean = true,
  var hitFlash: Float = 0f,
  var x: Float = 0f,
  var y: Float = 0f,
  var mapH: Float = 0f,
  val leftShore: Boolean = false,
  var roamT: Float = 0f,
  var roamA: Float = 0f,
  var roamB: Float = 0f,
  var roamSpeed: Float = 0f,
)

object StageMap {
  const val ZOOM = 1f
  const val LAND_PANELS = 6
  const val PANEL_H_OVER_W = 1536f / 1024f
  const val TILE_H_OVER_W = PANEL_H_OVER_W * LAND_PANELS

  fun periodN(viewAspect: Float): Float = viewAspect * TILE_H_OVER_W * ZOOM

  fun screenX(mapU: Float): Float = (1f - ZOOM) / 2f + mapU * ZOOM

  fun screenY(mapV: Float, scroll: Float, viewAspect: Float): Float {
    val period = periodN(viewAspect)
    val off = ((scroll % period) + period) % period
    var y = off + mapV * period
    val mid = 0.45f
    while (y > mid + period * 0.5f) y -= period
    while (y < mid - period * 0.5f) y += period
    return y
  }
}

/** Shared strip width so shore tiles stitch with matching seams. */
internal const val SHORE_W = 0.26f

internal fun GroundKind.isShoreTile(): Boolean =
  this == GroundKind.BARRACKS ||
    this == GroundKind.DOCK ||
    this == GroundKind.DOCK_SLIP ||
    this == GroundKind.HANGAR ||
    this == GroundKind.RUNWAY ||
    this == GroundKind.YARD

internal fun GroundKind.onLand(): Boolean =
  isShoreTile() || this == GroundKind.TANK || this == GroundKind.CANNON || this == GroundKind.BUNKER

internal fun GroundKind.shoots(): Boolean =
  this != GroundKind.BARRACKS &&
    this != GroundKind.DOCK &&
    this != GroundKind.DOCK_SLIP &&
    this != GroundKind.HANGAR &&
    this != GroundKind.RUNWAY &&
    this != GroundKind.YARD

internal fun GroundKind.baseLayer(): Boolean = isShoreTile()

internal fun groundW(kind: GroundKind): Float =
  when (kind) {
    GroundKind.PATROL -> 0.092f
    GroundKind.DESTROYER -> 0.092f
    GroundKind.BATTLESHIP -> 0.120f
    GroundKind.SUB -> groundW(GroundKind.DESTROYER) * 0.7f * 0.66f
    GroundKind.TANK -> 0.080f
    GroundKind.CANNON -> 0.078f
    GroundKind.BUNKER -> 0.095f
    GroundKind.BOAT -> 0.100f
    GroundKind.BARRACKS,
    GroundKind.DOCK,
    GroundKind.DOCK_SLIP,
    GroundKind.HANGAR,
    GroundKind.RUNWAY,
    GroundKind.YARD,
    -> SHORE_W
  }

internal fun groundH(kind: GroundKind): Float =
  when (kind) {
    GroundKind.PATROL -> 0.138f
    GroundKind.DESTROYER -> 0.220f
    GroundKind.BATTLESHIP -> 0.340f
    GroundKind.SUB -> groundH(GroundKind.DESTROYER) * 0.7f * 0.66f
    GroundKind.TANK -> 0.048f
    GroundKind.CANNON -> 0.080f
    GroundKind.BUNKER -> 0.090f
    GroundKind.BOAT -> 0.067f
    GroundKind.BARRACKS -> 0.16f
    GroundKind.DOCK -> 0.30f
    GroundKind.DOCK_SLIP -> 0.24f
    GroundKind.HANGAR -> 0.16f
    GroundKind.RUNWAY -> 0.22f
    GroundKind.YARD -> 0.16f
  }

internal fun groundDrawW(unit: GroundUnit): Float = groundW(unit.kind)

internal fun groundDrawH(unit: GroundUnit, viewAspect: Float): Float =
  if (unit.kind.isShoreTile()) unit.mapH * StageMap.periodN(viewAspect).coerceAtLeast(0.8f) else groundH(unit.kind)

internal fun groundHp(kind: GroundKind): Int =
  when (kind) {
    GroundKind.PATROL -> 3
    GroundKind.DESTROYER -> 8
    GroundKind.BATTLESHIP -> 16
    GroundKind.SUB -> 6
    GroundKind.TANK -> 5
    GroundKind.CANNON -> 4
    GroundKind.BARRACKS -> 10
    GroundKind.BUNKER -> 8
    GroundKind.DOCK -> 12
    GroundKind.DOCK_SLIP -> 12
    GroundKind.HANGAR -> 14
    GroundKind.RUNWAY -> 16
    GroundKind.YARD -> 12
    GroundKind.BOAT -> 3
  }

internal fun groundScore(kind: GroundKind): Int =
  when (kind) {
    GroundKind.PATROL -> 120
    GroundKind.DESTROYER -> 250
    GroundKind.BATTLESHIP -> 500
    GroundKind.SUB -> 200
    GroundKind.TANK -> 150
    GroundKind.CANNON -> 180
    GroundKind.BARRACKS -> 220
    GroundKind.BUNKER -> 200
    GroundKind.DOCK -> 180
    GroundKind.DOCK_SLIP -> 180
    GroundKind.HANGAR -> 240
    GroundKind.RUNWAY -> 200
    GroundKind.YARD -> 200
    GroundKind.BOAT -> 80
  }

internal fun groundArtW(kind: GroundKind): Float =
  when (kind) {
    GroundKind.PATROL -> 926f
    GroundKind.DESTROYER -> 274f
    GroundKind.BATTLESHIP -> 396f
    GroundKind.SUB -> 226f
    GroundKind.TANK -> 440f
    GroundKind.CANNON -> 796f
    GroundKind.BARRACKS -> 1024f
    GroundKind.BUNKER -> 737f
    GroundKind.DOCK -> 1007f
    GroundKind.DOCK_SLIP -> 1024f
    GroundKind.HANGAR -> 1024f
    GroundKind.RUNWAY -> 1024f
    GroundKind.YARD -> 1024f
    GroundKind.BOAT -> 1343f
  }

internal fun groundArtH(kind: GroundKind): Float =
  when (kind) {
    GroundKind.PATROL -> 1389f
    GroundKind.DESTROYER -> 1364f
    GroundKind.BATTLESHIP -> 1496f
    GroundKind.SUB -> 1467f
    GroundKind.TANK -> 814f
    GroundKind.CANNON -> 937f
    GroundKind.BARRACKS -> 1536f
    GroundKind.BUNKER -> 846f
    GroundKind.DOCK -> 1507f
    GroundKind.DOCK_SLIP -> 1536f
    GroundKind.HANGAR -> 1536f
    GroundKind.RUNWAY -> 1536f
    GroundKind.YARD -> 1536f
    GroundKind.BOAT -> 453f
  }

internal fun groundFireReload(kind: GroundKind): Float =
  when (kind) {
    GroundKind.PATROL -> 3.4f
    GroundKind.DESTROYER -> 2.8f
    GroundKind.BATTLESHIP -> 2.2f
    GroundKind.SUB -> 4.0f
    GroundKind.TANK -> 3.2f
    GroundKind.CANNON -> 2.6f
    GroundKind.BARRACKS -> 99f
    GroundKind.BUNKER -> 2.8f
    GroundKind.DOCK -> 99f
    GroundKind.DOCK_SLIP -> 99f
    GroundKind.HANGAR -> 99f
    GroundKind.RUNWAY -> 99f
    GroundKind.YARD -> 99f
    GroundKind.BOAT -> 3.6f
  }

internal fun groundShotSpeed(kind: GroundKind): Float =
  when (kind) {
    GroundKind.SUB -> 0.11f
    GroundKind.BATTLESHIP -> 0.16f
    GroundKind.CANNON, GroundKind.BUNKER -> 0.18f
    else -> 0.15f
  }

/** Map UV anchors, one looping tile. Same every run. */
internal data class GroundAnchor(
  val kind: GroundKind,
  val u: Float,
  val v: Float,
  val fire: Float,
  val leftShore: Boolean = false,
  val mapH: Float = 0f,
  val roamA: Float = 0f,
  val roamB: Float = 0f,
  val roamSpeed: Float = 0f,
)

internal fun GroundUnit.flipDrawX(): Boolean {
  if (kind == GroundKind.BOAT && roamSpeed != 0f) {
    return kotlin.math.cos(roamT * roamSpeed) < 0f
  }
  return kind.isShoreTile() && leftShore
}

private const val WATER_L = 0.24f
private const val WATER_R = 0.76f

private fun shoreU(left: Boolean, kind: GroundKind): Float {
  val half = groundW(kind) / 2f
  val pad = 0.016f
  return if (left) WATER_L + pad + half else WATER_R - pad - half
}

private fun packShore(left: Boolean, kinds: List<GroundKind>): List<GroundAnchor> {
  val period = StageMap.periodN(9f / 16f).coerceAtLeast(0.8f)
  var v = if (left) 0.08f else 0.10f
  val out = ArrayList<GroundAnchor>(kinds.size)
  for (kind in kinds) {
    val halfV = groundH(kind) / period * 0.5f
    v += halfV + 0.006f
    if (v + halfV > 0.90f) break
    out += GroundAnchor(kind, shoreU(left, kind), v, groundFireReload(kind) * 0.45f)
    v += halfV + 0.018f
  }
  return out
}

private val LEFT_ANCHORS =
  listOf(
    GroundKind.DESTROYER,
    GroundKind.SUB,
    GroundKind.DESTROYER,
    GroundKind.BATTLESHIP,
    GroundKind.SUB,
    GroundKind.DESTROYER,
    GroundKind.SUB,
    GroundKind.BATTLESHIP,
    GroundKind.DESTROYER,
    GroundKind.SUB,
  )

private val RIGHT_ANCHORS =
  listOf(
    GroundKind.SUB,
    GroundKind.DESTROYER,
    GroundKind.BATTLESHIP,
    GroundKind.DESTROYER,
    GroundKind.SUB,
    GroundKind.DESTROYER,
    GroundKind.BATTLESHIP,
    GroundKind.SUB,
    GroundKind.DESTROYER,
    GroundKind.SUB,
  )

internal val STAGE_GROUNDS: List<GroundAnchor> =
  packShore(left = true, LEFT_ANCHORS) +
    packShore(left = false, RIGHT_ANCHORS) +
    listOf(
      GroundAnchor(GroundKind.BOAT, 0.38f, 0.16f, 1.2f, roamA = 0.32f, roamB = 0.44f, roamSpeed = 0.55f),
      GroundAnchor(GroundKind.BOAT, 0.62f, 0.38f, 1.3f, roamA = 0.56f, roamB = 0.68f, roamSpeed = 0.40f),
      GroundAnchor(GroundKind.BOAT, 0.38f, 0.62f, 1.4f, roamA = 0.32f, roamB = 0.44f, roamSpeed = 0.70f),
      GroundAnchor(GroundKind.BOAT, 0.62f, 0.82f, 1.2f, roamA = 0.56f, roamB = 0.68f, roamSpeed = 0.48f),
    )
