package pl.watchme.domain.usecase

import java.time.Clock
import java.time.Instant
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import pl.watchme.domain.model.Channel
import pl.watchme.domain.model.ChannelId
import pl.watchme.domain.model.ChannelLineup
import pl.watchme.domain.model.TimeWindow
import pl.watchme.domain.ranking.RankedGuide
import pl.watchme.domain.ranking.RankingPolicy
import pl.watchme.domain.repository.CatalogRepository
import pl.watchme.domain.repository.GuideRepository
import pl.watchme.domain.repository.LineupRepository
import pl.watchme.domain.valueOrNull

data class Ranking(
    val guide: RankedGuide,
    val at: Instant,
    val hasChannels: Boolean,
    val updatedAt: Instant? = null,
)

class ObserveRankingUseCase @Inject constructor(
    private val lineups: LineupRepository,
    private val guides: GuideRepository,
    private val catalogs: CatalogRepository,
    private val clock: Clock,
) {
    @OptIn(ExperimentalCoroutinesApi::class)
    operator fun invoke(at: Instant?): Flow<Ranking> =
        lineups.observe().flatMapLatest { lineup ->
            val now = clock.instant()
            val moment = at?.let(TimeWindow.rankingRange(now)::clamp) ?: now
            if (lineup == null || lineup.isEmpty) {
                flowOf(Ranking(RankedGuide.EMPTY, moment, hasChannels = false))
            } else {
                val channels = channelsOf(lineup)
                guides.observe(lineup.channelIds, TimeWindow.around(now)).map { programmes ->
                    Ranking(
                        guide = RankingPolicy.rank(programmes, channels, moment),
                        at = moment,
                        hasChannels = true,
                        updatedAt = guides.lastRefresh(lineup.channelIds),
                    )
                }
            }
        }

    private suspend fun channelsOf(lineup: ChannelLineup): Map<ChannelId, Channel> =
        catalogs.catalog().valueOrNull()
            ?.channels
            ?.filter { it.id in lineup.channelIds }
            ?.associateBy { it.id }
            .orEmpty()
}
