package pl.watchme.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import pl.watchme.domain.usecase.ObserveSessionUseCase
import pl.watchme.domain.usecase.SignOutUseCase

data class SettingsUiState(val email: String? = null, val isSigningOut: Boolean = false)

sealed interface SettingsEvent {
    data object SignedOut : SettingsEvent
}

@HiltViewModel
class SettingsViewModel @Inject constructor(
    observeSession: ObserveSessionUseCase,
    private val signOut: SignOutUseCase,
) : ViewModel() {

    private val signingOut = MutableStateFlow(false)
    private val eventChannel = Channel<SettingsEvent>(Channel.BUFFERED)

    val events: Flow<SettingsEvent> = eventChannel.receiveAsFlow()

    val state: StateFlow<SettingsUiState> = combine(observeSession(), signingOut) { session, isSigningOut ->
        SettingsUiState(email = session?.email?.value, isSigningOut = isSigningOut)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, SettingsUiState())

    fun onSignOut() {
        if (signingOut.value) return
        signingOut.value = true
        viewModelScope.launch {
            signOut()
            eventChannel.send(SettingsEvent.SignedOut)
        }
    }
}
