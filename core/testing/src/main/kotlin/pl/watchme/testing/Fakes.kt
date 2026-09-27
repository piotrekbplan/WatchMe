package pl.watchme.testing

import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import pl.watchme.domain.Outcome
import pl.watchme.domain.model.Catalog
import pl.watchme.domain.model.ChannelId
import pl.watchme.domain.model.ChannelLineup
import pl.watchme.domain.model.Programme
import pl.watchme.domain.model.TimeWindow
import pl.watchme.domain.repository.CatalogRepository
import pl.watchme.domain.repository.GuideRepository
import pl.watchme.domain.repository.LineupRepository

class MutableClock(var now: Instant, private val zone: ZoneId = ZoneOffset.UTC) : Clock() {
    override fun getZone(): ZoneId = zone
    override fun withZone(zone: ZoneId): Clock = MutableClock(now, zone)
    override fun instant(): Instant = now
}

class FakeLineupRepository(initial: ChannelLineup? = null) : LineupRepository {
    val lineup = MutableStateFlow(initial)
    val saved = mutableListOf<ChannelLineup>()

    override fun observe(): Flow<ChannelLineup?> = lineup

    override suspend fun save(lineup: ChannelLineup) {
        saved += lineup
        this.lineup.value = lineup
    }
}

class FakeGuideRepository : GuideRepository {
    val programmes = MutableStateFlow<List<Programme>>(emptyList())
    var lastRefreshAt: Instant? = null
    var refreshOutcome: Outcome<Unit> = Outcome.Success(Unit)
    val observed = mutableListOf<Pair<Set<ChannelId>, TimeWindow>>()
    val refreshed = mutableListOf<Pair<Set<ChannelId>, TimeWindow>>()

    override fun observe(channels: Set<ChannelId>, window: TimeWindow): Flow<List<Programme>> {
        observed += channels to window
        return programmes.map { all -> all.filter { it.channelId in channels } }
    }

    override suspend fun lastRefresh(channels: Set<ChannelId>): Instant? = lastRefreshAt

    override suspend fun refresh(channels: Set<ChannelId>, window: TimeWindow): Outcome<Unit> {
        refreshed += channels to window
        return refreshOutcome
    }
}

class FakeCatalogRepository(var outcome: Outcome<Catalog>) : CatalogRepository {
    var calls = 0

    override suspend fun catalog(): Outcome<Catalog> {
        calls++
        return outcome
    }
}
