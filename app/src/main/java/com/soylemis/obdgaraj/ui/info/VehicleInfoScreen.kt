package com.soylemis.obdgaraj.ui.info

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.soylemis.obdgaraj.AppContainer
import com.soylemis.obdgaraj.R
import com.soylemis.obdgaraj.obd.ConnState
import com.soylemis.obdgaraj.obd.MonitorStatus
import com.soylemis.obdgaraj.obd.VehicleInfo
import com.soylemis.obdgaraj.ui.appViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.IOException

class InfoViewModel(private val c: AppContainer) : ViewModel() {
    val state: StateFlow<ConnState> = c.connectionManager.state
    private val _info = MutableStateFlow<VehicleInfo?>(null)
    val info = _info.asStateFlow()
    private val _monitors = MutableStateFlow<Map<String, MonitorStatus>>(emptyMap())
    val monitors = _monitors.asStateFlow()
    private val _loading = MutableStateFlow(false)
    val loading = _loading.asStateFlow()

    fun load() {
        val session = c.connectionManager.session ?: return
        viewModelScope.launch {
            _loading.value = true
            try {
                _info.value = session.readVehicleInfo()
                _monitors.value = session.readMonitorStatus()
            } catch (e: CancellationException) {
                throw e
            } catch (e: IOException) {
                c.connectionManager.markLost()
            } finally {
                _loading.value = false
            }
        }
    }
}

@Composable
fun VehicleInfoScreen() {
    val vm = appViewModel { InfoViewModel(it) }
    val state by vm.state.collectAsState()
    val info by vm.info.collectAsState()
    val monitors by vm.monitors.collectAsState()
    val loading by vm.loading.collectAsState()
    val connected = state as? ConnState.Connected

    LaunchedEffect(connected != null) { if (connected != null && info == null) vm.load() }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Button(onClick = vm::load, enabled = connected != null && !loading) { Text(stringResource(R.string.refresh)) }
            if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
        }
        if (connected == null) {
            item { Text(stringResource(R.string.not_connected)) }
            return@LazyColumn
        }
        item { Field(stringResource(R.string.info_vin), info?.vin ?: stringResource(R.string.info_vin_none), mono = true) }
        item { Field(stringResource(R.string.info_protocol), connected.init.protocol.name) }
        item { Field(stringResource(R.string.info_adapter), "${connected.adapterLabel} · ${connected.init.elmVersion}") }
        item { Field(stringResource(R.string.info_ecus), connected.init.ecus.joinToString(", ")) }
        info?.ecuNames?.forEach { (ecu, name) ->
            item(key = "n$ecu") { Field("${stringResource(R.string.info_ecu_name)} ($ecu)", name) }
        }
        info?.calibrationIds?.forEach { (ecu, ids) ->
            item(key = "c$ecu") { Field("${stringResource(R.string.info_cal)} ($ecu)", ids.joinToString("\n"), mono = true) }
        }
        connected.supported.toSortedMap().forEach { (ecu, pids) ->
            item(key = "p$ecu") {
                Field("${stringResource(R.string.info_supported)} ($ecu)", pids.sorted().joinToString(" ") { "%02X".format(it) }, mono = true)
            }
        }
        monitors.forEach { (ecu, s) ->
            item(key = "mh$ecu") { Text("${stringResource(R.string.info_monitors)} ($ecu)", style = MaterialTheme.typography.titleMedium) }
            items(s.monitors.filter { it.supported }, key = { "m$ecu${it.nameTr}" }) { m ->
                Row(Modifier.fillMaxWidth()) {
                    Text(m.nameTr, modifier = Modifier.weight(1f))
                    Text(
                        stringResource(if (m.complete) R.string.monitor_complete else R.string.monitor_incomplete),
                        color = if (m.complete) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
    }
}

@Composable
private fun Field(label: String, value: String, mono: Boolean = false) {
    Column {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        SelectionContainer {
            Text(value, style = MaterialTheme.typography.bodyLarge, fontFamily = if (mono) FontFamily.Monospace else null)
        }
    }
}
