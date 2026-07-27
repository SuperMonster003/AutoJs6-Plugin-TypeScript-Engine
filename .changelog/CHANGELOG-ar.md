******

### سجل الإصدارات

******

# v0.3.0

###### 2026/07/27

* `تلميح` تظل `compilerAvailable=false` الحالة المعتمدة في v0.3.0: لا ينفذ prewarm في R1 سوى golden probe داخل compiler host المقيد, ويظل تجميع المشاريع غير متاح حتى اكتمال R2
* `ميزة` انشاء WebView/Worker compiler host في عملية `:typescript_compiler` المخصصة مع HTTPS asset origin خاص وresource allowlist دقيقة وWebMessagePort وقدرات network وfile وcontent وstorage وwindow مقيدة
* `ميزة` يقوم prewarm في R1 بتحميل runtime الحقيقي لTypeScript 6.0.3 والتحقق من runtime version وenum emit وstructured syntax diagnostic وSource Map v3 وoutput fingerprints الثابتة وعدم وجود inline source content
* `تحسين` تغطية 100 hot probes لكل جهاز Android 12 وAndroid 13 مع recovery عبر اعادة بناء Worker بعد timeout, والتحقق من minified release بواسطة helper مستقل على Android 13 مع بقاء جميع project compilation capabilities على false
* `تبعية` اضافة AndroidX WebKit الاصدار 1.14.0

# v0.2.0

###### 2026/07/27

* `تلميح` تظل `compilerAvailable=false` الحالة المعتمدة في v0.2.0: لا ينفذ R1 سوى اعداد compiler distribution والتحقق منه ومن IPC/FD transport, ويظل تجميع المشاريع غير متاح حتى اكتمال R2
* `ميزة` انشاء pipeline قابل لاعادة الانتاج لتوزيع TypeScript 6.0.3 الرسمي يتحقق من SHA-1 وSHA-256 وSRI SHA-512, ويستخرج بامان allowlist صارمة لادخالات tar, وينتج `distribution.json` canonical مع fingerprints للcompiler وstandard libraries وgenerator والتوزيع
* `ميزة` تعزيز بوابات محتوى APK لوضعي debug/release باعادة حساب bytes المضمنة ومقارنتها مع `distribution.json`, وفرض وجود نسخة واحدة فقط من `lib/typescript.js` ضمن allowlist, والتحقق من اكتمال declarations وlicense وthird-party notice
* `ميزة` تغطي اختبارات instrumentation اكتشاف خدمة Binder البعيدة الحقيقية `:typescript_compiler` وقدراتها ورفض R0 المتزامن, مع التحقق من بقاء FD التي يملكها المستدعي صالحة بعد الرفض, ومن أن descriptors غير الصالحة أو المشتركة لا تشغل أي callback خلال نافذة مراقبة قصيرة ومحددة
* `تبعية` اضافة AndroidX Test Runner الاصدار 1.7.0
* `تبعية` اضافة AndroidX Test JUnit Extension الاصدار 1.3.0

# v0.1.0

###### 2026/07/27

* `تلميح` تشير `compilerAvailable=false` إلى أن R0 يوفر اكتشاف الملحق وعقد تجميع مؤقت فقط, ولا يمكنه تجميع TypeScript أو تشغيله بعد
* `ميزة` إنشاء مشروع مستقل لملحق واجهة تجميع TypeScript في AutoJs6, مع تثبيت TypeScript 6.0.3 وعدم الاعتماد على ملحق Node.js Runtime
* `ميزة` توفير اكتشاف الملحق والتفاوض على قدرات المترجم عبر خدمتي `org.autojs.plugin.INFO` و`org.autojs.plugin.typescript.COMPILER` المحميتين بإذن `org.autojs.permission.PLUGIN`
* `ميزة` تشغيل خدمة المترجم في عملية `:typescript_compiler` المخصصة, مع Binder envelope مؤقت ينقل بيانات `Bundle` وصفية صغيرة وملفات archive للإدخال/الإخراج عبر `ParcelFileDescriptor`
* `ميزة` إضافة بوابات R0 آلية لعقد API والقبول/الرفض وقفل توزيع المترجم والاستقلال عن Node Runtime وLint ومحتويات universal APK
