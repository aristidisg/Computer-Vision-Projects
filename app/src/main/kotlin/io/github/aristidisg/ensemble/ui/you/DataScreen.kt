package io.github.aristidisg.ensemble.ui.you

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavHostController
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.aristidisg.ensemble.BuildConfig
import io.github.aristidisg.ensemble.R
import io.github.aristidisg.ensemble.data.files.Backup
import io.github.aristidisg.ensemble.data.files.BackupPreview
import io.github.aristidisg.ensemble.data.repo.SettingsRepository
import io.github.aristidisg.ensemble.domain.TimeSource
import io.github.aristidisg.ensemble.domain.WardrobeCleaner
import io.github.aristidisg.ensemble.work.AutoBackup
import io.github.aristidisg.ensemble.ui.common.BackBar
import io.github.aristidisg.ensemble.ui.common.SectionTitle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.DateFormat
import java.time.LocalDate
import java.util.Date
import javax.inject.Inject

@HiltViewModel
class DataViewModel @Inject constructor(
    @ApplicationContext private val app: Context,
    private val backup: Backup,
    private val settings: SettingsRepository,
    private val autoBackup: AutoBackup,
    private val time: TimeSource,
    private val cleaner: WardrobeCleaner,
) : ViewModel() {
    val s = settings.settings.stateIn(viewModelScope, SharingStarted.Eagerly, null)
    private val _msg = MutableStateFlow<String?>(null)
    val msg = _msg.asStateFlow()
    private val _preview = MutableStateFlow<Pair<Uri, BackupPreview>?>(null)
    val preview = _preview.asStateFlow()

    fun export(uri: Uri) = viewModelScope.launch {
        runCatching { app.contentResolver.openOutputStream(uri)?.use { backup.export(it, BuildConfig.VERSION_NAME, time.nowMillis()) } }
            .onSuccess { _msg.value = "✓" }.onFailure { _msg.value = it.message }
    }

    fun preview(uri: Uri) = viewModelScope.launch {
        runCatching { withContext(Dispatchers.IO) { app.contentResolver.openInputStream(uri)!!.use { backup.preview(it) } } }
            .onSuccess { _preview.value = uri to it }.onFailure { _msg.value = it.message }
    }

    fun import(replace: Boolean) = viewModelScope.launch {
        val (uri, _) = _preview.value ?: return@launch
        _preview.value = null
        runCatching { app.contentResolver.openInputStream(uri)!!.use { backup.import(it, replace) } }
            .onSuccess { _msg.value = "✓" }.onFailure { _msg.value = it.message }
    }

    fun dismissPreview() { _preview.value = null }

    fun seedDemo() = viewModelScope.launch { cleaner.seedDemo(); _msg.value = "✓" }

    fun removeDemo() = viewModelScope.launch {
        val n = cleaner.removeDemo()
        _msg.value = app.getString(R.string.removed_n, n)
    }

    fun deleteAll() = viewModelScope.launch { backup.deleteAll(); _msg.value = "✓" }

    fun setFolder(uri: Uri) = viewModelScope.launch {
        app.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        settings.update { it.copy(autoBackupUri = uri.toString()) }
        autoBackup.schedule()
        backupNow()
    }

    fun backupNow() = viewModelScope.launch {
        runCatching { autoBackup.run() }.onSuccess { _msg.value = "✓" }.onFailure { _msg.value = it.message }
    }

    fun disableAuto() = viewModelScope.launch { settings.update { it.copy(autoBackupUri = null) }; autoBackup.cancel() }
}

@Composable
fun DataScreen(nav: NavHostController, vm: DataViewModel = hiltViewModel()) {
    val s by vm.s.collectAsStateWithLifecycle()
    val msg by vm.msg.collectAsStateWithLifecycle()
    val preview by vm.preview.collectAsStateWithLifecycle()
    var confirm by remember { mutableStateOf(false) }
    var confirmDemo by remember { mutableStateOf(false) }
    var typed by remember { mutableStateOf("") }
    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { it?.let(vm::export) }
    val import = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { it?.let(vm::preview) }
    val folder = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { it?.let(vm::setFolder) }
    Column {
        BackBar(stringResource(R.string.data), onBack = { nav.popBackStack() })
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
            Text(stringResource(R.string.storage_note), style = MaterialTheme.typography.bodyMedium)
            msg?.let { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            Button(onClick = { export.launch("ensemble-${LocalDate.now()}.zip") }, modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) { Text(stringResource(R.string.export_data)) }
            OutlinedButton(onClick = { import.launch(arrayOf("application/zip", "application/octet-stream")) }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.import_data)) }
            SectionTitle(stringResource(R.string.auto_backup))
            val last = s?.autoBackupLastMillis ?: 0
            if (last > 0) Text(stringResource(R.string.auto_backup_last, DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(last))), style = MaterialTheme.typography.bodySmall)
            OutlinedButton(onClick = { folder.launch(null) }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.auto_backup_pick)) }
            if (s?.autoBackupUri != null) {
                OutlinedButton(onClick = vm::backupNow, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.backup_now)) }
                TextButton(onClick = vm::disableAuto) { Text(stringResource(R.string.off)) }
            }
            SectionTitle(stringResource(R.string.demo_wardrobe))
            OutlinedButton(onClick = vm::seedDemo, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.seed_demo)) }
            OutlinedButton(onClick = { confirmDemo = true }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.remove_demo)) }
            SectionTitle(stringResource(R.string.delete_all))
            OutlinedButton(onClick = { confirm = true }, modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp)) { Text(stringResource(R.string.delete_all), color = MaterialTheme.colorScheme.error) }
        }
    }
    preview?.let { (_, p) ->
        AlertDialog(
            onDismissRequest = vm::dismissPreview,
            title = { Text(stringResource(R.string.import_preview)) },
            text = { Text(p.counts.entries.joinToString("\n") { "${it.key}: ${it.value}" }) },
            confirmButton = { TextButton(onClick = { vm.import(replace = true) }) { Text(stringResource(R.string.import_replace)) } },
            dismissButton = { TextButton(onClick = { vm.import(replace = false) }) { Text(stringResource(R.string.import_merge)) } },
        )
    }
    if (confirmDemo) AlertDialog(
        onDismissRequest = { confirmDemo = false },
        title = { Text(stringResource(R.string.remove_demo)) },
        text = { Text(stringResource(R.string.remove_demo_body)) },
        confirmButton = { TextButton(onClick = { confirmDemo = false; vm.removeDemo() }) { Text(stringResource(R.string.delete)) } },
        dismissButton = { TextButton(onClick = { confirmDemo = false }) { Text(stringResource(R.string.cancel)) } },
    )
    if (confirm) AlertDialog(
        onDismissRequest = { confirm = false },
        title = { Text(stringResource(R.string.delete_all)) },
        text = { Column { Text(stringResource(R.string.delete_all_confirm)); OutlinedTextField(typed, { typed = it }, singleLine = true) } },
        confirmButton = { TextButton(onClick = { vm.deleteAll(); confirm = false; typed = "" }, enabled = typed.trim() == "DELETE") { Text(stringResource(R.string.delete)) } },
        dismissButton = { TextButton(onClick = { confirm = false }) { Text(stringResource(R.string.cancel)) } },
    )
}
