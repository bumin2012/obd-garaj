package com.soylemis.obdgaraj.transport

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeoutOrNull
import java.io.IOException

enum class GattOp { CONNECT, DISCOVER, MTU, DESCRIPTOR, WRITE }

/**
 * Android GATT aynı anda tek işlem kabul eder. Bekleyen işlemin türü tutulur;
 * başka türden (ör. kendiliğinden gelen MTU) geri çağrılar bekleyeni bitirmez.
 */
class GattSteps {
    private class Pending(val op: GattOp, val result: CompletableDeferred<Int>)

    @Volatile private var pending: Pending? = null

    /** [start] işlemi başlatır; false dönerse işlem kabul edilmedi demektir. */
    suspend fun run(op: GattOp, timeoutMs: Long = 10_000, start: () -> Boolean): Int {
        val p = Pending(op, CompletableDeferred())
        pending = p
        try {
            if (!start()) throw IOException("BLE işlemi başlatılamadı: $op")
            return withTimeoutOrNull(timeoutMs) { p.result.await() }
                ?: throw IOException("BLE işlemi zaman aşımına uğradı: $op")
        } finally {
            if (pending === p) pending = null
        }
    }

    /** Geri çağrıdan gelir; bekleyen işlem bu türdense tamamlar ve true döner. */
    fun complete(op: GattOp, status: Int): Boolean {
        val p = pending ?: return false
        if (p.op != op) return false
        return p.result.complete(status)
    }

    fun fail(error: IOException) {
        pending?.result?.completeExceptionally(error)
    }
}
