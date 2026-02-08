package net.dom53.inkita.ui.people

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import net.dom53.inkita.core.logging.LoggingManager
import net.dom53.inkita.core.network.KavitaApiFactory
import net.dom53.inkita.core.storage.AppPreferences

class PersonDetailViewModel(
    val personName: String,
    private val appPreferences: AppPreferences,
) : ViewModel() {
    private val _state = MutableStateFlow(PersonDetailUiState())
    val state: StateFlow<PersonDetailUiState> = _state
    private var latestConfig: net.dom53.inkita.core.storage.AppConfig? = null

    init {
        load()
    }

    private fun load() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            if (LoggingManager.isDebugEnabled()) {
                LoggingManager.d(
                    "PersonDetail",
                    "Load person=$personName",
                )
            }
            val config = appPreferences.configFlow.first()
            latestConfig = config
            if (!config.isConfigured) {
                _state.update { it.copy(isLoading = false, error = "Not configured") }
                return@launch
            }

            try {
                val api = KavitaApiFactory.createAuthenticated(config.serverUrl, config.apiKey)
                var error: String? = null

                val personResponse = async { api.getPerson(personName) }.await()
                val person =
                    if (!personResponse.isSuccessful) {
                        error = "Person: HTTP ${personResponse.code()} ${personResponse.message()}"
                        null
                    } else {
                        personResponse.body()
                    }

                _state.update {
                    it.copy(
                        isLoading = false,
                        error = error,
                        detail = PersonDetail(person = person),
                    )
                }
            } catch (e: Exception) {
                if (LoggingManager.isDebugEnabled()) {
                    LoggingManager.e("PersonDetail", "Load failed person=$personName", e)
                }
                _state.update { it.copy(isLoading = false, error = e.message ?: "Failed to load person detail") }
            }
        }
    }

    companion object {
        fun provideFactory(
            personName: String,
            appPreferences: AppPreferences,
        ): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    @Suppress("UNCHECKED_CAST")
                    return PersonDetailViewModel(personName, appPreferences) as T
                }
            }
    }
}
