package com.example.wingstrike.game

object LandMask {
  fun isLand(u: Float, v: Float): Boolean {
    val x = wrap(u, LandMaskData.W)
    val y = wrap(v, LandMaskData.H)
    val i = y * LandMaskData.W + x
    return LandMaskData.bits[i ushr 6] ushr (i and 63) and 1L != 0L
  }

  fun clear(u: Float, v: Float, du: Float, dv: Float, land: Boolean): Boolean {
    val stepsX = 5
    val stepsY = 5
    for (iy in 0 until stepsY) {
      for (ix in 0 until stepsX) {
        val su = u + (ix / (stepsX - 1f) - 0.5f) * du
        val sv = v + (iy / (stepsY - 1f) - 0.5f) * dv
        if (isLand(su, sv) != land) return false
      }
    }
    return true
  }

  /** True if this land cell sits on the channel edge (water a few steps toward mid-map). */
  fun onShore(u: Float, v: Float, leftCoast: Boolean): Boolean {
    if (!isLand(u, v)) return false
    val dir = if (leftCoast) 1f else -1f
    var sawWater = false
    for (k in 1..8) {
      if (!isLand(u + dir * k * 0.012f, v)) {
        sawWater = true
        break
      }
    }
    return sawWater
  }

  private fun wrap(t: Float, n: Int): Int {
    val u = t - kotlin.math.floor(t.toDouble()).toFloat()
    return (u * n).toInt().coerceIn(0, n - 1)
  }
}
