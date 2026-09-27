package pl.watchme.navigation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import pl.watchme.domain.usecase.ObserveLineupUseCase

enum class StartDestination { LINEUP, RANKING }

@HiltViewModel
class StartViewModel @Inject constructor(observeLineup: ObserveLineupUseCase) : ViewModel() {

    val destination: StateFlow<StartDestination?> = flow {
        val lineup = observeLineup().first()
        emit(if (lineup == null || lineup.isEmpty) StartDestination.LINEUP else StartDestination.RANKING)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)
}
