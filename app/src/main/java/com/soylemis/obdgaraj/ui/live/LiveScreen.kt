package com.soylemis.obdgaraj.ui.live

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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

class LiveViewModel(private val c: AppContainer) : ViewModel() {
    /** ECU → izlenebilir PID'ler (bit maskesi PID'leri ve 01 hariç). */
    val groups: StateFlow<List<Pair<String, List<Int>>>> = c.connectionManager.state.map { s ->
        if (s !is ConnState.Connected) emptyList()
        else s.supported.toSortedMap().map { (ecu, pids) -> ecu to pids.filter { it % 0x20 != 0 && it != 0x01 }.sorted() }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val _selected = MutableStateFlow<Set<Int>?>(null)
    val selected = _selected.asStateFlow()
    private val _readings = MutableStateFlow<Map<Int, PidReading>>(emptyMap())
    val readings = _readings.asStateFlow()
    private val _dropped = MutableStateFlow<Set<Int>>(emptySet())
    val dropped = _dropped.asStateFlow()

    private var poller: LivePoller? = null
    private var mirror: Job? = null

    fun start() {
        stop()
        if (c.connectionManager.state.value !is ConnState.Connected) return
        val all = groups.value.flatMap { it.second }.toSet()
        val sel = _selected.value ?: (DEFAULTS intersect all).also { _selected.value = it }
        val p = c.connectionManager.newPoller().also { poller = it }
        p.setPids(sel)
        p.start()
        mirror = viewModelScope.launch {
            launch { p.readings.collect { _readings.value = it } }
            launch { p.dropped.collect { _dropped.value = it } }
        }
    }

    fun stop() {
        poller?.stop()
        poller = null
        mirror?.cancel()
        mirror = null
    }

    fun toggle(pid: Int) {
        _selected.update { cur -> (cur ?: emptySet()).let { if (pid in it) it - pid else it + pid } }
        poller?.setPids(_selected.value.orEmpty())
    }

    override fun onCleared() = stop()

    private companion object {
        val DEFAULTS = setOf(0x0C, 0x0D, 0x05, 0x42, 0x11, 0x04, 0x0F, 0x5C, 0x2F, 0x0B)
    }
}

@Composable
fun LiveScreen() {
    val vm = appViewModel { LiveViewModel(it) }
    val groups by vm.groups.collectAsState()
    val selected by vm.selected.collectAsState()
    val readings by vm.readings.collectAsState()
    val dropped by vm.dropped.collectAsState()
    val connected = groups.isNotEmpty()

    LaunchedEffect(connected) { if (connected) vm.start() else vm.stop() }
    DisposableEffect(Unit) { onDispose { vm.stop() } }

    if (!connected) {
        Text(stringResource(R.string.not_connected), modifier = Modifier.padding(16.dp))
        return
    }
    val sel = selected.orEmpty()
    LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(vertical = 8.dp)) {
        item {
            Text(stringResource(R.string.live_hint), style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(horizontal = 16.dp))
        }
        // Önce seçilenler (canlı değerleriyle), sonra ECU'ya göre tüm liste.
        items(sel.sorted(), key = { "s$it" }) { pid ->
            LiveRow(pid, readings[pid], pid in dropped, checked = true) { vm.toggle(pid) }
        }
        groups.forEach { (ecu, pids) ->
            item(key = "h$ecu") {
                HorizontalDivider()
                Text(stringResource(R.string.live_ecu, ecu), style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(16.dp, 8.dp))
            }
            items(pids.filter { it !in sel }, key = { "$ecu-$it" }) { pid ->
                LiveRow(pid, null, pid in dropped, checked = false) { vm.toggle(pid) }
            }
        }
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
