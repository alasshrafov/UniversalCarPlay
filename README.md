# 🌟 CarTwin — Premium Connect (Android CarPlay Receiver)

**CarTwin** — **BYD, Changan, Li Auto (Li Xiang), Zeekr** və bütün **Android əsaslı avtomobil monitorları** üçün hazırlanmış rəsmi brendləşdirilmiş, dongle-sız Apple CarPlay qəbuledici tətbiqidir.

Heç bir xarici fiziki adapterə (dongle) ehtiyac olmadan həm simli (USB), həm də simsiz (Wi-Fi / Bluetooth) Apple CarPlay bağlantısını, daxili internet brauzerini və reklam çarxı formatlı dinamik açılış şoularını təmin edir.

---

## 📥 Hazır APK Yükləmə (Release v1.3.0)
* 🚀 **Birbaşa APK Yüklə:** [CarTwin-v1.3.apk](https://github.com/alasshrafov/UniversalCarPlay/releases/download/v1.3.0/CarTwin-v1.3.apk)
* 📦 **Bütün Buraxılışlar:** [GitHub Releases Səhifəsi](https://github.com/alasshrafov/UniversalCarPlay/releases)

---

## 🌟 Əsas Funksiyalar

* 🚘 **Orijinal Avtomobil Loqoları (3D Xrom & Fotorealistik):** BYD, Changan, Li Auto, Zeekr, Geely, Chery və Haval rəsmi vektor emblemləri ilə lazer parıltılı açılış şousu.
* 🧭 **iOS 17/18 Retina CarPlay İkonları & Split Dashboard:** Kristal təmiz vektor qrafikaları və canlı naviqasiya + musiqi idarəetməsi.
* 💎 **CarTwin Orijinal Loqosu və İnterfeysi:** Metalik sonsuzluq ilmələri (gümüşü "C" və qızılı "T"), "CARTWIN PREMIUM CONNECT" açılış şousu.
* 🌐 **İnteqrasiya Olunmuş İnternet Brauzeri:** Google, YouTube, Vikipediya və istənilən veb-sayta birbaşa multimedia ekranından daxil olun.
* 🎙️ **Azərbaycan Dilli Səsli Köməkçi:** *"Brauzeri aç"*, *"YouTube-u başlat"*, kondisioner, naviqasiya və zənglər.
* 🎬 **Reklam Çarxı Stilində Açılış Şousu:** BYD, Changan, Li Auto və Zeekr orijinal vektor loqoları ilə dinamik animasiya.
* 🚗 **Avtomobil Modelini 100% Avtomatik Tanıma:** Heç bir əl konfiqurasiyası tələb olunmur (`Build.BRAND` və `Build.MODEL`).
* 📶 **Dongle-sız Qoşulma:** Avtomobilin daxili Wi-Fi və Bluetooth modulları vasitəsilə birbaşa əlaqə.
* 🎛️ **Lüks Split-Screen Dashboard:** Sürətölçən (km/h), batareya faizi, ikili iqlim nəzarəti və YouTube Park Rejimi.
* ⚡ **Hardware Sürətləndirməli Dekoder:** Android `MediaCodec` vasitəsilə 30-50ms aşağı gecikməli (low-latency) H.264/H.265 video axını.
* 🔊 **Çoxkanallı Səs:** Musiqi (Stereo 48kHz) və Danışıq/Siri (16kHz) kanallarının ayrılması, arxa kamera və parktronik zamanı səsin avtomatik tənzimlənməsi (audio ducking).
* 🖥️ **Daxili Virtual Test Laboratoriyası:** Kompüterdə `http://localhost:9999` veb monitoru vasitəsilə maşın və iPhone olmadan sınaq imkanı.

---

## 📁 Layihə Arxitekturası

```
UniversalCarPlay/
├── app/
│   ├── src/main/java/com/universal/carplay/
│   │   ├── MainActivity.kt               # CarTwin tam ekran avtomobil pəncərəsi & Brauzer
│   │   ├── engine/
│   │   │   ├── VideoDecoder.kt           # Hardware MediaCodec video motoru
│   │   │   ├── AudioEngine.kt            # Çoxkanallı səs və fokus meneceri
│   │   │   └── TouchManager.kt           # Toxunma koordinatları və jestlər
│   │   ├── profiles/
│   │   │   ├── VehicleProfile.kt         # CarTwin & Avtomobil profil bazası
│   │   │   ├── BydProfile.kt             # BYD xüsusiyyətləri & Loqo
│   │   │   ├── ChanganProfile.kt         # Changan xüsusiyyətləri & Loqo
│   │   │   └── LiAutoProfile.kt          # Li Auto xüsusiyyətləri & Loqo
│   │   └── protocol/
│   │       ├── CarPlaySession.kt         # Əsas sessiya koordinatoru
│   │       └── MockCarPlayClient.kt      # Virtual test müştərisi
│   └── src/main/res/drawable/
│       └── cartwin_logo.png              # Rəsmi CarTwin Loqosu
├── tools/
│   └── mock_iphone/
│       ├── server.js                     # Kompüterdə virtual iPhone vericisi (Port 9999/9998)
│       └── public/
│           ├── index.html                # İnteraktiv Avtomobil Monitoru Simulyatoru
│           └── cartwin_logo.png          # Web Simulator brend aktivi
└── README.md
```

---

## 🛠️ İnkişaf və Test Qaydası

1. **Virtual Monitor və iPhone Simulyatorunu Başlatmaq (Kompüterdə):**
   ```bash
   cd tools/mock_iphone
   node server.js
   ```
   Brauzerdə `http://localhost:9999` ünvanını açın.
2. **Android Tətbiqini Quraşdırmaq:**
   `CarTwin-v1.2.apk` faylını avtomobil monitoruna USB fleşkartla köçürüb quraşdırın. Tətbiq açılan kimi avtomobili avtomatik tanıyır və CarTwin lüks interfeysini aktivləşdirir.
