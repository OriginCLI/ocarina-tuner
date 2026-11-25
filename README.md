# Ocarina Trainer

A low-latency tuner and breath-control trainer for a 12-hole tenor ocarina in C (A4 to F6),
built for Android with Kotlin, Jetpack Compose and a native C++ audio engine.

## Install

1. Open the latest [Release](../../releases/latest) on your phone and download the `.apk`.
2. Open it and allow your browser/file manager to install apps when Android asks.
3. Later updates: **Settings (gear) > App update > Check for update**. The app downloads the new
   APK and hands it to Android's installer. Every build is signed with the same key, so updates
   install over the top and keep your settings and skins.

Every push to `main` builds a release automatically (`.github/workflows/build.yml`).
Pull requests build an APK too: it is attached to the workflow run as an artifact.

## Features

- **Tuner**: big note name, Hz and cents, arc gauge, and the fingering for the note you're playing
  drawn on the ocarina (violet = covered). The stepper shows any fingering when you're silent.
- **Breath**: on an ocarina more air = sharp, less air = flat, so the cents offset is a breath
  meter. "Blow softer / harder", a 5 s scrolling pitch trace, wobble stats and attack check.
- **Practice**
  - *Long tone*: hold a note within the in-tune window (±10¢ by default) for 8 s of sound;
    scores time in tune and steadiness.
  - *Scale climb*: A4 to F6, holding each note 1.5 s; shows your average offset per note so you
    learn how much air each one needs.
  - *Attack*: shows whether each note starts sharp or flat compared with where it settles.
- **Settings**: A4 reference (430 to 450 Hz), in-tune window, mic sensitivity, debug overlay.
- Works offline, no accounts, no ads, screen stays on while open. Network is only used when you
  tap *Check for update*.

## Fingerings

`app/src/main/assets/fingerings.json` lists, for each note, the holes that are **open**.
Add sharps/flats by adding entries (e.g. `{ "note": "C#5", "open": [...] }`).
Without rebuilding: *Settings > Fingerings > Export*, edit the file, then *Import*.
A broken file is rejected with a message and the built-in chart stays in use.

## Skins

Five skins are bundled (`app/src/main/assets/skins/`: Golden Clouds (default), Great Wave, Sakura, Royal Crest, Forest Ivy). More are in
`extra-skins/`: copy one to your phone and use *Skins (palette button) > Import your own*.

To make your own: a hole-free, top-down picture of the ocarina, wide end left, mouthpiece at the
lower left, transparent background (PNG). On import the app crops it, places the 12 holes by
following the outline, and opens an editor where you drag holes into place. Imported skins stay
on that phone. The pencil on any skin (bundled ones too) opens the same editor; adjustments are
saved on the phone and *Reset* restores the default layout.

The default layout (`tools/prepare_skins.py` and `HoleLayout` in `core`) was measured from a photo
of an STL 12-hole: left-hand holes along the far side of the wide end, right-hand holes along the
near side toward the tip, subholes between the rows.

To bundle a new skin in the APK: `python3 tools/prepare_skins.py SRC_DIR app/src/main/assets/skins`
(`SRC_DIR` holds `<id>.png` files), then add/adjust its `order` in `skins.json`.

## How it stays fast

| Stage | What happens | Typical cost |
|---|---|---|
| Mic input | Oboe/AAudio, low-latency + exclusive mode, device native sample rate, `Unprocessed` input preset (falls back to `VoiceRecognition`), buffer = 1 burst | device dependent, ~5 to 20 ms |
| Analysis | YIN in the time domain on the audio callback thread (high priority), 1024-sample window (~21 ms), hop 128 (~2.7 ms), lag search limited to 400 to 1500 Hz, parabolic interpolation, 3-frame median | ~20 µs per window on a desktop CPU |
| Hand-off | lock-free single-producer/single-consumer ring | none |
| UI | each display frame (up to 120 Hz) drains the ring right before drawing; gauges read state in the draw phase only | 1 frame |

Readings are hidden when the level is under the sensitivity gate or the YIN confidence is low.
The **debug overlay** (on by default; toggle in Settings) shows API, preset, sample rate, burst and
buffer size, analysis time, input latency, reading age, display rate and an estimated sound-to-screen
latency (target < 50 ms). That figure is an estimate built from those parts; only a real device can
tell you the true number.

## Project layout

- `app/src/main/cpp/` Oboe input stream, YIN detector (`PitchDetector.h`), JNI bridge
- `core/` pure Kotlin: notes/cents, fingerings, skins, drills, attack detection (unit tested on the JVM)
- `app/src/main/java/` Compose UI, controller, updater
- `tests/dsp/` desktop tests for the pitch detector: `cmake -S tests/dsp -B build && cmake --build build && ./build/dsp_tests`

Build locally with Android Studio or `./gradlew :app:assembleRelease`.

## Testing without a microphone

Debug builds (`./gradlew :app:assembleDebug`) accept a test tone over adb, which replaces the
mic input. This is handy on an emulator:

```
adb shell am broadcast -a app.ocarinatuner.TEST_TONE -p app.ocarinatuner --es note A4
adb shell am broadcast -a app.ocarinatuner.TEST_TONE -p app.ocarinatuner --es note C5 --ef cents -12 --ef db -40
adb shell am broadcast -a app.ocarinatuner.TEST_TONE -p app.ocarinatuner --es note off
```

`db` is the RMS level in dBFS (default -20). Release builds don't include this.
