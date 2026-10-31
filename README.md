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

Uygulama araca yalnızca okuma komutları gönderir (`AT*`, servis `01 02 03 07 09 0A`).
Arıza kodu silme (`04`) yalnızca onay ekranından sonra gönderilir.
