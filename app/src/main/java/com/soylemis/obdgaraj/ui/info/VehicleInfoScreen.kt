package com.soylemis.obdgaraj.ui.info

import android.content.Context
import android.content.Intent
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
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.soylemis.obdgaraj.AppContainer
import com.soylemis.obdgaraj.R
import com.soylemis.obdgaraj.data.TripCsv
import com.soylemis.obdgaraj.log.LogDirection
import com.soylemis.obdgaraj.obd.ConnState
import com.soylemis.obdgaraj.obd.ExtendedPids
import com.soylemis.obdgaraj.obd.Manufacturer
import com.soylemis.obdgaraj.obd.MonitorStatus
import com.soylemis.obdgaraj.obd.PidReading
import com.soylemis.obdgaraj.obd.VehicleInfo
import com.soylemis.obdgaraj.obd.VinDecoder
import com.soylemis.obdgaraj.obd.PidTable
import com.soylemis.obdgaraj.ui.appViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class InfoViewModel(private val c: AppContainer) : ViewModel() {
    val state: StateFlow<ConnState> = c.connectionManager.state
    private val _info = MutableStateFlow<VehicleInfo?>(null)
    val info = _info.asStateFlow()
    private val _monitors = MutableStateFlow<Map<String, MonitorStatus>>(emptyMap())
    val monitors = _monitors.asStateFlow()
    private val _extended = MutableStateFlow<List<PidReading>?>(null)
    val extended = _extended.asStateFlow()
    private val _loading = MutableStateFlow(false)
    val loading = _loading.asStateFlow()
    private val _exported = MutableStateFlow<String?>(null)
    val exported = _exported.asStateFlow()

    val manufacturer: Manufacturer get() = VinDecoder.manufacturer((_info.value?.vin) ?: (state.value as? ConnState.Connected)?.vin)

    fun load() {
        val session = c.connectionManager.session ?: return
        viewModelScope.launch {
            _loading.value = true
            try {
                val vin = session.readVehicleInfo()
                _info.value = vin
                _monitors.value = session.readMonitorStatus()
                // Üretici-özel PID'ler yalnız CAN + bilinen üreticide; hata sessizce boş liste.
                val m = VinDecoder.manufacturer(vin.vin)
                _extended.value = if (ExtendedPids.isSupported(m)) session.readExtendedPids(m) else emptyList()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (e is IOException) c.connectionManager.markLost() else c.log.record(LogDirection.ERROR, "Hata: $e")
            } finally {
                _loading.value = false
            }
        }
    }

    /** Son sefin CSV'sini önbelleğe yazır, paylaşım niyetini döner (UI başlatır). */
    fun exportLastTripCsv(context: Context): String? {
        val vehicleId = c.currentVehicleId.value ?: return null
        viewModelScope.launch {
            val trip = c.tripRepository.lastTrip(vehicleId) ?: return@launch
            val samples = c.tripRepository.samplesOf(trip.id)
            val text = TripCsv.render(trip, samples, pidOf = { pid ->
                PidTable.defs[pid]?.let { if (it.unit.isEmpty()) it.nameTr else "${it.nameTr} [${it.unit}]" } ?: "PID %02X".format(pid)
            })
            val dir = File(context.cacheDir, "logs").apply { mkdirs() }
            val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.ROOT).format(Date(trip.startedAt))
            val file = File(dir, "trip-$stamp.csv").apply { writeText(text) }
            _exported.value = context.packageName.let { "${it}.files" } + "|" + file.absolutePath
        }
        return null // asenkron; sonuç [exported] akışında
    }

    fun shareCsv(context: Context, spec: String) {
        val (authority, path) = spec.split("|", limit = 2)
        val uri = FileProvider.getUriForFile(context, authority, File(path))
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/comma-separated-values"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, null))
    }
}

@Composable
fun VehicleInfoScreen() {
    val vm = appViewModel { InfoViewModel(it) }
    val state by vm.state.collectAsState()
    val info by vm.info.collectAsState()
    val monitors by vm.monitors.collectAsState()
    val extended by vm.extended.collectAsState()
    val loading by vm.loading.collectAsState()
    val exported by vm.exported.collectAsState()
    val connected = state as? ConnState.Connected
    val context = LocalContext.current
    var shared by remember { mutableStateOf(false) }

    LaunchedEffect(connected != null) { if (connected != null && info == null) vm.load() }
    LaunchedEffect(exported) { if (exported != null && !shared) { vm.shareCsv(context, exported!!); shared = true } }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { shared = false; vm.load() }, enabled = connected != null && !loading) {
                    Text(stringResource(R.string.refresh))
                }
                OutlinedButton(onClick = { vm.exportLastTripCsv(context) }) {
                    Text(stringResource(R.string.info_export_csv))
                }
            }
            if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
        }
        if (connected == null) {
            item { Text(stringResource(R.string.not_connected)) }
            return@LazyColumn
        }
        item { Field(stringResource(R.string.info_vin), info?.vin ?: stringResource(R.string.info_vin_none), mono = true) }
        item { Field(stringResource(R.string.info_manufacturer), vm.manufacturer.label) }
        item { Field(stringResource(R.string.info_protocol), connected.init.protocol.name) }
        item { Field(stringResource(R.string.info_adapter), "${connected.adapterLabel} · ${connected.init.elmVersion}") }
        item { Field(stringResource(R.string.info_ecus), connected.init.ecus.joinToString(", ")) }
        extended?.let { list ->
            if (list.isNotEmpty()) {
                item { Text(stringResource(R.string.info_extended), style = MaterialTheme.typography.titleMedium) }
                items(list, key = { "x${it.ecu}${it.pid}" }) {
                    Row(Modifier.fillMaxWidth()) {
                        Text(ExtendedPids.forManufacturer(vm.manufacturer).firstOrNull { x -> x.pid == it.pid }?.nameTr
                            ?: "PID %04X".format(it.pid), modifier = Modifier.weight(1f))
                        Text(it.text)
                    }
                }
                item { Text(stringResource(R.string.info_extended_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline) }
            }
        }
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
