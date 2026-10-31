package com.soylemis.obdgaraj.transport

import java.util.UUID

fun uuid16(short: Int): UUID = UUID.fromString("0000%04x-0000-1000-8000-00805f9b34fb".format(short))

data class CharProps(val uuid: UUID, val canWrite: Boolean, val canNotify: Boolean)

data class BlePort(val service: UUID, val write: UUID, val notify: UUID)

/** BLE ELM327 klonlarının kullandığı servis/karakteristik çiftlerini seçer. */
object BlePortCandidates {
    val known: List<BlePort> = listOf(
        BlePort(uuid16(0xFFF0), uuid16(0xFFF2), uuid16(0xFFF1)),
        BlePort(uuid16(0xFFE0), uuid16(0xFFE1), uuid16(0xFFE1)),
        BlePort(uuid16(0x18F0), uuid16(0x2AF1), uuid16(0x2AF0)),
    )

    fun pick(services: Map<UUID, List<CharProps>>): BlePort? {
        for (port in known) {
            val chars = services[port.service] ?: continue
            val w = chars.firstOrNull { it.uuid == port.write && it.canWrite }
            val n = chars.firstOrNull { it.uuid == port.notify && it.canNotify }
            if (w != null && n != null) return port
        }
        for ((service, chars) in services) {
            val n = chars.firstOrNull { it.canNotify } ?: continue
            val w = chars.firstOrNull { it.canWrite } ?: continue
            return BlePort(service, w.uuid, n.uuid)
        }
        return null
    }
}
