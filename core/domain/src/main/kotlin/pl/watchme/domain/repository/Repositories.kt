package pl.watchme.domain.repository

import java.time.Instant
import kotlinx.coroutines.flow.Flow
import pl.watchme.domain.Outcome
import pl.watchme.domain.model.Catalog
import pl.watchme.domain.model.ChannelId
import pl.watchme.domain.model.ChannelLineup
import pl.watchme.domain.model.Programme
import pl.watchme.domain.model.TimeWindow

interface CatalogRepository {
    suspend fun catalog(): Outcome<Catalog>
}

interface LineupRepository {
    fun observe(): Flow<ChannelLineup?>
    suspend fun save(lineup: ChannelLineup)
}

interface GuideRepository {
    fun observe(channels: Set<ChannelId>, window: TimeWindow): Flow<List<Programme>>
    suspend fun lastRefresh(channels: Set<ChannelId>): Instant?
    suspend fun refresh(channels: Set<ChannelId>, window: TimeWindow): Outcome<Unit>
}
