package com.soylemis.obdgaraj.transport

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.os.Build
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.withTimeout
import java.io.IOException

/** BLE (GATT) ELM327 adaptörü: bir karakteristiğe yazar, diğerinden bildirim alır. */
@SuppressLint("MissingPermission")
@Suppress("DEPRECATION")
class BleTransport(private val context: Context, private val device: BluetoothDevice) : ObdTransport {
    override val label: String = device.name ?: device.address
    private val _incoming = MutableSharedFlow<ByteArray>(extraBufferCapacity = 256)
    override val incoming: Flow<ByteArray> = _incoming

    private var gatt: BluetoothGatt? = null
    private var writeChar: BluetoothGattCharacteristic? = null
    private var chunkSize = 20
    private var step: CompletableDeferred<Int>? = null

    private val callback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(g: BluetoothGatt, status: Int, newState: Int) {
            if (newState == BluetoothProfile.STATE_CONNECTED) step?.complete(status)
            else step?.completeExceptionally(IOException("BLE bağlantısı koptu ($status)"))
        }

        override fun onServicesDiscovered(g: BluetoothGatt, status: Int) {
            step?.complete(status)
        }

        override fun onMtuChanged(g: BluetoothGatt, mtu: Int, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) chunkSize = (mtu - 3).coerceIn(20, 244)
            step?.complete(status)
        }

        override fun onDescriptorWrite(g: BluetoothGatt, d: BluetoothGattDescriptor, status: Int) {
            step?.complete(status)
        }

        override fun onCharacteristicWrite(g: BluetoothGatt, c: BluetoothGattCharacteristic, status: Int) {
            step?.complete(status)
        }

        override fun onCharacteristicChanged(g: BluetoothGatt, c: BluetoothGattCharacteristic) {
            if (Build.VERSION.SDK_INT < 33) c.value?.let { _incoming.tryEmit(it.copyOf()) }
        }

        override fun onCharacteristicChanged(g: BluetoothGatt, c: BluetoothGattCharacteristic, value: ByteArray) {
            _incoming.tryEmit(value.copyOf())
        }
    }

    /** Bir GATT işlemini başlatır ve geri çağrısını bekler (Android aynı anda tek işlem kabul eder). */
    private suspend fun await(action: () -> Boolean): Int {
        val d = CompletableDeferred<Int>().also { step = it }
        if (!action()) throw IOException("BLE işlemi başlatılamadı")
        return withTimeout(10_000) { d.await() }
    }

    override suspend fun open() {
        await { device.connectGatt(context, false, callback, BluetoothDevice.TRANSPORT_LE).also { gatt = it } != null }
        val g = gatt ?: throw IOException("BLE bağlantısı kurulamadı")
        await { g.discoverServices() }
        runCatching { await { g.requestMtu(247) } }
        val services = g.services.associate { s ->
            s.uuid to s.characteristics.map { c ->
                CharProps(
                    c.uuid,
                    canWrite = c.properties and (WRITE or WRITE_NR) != 0,
                    canNotify = c.properties and (NOTIFY or INDICATE) != 0,
                )
            }
        }
        val port = BlePortCandidates.pick(services) ?: throw IOException("Bu cihazda ELM327 servisi bulunamadı")
        val service = g.getService(port.service)
        writeChar = service.getCharacteristic(port.write)
        val notify = service.getCharacteristic(port.notify)
        g.setCharacteristicNotification(notify, true)
        val cccd = notify.getDescriptor(CCCD) ?: return
        val value = if (notify.properties and NOTIFY != 0) {
            BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
        } else {
            BluetoothGattDescriptor.ENABLE_INDICATION_VALUE
        }
        await {
            if (Build.VERSION.SDK_INT >= 33) {
                g.writeDescriptor(cccd, value) == BluetoothGatt.GATT_SUCCESS
            } else {
                cccd.value = value
                g.writeDescriptor(cccd)
            }
        }
    }

    override suspend fun write(bytes: ByteArray) {
        val g = gatt ?: throw IOException("Bağlantı açık değil")
        val c = writeChar ?: throw IOException("Yazma karakteristiği yok")
        val type = if (c.properties and WRITE != 0) {
            BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
        } else {
            BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
        }
        for (chunk in bytes.toList().chunked(chunkSize)) {
            val data = chunk.toByteArray()
            await {
                if (Build.VERSION.SDK_INT >= 33) {
                    g.writeCharacteristic(c, data, type) == BluetoothGatt.GATT_SUCCESS
                } else {
                    c.writeType = type
                    c.value = data
                    g.writeCharacteristic(c)
                }
            }
        }
    }

    override suspend fun close() {
        gatt?.let { runCatching { it.disconnect(); it.close() } }
        gatt = null
    }

    private companion object {
        val CCCD = uuid16(0x2902)
        const val WRITE = BluetoothGattCharacteristic.PROPERTY_WRITE
        const val WRITE_NR = BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE
        const val NOTIFY = BluetoothGattCharacteristic.PROPERTY_NOTIFY
        const val INDICATE = BluetoothGattCharacteristic.PROPERTY_INDICATE
    }
}
