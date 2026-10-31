package com.soylemis.obdgaraj.transport

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow

/**
 * Araçsız test ve demo için ELM327 taklidi. Her komut için [responder] çağrılır;
 * null dönerse cevap gelmez (zaman aşımı senaryosu).
 */
class FakeTransport(
    private val responder: (String) -> String?,
    private val delayMs: Long = 0,
    private val chunkSize: Int? = null,
    override val label: String = "Demo",
) : ObdTransport {
    private val _incoming = MutableSharedFlow<ByteArray>(extraBufferCapacity = 1024)
    override val incoming: Flow<ByteArray> = _incoming
    private val pending = StringBuilder()
    val written = mutableListOf<String>()

    override suspend fun open() = Unit

    override suspend fun write(bytes: ByteArray) {
        pending.append(String(bytes, Charsets.US_ASCII))
        while (true) {
            val end = pending.indexOf("\r")
            if (end < 0) break
            val cmd = pending.substring(0, end).trim()
            pending.delete(0, end + 1)
            written += cmd
            val reply = responder(cmd) ?: continue
            if (delayMs > 0) delay(delayMs)
            val text = reply.replace('|', '\r') + "\r\r>"
            text.chunked(chunkSize ?: text.length).forEach { _incoming.emit(it.toByteArray(Charsets.US_ASCII)) }
        }
    }

    override suspend fun close() = Unit
}
