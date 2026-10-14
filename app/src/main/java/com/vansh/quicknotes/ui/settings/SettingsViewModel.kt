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
) : ViewModel() {

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
