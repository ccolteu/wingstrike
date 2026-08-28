package com.example.wingstrike.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.SoundPool
import com.example.wingstrike.R
import com.example.wingstrike.game.SfxCue
import com.example.wingstrike.game.SfxKind

class GameAudio(context: Context) {
  private val app = context.applicationContext
  private val mixer =
    AudioAttributes.Builder()
      .setUsage(AudioAttributes.USAGE_GAME)
      .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
      .build()
  private val pool =
    SoundPool.Builder()
      .setMaxStreams(12)
      .setAudioAttributes(mixer)
      .build()
  private val shotId = pool.load(app, R.raw.sfx_shot, 1)
  private val boomId = pool.load(app, R.raw.sfx_boom, 1)
  private val boomBigId = pool.load(app, R.raw.sfx_boom_big, 1)
  private var bgm: MediaPlayer? = null
  private var muted = false
  private var wantMusic = false

  fun playCues(cues: List<SfxCue>) {
    if (muted || cues.isEmpty()) return
    cues.forEach { playNow(it) }
  }

  private fun playNow(cue: SfxCue) {
    val id =
      when (cue.kind) {
        SfxKind.SHOT -> shotId
        SfxKind.BOOM -> boomId
        SfxKind.BOOM_BIG -> boomBigId
      }
    val pan = cue.x.coerceIn(0f, 1f)
    val left = (1f - pan).coerceIn(0.35f, 1f)
    val right = pan.coerceIn(0.35f, 1f)
    val vol =
      when (cue.kind) {
        SfxKind.SHOT -> 0.36f
        SfxKind.BOOM -> 0.95f
        SfxKind.BOOM_BIG -> 1.0f
      }
    pool.play(id, left * vol, right * vol, 1, 0, 1f)
  }

  fun setStageMusic(on: Boolean) {
    wantMusic = on
    if (!on) {
      bgm?.pause()
      return
    }
    if (muted) return
    val player = bgm ?: MediaPlayer.create(app, R.raw.bgm_stage)?.also {
      it.isLooping = true
      it.setVolume(0.42f, 0.42f)
      it.setAudioAttributes(
        AudioAttributes.Builder()
          .setUsage(AudioAttributes.USAGE_GAME)
          .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
          .build(),
      )
      bgm = it
    }
    if (player != null && !player.isPlaying && !muted) player.start()
  }

  fun pause() {
    muted = true
    bgm?.pause()
  }

  fun resume() {
    muted = false
    if (wantMusic) setStageMusic(true)
  }

  fun release() {
    pool.release()
    bgm?.release()
    bgm = null
  }
}
