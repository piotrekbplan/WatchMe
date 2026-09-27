package pl.watchme.feature.lineup

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import pl.watchme.designsystem.component.ChannelLogo
import pl.watchme.designsystem.component.EmptyState
import pl.watchme.designsystem.component.SectionHeader
import pl.watchme.designsystem.theme.WatchMeTheme

@Composable
fun LineupRoute(onSaved: () -> Unit, viewModel: LineupViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                LineupEvent.Saved -> onSaved()
            }
        }
    }
    LineupScreen(
        state = state,
        actions = LineupActions(
            onRetry = viewModel::onRetry,
            onOperatorClicked = viewModel::onOperatorClicked,
            onPackageSelected = viewModel::onPackageSelected,
            onSkipPackage = viewModel::onSkipPackage,
            onBackToOperators = viewModel::onBackToOperators,
            onChannelToggled = viewModel::onChannelToggled,
            onQueryChanged = viewModel::onQueryChanged,
            onSave = viewModel::onSave,
        ),
    )
}

data class LineupActions(
    val onRetry: () -> Unit = {},
    val onOperatorClicked: (String) -> Unit = {},
    val onPackageSelected: (String, String) -> Unit = { _, _ -> },
    val onSkipPackage: () -> Unit = {},
    val onBackToOperators: () -> Unit = {},
    val onChannelToggled: (String) -> Unit = {},
    val onQueryChanged: (String) -> Unit = {},
    val onSave: () -> Unit = {},
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LineupScreen(state: LineupUiState, actions: LineupActions) {
    val step = (state as? LineupUiState.Content)?.step
    BackHandler(enabled = step == LineupStep.CHANNELS, onBack = actions.onBackToOperators)
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(
                            if (step == LineupStep.CHANNELS) R.string.lineup_title_channels else R.string.lineup_title_operator,
                        ),
                    )
                },
                navigationIcon = {
                    if (step == LineupStep.CHANNELS) {
                        IconButton(onClick = actions.onBackToOperators) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.lineup_back))
                        }
                    }
                },
            )
        },
        bottomBar = {
            if (state is LineupUiState.Content && state.step == LineupStep.CHANNELS) {
                Surface(tonalElevation = 3.dp) {
                    Button(
                        onClick = actions.onSave,
                        enabled = state.canSave,
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                    ) {
                        Text(stringResource(R.string.lineup_save, state.selectedCount))
                    }
                }
            }
        },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            when (state) {
                LineupUiState.Loading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                LineupUiState.Error -> EmptyState(
                    title = stringResource(R.string.lineup_error_title),
                    message = stringResource(R.string.lineup_error_message),
                    actionLabel = stringResource(R.string.lineup_retry),
                    onAction = actions.onRetry,
                )
                is LineupUiState.Content -> when (state.step) {
                    LineupStep.OPERATOR -> OperatorStep(state, actions)
                    LineupStep.CHANNELS -> ChannelsStep(state, actions)
                }
            }
        }
    }
}

@Composable
private fun OperatorStep(state: LineupUiState.Content, actions: LineupActions) {
    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text(
                stringResource(R.string.lineup_operator_intro),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        items(state.operators, key = { it.id }) { operator ->
            OperatorCard(
                operator = operator,
                expanded = operator.id == state.expandedOperatorId,
                onClick = { actions.onOperatorClicked(operator.id) },
                onPackageSelected = { packageId -> actions.onPackageSelected(operator.id, packageId) },
            )
        }
        item {
            TextButton(onClick = actions.onSkipPackage, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.lineup_skip))
            }
        }
    }
}

@Composable
private fun OperatorCard(
    operator: OperatorUi,
    expanded: Boolean,
    onClick: () -> Unit,
    onPackageSelected: (String) -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = operator.name,
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(16.dp),
        )
        if (expanded) {
            operator.packages.forEach { pkg ->
                HorizontalDivider()
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onPackageSelected(pkg.id) }
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(selected = pkg.selected, onClick = { onPackageSelected(pkg.id) })
                    Column(Modifier.weight(1f)) {
                        Text(pkg.name, style = MaterialTheme.typography.bodyLarge)
                        Text(
                            stringResource(R.string.lineup_package_channels, pkg.channelCount),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ChannelsStep(state: LineupUiState.Content, actions: LineupActions) {
    Column(Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = state.query,
            onValueChange = actions.onQueryChanged,
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            placeholder = { Text(stringResource(R.string.lineup_search)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        )
        if (state.groups.isEmpty()) {
            Text(
                stringResource(R.string.lineup_no_results),
                modifier = Modifier.padding(16.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        LazyColumn(Modifier.fillMaxSize()) {
            state.groups.forEach { group ->
                item(key = "group-${group.name}") { SectionHeader(group.name) }
                items(group.channels, key = { it.id }) { channel ->
                    ChannelRow(channel, onToggle = { actions.onChannelToggled(channel.id) })
                }
            }
        }
    }
}

@Composable
private fun ChannelRow(channel: ChannelItemUi, onToggle: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        ChannelLogo(url = channel.logoUrl, name = channel.name)
        Text(channel.name, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Switch(checked = channel.selected, onCheckedChange = { onToggle() })
    }
}

@Preview
@Composable
private fun LineupChannelsPreview() {
    WatchMeTheme(darkTheme = true) {
        LineupScreen(
            state = LineupUiState.Content(
                step = LineupStep.CHANNELS,
                operators = emptyList(),
                expandedOperatorId = null,
                groups = listOf(
                    ChannelGroupUi(
                        "Ogólne",
                        listOf(ChannelItemUi("tvp-1", "TVP 1", null, true), ChannelItemUi("tvn", "TVN", null, false)),
                    ),
                ),
                query = "",
                selectedCount = 1,
                isSaving = false,
            ),
            actions = LineupActions(),
        )
    }
}
