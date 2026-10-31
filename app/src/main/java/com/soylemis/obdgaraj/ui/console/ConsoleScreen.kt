package com.soylemis.obdgaraj.ui.console

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.soylemis.obdgaraj.R
import com.soylemis.obdgaraj.log.LogDirection
import com.soylemis.obdgaraj.ui.appViewModel
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ConsoleScreen() {
    val vm = appViewModel { ConsoleViewModel(it.connectionManager, it.log) }
    val entries by vm.entries.collectAsState()
    val context = LocalContext.current
    var input by rememberSaveable { mutableStateOf("") }
    val listState = rememberLazyListState()
    val time = SimpleDateFormat("HH:mm:ss.SSS", Locale.ROOT)

    LaunchedEffect(entries.size) { if (entries.isNotEmpty()) listState.scrollToItem(entries.size - 1) }

    fun send() {
        vm.send(input)
        input = ""
    }

    Column(Modifier.fillMaxSize().padding(12.dp)) {
        Text(stringResource(R.string.console_readonly), style = MaterialTheme.typography.bodySmall)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 8.dp)) {
            OutlinedButton(onClick = { shareLog(context, vm.exportText()) }) { Text(stringResource(R.string.console_share)) }
            OutlinedButton(onClick = vm::clear) { Text(stringResource(R.string.console_clear)) }
        }
        LazyColumn(state = listState, modifier = Modifier.weight(1f).fillMaxWidth()) {
            items(entries) { e ->
                val color = when (e.direction) {
                    LogDirection.TX -> MaterialTheme.colorScheme.primary
                    LogDirection.RX -> MaterialTheme.colorScheme.onSurface
                    LogDirection.INFO -> MaterialTheme.colorScheme.onSurfaceVariant
                    LogDirection.ERROR -> MaterialTheme.colorScheme.error
                }
                Text(
                    "${time.format(Date(e.timeMillis))} ${e.direction.name.padEnd(5)} ${e.text.replace('\r', '⏎')}",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    color = if (color == Color.Unspecified) MaterialTheme.colorScheme.onSurface else color,
                )
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                placeholder = { Text(stringResource(R.string.console_hint)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters, imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { send() }),
                modifier = Modifier.weight(1f),
            )
            Button(onClick = ::send, modifier = Modifier.padding(start = 8.dp)) { Text(stringResource(R.string.console_send)) }
        }
    }
}

private fun shareLog(context: Context, text: String) {
    val dir = File(context.cacheDir, "logs").apply { mkdirs() }
    val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.ROOT).format(Date())
    val file = File(dir, "obd-log-$stamp.txt").apply { writeText(text) }
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_STREAM, uri)
        putExtra(Intent.EXTRA_SUBJECT, file.name)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(intent, null))
}
