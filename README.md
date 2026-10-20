# Ağ İzleyici (Android / Kotlin)

Android Studio kurmadan, GitHub Actions üzerinden APK üretilebilen basit bir ağ izleme uygulamasıdır.

## Özellikler

- Kullanıcı başlatınca ön plan servisiyle sürekli çalışır.
- Her 10 saniyede iki hedefi kontrol eder:
  - Bağlı olunan ağın varsayılan ağ geçidi (gateway/modem)
  - Cloudflare `1.1.1.1`
- Tarih-saat, hedef, IP, sonuç, gecikme, bağlantı türü ve hata bilgisini CSV olarak kaydeder.
- Son ölçümleri uygulama ekranında gösterir.
- **CSV Dışa Aktar** düğmesiyle kayıtları telefonda seçtiğiniz konuma kaydeder.
- Android 8.0 ve üzerini destekler (`minSdk 26`).

## GitHub'a yükleme ve APK oluşturma

1. GitHub'da boş ve **Private** bir depo oluşturun. README, `.gitignore` veya lisans ekletmeyin.
2. Bu ZIP'i bilgisayarınızda açın.
3. ZIP'in içindeki bütün dosya ve klasörleri deponun ana sayfasındaki **uploading an existing file** bağlantısıyla yükleyin. `.github`, `app`, `build.gradle.kts`, `settings.gradle.kts` ve diğer dosyalar doğrudan depo kökünde görünmelidir; fazladan bir üst klasör olmamalıdır.
4. Değişiklikleri `main` dalına kaydedin (commit).
5. Depoda **Actions** sekmesine girin. İlk kullanımda istenirse Actions'ı etkinleştirin.
6. **Android APK Oluştur** iş akışını açın. Yükleme sonrasında otomatik başlamadıysa **Run workflow** düğmesine basın.
7. Yeşil onay işareti geldikten sonra çalıştırma sayfasının en altındaki **Artifacts** bölümünden `NetworkMonitor-debug-apk` dosyasını indirin.
8. İndirilen artifact ZIP'ini açın; içindeki `app-debug.apk` dosyasını telefona aktarın ve kurun. Android, bilinmeyen kaynaklardan yükleme için bir defalık izin isteyebilir.

> GitHub'a proje ZIP'ini tek dosya olarak yüklemeyin. ZIP'i açıp içeriğini depo köküne yükleyin; aksi halde iş akışı görünmez.

## Kullanım

1. Uygulamayı açın ve bildirim iznini verin.
2. **İzlemeyi Başlat** düğmesine dokunun.
3. Kalıcı bildirim, servisin çalıştığını ve son ölçümlerin durumunu gösterir.
4. Sorunu yeterince gözlemledikten sonra uygulamaya dönüp **İzlemeyi Durdur** düğmesine dokunun.
5. **CSV Dışa Aktar** düğmesine dokunup dosyanın kaydedileceği yeri seçin.

## CSV sütunları

| Sütun | Açıklama |
|---|---|
| `timestamp` | Saat dilimi dahil ISO 8601 tarih-saat |
| `target` | `Gateway`, `Cloudflare` veya sistem kaydı |
| `address` | Ölçülen IP adresi |
| `success` | `true` / `false` |
| `latency_ms` | Milisaniye olarak gecikme; ölçülemediyse boş |
| `network` | Wi-Fi, mobil, Ethernet, VPN veya bağlantı yok |
| `error` | Başarısızlık ayrıntısı |

## Teknik notlar

- Android bazı cihazlarda pil tasarrufu nedeniyle uzun süreli servisleri kısıtlayabilir. Kesintisiz uzun test için uygulamayı cihazın pil optimizasyonu dışına almanız gerekebilir.
- Telefon yeniden başlatıldığında izleme otomatik başlamaz; uygulamadan yeniden başlatılmalıdır.
- Bazı modemler ICMP/ping yanıtını kapatır. Uygulama başarısız ping sonrasında alternatif erişilebilirlik kontrolü dener; yine de modem ayarları ölçümü etkileyebilir.
- Üretilen APK bir debug APK'sıdır. Kişisel test için uygundur; Play Store yayını için imzalı release yapılandırması gerekir.

## Proje yapısı

```text
.github/workflows/build-apk.yml  GitHub Actions derleme işi
app/src/main/                   Android uygulama kaynakları
build.gradle.kts                Android ve Kotlin eklenti sürümleri
settings.gradle.kts             Proje ve depo ayarları
```

## Gizlilik

Kayıtlar yalnızca uygulamanın özel depolama alanında tutulur. Kullanıcı **CSV Dışa Aktar** seçeneğini kullanmadıkça başka bir konuma kopyalanmaz. Uygulama kayıtları herhangi bir sunucuya göndermez.
