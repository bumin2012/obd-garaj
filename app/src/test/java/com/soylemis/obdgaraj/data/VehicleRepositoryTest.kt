package com.soylemis.obdgaraj.data

import com.soylemis.obdgaraj.obd.Dtc
import com.soylemis.obdgaraj.obd.DtcKind
import com.soylemis.obdgaraj.obd.ObdProtocol
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FakeVehicleDao : VehicleDao {
    val vehicles = MutableStateFlow<List<VehicleEntity>>(emptyList())
    val dtcs = MutableStateFlow<List<DtcHistoryEntity>>(emptyList())

    override fun observeAll(): Flow<List<VehicleEntity>> = vehicles
    override suspend fun count(): Int = vehicles.value.size
    override suspend fun insertAll(list: List<VehicleEntity>) {
        var next = (vehicles.value.maxOfOrNull { it.id } ?: 0) + 1
        vehicles.value = vehicles.value + list.map { it.copy(id = next++) }
    }
    override suspend fun get(id: Long): VehicleEntity? = vehicles.value.firstOrNull { it.id == id }
    override suspend fun findByVin(vin: String): VehicleEntity? = vehicles.value.firstOrNull { it.vin == vin }
    override suspend fun update(v: VehicleEntity) {
        vehicles.value = vehicles.value.map { if (it.id == v.id) v else it }
    }
    override suspend fun insertDtcs(list: List<DtcHistoryEntity>) {
        dtcs.value = dtcs.value + list
    }
    override fun observeDtcs(vehicleId: Long): Flow<List<DtcHistoryEntity>> =
        dtcs.map { all -> all.filter { it.vehicleId == vehicleId } }
}

class VehicleRepositoryTest {
    private val dao = FakeVehicleDao()
    private val repo = VehicleRepository(dao, clock = { 1000L })

    @Test
    fun seedCreatesThreeVehicles() = runTest {
        repo.seedIfEmpty()
        assertEquals(
            listOf(
                "Citroën C4 (2022)" to VehicleKind.CAR,
                "Yamaha MT-125 (2025)" to VehicleKind.MOTORCYCLE,
                "Peugeot XP400 (2024)" to VehicleKind.MOTORCYCLE,
            ),
            repo.observeAll().first().map { it.name to it.kind },
        )
    }

    @Test
    fun seedIsIdempotent() = runTest {
        repo.seedIfEmpty()
        repo.seedIfEmpty()
        assertEquals(3, repo.observeAll().first().size)
    }

    @Test
    fun matchByVinAfterSave() = runTest {
        repo.seedIfEmpty()
        assertNull(repo.matchByVin("VF7AAAAAAAAAAAAAA"))
        repo.saveConnection(1, "VF7AAAAAAAAAAAAAA", ObdProtocol.CAN_11_500, mapOf("7E8" to setOf(0x0C)))
        val v = repo.matchByVin("VF7AAAAAAAAAAAAAA")!!
        assertEquals(1L, v.id)
        assertEquals("CAN_11_500", v.protocol)
        assertEquals(1000L, v.lastConnectedAt)
    }

    @Test
    fun supportedPidsRoundTrip() {
        val map = mapOf("7E8" to setOf(0x01, 0x05, 0x0C), "7E9" to setOf(0x20))
        assertEquals(map, VehicleRepository.decodeSupported(VehicleRepository.encodeSupported(map)))
        assertEquals("7E8:01,05,0C;7E9:20", VehicleRepository.encodeSupported(map))
        assertEquals(emptyMap<String, Set<Int>>(), VehicleRepository.decodeSupported(""))
    }

    @Test
    fun recordDtcsStoresHistoryAndCount() = runTest {
        repo.seedIfEmpty()
        repo.recordDtcs(2, listOf(Dtc("P0133", "7E8", DtcKind.STORED), Dtc("P0420", "7E8", DtcKind.PENDING)))
        assertEquals(2, dao.dtcs.value.size)
        assertEquals(2, dao.get(2)!!.lastDtcCount)
    }
}
