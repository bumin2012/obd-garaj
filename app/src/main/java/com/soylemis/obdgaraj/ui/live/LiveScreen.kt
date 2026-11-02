package com.soylemis.obdgaraj.ui.live

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.soylemis.obdgaraj.AppContainer
import com.soylemis.obdgaraj.R
import com.soylemis.obdgaraj.obd.ConnState
import com.soylemis.obdgaraj.obd.LivePoller
import com.soylemis.obdgaraj.obd.PidReading
import com.soylemis.obdgaraj.obd.PidTable
import com.soylemis.obdgaraj.ui.appViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** AndrOBD kalıbı: PID'ler anlamlı gruplarla sunulur, kullanıcı tek dokunuşla grup seçer. */
enum class PidGroup(val labelRes: Int, val pids: Set<Int>) {
    ENGINE(R.string.group_engine, setOf(0x0C, 0x05, 0x04, 0x0F, 0x11, 0x5C, 0x42, 0x45)),
    FUEL(R.string.group_fuel, setOf(0x06, 0x07, 0x08, 0x09, 0x0E, 0x2F, 0x5E, 0x10)),
    PERF(R.string.group_perf, setOf(0x0D, 0x0C, 0x11, 0x49, 0x4C, 0x43, 0x2C)),
    EMISSION(R.string.group_emission, setOf(0x44, 0x45, 0x3C, 0x21, 0x31, 0x4D, 0x4E)),
}

class LiveViewModel(private val c: AppContainer) : ViewModel() {
    /** ECU → izlenebilir PID'ler (bit maskesi PID'leri ve 01 hariç). */
    val groups: StateFlow<List<Pair<String, List<Int>>>> = c.connectionManager.state.map { s ->
        if (s !is ConnState.Connected) emptyList()
        else s.supported.toSortedMap().map { (ecu, pids) -> ecu to pids.filter { it % 0x20 != 0 && it != 0x01 }.sorted() }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** Bağlı aracın desteklediği tüm izlenebilir PID'ler. */
    val supported: StateFlow<Set<Int>> = groups.map { g -> g.flatMap { it.second }.toSet() }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptySet())

    private val _selected = MutableStateFlow<Set<Int>?>(null)
    val selected = _selected.asStateFlow()
    private val _readings = MutableStateFlow<Map<Int, PidReading>>(emptyMap())
    val readings = _readings.asStateFlow()
    private val _dropped = MutableStateFlow<Set<Int>>(emptySet())
    val dropped = _dropped.asStateFlow()

    /** Sefer kaydı açık mı ve son N örnek (grafik için PID → değerler). */
    private val _recording = MutableStateFlow(false)
    val recording = _recording.asStateFlow()
    private val _history = MutableStateFlow<Map<Int, List<Double>>>(emptyMap())
    val history = _history.asStateFlow()
    private var tripId: Long? = null

    private var poller: LivePoller? = null
    private var mirror: Job? = null

    fun start() {
        stop()
        if (c.connectionManager.state.value !is ConnState.Connected) return
        val all = groups.value.flatMap { it.second }.toSet()
        val sel = (_selected.value ?: DEFAULTS).intersect(all).ifEmpty { all }
            .also { _selected.value = it }
        val p = c.connectionManager.newPoller().also { poller = it }
        p.setPids(sel)
        p.start()
        mirror = viewModelScope.launch {
            launch { p.readings.collect { r -> _readings.value = r; appendHistory() } }
            launch { p.dropped.collect { _dropped.value = it } }
        }
    }

    fun stop() {
        poller?.stop()
        poller = null
        mirror?.cancel()
        mirror = null
        if (_recording.value) stopRecording()
    }

    fun toggle(pid: Int) {
        _selected.update { cur -> (cur ?: emptySet()).let { if (pid in it) it - pid else it + pid } }
        applySelection()
    }

    /** Grup çipi: grubun desteklenen PID'leri seçili mi? (tam kapsanıyorsa seçili görünür) */
    fun groupActive(g: PidGroup): Boolean = g.pids.intersect(supported.value).let { it.isNotEmpty() && selected.value.orEmpty().containsAll(it) }

    /** Grup çipi dokunuşu: grubu seçili duruma getirir ya da seçimden çıkarır. */
    fun toggleGroup(g: PidGroup) {
        val gp = g.pids intersect supported.value
        _selected.update { cur ->
            val s = cur ?: DEFAULTS intersect supported.value
            if (gp.isNotEmpty() && s.containsAll(gp)) s - gp else s + gp
        }
        applySelection()
    }

    private fun applySelection() {
        poller?.setPids(_selected.value.orEmpty())
        _readings.update { m -> m.filterKeys { it in _selected.value.orEmpty() } }
        _history.update { m -> m.filterKeys { it in _selected.value.orEmpty() } }
    }

    /** Seçili PID'leri 1 sn'de bir sefer tablosuna yazar (ObdGraphs kalıbı). */
    fun startRecording() {
        val vehicleId = c.currentVehicleId.value ?: return
        if (c.demoMode) return
        val state = c.connectionManager.state.value as? ConnState.Connected ?: return
        viewModelScope.launch {
            c.tripRepository.closeOpenTrips()
            tripId = c.tripRepository.startTrip(vehicleId, state.init.protocol.name, state.vin, _selected.value.orEmpty())
            _recording.value = true
        }
    }

    fun stopRecording() {
        val id = tripId ?: return
        tripId = null
        _recording.value = false
        viewModelScope.launch { c.tripRepository.endTrip(id) }
    }

    /** Her yeni okuma değerini geçmiş kuyruğuna ekler (en fazla HISTORY_POINTS). */
    private fun appendHistory() {
        if (!_recording.value) return
        val now = System.currentTimeMillis()
        if (now - lastSampleAt < SAMPLE_MS) return
        lastSampleAt = now
        val samples = _selected.value.orEmpty().mapNotNull { pid ->
            val r = _readings.value[pid] ?: return@mapNotNull null
            val def = PidTable.defs[pid] ?: return@mapNotNull null
            if (r.raw.size < def.byteCount) return@mapNotNull null
            pid to def.decode(r.raw)
        }
        if (samples.isEmpty()) return
        viewModelScope.launch {
            tripId?.let { c.tripRepository.appendSamples(it, samples) }
        }
        _history.update { cur ->
            cur + samples.map { (pid, v) ->
                pid to ((cur[pid] ?: emptyList()) + v).takeLast(HISTORY_POINTS)
            }.toMap()
        }
    }

    private var lastSampleAt = 0L

    override fun onCleared() = stop()

    private companion object {
        val DEFAULTS = setOf(0x0C, 0x0D, 0x05, 0x42, 0x11, 0x04, 0x0F, 0x5C, 0x2F, 0x0B)
        const val SAMPLE_MS = 1000L
        const val HISTORY_POINTS = 120
    }
}

@Composable
fun LiveScreen() {
    val vm = appViewModel { LiveViewModel(it) }
    val groups by vm.groups.collectAsState()
    val supported by vm.supported.collectAsState()
    val selected by vm.selected.collectAsState()
    val readings by vm.readings.collectAsState()
    val dropped by vm.dropped.collectAsState()
    val recording by vm.recording.collectAsState()
    val history by vm.history.collectAsState()
    val connected = groups.isNotEmpty()
    var listOpen by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(connected) { if (connected) vm.start() else vm.stop() }
    DisposableEffect(Unit) { onDispose { vm.stop() } }

    if (!connected) {
        Text(stringResource(R.string.not_connected), modifier = Modifier.padding(16.dp))
        return
    }
    val sel = selected.orEmpty()
    LazyColumn(modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(vertical = 8.dp)) {
        // 1) Grup çipleri — AndrOBD'nin "hazır sensör listeleri" kalıbı.
        item {
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                PidGroup.entries.forEach { g ->
                    if (g.pids.any { it in supported }) {
                        FilterChip(
                            selected = vm.groupActive(g),
                            onClick = { vm.toggleGroup(g) },
                            label = { Text(stringResource(g.labelRes)) },
                        )
                    }
                }
            }
        }
        // 2) Kayıt anahtarı
        item {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(stringResource(R.string.live_record), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                Switch(checked = recording, onCheckedChange = { if (it) vm.startRecording() else vm.stopRecording() })
            }
        }
        // 3) Seçili veriler: büyük değerli kartlar
        if (sel.isEmpty()) {
            item { Text(stringResource(R.string.live_none_selected), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(16.dp)) }
        }
        items(sel.sorted().chunked(2), key = { "row${it.joinToString()}" }) { pair ->
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                pair.forEach { pid ->
                    ValueCard(
                        modifier = Modifier.weight(1f),
                        pid = pid,
                        reading = readings[pid],
                        isDropped = pid in dropped,
                        points = history[pid],
                        onRemove = { vm.toggle(pid) },
                    )
                }
                if (pair.size == 1) Box(Modifier.weight(1f))
            }
        }
        // 4) Tüm liste — kapalı başlar, "diğer veriler" ile açılır
        item {
            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            TextButton(onClick = { listOpen = !listOpen }) {
                Text(stringResource(if (listOpen) R.string.live_list_hide else R.string.live_list_show))
            }
        }
        if (listOpen) {
            groups.forEach { (ecu, pids) ->
                item(key = "h$ecu") {
                    Text(stringResource(R.string.live_ecu, ecu), style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(16.dp, 8.dp))
                }
                items(pids, key = { "$ecu-$it" }) { pid ->
                    LiveRow(pid, readings[pid], pid in dropped, checked = pid in sel) { vm.toggle(pid) }
                }
            }
        }
    }
}

/** Seçili PID kartı: büyük değer + ad + (kayıt açıkken) sparkline; dokununca seçimden çıkarır. */
@Composable
private fun ValueCard(modifier: Modifier, pid: Int, reading: PidReading?, isDropped: Boolean, points: List<Double>?, onRemove: () -> Unit) {
    val def = PidTable.defs[pid]
    OutlinedCard(modifier = modifier.clickable(onClick = onRemove)) {
        Column(Modifier.padding(12.dp)) {
            Text(
                def?.nameTr ?: "PID %02X".format(pid),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
            )
            val value = when {
                isDropped -> stringResource(R.string.live_dropped_short)
                reading == null -> "…"
                else -> reading.text
            }
            Text(
                value,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = if (isDropped) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.primary,
            )
            if (points != null && points.size >= 2) Sparkline(points)
        }
    }
}

/** Seçili PID'in son ~2 dakikası için küçük çizgi grafik (kayıt açıkken dolar). */
@Composable
private fun Sparkline(values: List<Double>) {
    val line = MaterialTheme.colorScheme.primary
    Canvas(modifier = Modifier.fillMaxWidth().height(36.dp).padding(top = 4.dp)) {
        val lo = values.min()
        val hi = values.max()
        val span = if (hi - lo < 1e-6) 1.0 else hi - lo
        val stepX = size.width / (values.size - 1).coerceAtLeast(1)
        val path = Path()
        values.forEachIndexed { i, v ->
            val x = i * stepX
            val y = size.height - ((v - lo) / span).toFloat() * size.height
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(path, line, style = Stroke(width = 3f, cap = StrokeCap.Round))
        drawLine(line.copy(alpha = 0.15f), Offset(0f, size.height / 2), Offset(size.width, size.height / 2))
    }
}

@Composable
private fun LiveRow(pid: Int, reading: PidReading?, dropped: Boolean, checked: Boolean, onToggle: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onToggle).padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = checked, onCheckedChange = { onToggle() })
        Column(Modifier.weight(1f)) {
            Text(PidTable.name(pid), style = MaterialTheme.typography.bodyMedium)
            if (dropped) Text(stringResource(R.string.live_dropped), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
        }
        if (checked && !dropped) {
            Text(
                reading?.text ?: "…",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(end = 8.dp),
            )
        }
    }
}
