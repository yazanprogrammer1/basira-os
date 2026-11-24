# Basira OS — Architecture

## The one-sentence version

Every layer feeds `:core:render`, which draws both eyes 60 times a second: the camera image as the background, then each window as a textured quad placed by its world pose, then lens distortion per eye.

## Data flow

```
                 ┌──────────────┐        ┌──────────────────────────────┐
   Microphone ──▶│ :core:voice  │──text─▶│ :core:agent (LLM + tools)    │
                 └──────────────┘        └──────────────┬───────────────┘
                                                        │ commands
   Back camera ─┬──────────────▶ :core:tracking ──pose──┼──────────────┐
   (ARCore)     │                (ARCore / 3DoF)        ▼              │
                └─low-res frame─▶ :core:hands ──events─▶ :core:windows ◀──▶ :core:spaces (Room)
                                                        │ textures + poses
                                                        ▼
                                  :core:thermal ──QualityLevel──▶ :core:render ──▶ screen (L | R)
```

## Key technical decisions

### 1. Rendering pipeline (`:core:render`)
Per frame, on the GL thread:
1. `session.update()` (ARCore) -> camera texture (OES) + camera pose.
2. Render the scene **once per eye** into an offscreen FBO (eye offset = IPD / 2 along the head's x axis):
   - full-screen camera quad (passthrough background)
   - each window quad: model matrix from its anchor pose, texture from its `SurfaceTexture`
   - cursor reticle at the gaze hit point
3. Draw each eye FBO onto its half of the screen with the **barrel distortion shader**:
   `r' = r * (1 + k1*r² + k2*r⁴)`, with chromatic aberration correction optional later.
4. Late reprojection (phase 8): rotate the final image by the delta between the pose used to render and the newest rotation-vector reading.

Passthrough note: the camera image is mono. Both eyes get the same camera image (a small per-eye shift is acceptable); only the windows have true stereo depth. This is how Orion-style systems work and it is comfortable.

Lens calibration values (IPD in mm, k1, k2, screen-to-lens distance, FOV) come from a calibration screen and are stored in DataStore.

### 2. Windows (`:core:windows`)
- A `Panel` = `VirtualDisplay` (our own, no permission needed) + `Presentation` whose content view is a `WebView` or `ComposeView`, rendering into a `Surface(SurfaceTexture)` the renderer samples.
- `SurfaceTexture.updateTexImage()` only when `onFrameAvailable` fired (saves GPU and heat).
- Panel size in pixels (e.g. 1280×800) is independent of its size in metres (e.g. 1.2 m × 0.75 m).
- **Input**: gaze ray (from head pose, through screen centre) intersects each window plane -> nearest hit -> local (u, v) -> pixel (x, y) -> `MotionEvent` dispatched to the Presentation's root view. Pinch down = `ACTION_DOWN`, hold + move = `ACTION_MOVE` (scroll/drag), release = `ACTION_UP`.
- Window chrome (grab bar, resize corner, close) is drawn as separate small quads owned by the WindowManager, not inside the web content.

### 3. Hands (`:core:hands`)
- Same camera frame as ARCore (CPU image via `frame.acquireCameraImage()`), downscaled, sent to `HandLandmarker` in LIVE_STREAM mode at 12–20 fps.
- Pinch distance = |thumb tip (4) − index tip (8)| normalised by palm size (0 -> 9).
- `PinchStateMachine`: OPEN -> NEAR -> PINCHED -> DRAGGING with two thresholds (hysteresis) and a 3-frame confirmation. One Euro filter on landmarks.
- v1: **gaze picks the target, the pinch confirms**. Direct hand rays are v2.
- Open palm held 0.5 s = Home.

### 4. Voice + agent (`:core:voice`, `:core:agent`)
- Interfaces: `WakeWordDetector`, `SpeechToText`, `TextToSpeech`, `LlmClient`. Implementations are swappable (cloud now, on-device later).
- Start with a push-to-talk button in the Dock; add the wake word «يا بصيرة» after the pipeline works.
- Offline fast path: simple commands (افتح X، سكّر، رجوع، الرئيسية) matched locally before calling the LLM.
- LLM tools (function calling):
  `open_app(name, placement?)`, `close_window(target)`, `close_all()`, `place_window(target, where)`,
  `search(query)`, `summarize_page(target)`, `ask_page(target, question)`, `fill_form(target, fields)`,
  `click_text(target, text)`, `recall_space(name)`, `save_space(name)`
- Page reading: `evaluateJavascript` extracts readable text (Readability-style) from the WebView.
- Any action that submits, sends or pays asks for confirmation.

### 5. Spaces (`:core:spaces`)
- Room entities: `Space(id, name, createdAt)`, `WindowState(id, spaceId, url, appId, poseRelativeToOrigin, widthM, heightM, zOrder)`.
- v1 persistence: the user sets a room origin with a look + pinch; windows are saved relative to it. v2: try ARCore Cloud Anchors.

### 6. Thermal (`:core:thermal`)
`PowerManager.addThermalStatusListener` + `getThermalHeadroom(10)` (API 30+) -> `StateFlow<QualityLevel>`:

| Component | NORMAL | WARM | HOT |
| --- | --- | --- | --- |
| Render | 60 fps | 60 fps | 45 fps |
| Hand tracking | 20 fps, paused when no hand | 12 fps | off (gaze + voice / viewer button) |
| Window texture scale | 1.0 | 0.8 | 0.6 |
| Live windows | 4 | 3 | 2 (others frozen as images) |
| Wake word | on | on | off (push-to-talk stays) |

The status pill shows temperature state and «وضع التبريد» when not NORMAL.
