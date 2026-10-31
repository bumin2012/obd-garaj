package com.soylemis.obdgaraj.transport

import kotlinx.coroutines.flow.Flow

/**
 * Adaptörle ham bayt alışverişi. [incoming] sıcak bir akıştır: dinleyen yokken
 * gelen baytlar kaybolabilir, bu yüzden okuyucu yazmadan önce abone olmalıdır.
 */
interface ObdTransport {
    val label: String
    val incoming: Flow<ByteArray>
    suspend fun open()
    suspend fun write(bytes: ByteArray)
    suspend fun close()
}
