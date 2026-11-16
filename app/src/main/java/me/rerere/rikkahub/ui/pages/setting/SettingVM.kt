package me.rerere.rikkahub.ui.pages.setting

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import me.rerere.ai.provider.ProviderSetting
import me.rerere.rikkahub.data.codex.CodexAccountRepository
import me.rerere.rikkahub.data.datastore.Settings
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.data.ai.mcp.McpManager

class SettingVM(
    private val settingsStore: SettingsStore,
    private val mcpManager: McpManager,
    private val codexAccountRepository: CodexAccountRepository
) :
    ViewModel() {
    val settings: StateFlow<Settings> = settingsStore.settingsFlow
        .stateIn(viewModelScope, SharingStarted.Lazily, Settings(init = true, providers = emptyList()))

    fun updateSettings(settings: Settings) {
        viewModelScope.launch {
            settingsStore.update(settings)
        }
    }

    fun deleteProvider(provider: ProviderSetting) {
        val newSettings = settings.value.copy(
            providers = settings.value.providers - provider
        )
        updateSettings(newSettings)
        if (provider is ProviderSetting.Codex &&
            newSettings.providers.none { it is ProviderSetting.Codex }
        ) {
            viewModelScope.launch {
                runCatching {
                    codexAccountRepository.clearAll()
                }
            }
        }
    }
}
