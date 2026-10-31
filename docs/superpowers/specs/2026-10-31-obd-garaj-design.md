# OBD Garaj — Tasarım Dokümanı

Tarih: 2026-10-31
Durum: Onay bekliyor

## 1. Amaç ve neden

Hasan'ın üç aracı var: **Citroën C4 (2022)**, **Yamaha MT-125 (2025)** ve **Peugeot XP400 (2024)**. Android telefonundan, 16 pinli bir ELM327 Bluetooth adaptörüyle bu araçlara bağlanıp:

- aracın **desteklediği tüm verileri** görmek,
- **arıza kodlarını** ve ayrıntılarını görmek

istiyor. Hazır uygulamalar yerine kendi araçlarına göre şekillenen, "test ettikçe ekleriz" yaklaşımıyla büyüyen kendi aracını istiyor. GitHub'daki `obd-ii` konulu projeler referans olarak kullanılacak.

## 2. Hedefler

1. **Otomatik keşif:** Bağlanınca araca hangi verileri desteklediği sorulur (Mode 01 PID 00/20/40/…/C0 bit maskeleri, ECU bazında). Ekranlar sadece gelen cevaba göre dolar; hiçbir araç için elle PID listesi tutulmaz.
2. **Canlı veri:** Aracın desteklediği tüm Mode 01 PID'leri birimleriyle okunur. Kullanıcı hangilerini izleyeceğini seçer.
3. **Arıza kodları:**
   - Aktif kodlar (Mode 03), bekleyen kodlar (Mode 07), kalıcı kodlar (Mode 0A)
   - Her kod için açıklama (genel SAE J2012 kodları)
   - Arıza anı verisi / freeze frame (Mode 02)
   - Arıza lambası (MIL) durumu ve hazırlık monitörleri (PID 01 01)
   - Kod silme (Mode 04). **Yalnızca** açık bir onay ekranından sonra yapılır.
4. **Araç kimliği:** VIN (Mode 09 PID 02), ECU adı (09 0A), kalibrasyon kimlikleri (09 04), algılanan protokol ve cevap veren ECU'lar.
5. **Araç profilleri:** Üç araç kaydedilir. Her profil kendi protokolünü, desteklenen PID listesini ve arıza geçmişini saklar. Bağlanınca VIN ile otomatik eşleşilir. VIN vermeyen motosikletlerde kullanıcı aracı elle seçer.
6. **Ham kayıt ve konsol:** Gönderilen her komut ve gelen her cevap zaman damgasıyla kaydedilir ve dışa aktarılabilir. Ham komut gönderilebilen bir konsol ekranı bulunur. "Test ettikçe ekleriz" yöntemi buna dayanır: araçta bir şey ters giderse log'dan sorun bulunur, yeni özellikler de konsolda denenir.
7. **Adaptör desteği:** Hem klasik Bluetooth (SPP) hem BLE ELM327 adaptörleri desteklenir.

## 3. Hedef dışı (v1'de yok)

- **Üreticiye özel veriler:** PSA/Stellantis UDS (yağ seviyesi, servis, ABS, hava yastığı, BSI vb.), Yamaha ve Peugeot Motocycles özel komutları. Bunlar 2. aşamaya kalıyor. Konsol ve log altyapısı bu aşamanın zeminini hazırlar.
- ECU'ya yazma, kodlama, adaptasyon, yazılım güncelleme.
- iOS, web veya masaüstü sürümü.
- Play Store'da yayınlama. v1 APK olarak kurulur.
- Bulut senkronizasyonu ve hesap sistemi.
- Mode 06 (on-board test sonuçları). İleride eklenebilir.

## 4. Anti-hedefler (çalışsa bile başarısızlık sayılır)

- Araca **okuma dışında** bir komut göndermek. Tek istisna, onaylı Mode 04 (kod silme). v1'de UDS yazma servisleri (0x2E, 0x31, 0x34 vb.) asla gönderilmez. Bu kural kod düzeyinde bir izin listesiyle uygulanır.
- Kodları kullanıcı fark etmeden ya da tek dokunuşla silmek.
- Desteklenmeyen PID'leri sürekli sorgulayıp veri yolunu boşuna meşgul etmek veya ECU'yu yormak.
- Bağlantı başarısız olunca "bağlanamadı" deyip hiçbir iz bırakmamak. Her hata log'da görünür olmalı.

## 5. Kısıtlar ve araç bazlı notlar

| Araç | Soket | Beklenti | Belirsizlik |
|---|---|---|---|
| Citroën C4 2022 | Standart OBD2, 16 pin | ISO 15765-4 CAN 500k. Standart PID'ler ve DTC'ler sorunsuz gelir. | Düşük |
| Yamaha MT-125 2025 | Yamaha teşhis soketi + OBD2 adaptör kablo | Euro 5 OBD, CAN tabanlı. Temel PID'ler ve DTC'ler büyük ihtimalle gelir. | Orta. Araçta doğrulanmalı. |
| Peugeot XP400 2024 | Üreticiye özel teşhis soketi + adaptör kablo | Euro 5 OBD gereği bir şeyler vermesi beklenir. | Yüksek. Soket pin dizilimi ve protokol araçta doğrulanmalı. |

- Motosikletlerde VIN gelmeyebilir ve desteklenen PID sayısı az olabilir. Uygulama bunu hata saymaz, eldeki veriyi gösterir.
- Ucuz ELM327 klonları (v1.5 / v2.1) bazı AT komutlarını desteklemez, yavaş cevap verir ya da cevabı bozuk gönderir. Başlatma dizisi buna dayanıklı olmalıdır.
- Motosiklet adaptör kablolarının pin eşlemesi ve hangi kablonun alınacağı kullanıcı tarafında çözülür. Uygulama yalnızca 16 pinli ELM327'yi görür.

## 6. Yaklaşımlar

| # | Yaklaşım | Artısı | Eksisi |
|---|---|---|---|
| **1** | **Sıfırdan native Android (Kotlin + Jetpack Compose), kendi ELM327 katmanımızla** | Android'in Bluetooth API'lerine tam erişim, küçük ve anlaşılır kod, araçlara göre büyür | İlk sürüm hazır projeyi çatallamaktan biraz uzun sürer |
| 2 | Flutter (Dart) | Teoride iOS'a taşınabilir | Klasik Bluetooth desteği zayıf, ucuz ELM327'ler zaten iOS'ta çalışmaz. Kazancı yok, ek katman getirir. |
| 3 | AndrOBD gibi hazır bir uygulamayı çatallamak | Birçok özellik ilk günden hazır | Büyük ve eski Java kod tabanı, GPL lisansı, özelleştirmesi zor |

**Seçilen: 1.** Hasan bu yolu seçti. Gerekçe: araçlara göre "test ettikçe ekleriz" yaklaşımına en uygun ve en sade seçenek bu. 2 ve 3 kod kaynağı olarak değil, yalnızca referans olarak kullanılır. AndrOBD (GPL) kodu **kopyalanmaz**, sadece davranışına bakılır.

## 7. Kararlaştırılan teknik yapı

> Not: Bu bölümdeki teknik seçimler Claude'un kararıdır. Hasan yapımı Claude'a bıraktı.

- **Dil ve arayüz:** Kotlin, Jetpack Compose, Material 3. Arayüz dili Türkçe.
- **Android sürümü:** minSdk 26 (Android 8), targetSdk güncel kararlı sürüm.
- **Eşzamanlılık:** Kotlin Coroutines ve Flow.
- **Depolama:** Araç profilleri, desteklenen PID önbelleği ve arıza geçmişi için Room.
- **Bağımlılık yönetimi:** Elle kurulan basit DI. Hilt ancak gerekirse eklenir.
- **Paket adı:** `com.soylemis.obdgaraj`

### Katmanlar

```
ui/          Compose ekranları + ViewModel'ler
domain/      Araç profili, PID tanımları, DTC modeli, keşif mantığı
obd/         ELM327 oturumu, komut kuyruğu, cevap ayrıştırıcı, PID çözücüler
transport/   ObdTransport arayüzü: ClassicSppTransport, BleTransport, (test) FakeTransport
data/        Room, DTC açıklama veritabanı, log deposu
```

- **Transport:** Tek bir arayüzün arkasında `connect()`, `send(bytes)` ve `'>'` istemine kadar okuma işlemleri bulunur.
  - Klasik: SPP UUID `00001101-0000-1000-8000-00805F9B34FB`.
  - BLE: Yaygın ELM327 servisleri (`FFE0/FFE1`, `FFF0/FFF1/FFF2`, `18F0`) otomatik taranır. Yazma ve bildirim karakteristikleri bulunur.
  - Android 12+ için `BLUETOOTH_SCAN` ve `BLUETOOTH_CONNECT`, eski sürümler için konum izni istenir.
- **ELM327 oturumu:** Komutlar tek bir kuyrukta sırayla gider. Aynı anda bekleyen yalnızca bir komut olur. Her komut için zaman aşımı tanımlanır.
  - Başlatma dizisi: `ATZ`, `ATE0`, `ATL0`, `ATS0`, `ATH1`, `ATSP0`, `ATAT1`, ardından `0100` (protokol araması) ve `ATDPN`.
  - `ATH1` sayesinde birden fazla ECU'nun cevabı başlıklarına göre ayrılır.
  - Desteklenmeyen AT komutlarına (`?`) dayanıklıdır. Bir komut desteklenmezse atlanır ve log'a yazılır.
- **Cevap ayrıştırıcı:** `SEARCHING...`, `NO DATA`, `UNABLE TO CONNECT`, `BUS INIT: ...ERROR`, `CAN ERROR`, `STOPPED`, `?` ve boş satırlar ele alınır. ISO-TP çok çerçeveli cevaplar (`0:`, `1:` satır önekleri) birleştirilir. Çoklu ECU cevapları ayrıştırılır.
- **PID çözücüler:** Formüller SAE J1979 tablosundan, birim ve aralık bilgisiyle tanımlanır. Tablo veri olarak tutulur ve her biri birim testlerle doğrulanır.
- **DTC açıklamaları:** Genel (P0xxx, P2xxx, C0, B0, U0) kodların açıklamaları lisansı uygun açık bir kaynaktan alınır ve uygulamaya gömülür. Üreticiye özel kodlar (P1xxx vb.) "üreticiye özel" olarak işaretlenir. Açıklama yoksa ham kod gösterilir.
- **Canlı veri döngüsü:** Yalnızca ekranda seçili ve desteklenen PID'ler sorgulanır. Sorgu hızı adaptörün cevap süresine göre ayarlanır.
- **Güvenlik izin listesi:** Gönderilebilir servisler `01, 02, 03, 07, 09, 0A`, onaylı akışta `04` ve `AT*` komutlarıyla sınırlıdır. Konsol da bu listeye tabidir; v1'de konsoldan yazma servisi gönderilemez.

### Ekranlar

1. **Garaj:** Üç araç kartı, son bağlantı zamanı ve son arıza özeti.
2. **Bağlan:** Eşleşmiş ya da taranan adaptörler, bağlantı ilerlemesi (başlatma, protokol, ECU'lar).
3. **Canlı Veri:** Desteklenen PID'lerin listesi ve seçilenlerin canlı değerleri.
4. **Arızalar:** MIL durumu, aktif, bekleyen ve kalıcı kodlar, açıklamalar, freeze frame, onaylı silme.
5. **Araç Bilgisi:** VIN, ECU adları, kalibrasyon kimlikleri, protokol, desteklenen PID listesi, hazırlık monitörleri.
6. **Konsol & Log:** Ham komut gönderme, oturum log'u, log'u paylaşma (dosya veya paylaş menüsü).

## 8. Test yaklaşımı

- **Birim testleri:** Ayrıştırıcı, PID çözücüler ve DTC çözümü, gerçek ELM327 cevaplarından oluşan örnek dosyalarla test edilir. Bu dosyalar Citroën'den alınan log'larla büyür.
- **Araçsız entegrasyon testi:** `FakeTransport` ile önceden kaydedilmiş oturumlar oynatılır. Bilgisayarda gerekirse bir ELM327 emülatörü (ör. açık kaynak `ELM327-emulator`) kullanılır.
- **Araç testleri:** Önce Citroën C4 (en öngörülebilir araç), sonra Yamaha, en son XP400. Her araç testinden sonra log dışa aktarılır, örnek dosyalara eklenir ve bulgular dokümana işlenir.

## 9. Depo ve sürüm

- Kod ayrı bir git deposunda tutulur (`obd-garaj`) ve GitHub'a gönderilir.
- APK, GitHub Releases'a elle ya da GitHub Actions ile eklenebilir. Bu iş plan aşamasında netleşecek.

## 10. Yapımcıya bırakılanlar

- PID tablosunun ve DTC açıklama kaynağının kesin seçimi (lisans uyumu şartıyla)
- Ekran tasarımının ayrıntıları ve canlı veri gösterim biçimi (liste, gösterge, grafik)
- Sorgu hızı algoritması ve zaman aşımı değerleri
- Log dosya formatı

## 11. Açık konular (araçta doğrulanacak)

- XP400 teşhis soketinin pin dizilimi ve uygun adaptör kablo
- MT-125'in VIN ve Mode 09 desteği
- Kullanılacak ELM327'nin klasik mi BLE mi olduğu (iki tür de desteklendiği için tasarımı engellemiyor)
