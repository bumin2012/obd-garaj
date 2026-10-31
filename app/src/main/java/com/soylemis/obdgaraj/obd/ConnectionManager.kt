package com.soylemis.obdgaraj.obd

import com.soylemis.obdgaraj.log.LogDirection
import com.soylemis.obdgaraj.log.ObdLog
import com.soylemis.obdgaraj.transport.ObdTransport
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface ConnState {
    data object Idle : ConnState
    data class Connecting(val step: String) : ConnState
    data class Connected(
        val init: InitResult,
        val supported: Map<String, Set<Int>>,
        val vin: String?,
        val adapterLabel: String,
    ) : ConnState
    data class Failed(val reason: String) : ConnState
    data object Lost : ConnState
}

/** Adaptöre bağlanma, başlatma ve keşif akışını yönetir; uygulama boyunca tek örnek. */
class ConnectionManager(private val log: ObdLog, private val scope: CoroutineScope) {
    private val _state = MutableStateFlow<ConnState>(ConnState.Idle)
    val state: StateFlow<ConnState> = _state.asStateFlow()

    var session: ObdSession? = null
        private set
    /** Konsolun ham komut göndermesi için; komutlar yine [CommandGuard]'dan geçer. */
    var connection: ElmConnection? = null
        private set
    private var transport: ObdTransport? = null
    private var job: Job? = null

    fun connect(transport: ObdTransport) {
        disconnect()
        this.transport = transport
        job = scope.launch {
            var connected = false
            try {
                step("Adaptöre bağlanılıyor (${transport.label})")
                transport.open()
                val conn = ElmConnection(transport, log)
                val s = ObdSession(conn)
                step("Adaptör başlatılıyor, araç protokolü aranıyor")
                val init = s.initialize()
                log.record(LogDirection.INFO, "Protokol: ${init.protocol}, ECU'lar: ${init.ecus}, ${init.elmVersion}")
                if (init.skippedCommands.isNotEmpty()) {
                    log.record(LogDirection.INFO, "Adaptörün desteklemediği komutlar atlandı: ${init.skippedCommands}")
                }
                step("Desteklenen veriler okunuyor")
                val supported = s.discoverSupportedPids()
                step("Araç kimliği okunuyor")
                val vin = s.readVehicleInfo().vin
                ensureActive()
                session = s
                connection = conn
                _state.value = ConnState.Connected(init, supported, vin, transport.label)
                connected = true
            } catch (e: CancellationException) {
                // Bu iş iptal edildiyse (yeni bağlantı/disconnect) sessizce çık; değilse bir zaman aşımıdır.
                if (!isActive) throw e
                fail("Bağlantı zaman aşımına uğradı")
            } catch (e: Exception) {
                // İptal edilmiş eski bir deneme yeni bağlantının durumunu ezmemeli.
                if (isActive) fail(if (e is NoVehicleResponseException) e.message.orEmpty() else "Bağlantı hatası: ${e.message}")
            } finally {
                if (!connected) withContext(NonCancellable) { runCatching { transport.close() } }
            }
        }
    }

    private fun fail(reason: String) {
        log.record(LogDirection.ERROR, reason)
        _state.value = ConnState.Failed(reason)
    }

    fun disconnect() {
        job?.cancel()
        job = null
        val t = transport
        transport = null
        session = null
        connection = null
        if (t != null) scope.launch { runCatching { t.close() } }
        _state.value = ConnState.Idle
    }

    /** Okuma sırasında adaptör cevap vermezse çağrılır. */
    fun markLost() {
        log.record(LogDirection.ERROR, "Adaptör bağlantısı koptu")
        val t = transport
        transport = null
        session = null
        connection = null
        if (t != null) scope.launch { runCatching { t.close() } }
        _state.value = ConnState.Lost
    }

    fun newPoller(): LivePoller {
        val s = session ?: error("Bağlı değil")
        return LivePoller(s, scope, onLost = ::markLost)
    }

    private suspend fun step(text: String) {
        currentCoroutineContext().ensureActive()
        log.record(LogDirection.INFO, text)
        _state.value = ConnState.Connecting(text)
    }
}
