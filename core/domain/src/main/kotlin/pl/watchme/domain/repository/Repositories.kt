package pl.watchme.domain.repository

import java.time.Instant
import kotlinx.coroutines.flow.Flow
import pl.watchme.domain.Outcome
import pl.watchme.domain.model.Catalog
import pl.watchme.domain.model.ChannelId
import pl.watchme.domain.model.ChannelLineup
import pl.watchme.domain.model.Email
import pl.watchme.domain.model.Password
import pl.watchme.domain.model.Programme
import pl.watchme.domain.model.Session
import pl.watchme.domain.model.TimeWindow

interface CatalogRepository {
    fun observe(): Flow<Catalog?>
    suspend fun catalog(): Outcome<Catalog>
}

interface LineupRepository {
    fun observe(): Flow<ChannelLineup?>
    suspend fun save(lineup: ChannelLineup)
    suspend fun sync(): Outcome<Unit>
    suspend fun clear()
}

interface GuideRepository {
    fun observe(channels: Set<ChannelId>, window: TimeWindow): Flow<List<Programme>>
    suspend fun lastRefresh(channels: Set<ChannelId>): Instant?
    suspend fun refresh(channels: Set<ChannelId>, window: TimeWindow): Outcome<Unit>
}

interface AuthRepository {
    fun observeSession(): Flow<Session?>
    suspend fun signIn(email: Email, password: String): Outcome<Session>
    suspend fun signUp(email: Email, password: Password): Outcome<Session>
    suspend fun sendPasswordReset(email: Email): Outcome<Unit>
    suspend fun signOut()
}
