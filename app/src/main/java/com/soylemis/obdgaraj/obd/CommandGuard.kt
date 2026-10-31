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
    IllegalArgumentException("Bu komut araca gönderilemez: '$cmd'")

/** Araca yalnızca okuma komutlarının gitmesini sağlayan izin listesi. */
object CommandGuard {
    private val readServices = setOf("01", "02", "03", "07", "09", "0A")
    private const val CLEAR_SERVICE = "04"

    fun check(cmd: String, confirmation: ClearConfirmation? = null) {
        val normalized = normalize(cmd)
        if (normalized.startsWith("AT")) return
        if (normalized.length < 2) throw ForbiddenCommandException(cmd)
        val service = normalized.substring(0, 2)
        when {
            service in readServices -> return
            service == CLEAR_SERVICE && normalized.length == 2 && confirmation != null -> return
            else -> throw ForbiddenCommandException(cmd)
        }
    }

    fun normalize(cmd: String): String = cmd.filterNot { it.isWhitespace() }.uppercase()
}
