package com.soylemis.obdgaraj.transport

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import java.util.UUID

/** Klasik Bluetooth (SPP/RFCOMM) ELM327 adaptörü. */
@SuppressLint("MissingPermission")
class ClassicSppTransport(private val device: BluetoothDevice) : ObdTransport {
    override val label: String = device.name ?: device.address
    private val _incoming = MutableSharedFlow<ByteArray>(extraBufferCapacity = 256)
    override val incoming: Flow<ByteArray> = _incoming
    private var socket: BluetoothSocket? = null
    private var scope: CoroutineScope? = null

    @Suppress("DEPRECATION")
    override suspend fun open() = withContext(Dispatchers.IO) {
        BluetoothAdapter.getDefaultAdapter()?.cancelDiscovery()
        val s = try {
            device.createRfcommSocketToServiceRecord(SPP).also { it.connect() }
        } catch (e: IOException) {
            device.createInsecureRfcommSocketToServiceRecord(SPP).also { it.connect() }
        }
        socket = s
        val readScope = CoroutineScope(SupervisorJob() + Dispatchers.IO).also { scope = it }
        readScope.launch {
            val buf = ByteArray(1024)
            val input = s.inputStream
            while (isActive) {
                val n = try { input.read(buf) } catch (e: IOException) { -1 }
                if (n < 0) break
                if (n > 0) _incoming.emit(buf.copyOf(n))
            }
        }
        Unit
    }

    override suspend fun write(bytes: ByteArray) = withContext(Dispatchers.IO) {
        val out = socket?.outputStream ?: throw IOException("Bağlantı açık değil")
        out.write(bytes)
        out.flush()
    }

    override suspend fun close() {
        scope?.cancel()
        runCatching { socket?.close() }
        socket = null
    }

    private companion object {
        val SPP: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
    }
}
