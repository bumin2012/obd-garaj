package com.soylemis.obdgaraj.ui.vehicle

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.BluetoothDisabled
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.soylemis.obdgaraj.R
import com.soylemis.obdgaraj.container
import com.soylemis.obdgaraj.obd.ConnState
import com.soylemis.obdgaraj.ui.ConnectionBanner
import com.soylemis.obdgaraj.ui.console.ConsoleScreen
import com.soylemis.obdgaraj.ui.dtc.DtcScreen
import com.soylemis.obdgaraj.ui.info.VehicleInfoScreen
import com.soylemis.obdgaraj.ui.live.LiveScreen

private data class Tab(val label: Int, val icon: ImageVector)

private val tabs = listOf(
    Tab(R.string.tab_live, Icons.Filled.Speed),
    Tab(R.string.tab_dtc, Icons.Filled.Warning),
    Tab(R.string.tab_info, Icons.Filled.Info),
    Tab(R.string.tab_console, Icons.Filled.Terminal),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VehicleScreen(onBack: () -> Unit, onReconnect: () -> Unit) {
    val c = LocalContext.current.container
    val state by c.connectionManager.state.collectAsState()
    val vehicleId by c.currentVehicleId.collectAsState()
    val title by produceState("", vehicleId) {
        value = vehicleId?.let { c.repository.get(it)?.name }.orEmpty()
    }
    var tab by rememberSaveable { mutableIntStateOf(0) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                actions = {
                    if (state is ConnState.Connected) {
                        IconButton(onClick = { c.connectionManager.disconnect() }) {
                            Icon(Icons.Filled.BluetoothDisabled, contentDescription = stringResource(R.string.disconnect))
                        }
                    }
                },
            )
        },
        bottomBar = {
            NavigationBar {
                tabs.forEachIndexed { i, t ->
                    NavigationBarItem(
                        selected = tab == i,
                        onClick = { tab = i },
                        icon = { Icon(t.icon, contentDescription = null) },
                        label = { Text(stringResource(t.label)) },
                    )
                }
            }
        },
    ) { pad ->
        Column(Modifier.fillMaxSize().padding(pad)) {
            when (state) {
                ConnState.Lost -> ConnectionBanner(stringResource(R.string.connection_lost), onReconnect)
                is ConnState.Connected -> Unit
                else -> ConnectionBanner(stringResource(R.string.not_connected), onReconnect)
            }
            when (tab) {
                0 -> LiveScreen()
                1 -> DtcScreen()
                2 -> VehicleInfoScreen()
                else -> ConsoleScreen()
            }
        }
    }
}
