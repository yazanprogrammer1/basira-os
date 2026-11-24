# المرحلة 0: تجهيز وتجارب

**Tag:** `v0.0` · **الحالة:** الجزء البرمجي خلص، وبوابة الجهاز لسا ما انفحصت

## شو انبنى

- مشروع Gradle multi-module فيه 11 module (`:app` و9 `:core:*` و`:ui:system`)، مع version catalog وconvention plugins بـ `build-logic/`
- Hilt شغّال. كل `:core:*` بيعرض interfaces، وفيه implementation فاضي (`Stub*`) مربوط بـ `@Binds`. التنفيذات الحقيقية لهلّق: `ArCoreApkSupport` و`PowerManagerThermalStatusSource` و`DefaultDispatcherProvider` و`AndroidLogger`
- `QualityLevel` بيمثّل جدول ميزانية الحرارة من ARCHITECTURE.md بالكود، و`ThermalPolicy` بيحوّل القراءة لمستوى (مع tests)
- شاشة تشخيص بالـ Compose (عربي، RTL، ألوان الهوية، خط IBM Plex Sans Arabic) فيها:
  - ARCore: مدعوم؟ مثبّت؟ وزر تثبيت لما يلزم
  - الجهاز: الموديل، والمعالج، والشاشة ومعدّل تحديثها، وOpenGL ES، ووضع الأداء المستدام
  - الحرارة: حالة النظام، والـ headroom لعشر ثواني (API 30+)، ومستوى الجودة اللي رح ينختار
  - صلاحية الكاميرا مع زر
  - الوحدات: كل binding بـ Hilt، فاضي ولا حقيقي
  - زر «انسخ التقرير»: تقرير نصّي بيتلصق بالمحادثة أو بـ GitHub issue
- Manifest: صلاحيات الكاميرا والميكروفون والنت، وARCore `optional`، وlandscape بملء الشاشة، والشاشة ما بتطفي
- المفاتيح من `local.properties` لـ `BuildConfig` (`LLM_API_KEY` و`STT_API_KEY`)
- GitHub Actions: `test` و`assembleDebug`، والـ APK بيترفع كـ artifact

## القرارات

| القرار | ليش |
| --- | --- |
| نسخ موجودة بالـ cache بدل الأحدث (AGP 9.2.1، Kotlin 2.4.20، Gradle 9.7.1) | النت بطيء (حوالي 35KB/s)، والفرق مع الأحدث صغير. منحدّث لما يكون مريح (الأحدث مذكور براس `libs.versions.toml`) |
| AGP 9 مع built-in Kotlin | ما بقى يحتاج plugin `kotlin-android` |
| MediaPipe مش مضاف لهلّق | ما بينستعمل قبل المرحلة 3، وتحميله تقيل. منضيف 1.2.3 (أو الأحدث) وقتها |
| compileSdk وtargetSdk = 37، وminSdk = 29 | آخر API مستقر، و29 حسب CLAUDE.md |
| Convention plugins بـ `build-logic/` | 11 module بنفس الإعدادات بدون تكرار |
| الـ namespace بيطلع من مسار الـ module | `:core:render` بيصير `com.basira.os.core.render` |
| `SpatialWindowManager` بدل `WindowManager` | حتى ما يتلخبط مع `android.view.WindowManager` |
| الألواح الزجاجية بدون blur | blur لكل لوح ولكل عين غالي على الحرارة |
| الواجهة دايماً RTL | العربي أولاً، بغض النظر عن لغة الجهاز |
| الخط مضمّن بالـ APK | بيشتغل بدون نت، ورخصته OFL |

## الإصدارات

| | مستعمل | أحدث مستقر (2026-11-24) |
| --- | --- | --- |
| AGP | 9.2.1 | 9.5.2 |
| Gradle | 9.7.1 | 9.9.2 |
| Kotlin | 2.4.20 | 2.4.21 |
| KSP | 2.3.12 | 2.3.14 |
| Hilt | 2.60.1 | 2.60.1 |
| Compose BOM | 2026.11.01 | 2026.11.01 |
| ARCore | 1.57.0 | 1.57.0 |
| MediaPipe tasks-vision | (المرحلة 3) | 1.2.3 |

## بوابة الجهاز (بتتعبّى بعد ما تجرّب)

| الفحص | النتيجة |
| --- | --- |
| موديل الجهاز | OPPO CPH2699، Android 16 (API 36)، Snapdragon SM6450، شاشة 2400×1080 (60Hz حالياً، max 120)، GLES 3.2، بدون sustained performance mode |
| ARCore مدعوم ومثبّت | ✅ `SUPPORTED_INSTALLED` |
| الحرارة وقت الراحة | `NONE`، headroom 0.22 |
| صلاحية الكاميرا وHilt | ✅، و13 binding (3 حقيقيين و10 stubs) |
| عتبات خنق الجلد (من `dumpsys thermalservice`) | LIGHT 50°، MODERATE 52°، SEVERE 55°، CRITICAL 60° |
| الحرارة وقت الراحة | جلد 30.1°، بطارية 26°، CPU حوالي 32°، GPU 30.5° (على USB من اللابتوب) |
| MediaPipe HandLandmarker (GPU delegate، كاميرا أمامية، عبر تطبيق Kaff) | **11–12 fps** ثابت مع إيد ظاهرة |
| الحرارة بعد حوالي 1.5 دقيقة MediaPipe | جلد 33.8°، GPU 44°، CPU 44–47° |
| تجربة ARCore لعشر دقايق | **انتقلت للمرحلة 1:** عينات Google تقيلة على النت، وScene Viewer ممنوع من adb على OPPO. أول بناء passthrough مع الـ CSV logger هو نفسه هالتجربة |

### استنتاجات للمراحل الجاية

- **12fps لـ MediaPipe لحاله أقل من الـ 20 تبع NORMAL بالخطة.** بالمرحلة 3 منقارن CPU مع GPU، ومنصغّر الصورة اللي بتدخل، ومنعدّل تأكيد القرصة (3 فريمات بـ 12fps يعني حوالي 250ms تأخير)، ومنحدّث جدول الميزانية حسب الأرقام الحقيقية.
- **OPPO عندها `Thermal.FpsControl`** (بيبيّن بالـ logcat)، والنظام بيحدّد fps لكل تطبيق حسب الحرارة. بالمرحلة 1 منراقبه بالـ logcat جنب الـ thermal status العادي.
- **الجهاز متوسط (SM6450، بدون sustained performance).** الـ `renderScale` الافتراضي للعينين 0.85 بدل 1.0.
| الصندوق: المسافة بين العدستين / من الشاشة للعدسة (mm) | |
| الكاميرا مكشوفة بالصندوق؟ | |
