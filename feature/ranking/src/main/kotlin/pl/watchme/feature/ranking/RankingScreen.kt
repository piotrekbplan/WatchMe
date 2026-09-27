package pl.watchme.feature.ranking

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.time.Instant
import java.time.ZoneId
import pl.watchme.designsystem.component.ChannelLogo
import pl.watchme.designsystem.component.EmptyState
import pl.watchme.designsystem.component.OfflineBanner
import pl.watchme.designsystem.component.PosterImage
import pl.watchme.designsystem.component.RatingBadge
import pl.watchme.designsystem.component.SectionHeader
import pl.watchme.designsystem.theme.WatchMeTheme

@Composable
fun RankingRoute(onEditChannels: () -> Unit, viewModel: RankingViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    RankingScreen(
        state = state,
        zone = ZoneId.systemDefault(),
        onEditChannels = onEditChannels,
        onNowSelected = viewModel::onNowSelected,
        onTimeSelected = viewModel::onTimeSelected,
        onRefresh = viewModel::onRefresh,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RankingScreen(
    state: RankingUiState,
    zone: ZoneId,
    onEditChannels: () -> Unit,
    onNowSelected: () -> Unit,
    onTimeSelected: (Instant) -> Unit,
    onRefresh: () -> Unit,
) {
    var pickingTime by remember { mutableStateOf(false) }
    if (pickingTime) {
        RankingTimePickerDialog(
            zone = zone,
            onDismiss = { pickingTime = false },
            onConfirm = {
                pickingTime = false
                onTimeSelected(it)
            },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.ranking_title), color = MaterialTheme.colorScheme.primary) },
                actions = {
                    IconButton(onClick = onEditChannels) {
                        Icon(Icons.Filled.Edit, contentDescription = stringResource(R.string.ranking_edit_channels))
                    }
                },
            )
        },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            when (state) {
                RankingUiState.Loading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                RankingUiState.NoChannels -> EmptyState(
                    title = stringResource(R.string.ranking_no_channels_title),
                    message = stringResource(R.string.ranking_no_channels_message),
                    actionLabel = stringResource(R.string.ranking_no_channels_action),
                    onAction = onEditChannels,
                )
                RankingUiState.Error -> EmptyState(
                    title = stringResource(R.string.ranking_error_title),
                    message = stringResource(R.string.ranking_error_message),
                    actionLabel = stringResource(R.string.ranking_retry),
                    onAction = onRefresh,
                )
                is RankingUiState.Content -> PullToRefreshBox(
                    isRefreshing = state.isRefreshing,
                    onRefresh = onRefresh,
                    modifier = Modifier.fillMaxSize(),
                ) {
                    RankingContent(
                        state = state,
                        onNowSelected = onNowSelected,
                        onPickTime = { pickingTime = true },
                    )
                }
            }
        }
    }
}

@Composable
private fun RankingContent(
    state: RankingUiState.Content,
    onNowSelected: () -> Unit,
    onPickTime: () -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (state.isOffline) {
            item(key = "offline") {
                OfflineBanner(
                    state.offlineSince?.let { stringResource(R.string.ranking_offline_since, it) }
                        ?: stringResource(R.string.ranking_offline),
                )
            }
        }
        item(key = "time") {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilterChip(
                    selected = state.selectedTime == null,
                    onClick = onNowSelected,
                    label = { Text(stringResource(R.string.ranking_now)) },
                )
                FilterChip(
                    selected = state.selectedTime != null,
                    onClick = onPickTime,
                    leadingIcon = { Icon(Icons.Filled.DateRange, contentDescription = null) },
                    label = { Text(state.selectedLabel ?: stringResource(R.string.ranking_pick_time)) },
                )
            }
        }
        if (state.isEmpty) {
            item(key = "empty") {
                EmptyState(
                    title = stringResource(R.string.ranking_empty_title),
                    message = stringResource(R.string.ranking_empty_message),
                    modifier = Modifier.padding(top = 48.dp),
                )
            }
        }
        items(state.rated, key = { it.key }) { item -> RankedItemCard(item) }
        if (state.unrated.isNotEmpty()) {
            item(key = "unrated-header") { SectionHeader(stringResource(R.string.ranking_unrated)) }
            items(state.unrated, key = { it.key }) { item -> RankedItemCard(item) }
        }
    }
}

@Composable
private fun RankedItemCard(item: RankedItemUi) {
    Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        Row(
            modifier = Modifier.padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item.position?.let {
                Text(
                    text = it.toString(),
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.width(28.dp),
                )
            }
            PosterImage(url = item.posterUrl, contentDescription = item.title, modifier = Modifier.width(64.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(item.title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (item.subtitle.isNotEmpty()) {
                    Text(
                        item.subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ChannelLogo(url = item.channelLogoUrl, name = item.channelName)
                    Text(
                        "${item.channelName} · ${item.timeRange}",
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                LinearProgressIndicator(progress = { item.progress }, modifier = Modifier.fillMaxWidth())
                item.rating?.let { RatingBadge(it) }
            }
        }
    }
}

@Preview
@Composable
private fun RankingPreview() {
    val item = RankedItemUi(
        key = "tvp-1|1",
        position = 1,
        title = "Pianista",
        subtitle = "film wojenny · 2002",
        channelName = "TVP 1",
        channelLogoUrl = null,
        timeRange = "20:00–22:25",
        progress = 0.4f,
        rating = "8.5",
        posterUrl = null,
    )
    WatchMeTheme(darkTheme = true) {
        RankingScreen(
            state = RankingUiState.Content(
                rated = listOf(item),
                unrated = listOf(item.copy(key = "x", position = null, rating = null, title = "Ranczo")),
                selectedTime = null,
                selectedLabel = null,
                isRefreshing = false,
                isOffline = true,
                offlineSince = "18:30",
            ),
            zone = ZoneId.of("Europe/Warsaw"),
            onEditChannels = {},
            onNowSelected = {},
            onTimeSelected = {},
            onRefresh = {},
        )
    }
}
