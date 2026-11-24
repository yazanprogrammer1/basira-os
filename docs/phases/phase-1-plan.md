# المرحلة 1: Passthrough لعينين (الخطة)

> **الحالة:** مسودة بتنطر موافقتك. ما رح نبلّش تنفيذ قبل ما تنجح بوابة المرحلة 0 على جهازك.

## الهدف والبوابة

تلبس النظارة وبتشوف غرفتك من الكاميرا، بحجمها الحقيقي، لعينين، ومن غير ما تحس إنها مطعوجة.

**البوابة:**
1. 10 دقايق متواصلة بالنظارة بدون دوخة ولا وجع عيون
2. 60fps بثبات: متوسط الفريم أقل من 16.7ms، والـ p95 أقل من 20ms، والفريمات اللي بتتأخر أقل من 1% (منقيسها بالـ overlay وبملف CSV)
3. الحرارة ما بتوصل SEVERE خلال الـ 10 دقايق

**برا هالمرحلة:** النوافذ، والـ reprojection (المرحلة 2)، والإيدين، والصوت.

## القرارات الأساسية

| القرار | الاختيار | ليش |
| --- | --- | --- |
| حجم صورة الكاميرا | **بحجمها الزاوي الحقيقي**، مش ممطوطة على الشاشة كلها | مجال رؤية الكاميرا (حوالي 60–75°) أصغر من مجال العدسات (حوالي 90–100°). إذا مطّيناها، كل شي بيكبر وبيتحرك أسرع من حركة راسك، وهاد أسرع طريق للدوخة. بيطلع حوالين الصورة إطار غامق، وهاد مقبول |
| Mono أو stereo | نفس صورة الكاميرا للعينين | الكاميرا وحدة. بنعطي إزاحة أفقية صغيرة قابلة للمعايرة بس (افتراضياً صفر) |
| وضع ARCore | `UpdateMode.LATEST_CAMERA_IMAGE` | `session.update()` ما بيوقف الرسم ناطر الكاميرا. الكاميرا غالباً 30fps والرسم 60 |
| إعدادات الكاميرا | منفضّل `TargetFps.TARGET_FPS_60` إذا موجودة، وبعدين 30، وأقل دقة CPU image | 60 بالكاميرا بتقلّل التأخير. الدقة المنخفضة بتوفّر للإيدين بالمرحلة 3 |
| التشويه | **mesh** (شبكة 40×40 محسوبة مسبقاً) مش حساب لكل pixel | أرخص على الـ GPU وبالتالي أبرد. الـ shader اللي بيحسب لكل pixel بيضل كمرجع debug |
| عدد الـ passes | pass لكل عين على FBO، وبعدين pass للتشويه | هيك المرحلة 2 بتركّب النوافذ على نفس الـ FBO بدون ما نعيد التصميم. إذا طلعنا محدودين بالـ GPU، عنا مسار سريع: pass وحدة بتقرأ الكاميرا بإحداثيات مشوّهة مباشرة |
| معدّل تحديث الشاشة | نثبّت الشاشة على **60Hz** (`Surface.setFrameRate` و`preferredDisplayModeId`) | على موبايل 120Hz بيوفّر طاقة وحرارة، وبيخلّي الـ pacing ثابت |
| الأداء المستدام | `Window.setSustainedPerformanceMode(true)` إذا مدعوم | منقيس الفرق. شاشة التشخيص بتقلّك إذا جهازك بيدعمه |

## تصميم الـ GL thread

```
Main thread                         GL thread (GLSurfaceView)
───────────                         ─────────────────────────
onResume: session.resume()          onSurfaceCreated:
          glView.onResume()           compile shaders, create OES texture
                                      frameSource.attachCameraTexture(texId)
                                      build distortion meshes
                                    onSurfaceChanged(w, h):
                                      eyeLayout = EyeLayout.compute(w, h, dpi, viewerProfile)
                                      (re)create 2 eye FBOs at size * renderScale
                                    onDrawFrame:
                                      1. frame = frameSource.update()   // session.update(), non-blocking
                                      2. for eye in [L, R]:
                                           bind eyeFbo[eye]; clear(night)
                                           cameraPass.draw(frame, eyeProjection[eye])
                                      3. bind default FB; for eye: viewport(half)
                                           distortionPass.draw(eyeFbo[eye].texture, mesh[eye])
                                      4. stats.onFrame(nanoTime)
onPause:  glView.onPause()
          session.pause()
```

- **فصل الـ modules:** `:core:render` ما بيعرف ARCore. منحط interface اسمه `CameraFrameSource` بـ `:core:common`، وفيه `attachCameraTexture(texId: Int)` و`update(): CameraFrame?`. الـ `CameraFrame` فيه `timestampNs`، والـ intrinsics (fx وfy وcx وcy والعرض والطول)، والـ `HeadPose`. `:core:tracking` بينفّذه بـ ARCore.
- **أشياء الـ GLSurfaceView:** `setEGLContextClientVersion(3)`، `setPreserveEGLContextOnPause(true)`، `RENDERMODE_CONTINUOUSLY`، و`setEGLConfigChooser(8,8,8,8,0,0)` لأنه ما في depth بالـ default FB.
- **الأحمال التقيلة برا الـ GL thread:** القراءة من DataStore والحرارة على coroutines. الـ GL thread بيقرأ آخر قيمة من `AtomicReference` بس.
- **ممنوع allocations بـ onDrawFrame:** المصفوفات والـ buffers محضّرة مسبقاً.
- **QualityLevel:** `renderScale` بيصير 1.0 أو 0.85 أو 0.7. الـ FBOs بتنعاد بناؤها بس لما المستوى يتغير. بـ HOT منستهدف 45fps إذا الشاشة 90Hz، أو 30 إذا 60Hz (بنتخطى vsync بـ `Choreographer`).

## الـ Shaders

بتنحط بـ `core/render/src/main/assets/shaders/` وبتنقرا وقت التشغيل.

**1. `camera.vert` / `camera_oes.frag`:** مستطيل الكاميرا بحجمها الزاوي الصحيح.
```glsl
// camera_oes.frag
#version 300 es
#extension GL_OES_EGL_image_external_essl3 : require
precision mediump float;
uniform samplerExternalOES uCamera;
in vec2 vUv;
out vec4 fragColor;
void main() { fragColor = vec4(texture(uCamera, vUv).rgb, 1.0); }
```
المستطيل بينحط على مسافة ثابتة قدّام العين (مثلاً 1m)، وعرضه `2 * tan(hfovCam/2)` وطوله `2 * tan(vfovCam/2)`، والقيم من `camera.textureIntrinsics`. الـ UV تبع الدوران بييجي من `frame.transformCoordinates2d`. الـ projection لكل عين من الـ FOV المعاير تبع العدسة.

**2. `distortion.vert` / `distortion.frag`:** pre-warp للعدسة على mesh.
- منحسب الـ mesh بالـ CPU (Kotlin صافي، وعليه tests). كل vertex بالشاشة بيوخد UV مصدر:
  `r_src = r * (1 + k1·r² + k2·r⁴)`، و`r` مقاسة من مركز العدسة بوحدة tan-angle.
- الـ fragment shader بس `texture(uEye, vUv)` مع قص أسود إذا الـ UV طلعت برا [0,1].
- مرجع debug: `distortion_reference.frag` بيعمل نفس المعادلة لكل pixel، حتى نقارن الـ mesh بالنتيجة الدقيقة.

**3. `test_pattern.frag`:** شبكة خطوط وصليب بنص كل عين، وبيستعمل بالمعايرة. إذا التشويه مظبوط، الخطوط بتبيّن مستقيمة جوّا العدسة، والصليبين بيندمجوا بصليب واحد.

**بعدين (مش هلّق):** تصحيح الألوان المتفرقة بالعدسة (chromatic aberration) بثلاث UV، واحدة لكل لون.

## المعايرة

**القيم (`ViewerProfile`، بتنحفظ بـ DataStore):**
`interLensDistanceMm`، `screenToLensMm`، `trayToLensCenterMm`، `k1`، `k2`، `fovDeg` (يسار/يمين/فوق/تحت)، `passthroughShiftPx`، `orientation` (landscape أو reverse، حسب وين صارت الكاميرا بالصندوق).

**القيم المبدئية:** profile تبع Cardboard v2 (inter-lens 64mm، screen-to-lens 39mm، tray-to-lens 35mm، k1=0.34، k2=0.55، FOV 60°). هاي **نقطة بداية بس**، صندوقك الحقيقي بيحدد.

**تحويل mm لـ pixels:** من `DisplayMetrics.xdpi/ydpi` (`EyeLayout`، Kotlin صافي ومع tests). مركز كل عدسة على الشاشة = نص العرض ± inter-lens/2، والارتفاع من `trayToLensCenterMm`.

**شاشة المعايرة (طريقتين):**
1. **برا النظارة (لمس):** شاشة Compose فيها sliders لكل قيمة، ومعاينة حيّة للـ test pattern.
2. **جوّا النظارة (بدون لمس):** أزرار الصوت +/- بتعدّل القيمة المختارة، وزر الصندوق (أو لمسة الشاشة) بينقل للقيمة اللي بعدها، واسم القيمة ظاهر بنص العين. بالترتيب: IPD لحتى يندمج الصليب، بعدين k1 لحتى تستقيم الخطوط، بعدين k2 للأطراف.

**مسح QR تبع Cardboard (اختياري بهالمرحلة):** بعض الصناديق عليها QR بيوصّل لـ `google.com/cardboard/cfg?p=<base64 protobuf>`، وهاد فيه كل القيم. **انتبه:** أغلب أكواد QR القديمة روابط `goo.gl`، وGoogle وقّفت خدمة goo.gl (2025)، فممكن ما تشتغل. منفكّ الرابط المباشر إذا لقيناه، وإلا بنرجع للمعايرة اليدوية. منقرأ الـ QR من الـ CPU image تبع ARCore (ML Kit barcode).

## قياس الـ fps والحرارة

**جوّا التطبيق:**
- `FrameStatsTracker` (Kotlin صافي، مع tests): نافذة متحركة لآخر 120 فريم، فيها المتوسط والـ p95 وعدد الفريمات اللي أكتر من 20ms. وكمان عمر صورة الكاميرا (`nanoTime - frame.timestamp`) كقياس تقريبي للتأخير.
- **Debug overlay** بوضع المطوّر: سطر صغير فوق كل عين فيه fps وp95 وحالة الحرارة والـ headroom وحرارة البطارية، وبيتحدث مرتين بالثانية.
- **CSV** بـ `Android/data/com.basira.os/files/perf/session-<time>.csv` كل ثانية: `t, fps_avg, p95_ms, janky, cam_age_ms, thermal_status, headroom, battery_c, quality`.
- **ThermalGovernor الحقيقي:** بيدمج `ThermalStatusSource` مع `ThermalPolicy`، وفيه hysteresis: بيطلع للمستوى الأسخن فوراً، وبينزل بس بعد 30 ثانية ثبات. وعليه tests بـ test dispatcher.

**على جهازك (منفصل عن التطبيق):**
```bash
adb logcat -s Basira/Perf                      # stats line every 5 s
adb shell dumpsys thermalservice               # skin / CPU / GPU temperatures
adb shell dumpsys battery | grep temperature   # tenths of a degree C
adb pull /sdcard/Android/data/com.basira.os/files/perf/ .
```

**بروتوكول تجربة الـ 10 دقايق** (منفس الطريقة كل مرة حتى نقدر نقارن):
1. البطارية فوق 80%، **مش على الشاحن**، والموبايل بارد (5 دقايق بدون استعمال).
2. السطوع ثابت على 70%، والـ Wi-Fi شغّال، وكل التطبيقات التانية مسكّرة.
3. سجّل حرارة الغرفة تقريباً.
4. شغّل وضع passthrough، والبس النظارة، وتطلّع حواليك عادي (لف راسك، وامشي شوي).
5. بعد 10 دقايق: اسحب ملف CSV، وسجّل `dumpsys thermalservice` قبل وبعد.
6. قيّم الراحة من 1 لـ 5 (دوخة؟ وجع عيون؟ الأشياء بحجمها؟).

## الملفات

**`:core:common`**
- `camera/CameraFrameSource.kt`, `camera/CameraFrame.kt`, `viewer/ViewerProfile.kt`

**`:core:render`**
- `StereoRenderer.kt` (GLSurfaceView.Renderer)
- `gl/ShaderProgram.kt`, `gl/GlUtil.kt`, `gl/EyeFramebuffer.kt`, `gl/OesTexture.kt`
- `pass/CameraPass.kt`, `pass/DistortionPass.kt`, `pass/TestPatternPass.kt`
- `distortion/DistortionModel.kt`, `distortion/DistortionMesh.kt`, `layout/EyeLayout.kt` (كلهم pure)
- `stats/FrameStatsTracker.kt`, `stats/PerfCsvLogger.kt`, `pacing/FramePacer.kt`
- `calibration/CalibrationStore.kt` (DataStore)
- `assets/shaders/*.vert|frag`
- Tests: `DistortionModelTest`, `DistortionMeshTest`, `EyeLayoutTest`, `FrameStatsTrackerTest`

**`:core:tracking`**
- `arcore/ArCoreSessionManager.kt` (إنشاء، إعدادات الكاميرا، resume/pause، الأخطاء)
- `arcore/ArCoreCameraFrameSource.kt` (بينفّذ `CameraFrameSource`)

**`:core:thermal`**
- `RealThermalGovernor.kt` بدال الـ stub، و`BatteryTemperature.kt`، ومعهم `ThermalGovernorTest`

**`:app`**
- `passthrough/PassthroughScreen.kt` (GLSurfaceView داخل `AndroidView` مع الـ overlay)
- `calibration/CalibrationScreen.kt`، `calibration/InViewerCalibrationController.kt` (أزرار الصوت)
- `navigation`: تشخيص ← passthrough ← معايرة

**تعديلات:** `libs.versions.toml` (datastore، وML Kit barcode إذا اشتغلنا على QR)، و`ARCHITECTURE.md`، و`README.md`.

## الـ commits المتوقعة

1. `feat(common): camera frame source and viewer profile contracts`
2. `feat(render): pure distortion model, mesh and eye layout with tests`
3. `feat(render): GL scaffolding, shader loading and eye framebuffers`
4. `feat(tracking): ARCore session manager and camera frame source`
5. `feat(render): stereo renderer with camera pass and mesh distortion`
6. `feat(thermal): real governor with hysteresis`
7. `feat(render): frame stats, perf CSV logger and 60 Hz pinning`
8. `feat(app): passthrough screen with developer overlay`
9. `feat(app): calibration screen with in-viewer volume-key tuning`
10. `docs(phase-1): results, measurements and decisions`, وبعدها tag `v0.1`

## المخاطر

| الخطر | شو منعمل |
| --- | --- |
| الكاميرا 30fps بس، فالخلفية بتتحدث نص سرعة الرسم | مقبول لأن الرسم نفسه 60. منقيس عمر الصورة. بالمرحلة 2 الـ reprojection بيخفف الإحساس |
| تأخير الصورة بيدوّخ لما تلف راسك بسرعة | منقيسه. إذا طلع فوق 80ms، منجرّب إعدادات كاميرا أخف، ومنقدّم reprojection بسيط للخلفية |
| الكاميرا مش بين العيون (قدّام وعلى جنب) | بهالمرحلة منتجاهلها ومنشوف الإحساس. إذا أثّر، منضيف إزاحة ثابتة بالمعايرة |
| ما في 60fps بسبب الـ FBO والـ mesh | المسار السريع: pass وحدة، و`renderScale` أقل |
| الموبايل بيسخن لأن ARCore لحاله تقيل | منقارن بأرقامك من hello_ar بالمرحلة 0. مستويات الجودة شغّالة من هالمرحلة |
| QR تبع الصندوق رابط goo.gl ميت | المعايرة اليدوية هي الأساس، والـ QR إضافة |
| الكاميرا بتنغطى بالصندوق باتجاه معيّن | خيار landscape أو reverse بالمعايرة |

## اللي بحتاجه منك قبل ما نبلّش

1. **نتيجة بوابة المرحلة 0:** التقرير من زر «انسخ التقرير»، وأرقام hello_ar_kotlin (fps، والحرارة بعد 10 دقايق من `dumpsys thermalservice`)، وfps تبع عينة MediaPipe.
2. **قياسات الصندوق:**
   - المسافة بين مركزي العدستين بالمسطرة (mm)
   - المسافة من الشاشة للعدسة تقريباً (mm)
   - ارتفاع مركز العدسة عن الحافة السفلية للموبايل لما يكون بالصندوق (mm)
   - صورة للـ QR إذا موجود
   - هل في زر بالصندوق، ونوعه (مغناطيس ولا بيلمس الشاشة)
3. **وين صارت الكاميرا** لما تحط الموبايل بالصندوق: على اليمين ولا على اليسار، ومكشوفة كلها ولا لأ.
