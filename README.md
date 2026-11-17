# Media Player

مشغل فيديو وصوت يعمل بدون إنترنت — Kotlin + Jetpack Compose + Media3 (ExoPlayer)، بنية MVVM + Clean Architecture.

## البناء عبر GitHub
1. أنشئ مستودعًا جديدًا على GitHub وارفع محتوى هذا المجلد إلى فرع `main`.
2. من تبويب **Actions** سيعمل workflow باسم *Build APK & Release* تلقائيًا (أو شغّله يدويًا).
3. بعد نجاحه تجد الملف في **Releases** باسم `MediaPlayer.apk`، والرابط المباشر الثابت:
   `https://github.com/<user>/<repo>/releases/latest/download/MediaPlayer.apk`
   (يُحقن اسم المستودع تلقائيًا في التطبيق ليُستخدم في QR Code المشاركة).

## البنية
- `domain/` النماذج وواجهات المستودعات وحالات الاستخدام (بدون اعتماد على Android).
- `data/` MediaStore + SharedPreferences.
- `player/` ExoPlayer، LoudnessEnhancer (رفع حتى 300%)، Equalizer، BassBoost، خدمة التشغيل بالخلفية.
- `ui/` شاشات Compose.

## ملاحظات
- التوقيع الافتراضي لنسخة release بمفتاح debug ليكون الـ APK قابلًا للتثبيت. للنشر الرسمي أنشئ keystore خاصًا بك.
- شعار المشروع: `branding/logo.svg`، وأيقونة التطبيق في `res/drawable` و`res/mipmap-anydpi*`.
