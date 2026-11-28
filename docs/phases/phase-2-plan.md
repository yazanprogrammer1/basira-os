# المرحلة 2: نافذة ثابتة بالغرفة (الخطة)

> **الحالة:** مسودة بتنطر موافقتك.

## الهدف والبوابة

صفحة ويب حقيقية (WebView) طايرة قدّامك على بعد متر ونص، وبتضل **بمكانها بالغرفة** لما تلف وتمشي.

**البوابة:**
1. **اختبار المشي:** بتحط النافذة، وبتمشي مترين لجنب وبترجع، وبتلف 360°. النافذة بترجع لنفس المكان بالحيط، وما بتنزاح أكتر من حوالي 5cm (بنقيسها بعلامة لاصقة بالحيط ورا النافذة).
2. **الحركة:** النافذة ما بتهزّ ولا بتتأخر لما تلف راسك، يعني نفس ثبات الـ passthrough.
3. **الأداء:** 60fps، وضايع أقل من 1% **بتوقيت عرض حقيقي**، و10 دقايق بدون ما توصل الحرارة لـ SEVERE.
4. **القراءة:** نص الصفحة مقروء بحجم 16px على بعد متر ونص.
5. **بدون ARCore:** وضع 3DoF شغّال، والنافذة بتلحق الراس.

## التصميم

### 1. وضعية الراس: ARCore للمكان، والحساس للدوران
- ARCore بيعطي الوضعية الكاملة (مكان ودوران) بس بـ 30fps، ومتأخرة بقد عمر الصورة (حوالي 85ms).
- **الحل:** منحسب **الدوران** لحظة العرض بنفس طريقة الـ reprojection اللي عملناها: وضعية ARCore وقت اللقط، فوقها الدوران من الحساس من اللقط لهلّق. هيك الكاميرا والنوافذ بيتصححوا بنفس الدلتا، فما بيتزحلقوا عن بعض.
- **المكان** من ARCore مثل ما هو، لأن الراس بيتحرك ببطء نسبياً.

### 2. النافذة: WebView ← VirtualDisplay ← SurfaceTexture ← GL
- `VirtualDisplay` خاص فينا، حجمه 1280×800، وبيرسم على `Surface(SurfaceTexture)`. فوقه `Presentation` محتواه `WebView`.
- الـ `SurfaceTexture` مربوط بـ texture من نوع OES على الـ GL thread. ومنعمل `updateTexImage()` **بس لما يوصل `onFrameAvailable`**، فالصفحة الثابتة ما بتكلّف شي.
- **الحجم بالغرفة:** 1.2m × 0.75m، وهاد مستقل عن دقة الـ pixels. ومستوى الجودة بيغيّر دقة الـ VirtualDisplay (1.0 / 0.8 / 0.6).
- **`:core:render` ما بيعرف WebView** (متل ما بيطلب CLAUDE.md). بياخد interface اسمه `PanelTextureSource` (texture id، وحجم بالمتر، ووضعية بالعالم). والـ WebView بيضل بـ `:core:windows`.

### 3. التثبيت بالغرفة
- أول ما يصير ARCore عم يتتبّع، منعمل `Anchor` على بعد 1.5m قدّام النظر، وعلى ارتفاع العين.
- **التتبّع كان شغّال 75% بس بالمرحلة 1.** لما يضيع، منثبّت آخر وضعية معروفة، ومنكمّل الدوران من الحساس (3DoF مؤقت). ولما يرجع التتبّع، منرجّع النافذة لمكانها **بحركة ناعمة على 300ms** بدل قفزة.
- وضع 3DoF الكامل (للأجهزة بدون ARCore) بيستعمل نفس `OrientationTracker`، والنافذة بتكون مربوطة بالراس.

### 4. الرسم والدمج
1. الـ passthrough المباشر على الشاشة (نفس المرحلة 1).
2. النوافذ بالـ eye buffer مع alpha شفاف، ولكل عين view matrix (إزاحة ±IPD/2) وprojection من مجال رؤيتها. **النوافذ stereo حقيقي**، يعني إلها عمق، بعكس الكاميرا.
3. الـ eye buffer بيندمج فوق الكاميرا عبر الـ distortion mesh مع alpha blending.
- **حواف النافذة:** زوايا مدوّرة 26dp وإطار زجاجي، بالـ shader مباشرة (SDF)، بدون textures زيادة.
- **إضاءة الغرفة:** `LightEstimationMode.AMBIENT_INTENSITY` بيعطي معامل سطوع للنافذة، حتى ما تبيّن «ملصوقة» بغرفة معتمة. بنقيس كلفته عالحرارة، وإذا غلي بيطفى بـ WARM.

### 5. قياس توقيت العرض الحقيقي (إصلاح قيد المرحلة 1)
- منجرّب `EGL_ANDROID_get_frame_timestamps` حتى ناخد وقت العرض الفعلي لكل فريم. وإذا الجهاز ما بيدعمه، منضل على توقيت الـ CPU ومنكتب هالشي بالـ overlay.

## الملفات

| Module | الملفات |
| --- | --- |
| `:core:common` | `PanelTextureSource`، `WorldPose`، `Mat4` (view/projection صافيين مع tests) |
| `:core:tracking` | `HeadPoseProvider` (ARCore مع دوران الحساس + fallback لـ 3DoF)، `AnchorManager`، `TrackingRecovery` (منطق الحركة الناعمة، مع tests) |
| `:core:windows` | `WebPanel` (VirtualDisplay + Presentation + WebView)، `SpatialWindowManager` حقيقي (نافذة وحدة) |
| `:core:render` | `WindowPass` (quad بالعالم مع SDF للزوايا)، دمج الـ eye buffer بـ alpha، `PresentationTimer` |
| `:app` | زر «افتح نافذة»، وزر الصندوق بيعيد وضع النافذة قدّامك، وأرقام النافذة بالـ overlay |

## الـ commits المتوقعة
1. `feat(common): world pose, view and projection math with tests`
2. `feat(tracking): head pose from ARCore with sensor-predicted rotation and 3DoF fallback`
3. `feat(windows): WebView panel on a private VirtualDisplay`
4. `feat(render): world-locked window pass composited over the passthrough`
5. `feat(tracking): anchor placement and smooth recovery after tracking loss`
6. `feat(render): ambient light on windows and presentation-time frame stats`
7. `feat(app): open and recenter a window from the passthrough`
8. `docs(phase-2): results`، وبعدها tag `v0.2`

## المخاطر

| الخطر | الحل |
| --- | --- |
| `Presentation` على VirtualDisplay خاص ممكن يكون مقيّد بأندرويد 16 أو على OPPO | **أول شغلة منعملها spike صغير لهالنقطة.** إذا فشل: `WebView` بـ `Surface` عبر `lockHardwareCanvas` |
| WebView بيسخّن (JS وفيديو) | `updateTexImage` بس عند فريم جديد، والدقة حسب مستوى الجودة، والنوافذ المجمّدة بتصير صورة بـ HOT |
| التتبّع بيضيع 25% من الوقت | 3DoF مؤقت مع رجعة ناعمة، والـ overlay بيعرض «التتبّع ضايع» |
| النص مش مقروء من بعد متر ونص | منبلّش بـ 1280 px على 1.2m (حوالي 0.94 mm/px). وإذا ما زبط، منكبّر الـ zoom الافتراضي بالـ WebView |
| النافذة stereo والكاميرا mono، فممكن يصير تضارب بالعمق | النافذة على 1.5m، والكاميرا «على اللانهاية». منقيس الراحة، ومنضيف تحكم بمسافة النافذة إذا لزم |

## اللي بحتاجه منك
- **موافقة على الخطة.**
- **حيط فاضي** بالغرفة حتى تلزق عليه علامة لاختبار المشي (ورقة ملوّنة صغيرة بتكفي).
