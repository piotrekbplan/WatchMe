package pl.watchme.feature.lineup

enum class LineupStep { OPERATOR, CHANNELS }

data class PackageUi(val id: String, val name: String, val channelCount: Int, val selected: Boolean)

data class OperatorUi(val id: String, val name: String, val packages: List<PackageUi>)

data class ChannelItemUi(val id: String, val name: String, val logoUrl: String?, val selected: Boolean)

data class ChannelGroupUi(val name: String, val channels: List<ChannelItemUi>)

sealed interface LineupUiState {
    data object Loading : LineupUiState
    data object Error : LineupUiState
    data class Content(
        val step: LineupStep,
        val operators: List<OperatorUi>,
        val expandedOperatorId: String?,
        val groups: List<ChannelGroupUi>,
        val query: String,
        val selectedCount: Int,
        val isSaving: Boolean,
    ) : LineupUiState {
        val canSave: Boolean get() = selectedCount > 0 && !isSaving
    }
}

sealed interface LineupEvent {
    data object Saved : LineupEvent
}
