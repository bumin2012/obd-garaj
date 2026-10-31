package com.soylemis.obdgaraj.obd

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.IOException

/**
 * Seçili PID'leri sırayla sorgular. Arka arkaya [NO_DATA_LIMIT] kez NO DATA dönen PID
 * döngüden çıkarılır; anlık veri yolu hataları sayılmaz. Adaptör cevap vermezse döngü
 * durur ve [onLost] çağrılır.
 */
class LivePoller(
    private val session: ObdSession,
    private val scope: CoroutineScope,
    private val onLost: () -> Unit = {},
) {
    private val _readings = MutableStateFlow<Map<Int, PidReading>>(emptyMap())
    val readings: StateFlow<Map<Int, PidReading>> = _readings.asStateFlow()
    private val _dropped = MutableStateFlow<Set<Int>>(emptySet())
    val dropped: StateFlow<Set<Int>> = _dropped.asStateFlow()

    @Volatile private var pids: Set<Int> = emptySet()
    private val noDataCount = mutableMapOf<Int, Int>()
    private var job: Job? = null

    val isRunning: Boolean get() = job?.isActive == true

    fun setPids(pids: Set<Int>) {
        this.pids = pids
    }

    fun start() {
        if (isRunning) return
        job = scope.launch {
            while (isActive) {
                val active = pids - _dropped.value
                for (pid in active.sorted()) {
                    val result = try {
                        session.readPidResult(pid)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: IOException) {
                        onLost()
                        return@launch
                    }
                    when (result) {
                        is PidResult.Values -> {
                            noDataCount[pid] = 0
                            _readings.update { it + (pid to result.readings.first()) }
                        }
                        PidResult.NoData -> {
                            val n = (noDataCount[pid] ?: 0) + 1
                            noDataCount[pid] = n
                            if (n >= NO_DATA_LIMIT) _dropped.update { it + pid }
                        }
                        is PidResult.Failed -> Unit
                    }
                }
                delay(CYCLE_PAUSE_MS)
            }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
    }

    private companion object {
        const val NO_DATA_LIMIT = 2
        const val CYCLE_PAUSE_MS = 100L
    }
}
