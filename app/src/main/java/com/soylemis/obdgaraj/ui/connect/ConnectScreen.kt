package com.soylemis.obdgaraj.ui.connect

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.BluetoothSearching
import androidx.compose.material.icons.filled.Science
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.soylemis.obdgaraj.AppContainer
import com.soylemis.obdgaraj.R
import com.soylemis.obdgaraj.obd.ConnState
import com.soylemis.obdgaraj.transport.FoundAdapter
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

class ConnectViewModel(private val c: AppContainer) : ViewModel() {
    val state: StateFlow<ConnState> = c.connectionManager.state
    private val _bonded = MutableStateFlow<List<FoundAdapter>>(emptyList())
    val bonded = _bonded.asStateFlow()
    private val _ble = MutableStateFlow<List<FoundAdapter>>(emptyList())
    val ble = _ble.asStateFlow()
    private val _scanning = MutableStateFlow(false)
    val scanning = _scanning.asStateFlow()
    /** Bağlantı kaydedildi; araç ekranına geçilebilir. Değer: VIN ile eşleşen aracın adı (varsa). */
    private val _ready = MutableStateFlow<String?>(null)
    val ready = _ready.asStateFlow()
    private var requested = false
    private var scanJob: Job? = null

    val bluetoothEnabled: Boolean get() = c.scanner.isEnabled

    init {
        viewModelScope.launch {
            state.collect { s -> if (s is ConnState.Connected && requested) onConnected(s) }
        }
    }

    fun refreshBonded() {
        _bonded.value = runCatching { c.scanner.bonded() }.getOrDefault(emptyList())
    }

    fun scanBle() {
        scanJob?.cancel()
        _ble.value = emptyList()
        scanJob = viewModelScope.launch {
            _scanning.value = true
            withTimeoutOrNull(10_000) {
                c.scanner.scanBle().collect { found ->
                    _ble.update { list -> if (list.any { it.address == found.address }) list else list + found }
                }
            }
            _scanning.value = false
        }
    }

    fun connect(found: FoundAdapter) {
        scanJob?.cancel()
        requested = true
        c.demoMode = false
        c.connectionManager.connect(c.scanner.transportFor(found))
    }

    fun connectDemo() {
        requested = true
        c.demoMode = true
        c.connectionManager.connect(c.demoTransport())
    }

    private suspend fun onConnected(s: ConnState.Connected) {
        requested = false
        if (c.demoMode) {
            _ready.value = ""
            return
        }
        val repo = c.repository
        val matched = s.vin?.let { repo.matchByVin(it) }
        val id = matched?.id ?: c.currentVehicleId.value ?: return
        c.currentVehicleId.value = id
        repo.saveConnection(id, s.vin, s.init.protocol, s.supported)
        _ready.value = matched?.name ?: ""
    }

    override fun onCleared() {
        scanJob?.cancel()
    }
}

private fun requiredPermissions(): Array<String> =
    if (Build.VERSION.SDK_INT >= 31) {
        arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
    } else {
        arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
    }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConnectScreen(onBack: () -> Unit, onConnected: () -> Unit) {
    val vm = com.soylemis.obdgaraj.ui.appViewModel { ConnectViewModel(it) }
    val context = LocalContext.current
    val state by vm.state.collectAsState()
    val bonded by vm.bonded.collectAsState()
    val ble by vm.ble.collectAsState()
    val scanning by vm.scanning.collectAsState()
    val ready by vm.ready.collectAsState()

    fun granted() = requiredPermissions().all {
        ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
    }
    var hasPermission by remember { mutableStateOf(granted()) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        hasPermission = granted()
        if (hasPermission) vm.refreshBonded()
    }

    LaunchedEffect(hasPermission) { if (hasPermission) vm.refreshBonded() }
    LaunchedEffect(ready) { if (ready != null) onConnected() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.connect_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
            )
        },
    ) { pad ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(pad),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item { StatusCard(state) }
            item { Text(stringResource(R.string.connect_tip), style = MaterialTheme.typography.bodySmall) }

            if (!hasPermission) {
                item {
                    Text(stringResource(R.string.connect_permission_needed))
                    Button(onClick = { launcher.launch(requiredPermissions()) }) { Text(stringResource(R.string.connect_grant)) }
                }
            } else if (!vm.bluetoothEnabled) {
                item { Text(stringResource(R.string.connect_bt_off), color = MaterialTheme.colorScheme.error) }
            } else {
                item { SectionTitle(stringResource(R.string.connect_bonded)) }
                if (bonded.isEmpty()) {
                    item { Text(stringResource(R.string.connect_none_bonded), style = MaterialTheme.typography.bodySmall) }
                }
                items(bonded, key = { "b" + it.address }) { a ->
                    AdapterRow(Icons.Filled.Bluetooth, a.name, a.address) { vm.connect(a) }
                }
                item {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        SectionTitle(stringResource(R.string.connect_ble))
                        Spacer(Modifier.weight(1f))
                        OutlinedButton(onClick = vm::scanBle, enabled = !scanning) {
                            Text(stringResource(if (scanning) R.string.connect_scanning else R.string.connect_scan))
                        }
                    }
                }
                items(ble, key = { "l" + it.address }) { a ->
                    AdapterRow(Icons.Filled.BluetoothSearching, a.name, a.address) { vm.connect(a) }
                }
            }
            item {
                Spacer(Modifier.size(8.dp))
                AdapterRow(Icons.Filled.Science, stringResource(R.string.connect_demo), null) { vm.connectDemo() }
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 8.dp))
}

@Composable
private fun StatusCard(state: ConnState) {
    val (text, busy) = when (state) {
        ConnState.Idle -> stringResource(R.string.not_connected) to false
        is ConnState.Connecting -> state.step to true
        is ConnState.Connected -> "Bağlandı: ${state.adapterLabel} · ${state.init.protocol}" to false
        is ConnState.Failed -> state.reason to false
        ConnState.Lost -> stringResource(R.string.connection_lost) to false
    }
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            if (busy) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp))
                Spacer(Modifier.width(12.dp))
            }
            Text(
                text,
                color = if (state is ConnState.Failed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

@Composable
private fun AdapterRow(icon: ImageVector, name: String, address: String?, onClick: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null)
            Spacer(Modifier.width(12.dp))
            Column {
                Text(name, style = MaterialTheme.typography.bodyLarge)
                address?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
            }
        }
    }
}
