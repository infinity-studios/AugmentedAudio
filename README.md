# Augmented Audio — Android (libpd)

A personal augmented-audio instrument. The mic reacts to your surroundings;
your own music gets swelled, crushed, delayed and reverbed by room loudness,
detected beats, and phone tilt. Pure Data runs the DSP via libpd; this app is
the shell + sensor bridge.

## Setup

The audio engine (libpd) is **vendored in-tree** at `pd-core/` — no submodule,
no extra commands. The whole thing is plain files, ready to upload to GitHub
and build in the cloud (see `.github/workflows/build.yml`), or to open directly
in Android Studio and Run on your S22.

## Using it

- On launch it asks for microphone permission, then auto-plays a bundled
  ambient loop and starts reacting. Headphones recommended (mic + speaker in
  the same room will feed back).
- **Make noise / play a beat near the phone** → your track grits up and the
  echoes bloom.
- **Tilt the phone** → reverb swells (accelerometer Y axis).

## Swapping in your own music

The track lives at `app/src/main/res/raw/patch.zip` → inside is
`patch/mytrack.wav`. Replace that WAV with your own (keep it named
`mytrack.wav`, or edit the `open ./mytrack.wav` message in the patch), re-zip
the `patch/` contents, and rebuild. WAV/AIFF only — readsf~ won't read MP3.

## Where the sensor bridge is

`MainActivity.kt` → `onSensorChanged` sends `accelX / accelY / accelZ` into Pd.
The patch listens on `[r accelY]`. Add more mappings by receiving `accelX` or
`accelZ` in the patch — the app already sends all three.

## Status / honesty

The Pd patch (stages 1–4) was built but not yet hardware-tested. Expect to
fine-tune: `bonk~` beat sensitivity, the `clip 40 90` loudness range, and the
baseline bitcrush rate. All are small edits in the patch. The rjlib vocoder is
not wired in yet — that's the next addition.

## Licensing

The rjlib Pd abstractions bundled in `patch.zip` are GPLv3 (© Reality Jockey
Ltd.). This app therefore inherits GPLv3. Fine for personal use; if you ever
distribute it, keep it GPLv3 and include the rjlib licence.
