package com.soylemis.obdgaraj.ui.garage

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.TwoWheeler
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import com.soylemis.obdgaraj.AppContainer
import com.soylemis.obdgaraj.R
import com.soylemis.obdgaraj.data.VehicleEntity
import com.soylemis.obdgaraj.data.VehicleKind
import com.soylemis.obdgaraj.obd.ConnState
import com.soylemis.obdgaraj.ui.appViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import androidx.lifecycle.viewModelScope
import java.text.DateFormat
import java.util.Date

class GarageViewModel(private val c: AppContainer) : ViewModel() {
    val vehicles: StateFlow<List<VehicleEntity>> =
        c.repository.observeAll().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** Aracı seçer; zaten bağlıysa doğrudan araç ekranına gidilir. */
    fun select(id: Long): Boolean {
        c.currentVehicleId.value = id
        return c.connectionManager.state.value is ConnState.Connected
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GarageScreen(onOpenConnect: () -> Unit, onOpenVehicle: () -> Unit) {
    val vm = appViewModel { GarageViewModel(it) }
    val vehicles by vm.vehicles.collectAsState()
    Scaffold(topBar = { TopAppBar(title = { Text(stringResource(R.string.garage_title)) }) }) { pad ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(pad),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { Text(stringResource(R.string.garage_hint), style = MaterialTheme.typography.bodyMedium) }
            items(vehicles, key = { it.id }) { v ->
                VehicleCard(v) { if (vm.select(v.id)) onOpenVehicle() else onOpenConnect() }
            }
        }
    }
}

@Composable
private fun VehicleCard(v: VehicleEntity, onClick: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(
                if (v.kind == VehicleKind.CAR) Icons.Filled.DirectionsCar else Icons.Filled.TwoWheeler,
                contentDescription = null,
                modifier = Modifier.size(40.dp),
            )
            Spacer(Modifier.width(16.dp))
            Column {
                Text(v.name, style = MaterialTheme.typography.titleMedium)
                Text(
                    stringResource(if (v.kind == VehicleKind.CAR) R.string.kind_car else R.string.kind_motorcycle),
                    style = MaterialTheme.typography.bodySmall,
                )
                val last = v.lastConnectedAt
                Text(
                    if (last == null) stringResource(R.string.never_connected)
                    else stringResource(R.string.last_connected, DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(last))),
                    style = MaterialTheme.typography.bodySmall,
                )
                v.lastDtcCount?.let {
                    Text(
                        stringResource(R.string.last_dtc_count, it),
                        style = MaterialTheme.typography.bodySmall,
                        color = if (it > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                v.vin?.let { Text("VIN: $it", style = MaterialTheme.typography.bodySmall) }
            }
        }
    }
}
