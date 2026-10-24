# Video Avto — ovoz + rasmlardan avtomatik video

Android ilova (Kotlin + Jetpack Compose + Media3 Transformer).

## Ilova nima qiladi

1. Siz tayyor ovoz faylini (voice-over) tanlaysiz.
2. Rasmlarni tanlab, tartibini belgilaysiz.
3. (Ixtiyoriy) Ovozdagi matnni qo'yasiz — subtitr uchun.
4. "Videoni yaratish" bosiladi, ilova o'zi:
   - ovozni tahlil qilib, pauzalarni topadi;
   - rasmlarning almashish joylarini pauzalarga to'g'rilaydi (pauza topilmasa, vaqtni teng bo'ladi);
   - har bir rasmga sekin yaqinlashish/uzoqlashish va yengil siljish effekti beradi;
   - matnni qisqa qatorlarga bo'lib, ovoz vaqtiga mos subtitr qo'shadi;
   - 16:9 (YouTube) yoki 9:16 (Shorts/Reels), 720p yoki 1080p MP4 yig'adi va Galereyaga (Movies/VideoAvto) saqlaydi.

## APK ni qanday yig'ish

### 1-usul: GitHub orqali (kompyuterda Android Studio shart emas)

1. github.com da bepul akkaunt oching va yangi repozitoriy yarating.
2. Shu papkadagi **hamma fayllarni** (yashirin `.github` papkasi ham!) repozitoriyga yuklang.
3. Repozitoriyda **Actions** bo'limiga kiring → **"APK yig'ish"** → **Run workflow**.
4. 5–10 daqiqadan keyin ish tugagach, natija sahifasidagi **Artifacts** bo'limidan `VideoAvto-APK` ni yuklab oling (zip ichida `app-debug.apk`).
5. APK ni telefonga o'tkazib o'rnating (Sozlamalar → "Noma'lum manbalardan o'rnatish" ruxsatini bering).

### 2-usul: Android Studio

1. Android Studio ni o'rnating, **Open** orqali shu papkani oching.
2. Gradle sinxronlanishini kuting.
3. **Build → Build APK(s)**. Fayl: `app/build/outputs/apk/debug/app-debug.apk`.

## Muhim cheklovlar (halol ro'yxat)

- **Subtitr matni o'zi eshitilmaydi.** Ilova ovozni matnga aylantirmaydi (bu internet va alohida xizmat talab qiladi). Matnni siz qo'yasiz, ilova uni ovoz vaqtiga taxminan moslaydi. Moslik har doim aniq bo'lmasligi mumkin.
- Rasmlar orasida yumshoq o'tish (crossfade) hozircha yo'q — to'g'ridan-to'g'ri almashadi.
- Video yig'ish telefonni zo'rlaydi: yig'ilayotganda ilovani yopmang, ekran yoniq tursin. 10 daqiqalik 1080p video bir necha daqiqa olishi mumkin.
- Kod Android SDK bo'lmagan muhitda yozilgan va **hali kompilyatsiya qilib sinalmagan**. Birinchi yig'ishda kichik xato chiqsa, xato matnini yuboring — tuzataman.

## Fayllar

- `AudioAnalyzer.kt` — ovozni dekodlash, pauzalarni topish
- `Planner.kt` — sahnalar va subtitr vaqtlarini hisoblash
- `VideoExporter.kt` — effektlar, subtitr qatlami, MP4 yig'ish
- `MainViewModel.kt`, `MainActivity.kt` — ilova mantig'i va interfeys
- `Files.kt` — rasmlarni tayyorlash, Galereyaga saqlash
