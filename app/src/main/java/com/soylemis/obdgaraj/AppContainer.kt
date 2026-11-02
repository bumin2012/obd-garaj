package com.soylemis.obdgaraj

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import com.soylemis.obdgaraj.data.AppDatabase
import com.soylemis.obdgaraj.data.DtcDictionary
import com.soylemis.obdgaraj.data.TripRepository
import com.soylemis.obdgaraj.data.VehicleRepository
import com.soylemis.obdgaraj.log.ObdLog
import com.soylemis.obdgaraj.obd.ConnectionManager
import com.soylemis.obdgaraj.obd.PidTable
import com.soylemis.obdgaraj.transport.AdapterScanner
import com.soylemis.obdgaraj.transport.FakeTransport
import com.soylemis.obdgaraj.transport.ObdTransport
import com.soylemis.obdgaraj.transport.scriptResponder
import com.soylemis.obdgaraj.log.LogDirection
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

/** Uygulama boyunca yaşayan bağımlılıklar (elle DI). */
class AppContainer(private val context: Context) {
    val log = ObdLog()
    val appScope = CoroutineScope(
        SupervisorJob() + Dispatchers.Default +
            CoroutineExceptionHandler { _, e -> log.record(LogDirection.ERROR, "Beklenmeyen hata: $e") },
    )
    val connectionManager = ConnectionManager(log, appScope)
    val scanner = AdapterScanner(context)
    private val db by lazy { AppDatabase.create(context) }
    val repository by lazy { VehicleRepository(db.vehicleDao()) }
    val tripRepository by lazy { TripRepository(db.tripDao()) }
    val dtcDictionary by lazy { DtcDictionary.fromAssets(context) }
    private val prefs: SharedPreferences by lazy { context.getSharedPreferences("settings", Context.MODE_PRIVATE) }

    /** Garajda seçilen ya da VIN ile eşleşen araç. */
    val currentVehicleId = MutableStateFlow<Long?>(null)

    /** Demo adaptörle bağlıyken araç profiline ve arıza geçmişine yazılmaz. */
    @Volatile var demoMode: Boolean = false

    /** Kullanıcı PID eklentisi (pids.json metni); bozuk JSON yüklenmez. */
    var pidJson: String?
        get() = prefs.getString(KEY_PIDS, null) ?: context.assets.open("pids_custom.json")
            .bufferedReader().use { it.readText() }.takeIf { it.isNotBlank() && it != "[]" }
        set(value) { prefs.edit().putString(KEY_PIDS, value).apply() }

    init {
        appScope.launch { repository.seedIfEmpty() }
        PidTable.install(pidJson)
    }

    fun demoTransport(): ObdTransport {
        val script = context.assets.open("demo/c4_can.txt").bufferedReader().use { it.readText() }
        return FakeTransport(scriptResponder(script), delayMs = 40, label = "Demo (Citroën C4 betiği)")
    }

    private companion object { const val KEY_PIDS = "pids_json" }
}

class ObdApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}

val Context.container: AppContainer get() = (applicationContext as ObdApp).container
