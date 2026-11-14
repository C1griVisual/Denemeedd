# ScreenShotter — Telefondan APK üretme (bilgisayarsız)

Bu proje, GitHub Actions ile bulutta derlenir. Bilgisayara gerek yok — her şeyi iPhone'dan GitHub web sitesinden yapabilirsin. Çıkan APK'yı indirip bir Android cihaza kurarsın.

## Adımlar (iPhone / tarayıcı)

1. **GitHub'da yeni repo aç**
   - github.com → sağ üst **+** → **New repository**
   - İsim ver (örn. `screenshotter`), **Private** seç, **Create**.

2. **Dosyaları yükle**
   - Bu zip'i aç (Dosyalar uygulamasında zip'e dokun → klasör çıkar).
   - Repo sayfasında **Add file → Upload files**.
   - Klasörün **içindeki her şeyi** seç ve yükle. Önemli: `.github` klasörü ve `gradle` klasörü de dahil olmalı.
   - Not: GitHub web arayüzü klasörleri sürükleyince korur. Eğer `.github/workflows/build.yml` yolunu koruyamazsan, dosyaları tek tek "Create new file" ile o yola yazman gerekir.
   - En altta **Commit changes**.

3. **Derleme otomatik başlar**
   - Yükleme biter bitmez üstteki **Actions** sekmesine geç.
   - "Build APK" adında bir çalışma görürsün. Sarı nokta = derleniyor, yeşil tik = bitti (~3-5 dk).

4. **APK'yı indir**
   - Yeşil tike dokun → en altta **Artifacts** bölümünde `ScreenShotter-debug-apk`.
   - Dokun, iner. İçinden `app-debug.apk` çıkar.

5. **Android'e kur**
   - APK'yı Android cihaza aktar (Telegram, mail, kablo...).
   - Dokun → "bilinmeyen kaynaklara izin ver" → kur.

## Elle tekrar derlemek
Actions sekmesi → **Build APK** → sağda **Run workflow**.

## Ayarlar
`app/src/main/java/com/example/screenshotter/MainActivity.kt` içinde `Config` bloğu:
- `BOT_TOKEN`, `CHAT_ID` — Telegram hedefi
- `INTERVAL_SEC` — kaç saniyede bir kare (varsayılan 5)
- `JPEG_QUALITY` — sıkıştırma kalitesi

## Notlar
- Bu **debug** APK'dır; kurulum için imza sorunu çıkmaz, Play Store'a yüklenmez.
- Android 14 (SDK 34) hedefli derlenir; MediaProjection foreground service tipi doğru beyan edildi.
