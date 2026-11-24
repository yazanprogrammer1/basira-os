# CLAUDE.md — Basira OS (بصيرة)

## What this project is

Basira OS is a phone-based mixed reality system. An Android phone sits in a cheap phone VR viewer with its back camera uncovered. The camera feed is shown in stereo (one half of the screen per eye), and app windows float in the room, world-locked with ARCore. The user controls everything with head gaze + pinch (MediaPipe hands) and an Arabic-first voice assistant called «بصيرة» that can also read and act on the page in a window.

Owner: Yazan Abu Ali, Android developer (Kotlin, Jetpack Compose, MVVM / Clean Architecture, Hilt, Room). The repo is public and is part of his portfolio, so code quality, structure, README and docs matter as much as features.

Full plan: `docs/PLAN.md`. Architecture: `docs/ARCHITECTURE.md`. Design: `docs/DESIGN.md` and `design/`.

## Hard constraints (do not design around these by accident)

- **Windows are web content or our own UI only.** A normal Android app cannot host other apps' activities. "Open Instagram" means the web version in a WebView. Never propose hidden APIs, root, or system-signature tricks for v1.
- **The phone runs everything.** No PC, no server for rendering. Cloud calls only for STT/LLM/TTS, behind interfaces.
- **60 fps rendering is sacred.** Anything heavy (hand tracking, voice, texture updates) runs off the GL thread and degrades before the frame rate does.
- **Heat is the main enemy.** Every feature must work under the thermal quality levels in `docs/ARCHITECTURE.md`.
- **ARCore is optional at runtime.** Without it we fall back to 3DoF (rotation-vector sensor); windows follow the head.
- **Arabic first.** System UI text is Arabic, RTL, font IBM Plex Sans Arabic. Voice commands work in Levantine Arabic first.

## Stack

- Kotlin, Coroutines/Flow, Hilt, Gradle multi-module with version catalog (`gradle/libs.versions.toml`)
- Use the latest stable versions of AGP, Kotlin, Compose BOM, ARCore, MediaPipe Tasks. Check current versions before pinning; do not guess.
- Rendering: OpenGL ES 3.0, `GLSurfaceView`, hand-written GLSL. No Unity, no Sceneform.
- Tracking: ARCore (`com.google.ar:core`), shared camera texture (GL_TEXTURE_EXTERNAL_OES)
- Windows: `VirtualDisplay` + `Presentation` rendering into a `SurfaceTexture`; content = `WebView` or `ComposeView`
- Hands: MediaPipe Tasks `HandLandmarker` (LIVE_STREAM), One Euro filter, gesture state machine
- Storage: Room (spaces, windows), DataStore (settings, lens calibration)
- minSdk 29, target/compile latest stable

## Modules

```
:app                  entry Activity, Hilt wiring, permissions, debug overlay
:core:common          shared types (Pose, Ray, QualityLevel), dispatchers, logging
:core:render          GL thread, compositor, stereo split, lens distortion, reprojection
:core:tracking        ARCore session + 3DoF fallback -> Flow<HeadPose>
:core:windows         WindowManager, panel hosting (VirtualDisplay), gaze raycast, input injection
:core:hands           MediaPipe wrapper, filtering, PinchStateMachine -> Flow<HandEvent>
:core:voice           wake word, STT, TTS behind interfaces
:core:agent           LLM client + tools (open_app, place_window, summarize_page, ...)
:core:spaces          Room DB for spaces and window layouts
:core:thermal         ThermalGovernor -> StateFlow<QualityLevel>
:ui:system            Compose system UI: dock, home grid, assistant bar, status pills
```

Rules: `:core:*` modules never depend on `:app`. `:core:render` knows nothing about WebView or ARCore types; it receives textures + poses. Each module exposes interfaces; implementations are bound in Hilt modules.

## How we work

- Follow the phases in `docs/PLAN.md` in order. **Do not start a phase until the previous phase's gate is met** and the user confirmed it on the device.
- Start each phase with a short plan (files to add or change, risks), then implement in small, reviewable commits.
- Conventional Commits (`feat(render): stereo split shader`). One logical change per commit.
- Every phase ends with: updated README section, a `docs/phases/phase-N.md` note (what was built, decisions, measured fps and temperature), and a git tag `v0.N`.
- Unit-test pure logic (pinch state machine, ray–quad intersection, distortion math, command parsing). GL and camera code is verified on device; add a debug overlay instead of guessing.
- Never hardcode API keys. Read them from `local.properties` into `BuildConfig`; `local.properties` stays git-ignored.
- When something cannot be verified from here (device behaviour, lens values, heat), say so and tell the user exactly what to measure.

## Design tokens (from `docs/DESIGN.md`)

- Emerald `#10B981` (primary), Mint `#6EE7B7` (accent text), Night `#0B0F0D`, Glass panel `rgba(14,18,16,0.78)` with 1px `rgba(255,255,255,0.12)` border, White `#F4F7F5`
- Panels: radius 22–34 dp, pill buttons, touch targets ≥ 52 dp (gaze targets must be generous)
- Font: IBM Plex Sans Arabic (400/500/600/700)
