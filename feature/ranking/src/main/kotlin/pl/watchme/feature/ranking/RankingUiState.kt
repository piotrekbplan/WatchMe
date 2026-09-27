package pl.watchme.feature.ranking

import java.time.Instant

data class RankedItemUi(
    val key: String,
    val position: Int?,
    val title: String,
    val subtitle: String,
    val channelName: String,
    val channelLogoUrl: String?,
    val timeRange: String,
    val progress: Float,
    val rating: String?,
    val posterUrl: String?,
)

sealed interface RankingUiState {
    data object Loading : RankingUiState
    data object NoChannels : RankingUiState
    data object Error : RankingUiState
    data class Content(
        val rated: List<RankedItemUi>,
        val unrated: List<RankedItemUi>,
        val selectedTime: Instant?,
        val selectedLabel: String?,
        val isRefreshing: Boolean,
        val isOffline: Boolean,
        val offlineSince: String?,
    ) : RankingUiState {
        val isEmpty: Boolean get() = rated.isEmpty() && unrated.isEmpty()
    }
}
