#!/usr/bin/env python3
"""Procedural 16-bit PCM WAVs for Wing Strike (SFX + looping 90s shmup BGM)."""
from __future__ import annotations

import math
import os
import random
import struct
import sys
import wave

SR = 22050


def clamp(v: float) -> float:
    return -1.0 if v < -1.0 else 1.0 if v > 1.0 else v


def write_wav(path: str, samples: list[float]) -> None:
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with wave.open(path, "w") as w:
        w.setnchannels(1)
        w.setsampwidth(2)
        w.setframerate(SR)
        w.writeframes(b"".join(struct.pack("<h", int(clamp(s) * 32767)) for s in samples))


def env_exp(t: float, dur: float, tau: float) -> float:
    return math.exp(-t / tau) if 0 <= t < dur else 0.0


def noise() -> float:
    return random.random() * 2.0 - 1.0


def shot(dur: float = 0.085) -> list[float]:
    n = int(SR * dur)
    out = []
    for i in range(n):
        t = i / SR
        a = env_exp(t, dur, 0.028) * (1.0 - t / dur)
        f = 1480 - 920 * (t / dur)
        sq = 1.0 if math.sin(2 * math.pi * f * t) >= 0 else -1.0
        click = noise() * env_exp(t, 0.012, 0.004) * 0.35
        out.append(sq * a * 0.38 + click)
    return out


def boom(dur: float, bass: float, body: float) -> list[float]:
    n = int(SR * dur)
    out = []
    lp = 0.0
    for i in range(n):
        t = i / SR
        nse = noise()
        lp = lp * 0.82 + nse * 0.18
        crack = nse * env_exp(t, 0.04, 0.012)
        rumble = math.sin(2 * math.pi * (78 - 40 * t / dur) * t)
        rumble += 0.45 * math.sin(2 * math.pi * (46 - 18 * t / dur) * t)
        a = env_exp(t, dur, body)
        out.append(lp * a * 0.72 + crack * 0.28 + rumble * a * bass)
    peak = max(abs(s) for s in out) or 1.0
    return [s / peak * 0.92 for s in out]


def sq(phase: float, width: float = 0.5) -> float:
    return 1.0 if (phase % 1.0) < width else -1.0


def tri(phase: float) -> float:
    p = phase % 1.0
    return 4 * p - 1 if p < 0.5 else 3 - 4 * p


def note_hz(midi: int) -> float:
    return 440.0 * (2 ** ((midi - 69) / 12.0))


def music() -> list[float]:
    bpm = 148.0
    beat = 60.0 / bpm
    bars = 8
    beats = bars * 4
    n = int(SR * beats * beat)
    out = [0.0] * n

    bass_pat = [
        33, 33, 33, 33, 36, 36, 31, 31,
        33, 33, 38, 38, 36, 31, 33, 33,
    ]
    # sixteenth arpeggios, two octaves of A minor / C
    arp_a = [57, 60, 64, 69, 64, 60, 57, 52]
    arp_c = [60, 64, 67, 72, 67, 64, 60, 55]
    lead = [
        69, 0, 72, 0, 76, 76, 74, 0,
        72, 0, 69, 71, 72, 0, 64, 0,
        67, 0, 69, 71, 72, 74, 76, 0,
        74, 72, 71, 0, 69, 0, 64, 67,
        69, 0, 72, 0, 76, 79, 76, 0,
        74, 0, 72, 71, 69, 0, 64, 0,
        67, 69, 71, 72, 74, 0, 72, 0,
        69, 0, 64, 67, 69, 0, 57, 0,
    ]

    def add(i: int, v: float) -> None:
        if 0 <= i < n:
            out[i] += v

    for i in range(n):
        t = i / SR
        beat_i = t / beat
        bar = int(beat_i // 4) % bars
        eighth = int(beat_i * 2) % len(bass_pat)
        sixteenth = int(beat_i * 4)

        pos_in_beat = (t / beat) % 1.0
        if int(beat_i) % 2 == 0 and pos_in_beat < 0.12:
            add(i, math.sin(2 * math.pi * (90 - 50 * pos_in_beat / 0.12) * t) * (1 - pos_in_beat / 0.12) * 0.42)
        hat = 1.0 if (sixteenth % 2 == 1) else 0.35
        if (t / (beat / 2.0)) % 1.0 < 0.06:
            add(i, noise() * hat * 0.07)

        midi = bass_pat[eighth]
        add(i, sq(t * note_hz(midi), 0.42) * 0.16)
        add(i, math.sin(2 * math.pi * note_hz(midi - 12) * t) * 0.12)

        arp = arp_a if bar % 2 == 0 else arp_c
        add(i, sq(t * note_hz(arp[sixteenth % 8]), 0.25) * 0.07)

        lm = lead[int(beat_i * 2) % len(lead)]
        if lm:
            gate = 1.0 if ((t / (beat / 2.0)) % 1.0) < 0.82 else 0.0
            add(i, tri(t * note_hz(lm)) * 0.13 * gate)
            add(i, sq(t * note_hz(lm + 12), 0.18) * 0.03 * gate)

    delay = int(0.018 * SR)
    wet = out[:]
    for i in range(delay, n):
        wet[i] += out[i - delay] * 0.14
    out[:] = wet

    peak = max(abs(s) for s in out) or 1.0
    return [s / peak * 0.78 for s in out]


def main() -> None:
    raw = sys.argv[1] if len(sys.argv) > 1 else os.path.join(
        os.path.dirname(__file__), "..", "app", "src", "main", "res", "raw"
    )
    raw = os.path.abspath(raw)
    write_wav(os.path.join(raw, "sfx_shot.wav"), shot())
    write_wav(os.path.join(raw, "sfx_boom.wav"), boom(0.32, bass=0.22, body=0.09))
    write_wav(os.path.join(raw, "sfx_boom_big.wav"), boom(0.55, bass=0.38, body=0.16))
    write_wav(os.path.join(raw, "bgm_stage.wav"), music())
    print("wrote", raw)


if __name__ == "__main__":
    main()
