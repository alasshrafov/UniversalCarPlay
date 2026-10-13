# 🚗 Universal CarPlay Receiver (Android)

Universal Apple CarPlay qəbuledici tətbiqi — **BYD, Changan, Li Auto (Li Xiang)** və bütün **Android əsaslı avtomobil monitorları** üçün proqram təminatı.

Heç bir xarici fiziki adapterə (dongle) ehtiyac olmadan həm simli (USB), həm də simsiz (Wi-Fi / Bluetooth) Apple CarPlay bağlantısını təmin edir.

---

## 🌟 Əsas Funksiyalar

* **Dongle-sız Qoşulma:** Avtomobilin daxili Wi-Fi və Bluetooth modulları vasitəsilə birbaşa əlaqə.
* **Universal Avtomobil Dəstəyi:**
  * **BYD (DiLink):** 15.6" fırlanan ekranlar, HUD və cihazlar paneli inteqrasiyası.
  * **Changan / Deepal / Avatr:** Geniş ekran tənzimləmələri və xüsusi səs idarəetməsi.
  * **Li Auto (Li Xiang):** 15.7" 3K ekranlar üçün yüksək DPI və ultra keyfiyyət.
  * **Universal Rejim:** Bütün digər standart Android monitorlar (720p / 1080p).
* **Hardware Sürətləndirməli Dekoder:** Android `MediaCodec` vasitəsilə 30-50ms aşağı gecikməli (low-latency) H.264/H.265 video axını.
* **Çoxkanallı Səs:** Musiqi (Stereo 48kHz) və Danışıq/Siri (16kHz) kanallarının ayrılması, arxa kamera və parktronik zamanı səsin avtomatik tənzimlənməsi (audio ducking).
* **Sükan və Toxunma:** Multitouch jestləri və sükandakı düymələrin (media, zəng, səs) dəstəyi.
* **Daxili Virtual Test Laboratoriyası:** Kompüterdə çalışan mock iPhone serveri vasitəsilə maşın və iPhone olmadan sınaq imkanı.

---

## 📁 Layihə Arxitekturası

```
UniversalCarPlay/
├── app/
│   ├── src/main/java/com/universal/carplay/
│   │   ├── MainActivity.kt               # Tam ekran avtomobil pəncərəsi
│   │   ├── engine/
│   │   │   ├── VideoDecoder.kt           # Hardware MediaCodec video motoru
│   │   │   ├── AudioEngine.kt            # Çoxkanallı səs və fokus meneceri
│   │   │   └── TouchManager.kt           # Toxunma koordinatları və jestlər
│   │   ├── profiles/
│   │   │   ├── VehicleProfile.kt         # Avtomobil profil bazası
│   │   │   ├── BydProfile.kt             # BYD xüsusiyyətləri
│   │   │   ├── ChanganProfile.kt         # Changan xüsusiyyətləri
│   │   │   └── LiAutoProfile.kt          # Li Auto xüsusiyyətləri
│   │   └── protocol/
│   │       ├── CarPlaySession.kt         # Əsas sessiya koordinatoru
│   │       └── MockCarPlayClient.kt      # Virtual test müştərisi
├── tools/
│   └── mock_iphone/
│       ├── server.js                     # Kompüterdə virtual iPhone vericisi
│       └── package.json
└── README.md
```

---

## 🛠️ İnkişaf və Test Qaydası

1. **Virtual iPhone Serverini Başlatmaq (Kompüterdə):**
   ```bash
   cd tools/mock_iphone
   node server.js
   ```
2. **Android Tətbiqini İşə Salmaq:**
   Tətbiqi Android Studio və ya APK vasitəsilə planşet/telefonda başladın. Tətbiq yerli şəbəkədəki virtual iPhone-u tapıb ekran görüntüsünü və səsi real vaxtda göstərəcək.
