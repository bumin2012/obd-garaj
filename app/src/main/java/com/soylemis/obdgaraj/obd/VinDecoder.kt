package com.soylemis.obdgaraj.obd

/** VIN'den tanınan üretici. Mode 22 gibi üreticiye özel davranışlar buna bağlanır. */
enum class Manufacturer(val label: String) {
    PSA("Stellantis (Peugeot/Citroën/DS/Opel)"),
    RENAULT("Renault/Dacia"),
    TOYOTA("Toyota/Lexus"),
    HONDA("Honda"),
    HYUNDAI("Hyundai/Kia"),
    FORD("Ford"),
    VW("VW/Audi/Škoda/SEAT"),
    BMW("BMW/Mini"),
    MERCEDES("Mercedes-Benz"),
    YAMAHA("Yamaha"),
    HONDA_MOTO("Honda Motorsiklet"),
    KAWASAKI("Kawasaki"),
    SUZUKI("Suzuki"),
    UNKNOWN("Bilinmeyen üretici"),
}

/**
 * VIN çözümleyici. İki işi var:
 *  1. Mode 09 PID 02 cevabından 17 haneli VIN'i çıkarmak.
 *  2. WMI'ye (ilk 3 karakter) göre üreticiyi belirlemek — üretici-özel PID/DTC
 *     tabloları bu bilgiyle seçilir (hudson'un VIN-dispatch kalıbı).
 */
object VinDecoder {
    /** VIN alfabesi (I, O, Q yok); bu sayede sayaç, dolgu ve tekrar eden 49 02 baytları elenir. */
    private val vinChars = Regex("[A-HJ-NPR-Z0-9]")

    /** WMI önekleri. Uzun önek önce eşleşir; VF* Stellantis ailesi baştan yakalanır. */
    private val wmi: List<Triple<String, Manufacturer, String>> = listOf(
        // Fransa WMI'leri: VF3 Peugeot, VF6/VF7 Citroën/DS (Stellantis); VF1/VF2 Renault.
        Triple("VF3", Manufacturer.PSA, "VF3 Peugeot"),
        Triple("VF6", Manufacturer.PSA, "VF6 Citroën/DS"),
        Triple("VF7", Manufacturer.PSA, "VF7 Citroën/DS"),
        Triple("KL2", Manufacturer.PSA, "Kore Stellantis"),
        Triple("UU1", Manufacturer.PSA, "Hindistan Stellantis"),
        Triple("AL4", Manufacturer.PSA, "Arjantin Citroën/Peugeot"),
        Triple("9B", Manufacturer.PSA, "Brezilya Citroën"),
        Triple("VS1", Manufacturer.RENAULT, "Fransa Renault/Dacia"),
        Triple("VF1", Manufacturer.RENAULT, "VF1 Renault"),
        Triple("VF2", Manufacturer.RENAULT, "VF2 Renault"),
        Triple("JTN", Manufacturer.TOYOTA, "Japonya Toyota"),
        Triple("JTD", Manufacturer.TOYOTA, "Japonya Toyota"),
        Triple("JTH", Manufacturer.TOYOTA, "Japonya Lexus"),
        Triple("JT", Manufacturer.TOYOTA, "Japonya Toyota"),
        Triple("MR0", Manufacturer.TOYOTA, "Türkiye Toyota (Adapazarı)"),
        Triple("NMK", Manufacturer.RENAULT, "Türkiye Oyak Renault"),
        Triple("PL1", Manufacturer.HONDA, "Tayland Honda otomobil"),
        Triple("MAL", Manufacturer.HYUNDAI, "Kore Hyundai"),
        Triple("KMH", Manufacturer.HYUNDAI, "Kore Hyundai"),
        Triple("KNA", Manufacturer.HYUNDAI, "Kore Kia"),
        Triple("WF0", Manufacturer.FORD, "Almanya Ford Köln"),
        Triple("WVW", Manufacturer.VW, "Almanya VW"),
        Triple("WAU", Manufacturer.VW, "Almanya Audi"),
        Triple("WV1", Manufacturer.VW, "Almanya ticari VW"),
        Triple("WBA", Manufacturer.BMW, "Almanya BMW"),
        Triple("WDB", Manufacturer.MERCEDES, "Almanya Mercedes"),
        Triple("W0L", Manufacturer.VW, "Almanya Opel (Stellantis, WVW geleneği)"),
        // Motosiklet
        Triple("LYMX", Manufacturer.YAMAHA, "Yamaha Motor Taiwan (MT serisi bu bölgeden gelir)"),
        Triple("JY2", Manufacturer.YAMAHA, "Japonya Yamaha"),
        Triple("MH1", Manufacturer.HONDA_MOTO, "Japonya Honda motosiklet"),
        Triple("MLH", Manufacturer.HONDA_MOTO, "Japonya Honda motosiklet"),
        Triple("JKA", Manufacturer.KAWASAKI, "Japonya Kawasaki"),
        Triple("LS5", Manufacturer.SUZUKI, "Japonya Suzuki"),
    ).sortedByDescending { it.first.length }

    /** [payload] Mode 09 PID 02 cevabının "49 02" sonrasıdır. */
    fun decode(payload: ByteArray): String? {
        val chars = payload.map { (it.toInt() and 0xFF).toChar() }.filter { vinChars.matches(it.toString()) }
        return if (chars.size < 17) null else chars.takeLast(17).joinToString("")
    }

    /** WMI eşleşmesine göre üretici; eşleşme yoksa UNKNOWN. */
    fun manufacturer(vin: String?): Manufacturer {
        if (vin == null || vin.length < 3) return Manufacturer.UNKNOWN
        val head = vin.uppercase()
        return wmi.firstOrNull { head.startsWith(it.first) }?.second ?: Manufacturer.UNKNOWN
    }

    /** Seçili üreticinin araç tipine uygun olduğundan emin olmak için (garaj eşleşmesinde yumuşak kontrol). */
    fun isMotorcycle(m: Manufacturer): Boolean = m in setOf(
        Manufacturer.YAMAHA, Manufacturer.HONDA_MOTO, Manufacturer.KAWASAKI, Manufacturer.SUZUKI,
    )
}
