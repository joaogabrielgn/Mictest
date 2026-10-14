# Acoustic Direction Lab v0.2

Android AudioRecord experiment for direction-of-arrival research on Galaxy S25 (SM-S931B, Android 17).
Built with Android SDK 35, Gradle and Java 17. Public research prototype, NOT a validated acoustic camera.

## Install on the Galaxy S25 (phone only)

- Go to [GitHub Actions](https://github.com/joaogabrielgn/Mictest/actions).
- Open the newest green **Build Android APK** run, scroll to **Artifacts**.
- Download `AcousticDirectionLab-APK` (ZIP), extract `app-debug.apk` and install.
- **Important:** debug APKs built by GitHub Actions may have different signing keys. If Android refuses to update v0.1, export old WAV/TXT first, then uninstall v0.1 and install v0.2. Uninstalling removes the app's private recordings.
- Do not install from unknown GitHub forks.

## v0.2

1. Real-time **experimental GCC-PHAT** estimate using Hann window, pre-emphasis, phase-weighted cross-power spectrum and 3-point sub-sample peak interpolation.
2. Displays C0 versus each additional channel: relative delay (microseconds), peak/background ratio, peak contrast, channel level difference (dB), clipping fraction and conservative candidate/rejection label.
3. Constrains lag search to nominal S25 microphone spacing 142.6 mm (+4 sample guard margin); this is **not** a calibrated azimuth estimate.
4. Captures system orientation (rotation vector), gyroscope and accelerometer while recording. Shows live yaw/pitch/roll and exports raw IMU events.
5. Exports three types of data per recording: **WAV**, **analysis CSV**, **IMU CSV**. CSV read-completion timestamps are approximate and are NOT sample-accurate alignment of audio and IMU.
6. Default **CAMCORDER**, 2 channels, 48 kHz. Recording lasts up to 30 seconds; press **Parar** for shorter tests.

### Interpreting status

- CANDIDATO: passes provisional amplitude, clipping, difference and GCC-PHAT peak checks; not a statistical confidence level or verified direction.
- SINAL_FRACO: insufficient amplitude on at least one channel.
- CLIPPING: possible saturation above about 1% of samples in chosen window.
- CANAIS_SIMILARES: nearly duplicated channels.
- PICO_AMBIGUO: correlation peak insufficiently distinct.
- LIMITE_GEOMETRICO: max candidate lag at search boundary.
- Positive relative delay means channel B arrived after channel 0. **C0 and C1 are not automatically identifiable with the physical mic IDs** (Android reported both mics processed and mapped to C0).

The spatial DSP chooses the higher-energy 2048-frame segment from the current buffer (42.7 ms at 48 kHz). It logs summary estimates about every 250 ms, not for every acoustic event. Multiple simultaneous sources/reverberation make direction unreliable. Estimates with significant reflection or processed audio can be biased; please analyze WAV as ground truth.

## Guided validation: three stationary recordings

Keep phone still in a holder away from mechanical taps. Produce soft sharp sounds in air 0.5–1 m from phone, avoiding clipping. Record 15-20 seconds per position:

- **TOP:** source above upper mic.
- **BOTTOM:** source below lower mic.
- **CENTER:** centered in front of rear case at the phone's vertical midpoint.

Export WAV + analysis CSV from each run, and note which file is which. Compare delay distribution and invalid-event counts. Test in quiet non-reverberant area. After baseline tests, make one 20–30-second **slow sweep**, rotating and tilting the phone while the sound source stays FIXED and plays repeated broadband chirps or clicks. Export WAV + analysis CSV + IMU CSV.

**Do not assume different phone angles alone fix front/back ambiguity.** The current version records movement; it DOES NOT compute fused direction, 3D location or camera overlay yet.

## Known hardware facts from the S25 experiment

- Android microphone ID 22: `built_in_mic` at (0.0269, 0.0058, 0.0079) m.
- Android microphone ID 23: `back_mic` at (0.0546, 0.1456, 0.00415) m.
- An ID 30 TYPE_REMOTE_SUBMIX appears but is **virtual**, not an identified physical mic.
- `UNPROCESSED` is reported unsupported. With CAMCORDER stereo, both physical mics reported active, but channel mapping was `C0:PROCESSED` for each; true independent PCM is still unproven.

## Next version ideas

- AudioTimestamp-based mapping, more detailed hardware routes, controlled impulse-train detection and analysis with confidence calibration.
- Sound source direction estimation from stable repeated source + varying orientation.
- 3D ambiguity handling and camera overlay only AFTER validated angular accuracy.

## Build

CI runs JVM tests of the GCC-PHAT implementation, compiles debug APK and uploads the build artifact. Use the latest **green** run.
