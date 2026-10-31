package com.soylemis.obdgaraj.ui.console

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.soylemis.obdgaraj.log.LogDirection
import com.soylemis.obdgaraj.log.LogEntry
import com.soylemis.obdgaraj.log.ObdLog
import com.soylemis.obdgaraj.obd.ConnectionManager
import com.soylemis.obdgaraj.obd.ForbiddenCommandException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class ConsoleViewModel(private val manager: ConnectionManager, private val log: ObdLog) : ViewModel() {
    val entries: StateFlow<List<LogEntry>> = log.entries

    fun send(cmd: String) {
        val text = cmd.trim()
        if (text.isEmpty()) return
        val conn = manager.connection
        if (conn == null) {
            log.record(LogDirection.ERROR, "Bağlı değil: $text")
            return
        }
        viewModelScope.launch {
            try {
                conn.execute(text)
            } catch (e: CancellationException) {
                throw e
            } catch (e: ForbiddenCommandException) {
                log.record(LogDirection.ERROR, e.message.orEmpty())
            } catch (e: Exception) {
                log.record(LogDirection.ERROR, "Hata: ${e.message}")
            }
        }
    }

    fun clear() = log.clear()

    fun exportText(): String = log.exportText()
}
