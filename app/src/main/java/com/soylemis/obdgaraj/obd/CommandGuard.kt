package com.soylemis.obdgaraj.obd

/**
 * Kanıt nesnesi: kullanıcı kod silme uyarısını onayladı.
 * Yalnızca onay akışı (ve testler) üretebilir.
 */
class ClearConfirmation internal constructor() {
    companion object {
        internal fun forTest() = ClearConfirmation()
    }
}

class ForbiddenCommandException(val cmd: String) :
    IllegalArgumentException("Bu komut araca gönderilemez: '${cmd.replace("\r", "\\r").replace("\n", "\\n")}'")

/**
 * Araca yalnızca okuma komutlarının gitmesini sağlayan izin listesi.
 *
 * OBD komutları servis baytına göre süzülür; bu yalnızca ELM327 ISO-TP çerçevesini kendisi
 * eklerken (ATCAF1, varsayılan başlıklar) geçerlidir. Bu yüzden çerçevelemeyi, adreslemeyi
 * ya da adaptör belleğini değiştiren AT komutları ve kontrol karakterleri reddedilir.
 */
object CommandGuard {
    private val readServices = setOf("01", "02", "03", "07", "09", "0A")
    private const val CLEAR_SERVICE = "04"
    /**
     * Mode 22 (üretici-özel okuma) servis bazında serbesttir; hangi PID'lere izin
     * verildiğini ObdSession, ExtendedPids beyaz listesiyle denetler. UDS 0x2E gibi
     * yazma servisleri değildir — yalnız okuma istemidir.
     */
    private const val EXTENDED_READ_SERVICE = "22"

    /** Yalnızca harf, rakam, boşluk ve '@'; satır sonu gibi kontrol karakterleri yok. */
    private val allowedChars = Regex("^[0-9A-Za-z @]+$")
    private val hex = Regex("^[0-9A-F]+$")

    private val allowedAt = Regex(
        "^(Z|I|E[01]|L[01]|S[01]|H[01]|SPA?[0-9A-C]|TPA?[0-9A-C]|AT[012]|DPN?|RV|ST[0-9A-F]{1,2}|PC|@1|CS)$",
    )

    fun check(cmd: String, confirmation: ClearConfirmation? = null) {
        if (!allowedChars.matches(cmd)) throw ForbiddenCommandException(cmd)
        val normalized = normalize(cmd)
        if (normalized.startsWith("AT")) {
            if (allowedAt.matches(normalized.substring(2))) return
            throw ForbiddenCommandException(cmd)
        }
        if (normalized.length < 2 || normalized.length % 2 != 0 || !hex.matches(normalized)) {
            throw ForbiddenCommandException(cmd)
        }
        val service = normalized.substring(0, 2)
        when {
            service in readServices -> return
            service == EXTENDED_READ_SERVICE && normalized.length >= 4 -> return
            service == CLEAR_SERVICE && normalized.length == 2 && confirmation != null -> return
            else -> throw ForbiddenCommandException(cmd)
        }
    }

    fun normalize(cmd: String): String = cmd.filterNot { it.isWhitespace() }.uppercase()
}
