# OBD Garaj

ELM327 (klasik Bluetooth veya BLE) adaptörüyle araca bağlanıp aracın desteklediği
standart OBD2 verilerini ve arıza kodlarını gösteren Android uygulaması.

Hedef araçlar: Citroën C4 (2022), Yamaha MT-125 (2025), Peugeot XP400 (2024).

- Tasarım: [docs/superpowers/specs/2026-10-31-obd-garaj-design.md](docs/superpowers/specs/2026-10-31-obd-garaj-design.md)
- Plan: [docs/superpowers/plans/2026-10-31-obd-garaj-v1.md](docs/superpowers/plans/2026-10-31-obd-garaj-v1.md)

## Derleme

JDK 17 ve Android SDK (platform 35) gerekir.

```bash
./gradlew testDebugUnitTest assembleDebug
```

APK: `app/build/outputs/apk/debug/app-debug.apk`. Her push'ta GitHub Actions da
derleyip `obd-garaj-debug-apk` adıyla artifact olarak yükler.

## Güvenlik

Uygulama araca yalnızca okuma komutları gönderir (`AT*`, servis `01 02 03 07 09 0A 22`).
Arıza kodu silme (`04`) yalnızca onay ekranından sonra gönderilir.
Servis `22` (üretici-özel okuma, Mode 22) yalnız CAN protokollerinde ve `ExtendedPids`
beyaz listesindeki PID'ler için kullanılır; UDS yazma servisleri (`2E` vb.) CommandGuard'dan
geçemez.

## Özellikler (v0.2)

- **VIN → üretici tanıma:** WMI tablosu ile (Stellantis/Renault/Toyota/Yamaha…); Bilgi
  ekranında üretici etiketi gösterilir.
- **Üretici-özel veriler (Mode 22):** PSA ve Yamaha için genişletilmiş PID okuma;
  değerler kamuya açık kaynaklıdır ve araçta doğrulanmadıysa ekranda uyarı notu gösterilir.
- **Özelleştirilebilir PID tablosu:** `assets/pids_custom.json` (ya da ayarlara kaydedilen
  JSON) yerleşik tabloyu tamamlar/aşar; formüller güvenli aritmetik ayrıştırıcıyla
  çalışır (script yok).
- **Sefer kaydı + grafik:** Canlı sekmede kayıt açılınca seçili PID'ler 1 sn'de bir
  Room'a yazılır, satır altlarında sparkline grafik görünür.
- **CSV dışa aktarma:** Bilgi sekmesinden son sefer, Torque-uyumlu `;` ayraçlı CSV olarak
  paylaşılır (Türkçe Excel uyumlu).
