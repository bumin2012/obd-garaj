package com.soylemis.obdgaraj.log

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

enum class LogDirection { TX, RX, INFO, ERROR }

data class LogEntry(val timeMillis: Long, val direction: LogDirection, val text: String)

/** Adaptörle yapılan her konuşmanın zaman damgalı kaydı. */
class ObdLog(
    private val clock: () -> Long = System::currentTimeMillis,
    private val capacity: Int = 5000,
) {
    private val _entries = MutableStateFlow<List<LogEntry>>(emptyList())
    val entries: StateFlow<List<LogEntry>> = _entries.asStateFlow()

    fun record(direction: LogDirection, text: String) {
        val entry = LogEntry(clock(), direction, text)
        _entries.update { (it + entry).takeLast(capacity) }
    }

    fun clear() {
        _entries.value = emptyList()
    }

    fun exportText(): String = buildString {
        for (e in _entries.value) {
            append(FORMAT.format(Instant.ofEpochMilli(e.timeMillis)))
            append(' ').append(e.direction.name).append(' ').append(e.text).append('\n')
        }
    }

    private companion object {
        val FORMAT: DateTimeFormatter =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'").withZone(ZoneOffset.UTC)
    }
}
