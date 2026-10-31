package com.soylemis.obdgaraj.obd

import com.soylemis.obdgaraj.log.LogDirection
import com.soylemis.obdgaraj.log.ObdLog
import com.soylemis.obdgaraj.transport.ObdTransport
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
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
            try {
                step("Adaptöre bağlanılıyor (${transport.label})")
                transport.open()
                val conn = ElmConnection(transport, log)
                val s = ObdSession(conn)
                step("Adaptör başlatılıyor, araç protokolü aranıyor")
                val init = s.initialize()
                log.record(LogDirection.INFO, "Protokol: ${init.protocol}, ECU'lar: ${init.ecus}, ${init.elmVersion}")
                step("Desteklenen veriler okunuyor")
                val supported = s.discoverSupportedPids()
                step("Araç kimliği okunuyor")
                val vin = s.readVehicleInfo().vin
                session = s
                connection = conn
                _state.value = ConnState.Connected(init, supported, vin, transport.label)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                val reason = if (e is NoVehicleResponseException) e.message.orEmpty() else "Bağlantı hatası: ${e.message}"
                log.record(LogDirection.ERROR, reason)
                runCatching { transport.close() }
                _state.value = ConnState.Failed(reason)
            }
        }
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

    private fun step(text: String) {
        log.record(LogDirection.INFO, text)
        _state.value = ConnState.Connecting(text)
    }
}
