package pl.watchme.feature.lineup

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Clock
import javax.inject.Inject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory
import pl.watchme.domain.Outcome
import pl.watchme.domain.model.Catalog
import pl.watchme.domain.model.ChannelId
import pl.watchme.domain.model.ChannelLineup
import pl.watchme.domain.model.PackageRef
import pl.watchme.domain.usecase.GetCatalogUseCase
import pl.watchme.domain.usecase.ObserveLineupUseCase
import pl.watchme.domain.usecase.SaveLineupUseCase

@HiltViewModel
class LineupViewModel @Inject constructor(
    private val getCatalog: GetCatalogUseCase,
    private val observeLineup: ObserveLineupUseCase,
    private val saveLineup: SaveLineupUseCase,
    private val clock: Clock,
) : ViewModel() {

    private val log = LoggerFactory.getLogger(LineupViewModel::class.java)
    private val editor = MutableStateFlow(Editor())
    private val eventChannel = Channel<LineupEvent>(Channel.BUFFERED)

    val events: Flow<LineupEvent> = eventChannel.receiveAsFlow()

    val state: StateFlow<LineupUiState> = editor
        .map(::toUiState)
        .stateIn(viewModelScope, SharingStarted.Eagerly, LineupUiState.Loading)

    init {
        load()
    }

    fun onRetry() = load()

    fun onOperatorClicked(operatorId: String) = editor.update {
        it.copy(expandedOperatorId = if (it.expandedOperatorId == operatorId) null else operatorId)
    }

    fun onPackageSelected(operatorId: String, packageId: String) = editor.update { current ->
        val operator = current.catalog?.operators?.firstOrNull { it.id == operatorId } ?: return@update current
        val pkg = operator.packages.firstOrNull { it.id == packageId } ?: return@update current
        current.copy(
            draft = current.draftOrEmpty().applyPackage(operator, pkg, clock.instant()),
            step = LineupStep.CHANNELS,
            query = "",
        )
    }

    fun onSkipPackage() = editor.update { it.copy(step = LineupStep.CHANNELS, query = "") }

    fun onBackToOperators() = editor.update { it.copy(step = LineupStep.OPERATOR) }

    fun onChannelToggled(channelId: String) = editor.update {
        it.copy(draft = it.draftOrEmpty().toggle(ChannelId(channelId), clock.instant()))
    }

    fun onQueryChanged(query: String) = editor.update { it.copy(query = query) }

    fun onSave() {
        val draft = editor.value.draft ?: return
        viewModelScope.launch {
            editor.update { it.copy(saving = true) }
            saveLineup(draft)
            editor.update { it.copy(saving = false) }
            eventChannel.send(LineupEvent.Saved)
        }
    }

    private fun load() {
        viewModelScope.launch {
            editor.update { it.copy(failed = false) }
            val existing = observeLineup().first()
            when (val result = getCatalog()) {
                is Outcome.Success -> editor.update {
                    it.copy(
                        catalog = result.value,
                        draft = it.draft ?: existing,
                        expandedOperatorId = existing?.source?.operatorId,
                    )
                }
                is Outcome.Failure -> {
                    log.warn("Catalog unavailable: {}", result.error)
                    editor.update { it.copy(failed = true) }
                }
            }
        }
    }

    private fun Editor.draftOrEmpty(): ChannelLineup = draft ?: ChannelLineup.empty(clock.instant())

    private fun toUiState(editor: Editor): LineupUiState {
        if (editor.failed) return LineupUiState.Error
        val catalog = editor.catalog ?: return LineupUiState.Loading
        val selected = editor.draft?.channelIds.orEmpty()
        return LineupUiState.Content(
            step = editor.step,
            operators = catalog.operators.map { operator ->
                OperatorUi(
                    id = operator.id,
                    name = operator.name,
                    packages = operator.packages.map { pkg ->
                        PackageUi(
                            id = pkg.id,
                            name = pkg.name,
                            channelCount = pkg.channelIds.size,
                            selected = editor.draft?.source == PackageRef(operator.id, pkg.id),
                        )
                    },
                )
            },
            expandedOperatorId = editor.expandedOperatorId,
            groups = groupsOf(catalog, selected, editor.query),
            query = editor.query,
            selectedCount = selected.size,
            isSaving = editor.saving,
        )
    }

    private fun groupsOf(catalog: Catalog, selected: Set<ChannelId>, query: String): List<ChannelGroupUi> =
        catalog.channels
            .filter { SearchText.matches(it.name, query) }
            .groupBy { it.groupName }
            .map { (groupName, channels) ->
                ChannelGroupUi(
                    name = groupName,
                    channels = channels.map { ChannelItemUi(it.id.value, it.name, it.logoUrl, it.id in selected) },
                )
            }

    private data class Editor(
        val catalog: Catalog? = null,
        val draft: ChannelLineup? = null,
        val step: LineupStep = LineupStep.OPERATOR,
        val expandedOperatorId: String? = null,
        val query: String = "",
        val saving: Boolean = false,
        val failed: Boolean = false,
    )
}
