package com.soylemis.obdgaraj.ui.dtc

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.soylemis.obdgaraj.AppContainer
import com.soylemis.obdgaraj.R
import com.soylemis.obdgaraj.data.DtcDescription
import com.soylemis.obdgaraj.obd.ConnState
import com.soylemis.obdgaraj.obd.Dtc
import com.soylemis.obdgaraj.obd.DtcKind
import com.soylemis.obdgaraj.obd.MonitorStatus
import com.soylemis.obdgaraj.obd.PidReading
import com.soylemis.obdgaraj.obd.PidTable
import com.soylemis.obdgaraj.ui.appViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.IOException

data class DtcUiState(
    val loading: Boolean = false,
    val loaded: Boolean = false,
    val status: Map<String, MonitorStatus> = emptyMap(),
    val dtcs: Map<DtcKind, List<Dtc>> = emptyMap(),
    val freeze: List<PidReading> = emptyList(),
    val message: String? = null,
)

class DtcViewModel(private val c: AppContainer) : ViewModel() {
    private val _ui = MutableStateFlow(DtcUiState())
    val ui = _ui.asStateFlow()
    val clearFlow = DtcClearFlow()

    fun describe(code: String): DtcDescription = c.dtcDictionary.describe(code)

    fun read() {
        val session = c.connectionManager.session ?: return
        val state = c.connectionManager.state.value as? ConnState.Connected ?: return
        viewModelScope.launch {
            _ui.update { it.copy(loading = true, message = null) }
            try {
                val status = session.readMonitorStatus()
                val dtcs = DtcKind.entries.associateWith { session.readDtcs(it) }
                val freezePids = state.supported.values.flatten().toSet() intersect PidTable.defs.keys
                val freeze = session.readFreezeFrame(freezePids)
                _ui.value = DtcUiState(loaded = true, status = status, dtcs = dtcs, freeze = freeze)
                if (!c.demoMode) {
                    c.currentVehicleId.value?.let { id -> c.repository.recordDtcs(id, dtcs.values.flatten()) }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: IOException) {
                c.connectionManager.markLost()
                _ui.update { it.copy(loading = false) }
            }
        }
    }

    fun confirmClear(okText: String, failText: String) {
        val token = clearFlow.confirm() ?: return
        val session = c.connectionManager.session ?: return clearFlow.reset()
        viewModelScope.launch {
            val ok = try {
                session.clearDtcs(token)
            } catch (e: CancellationException) {
                throw e
            } catch (e: IOException) {
                c.connectionManager.markLost()
                false
            } finally {
                clearFlow.reset()
            }
            read()
            _ui.update { it.copy(message = if (ok) okText else failText) }
        }
    }
}

@Composable
fun DtcScreen() {
    val vm = appViewModel { DtcViewModel(it) }
    val ui by vm.ui.collectAsState()
    val clearStep by vm.clearFlow.step.collectAsState()
    val okText = stringResource(R.string.dtc_cleared)
    val failText = stringResource(R.string.dtc_clear_failed)

    LaunchedEffect(Unit) { if (!ui.loaded) vm.read() }

    if (clearStep == ClearStep.WARNING_SHOWN) {
        AlertDialog(
            onDismissRequest = vm.clearFlow::cancel,
            title = { Text(stringResource(R.string.dtc_clear_title)) },
            text = { Text(stringResource(R.string.dtc_clear_body)) },
            confirmButton = {
                Button(
                    onClick = { vm.confirmClear(okText, failText) },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                ) { Text(stringResource(R.string.dtc_clear_confirm)) }
            },
            dismissButton = { TextButton(onClick = vm.clearFlow::cancel) { Text(stringResource(R.string.cancel)) } },
        )
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = vm::read, enabled = !ui.loading) { Text(stringResource(R.string.dtc_read)) }
                val anyCodes = ui.dtcs.values.any { it.isNotEmpty() }
                OutlinedButton(onClick = vm.clearFlow::request, enabled = ui.loaded && !ui.loading && anyCodes) {
                    Text(stringResource(R.string.dtc_clear))
                }
            }
            if (ui.loading) LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 8.dp))
            ui.message?.let { Text(it, modifier = Modifier.padding(top = 8.dp)) }
        }
        ui.status.forEach { (ecu, s) ->
            item(key = "mil$ecu") { MilCard(ecu, s) }
        }
        listOf(
            DtcKind.STORED to R.string.dtc_stored,
            DtcKind.PENDING to R.string.dtc_pending,
            DtcKind.PERMANENT to R.string.dtc_permanent,
        ).forEach { (kind, label) ->
            if (ui.loaded) {
                item(key = "t$kind") { Text(stringResource(label), style = MaterialTheme.typography.titleMedium) }
                val list = ui.dtcs[kind].orEmpty()
                if (list.isEmpty()) item(key = "e$kind") { Text(stringResource(R.string.dtc_none), style = MaterialTheme.typography.bodySmall) }
                items(list, key = { "$kind${it.ecu}${it.code}" }) { DtcCard(it, vm.describe(it.code)) }
            }
        }
        if (ui.freeze.isNotEmpty()) {
            item { Text(stringResource(R.string.dtc_freeze), style = MaterialTheme.typography.titleMedium) }
            items(ui.freeze, key = { "f${it.ecu}${it.pid}" }) { r ->
                if (r.pid == 0x02) Text(stringResource(R.string.dtc_freeze_trigger, r.text), fontWeight = FontWeight.SemiBold)
                else Row(Modifier.fillMaxWidth()) {
                    Text(PidTable.name(r.pid), modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                    Text(r.text, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}

@Composable
private fun MilCard(ecu: String, s: MonitorStatus) {
    val colors = if (s.milOn) CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer) else CardDefaults.cardColors()
    Card(colors = colors, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text(stringResource(if (s.milOn) R.string.dtc_mil_on else R.string.dtc_mil_off), style = MaterialTheme.typography.titleMedium)
            Text("ECU $ecu · " + stringResource(R.string.dtc_count_reported, s.dtcCount), style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun DtcCard(dtc: Dtc, description: DtcDescription) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            Text(dtc.code, style = MaterialTheme.typography.titleLarge, fontFamily = FontFamily.Monospace)
            Text(
                when (description) {
                    is DtcDescription.Known -> description.text
                    DtcDescription.ManufacturerSpecific -> stringResource(R.string.dtc_manufacturer)
                    DtcDescription.Unknown -> stringResource(R.string.dtc_unknown)
                },
                style = MaterialTheme.typography.bodyMedium,
            )
            Text("ECU ${dtc.ecu}", style = MaterialTheme.typography.bodySmall)
        }
    }
}
