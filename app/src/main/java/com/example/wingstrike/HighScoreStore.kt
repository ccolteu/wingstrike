package com.example.wingstrike

import android.content.Context

class HighScoreStore(context: Context) {
  private val prefs = context.applicationContext.getSharedPreferences("wingstrike", Context.MODE_PRIVATE)

  fun load(): Int = prefs.getInt(KEY, 0)

  fun save(score: Int) {
    if (score <= load()) return
    prefs.edit().putInt(KEY, score).apply()
  }

  private companion object {
    const val KEY = "high_score"
  }
}
