# Sürüm Geçmişi

## [v0.1.0] – 2026-08-05

### Eklenenler
- Proje iskeleti (Empty Compose Activity)
- Merkezi Logger sistemi (UI, logcat ve dosya çıkışı)
- Ana UI bölünmesi (kamera önizleme + canlı log)
- CameraX Preview entegrasyonu
- Runtime kamera izni yönetimi
- Ön/arka kamera değiştirme
- Zoom kontrolü (1.0x – max)
- Clean Architecture katmanları (ViewModel, Repository)
- MJPEG Streaming (ServerSocket ile, çoklu istemci desteği)
- ImageAnalysis ile kare yakalama ve JPEG dönüşümü
- Görüntü dönme düzeltmesi
- Developer Console (Python) – ADB ve WebSocket ile iletişim
- WebSocket çift yönlü iletişim (telefon ↔ konsol)
- Test Çalıştırma (kamera testi, stream testi)
- Log dosyasını çekme ve temizleme
- UI iyileştirmeleri (butonlar, sürüklenebilir log alanı)
- Yayın URL'si ekran üstünde gösterimi

### Düzeltilenler
- Analiz başlatma/durdurma sorunu
- JSON float dönüşüm hatası (NoSuchMethodError)
- MJPEG sunucusunda çoklu istemci sorunu
- Görüntü yatık gelme sorunu

### Bilinen Sorunlar
- (Yok)

---

**Not:** Bu sürüm, kararlı temel özellikleri içerir. Gelecek sürümlerde video kaydı, performans bilgileri ve ses desteği eklenecektir.
