package com.soylemis.obdgaraj.obd

import com.soylemis.obdgaraj.log.LogDirection
import com.soylemis.obdgaraj.log.ObdLog
import com.soylemis.obdgaraj.transport.ObdTransport
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout
import java.io.IOException

class ElmTimeoutException(val cmd: String) : IOException("Adaptör '$cmd' komutuna zamanında cevap vermedi")

/** ELM327 ile komut-cevap konuşması: tek seferde tek komut, '>' istemine kadar okur. */
class ElmConnection(private val transport: ObdTransport, private val log: ObdLog) {
    private val mutex = Mutex()

    suspend fun execute(
        cmd: String,
        timeoutMs: Long = 3000,
        confirmation: ClearConfirmation? = null,
    ): String = mutex.withLock {
        CommandGuard.check(cmd, confirmation)
        coroutineScope {
            val buffer = StringBuilder()
            val reader = async(start = CoroutineStart.UNDISPATCHED) {
                transport.incoming.first { chunk ->
                    buffer.append(String(chunk, Charsets.US_ASCII))
                    buffer.contains('>')
                }
            }
            log.record(LogDirection.TX, cmd)
            try {
                transport.write("$cmd\r".toByteArray(Charsets.US_ASCII))
                withTimeout(timeoutMs) { reader.await() }
            } catch (e: TimeoutCancellationException) {
                reader.cancel()
                log.record(LogDirection.ERROR, "Zaman aşımı: $cmd")
                throw ElmTimeoutException(cmd)
            }
            val text = clean(buffer.substring(0, buffer.indexOf(">")), cmd)
            log.record(LogDirection.RX, text)
            text
        }
    }

    /** İstem ve (ATE0 öncesi) yankıyı atar, satırları '\r' ile birleştirir. */
    private fun clean(raw: String, cmd: String): String {
        val lines = raw.split('\r', '\n').map { it.trim() }.filter { it.isNotEmpty() }
        val noEcho = if (lines.firstOrNull()?.replace(" ", "")?.equals(cmd.replace(" ", ""), true) == true) lines.drop(1) else lines
        return noEcho.joinToString("\r")
    }
}
