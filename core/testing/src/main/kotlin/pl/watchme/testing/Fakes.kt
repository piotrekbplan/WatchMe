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
import pl.watchme.domain.model.Email
import pl.watchme.domain.model.Password
import pl.watchme.domain.model.Programme
import pl.watchme.domain.model.Session
import pl.watchme.domain.model.TimeWindow
import pl.watchme.domain.repository.AuthRepository
import pl.watchme.domain.repository.CatalogRepository
import pl.watchme.domain.repository.GuideRepository
import pl.watchme.domain.repository.LineupRepository
import pl.watchme.domain.valueOrNull

class MutableClock(var now: Instant, private val zone: ZoneId = ZoneOffset.UTC) : Clock() {
    override fun getZone(): ZoneId = zone
    override fun withZone(zone: ZoneId): Clock = MutableClock(now, zone)
    override fun instant(): Instant = now
}

class FakeLineupRepository(initial: ChannelLineup? = null) : LineupRepository {
    val lineup = MutableStateFlow(initial)
    val saved = mutableListOf<ChannelLineup>()
    var syncOutcome: Outcome<Unit> = Outcome.Success(Unit)
    var syncCalls = 0
    var clearCalls = 0

    override fun observe(): Flow<ChannelLineup?> = lineup

    override suspend fun save(lineup: ChannelLineup) {
        saved += lineup
        this.lineup.value = lineup
    }

    override suspend fun sync(): Outcome<Unit> {
        syncCalls++
        return syncOutcome
    }

    override suspend fun clear() {
        clearCalls++
        lineup.value = null
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
    val cached = MutableStateFlow(outcome.valueOrNull())
    var calls = 0

    override fun observe(): Flow<Catalog?> = cached

    override suspend fun catalog(): Outcome<Catalog> {
        calls++
        return outcome
    }
}

class FakeAuthRepository : AuthRepository {
    val session = MutableStateFlow<Session?>(null)
    var nextOutcome: Outcome<Session> = Outcome.Success(TestData.session)
    var resetOutcome: Outcome<Unit> = Outcome.Success(Unit)
    val signInCalls = mutableListOf<Pair<Email, String>>()
    val signUpCalls = mutableListOf<Pair<Email, Password>>()
    val resetCalls = mutableListOf<Email>()

    override fun observeSession(): Flow<Session?> = session

    override suspend fun signIn(email: Email, password: String): Outcome<Session> {
        signInCalls += email to password
        return nextOutcome.also { if (it is Outcome.Success) session.value = it.value }
    }

    override suspend fun signUp(email: Email, password: Password): Outcome<Session> {
        signUpCalls += email to password
        return nextOutcome.also { if (it is Outcome.Success) session.value = it.value }
    }

    override suspend fun sendPasswordReset(email: Email): Outcome<Unit> {
        resetCalls += email
        return resetOutcome
    }

    override suspend fun signOut() {
        session.value = null
    }
}
