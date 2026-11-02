package com.soylemis.obdgaraj.obd

/**
 * Üretici-özel genişletilmiş PID'ler (Mode 22 / UDS örnekleme) — hudson'un VIN-dispatch
 * kalıbı. Yalnız CAN protokollerinde ve yalnız beyaz listedeki PID'ler sorgulanır;
 * CommandGuard Mode 22'yi servis bazında serbest bırakır, PID'i burası kısıtlar.
 *
 * DEĞERLER DOĞRULANMAMIŞTIR: PSA motosiklet/araba PID'leri kamuya açık ELM327
 * kaynaklarından derlenmiştir; araçta denenmeden "güvenilmez" sayılır. Bu yüzden
 * liste varsayılan olarak KAPALIDIR ve kullanıcı Bilgi ekranından açar.
 */
data class ExtendedPid(
    val pid: Int,
    val nameTr: String,
    val unit: String,
    val byteCount: Int,
    val decode: (IntArray) -> Double,
)

object ExtendedPids {
    private fun ab(d: IntArray) = d[0] * 256.0 + d[1]
    private fun temp(d: IntArray) = d[0] - 40.0

    /** PSA (C4, XP400): yaygın STLA/PSA Mode-22 PID'leri [doğrulanmadı — araçta test edin]. */
    private val psa = listOf(
        ExtendedPid(0x0000, "Desteklenen genişletilmiş PID listesi", "", 1) { it[0].toDouble() },
        ExtendedPid(0x010C, "Motor devri (yedek kanal)", "rpm", 2) { ab(it) / 4 },
        ExtendedPid(0x011F, "Hava akışı (yedek)", "g/s", 2) { ab(it) / 100 },
        ExtendedPid(0x015C, "Motor yağı sıcaklığı (PSA)", "°C", 1, ::temp),
        ExtendedPid(0x015E, "Anlık yakıt tüketimi (PSA)", "L/h", 2) { ab(it) / 20 },
    )

    /** Yamaha (MT-125): JM25/Mode-22 kalıbı [doğrulanmadı — araçta test edin]. */
    private val yamaha = listOf(
        ExtendedPid(0x0105, "Soğutma suyu sıcaklığı (Yamaha)", "°C", 1, ::temp),
        ExtendedPid(0x010C, "Motor devri (Yamaha)", "rpm", 2) { ab(it) / 4 },
        ExtendedPid(0x0146, "Emme havası sıcaklığı (Yamaha)", "°C", 1, ::temp),
        ExtendedPid(0x015B, "Giriş havası basıncı (Yamaha)", "kPa", 2) { ab(it) / 4 },
    )

    fun forManufacturer(m: Manufacturer): List<ExtendedPid> = when (m) {
        Manufacturer.PSA -> psa
        Manufacturer.RENAULT -> psa // aynı Stellantis ailesi; [doğrulanmadı]
        Manufacturer.YAMAHA -> yamaha
        else -> emptyList()
    }

    /** Liste boşsa üretici desteklenmiyor demektir. */
    fun isSupported(m: Manufacturer): Boolean = forManufacturer(m).isNotEmpty()
}
