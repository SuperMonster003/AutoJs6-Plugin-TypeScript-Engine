******

### سجل الإصدارات

******

# v0.1.0

###### 2026/07/27

* `تلميح` تشير `compilerAvailable=false` إلى أن R0 يوفر اكتشاف الملحق وعقد تجميع مؤقت فقط, ولا يمكنه تجميع TypeScript أو تشغيله بعد
* `ميزة` إنشاء مشروع مستقل لملحق واجهة تجميع TypeScript في AutoJs6, مع تثبيت TypeScript 6.0.3 وعدم الاعتماد على ملحق Node.js Runtime
* `ميزة` توفير اكتشاف الملحق والتفاوض على قدرات المترجم عبر خدمتي `org.autojs.plugin.INFO` و`org.autojs.plugin.typescript.COMPILER` المحميتين بإذن `org.autojs.permission.PLUGIN`
* `ميزة` تشغيل خدمة المترجم في عملية `:typescript_compiler` المخصصة, مع Binder envelope مؤقت ينقل بيانات `Bundle` وصفية صغيرة وملفات archive للإدخال/الإخراج عبر `ParcelFileDescriptor`
* `ميزة` إضافة بوابات R0 آلية لعقد API والقبول/الرفض وقفل توزيع المترجم والاستقلال عن Node Runtime وLint ومحتويات universal APK
