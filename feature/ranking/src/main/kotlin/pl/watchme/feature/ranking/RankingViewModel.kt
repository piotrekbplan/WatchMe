package pl.watchme.feature.ranking

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory
import pl.watchme.domain.Outcome
import pl.watchme.domain.ranking.RankedEntry
import pl.watchme.domain.usecase.ObserveRankingUseCase
import pl.watchme.domain.usecase.Ranking
import pl.watchme.domain.usecase.RefreshGuideUseCase

@HiltViewModel
class RankingViewModel @Inject constructor(
    observeRanking: ObserveRankingUseCase,
    private val refreshGuide: RefreshGuideUseCase,
    private val clock: Clock,
    zone: ZoneId,
) : ViewModel() {

    private val log = LoggerFactory.getLogger(RankingViewModel::class.java)
    private val formatter = RankingFormatter(zone)
    private val selectedTime = MutableStateFlow<Instant?>(null)
    private val refresh = MutableStateFlow(RefreshStatus())

    private val ticks: Flow<Instant> = flow {
        while (true) {
            emit(clock.instant())
            delay(TICK_MILLIS)
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    val state: StateFlow<RankingUiState> = combine(
        selectedTime.flatMapLatest { observeRanking(it, ticks) },
        refresh,
        ::toUiState,
    )
        .onStart { refresh(force = false) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), RankingUiState.Loading)

    fun onTimeSelected(moment: Instant) {
        selectedTime.value = moment
    }

    fun onNowSelected() {
        selectedTime.value = null
    }

    fun onRefresh() = refresh(force = true)

    private fun refresh(force: Boolean) {
        viewModelScope.launch {
            refresh.update { it.copy(isRefreshing = true) }
            val outcome = refreshGuide(force)
            if (outcome is Outcome.Failure) log.warn("Guide refresh failed: {}", outcome.error)
            refresh.value = RefreshStatus(isRefreshing = false, failed = outcome is Outcome.Failure)
        }
    }

    private fun toUiState(ranking: Ranking, status: RefreshStatus): RankingUiState {
        val guide = ranking.guide
        val hasCache = ranking.updatedAt != null
        return when {
            !ranking.hasChannels -> RankingUiState.NoChannels
            guide.isEmpty && !hasCache && status.failed -> RankingUiState.Error
            guide.isEmpty && !hasCache && status.isRefreshing -> RankingUiState.Loading
            else -> RankingUiState.Content(
                rated = guide.rated.mapIndexed { index, entry -> entry.toItem(index + 1, ranking.at) },
                unrated = guide.unrated.map { it.toItem(null, ranking.at) },
                selectedTime = ranking.at.takeUnless { ranking.isNow },
                selectedLabel = formatter.dayTime(ranking.at).takeUnless { ranking.isNow },
                isRefreshing = status.isRefreshing,
                isOffline = status.failed,
                offlineSince = ranking.updatedAt?.takeIf { status.failed }?.let(formatter::time),
            )
        }
    }

    private fun RankedEntry.toItem(position: Int?, at: Instant): RankedItemUi = RankedItemUi(
        key = "${programme.channelId.value}|${programme.start.toEpochMilli()}",
        position = position,
        title = programme.title,
        subtitle = listOfNotNull(programme.category.takeIf { it.isNotBlank() }, programme.year?.toString())
            .joinToString(" · "),
        channelName = channel?.name ?: programme.channelId.value,
        channelLogoUrl = channel?.logoUrl,
        timeRange = formatter.range(programme.start, programme.stop),
        progress = programme.progressAt(at),
        rating = programme.rating?.value?.let(formatter::rating),
        posterUrl = programme.posterUrl,
    )

    private data class RefreshStatus(val isRefreshing: Boolean = false, val failed: Boolean = false)

    private companion object {
        const val TICK_MILLIS = 60_000L
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
