package com.soylemis.obdgaraj.transport

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.Context
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.emptyFlow

enum class AdapterType { CLASSIC, BLE }

data class FoundAdapter(val name: String, val address: String, val type: AdapterType)

/** Eşleşmiş adaptörleri listeler ve yakındaki BLE adaptörleri tarar. */
@SuppressLint("MissingPermission")
class AdapterScanner(private val context: Context) {
    private val adapter = context.getSystemService(BluetoothManager::class.java)?.adapter

    val isAvailable: Boolean get() = adapter != null
    val isEnabled: Boolean get() = adapter?.isEnabled == true

    fun bonded(): List<FoundAdapter> = adapter?.bondedDevices.orEmpty().map { d ->
        val type = if (d.type == BluetoothDevice.DEVICE_TYPE_LE) AdapterType.BLE else AdapterType.CLASSIC
        FoundAdapter(d.name ?: d.address, d.address, type)
    }.sortedBy { it.name }

    fun scanBle(): Flow<FoundAdapter> {
        val scanner = adapter?.bluetoothLeScanner ?: return emptyFlow()
        return callbackFlow {
            val cb = object : ScanCallback() {
                override fun onScanResult(callbackType: Int, result: ScanResult) {
                    val name = result.scanRecord?.deviceName ?: result.device.name ?: return
                    trySend(FoundAdapter(name, result.device.address, AdapterType.BLE))
                }
            }
            scanner.startScan(cb)
            awaitClose { runCatching { scanner.stopScan(cb) } }
        }
    }

    fun transportFor(found: FoundAdapter): ObdTransport {
        val device = adapter?.getRemoteDevice(found.address) ?: error("Bluetooth yok")
        return when (found.type) {
            AdapterType.CLASSIC -> ClassicSppTransport(device)
            AdapterType.BLE -> BleTransport(context, device)
        }
    }
}
