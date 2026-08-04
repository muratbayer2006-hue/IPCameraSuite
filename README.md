# IPCameraSuite

Android telefonunuzu profesyonel bir IP kameraya dönüştürün.

Test odaklı, modüler ve geliştirici dostu bir platform.

## 📦 Özellikler (v0.1.0)

- ✅ Ön/arka kamera desteği
- ✅ Kamera değiştirme
- ✅ Zoom kontrolü (1.0x – maksimum)
- ✅ MJPEG üzerinden Wi-Fi yayını
- ✅ Çoklu istemci desteği (aynı anda VLC + Tarayıcı)
- ✅ VLC ve tarayıcı ile canlı izleme
- ✅ Gerçek zamanlı log sistemi (UI, logcat, dosya)
- ✅ Linux Developer Console (Python)
- ✅ WebSocket ile çift yönlü iletişim
- ✅ Test arayüzü (kamera testi, stream testi)
- ✅ Sürüklenebilir log alanı

## 🚀 Hızlı Başlangıç

### Android Uygulaması

1. Projeyi Android Studio ile açın.
2. Cihazınızı USB ile bağlayın (USB Debugging açık).
3. Çalıştırın veya APK'yı derleyin:
   ```bash
   ./gradlew assembleDebug
   adb install -r app/build/outputs/apk/debug/app-debug.apk

Yayını Başlatma

    Telefon ekranında "YAYIN BAŞLAT" butonuna tıklayın.

    Ekranın üst kısmında URL görünecektir:
    http://<cihaz-ip>:8080/stream.mjpg

    VLC veya tarayıcınızda bu URL'yi açın.

Developer Console (Linux)
bash

cd DeveloperConsole
python3 -m venv venv
source venv/bin/activate
pip install websockets
python console.py

    12 ile WebSocket bağlanın.

    Komutlar: Yayın başlat/durdur, kamera değiştir, zoom, test çalıştır, log çek/temizle.

📚 Dokümantasyon

Proje dokümantasyonu aşağıdaki dosyalarda bulunur:

    VISION.md – Proje vizyonu

    ARCHITECTURE.md – Mimari katmanlar

    COMMUNICATION_PROTOCOL.md – WebSocket iletişim protokolü

    UI_GUIDELINES.md – Arayüz kuralları

📝 Sürüm Geçmişi

    v0.1.0 – İlk kararlı sürüm (MJPEG yayını, WebSocket konsol, testler, log yönetimi)

🛠️ Geliştirme Ortamı

    Linux Mint 22.3 XFCE

    Android Studio (Kotlin, Jetpack Compose, CameraX)

    OPPO A54 (Gerçek cihaz)

📜 Lisans

Bu proje özel bir lisans altında geliştirilmiştir. Tüm hakları saklıdır.

Keyifli kullanımlar! 📷
