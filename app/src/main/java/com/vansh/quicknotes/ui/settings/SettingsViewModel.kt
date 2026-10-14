package com.vansh.quicknotes.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vansh.quicknotes.data.settings.SettingsRepository
import com.vansh.quicknotes.domain.ai.AiClient
import com.vansh.quicknotes.domain.ai.AiException
import com.vansh.quicknotes.domain.ai.AiService
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settings: SettingsRepository,
    private val aiClient: AiClient,
    private val backupManager: com.vansh.quicknotes.domain.backup.BackupManager,
) : ViewModel() {

    sealed interface BackupState {
        data object Idle : BackupState
        data object Busy : BackupState
        data class Exported(val path: String, val notes: Int) : BackupState
        data class Imported(val notes: Int, val skipped: Int) : BackupState
        data class Failed(val message: String) : BackupState
    }

    private val _backupState = MutableStateFlow<BackupState>(BackupState.Idle)
    val backupState: StateFlow<BackupState> = _backupState.asStateFlow()

    fun exportBackup() {
        _backupState.value = BackupState.Busy
        viewModelScope.launch {
            _backupState.value = try {
                val file = backupManager.defaultExportFile()
                val count = backupManager.exportZip(file)
                BackupState.Exported(file.absolutePath, count)
            } catch (t: Throwable) {
                BackupState.Failed("Export failed")
            }
        }
    }

    fun importBackup(uri: android.net.Uri, resolver: android.content.ContentResolver) {
        _backupState.value = BackupState.Busy
        viewModelScope.launch {
            _backupState.value = try {
                val tmp = java.io.File.createTempFile("qn-import", ".zip")
                resolver.openInputStream(uri)?.use { input -> tmp.outputStream().use { input.copyTo(it) } }
                    ?: run {
                        _backupState.value = BackupState.Failed("Could not read the file")
                        return@launch
                    }
                val result = backupManager.importZip(tmp)
                tmp.delete()
                BackupState.Imported(result.notes, result.skipped)
            } catch (t: Throwable) {
                BackupState.Failed("Import failed")
            }
        }
    }

    val state: StateFlow<SettingsRepository.Settings> = settings.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsRepository.Settings())

    sealed interface TestState {
        data object Idle : TestState
        data object Running : TestState
        data class Ok(val reply: String) : TestState
        data class Failed(val message: String) : TestState
    }

    private val _testState = MutableStateFlow<TestState>(TestState.Idle)
    val testState: StateFlow<TestState> = _testState.asStateFlow()

    fun saveBaseUrl(v: String) = viewModelScope.launch { settings.setBaseUrl(v) }
    fun saveApiKey(v: String) = viewModelScope.launch { settings.setApiKey(v) }
    fun saveModel(v: String) = viewModelScope.launch { settings.setModel(v) }
    fun saveServerTranscription(v: Boolean) = viewModelScope.launch { settings.setServerTranscription(v) }
    fun saveReduceMotion(v: Boolean) = viewModelScope.launch { settings.setReduceMotion(v) }

    fun runTest() {
        _testState.value = TestState.Running
        viewModelScope.launch {
            _testState.value = try {
                val reply = AiService(aiClient).testConnection()
                if (reply.contains("OK", ignoreCase = true)) TestState.Ok(reply) else TestState.Ok(reply)
            } catch (e: AiException) {
                TestState.Failed(e.message ?: "Request failed")
            } catch (t: Throwable) {
                TestState.Failed("Something went wrong")
            }
        }
    }
}
