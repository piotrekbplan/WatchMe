package pl.watchme.navigation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import pl.watchme.domain.usecase.ObserveLineupUseCase
import pl.watchme.domain.usecase.ObserveSessionUseCase
import pl.watchme.domain.usecase.SyncLineupUseCase

enum class StartDestination { LOGIN, LINEUP, RANKING }

@HiltViewModel
class StartViewModel @Inject constructor(
    private val observeSession: ObserveSessionUseCase,
    private val observeLineup: ObserveLineupUseCase,
    private val syncLineup: SyncLineupUseCase,
) : ViewModel() {

    val destination: StateFlow<StartDestination?> = flow {
        if (observeSession().first() == null) {
            emit(StartDestination.LOGIN)
            return@flow
        }
        val lineup = observeLineup().first()
        emit(if (lineup == null || lineup.isEmpty) StartDestination.LINEUP else StartDestination.RANKING)
        viewModelScope.launch { syncLineup() }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val sessionEnded: Flow<Unit> = flow {
        var wasSignedIn = false
        observeSession().collect { session ->
            val signedIn = session != null
            if (wasSignedIn && !signedIn) emit(Unit)
            wasSignedIn = signedIn
        }
    }
}
