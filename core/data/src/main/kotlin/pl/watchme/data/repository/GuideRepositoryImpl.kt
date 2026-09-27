package pl.watchme.data.repository

import java.time.Clock
import java.time.Instant
import javax.inject.Inject
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import org.slf4j.LoggerFactory
import pl.watchme.data.local.GuideSyncDao
import pl.watchme.data.local.GuideSyncEntity
import pl.watchme.data.local.ProgrammeDao
import pl.watchme.data.local.TransactionRunner
import pl.watchme.data.mapper.GuideMapper
import pl.watchme.data.mapper.toDomain
import pl.watchme.data.remote.EpgRemoteSource
import pl.watchme.data.remote.GuideFetch
import pl.watchme.domain.DomainError
import pl.watchme.domain.Outcome
import pl.watchme.domain.model.ChannelId
import pl.watchme.domain.model.Programme
import pl.watchme.domain.model.TimeWindow
import pl.watchme.domain.repository.GuideRepository

class GuideRepositoryImpl @Inject constructor(
    private val remote: EpgRemoteSource,
    private val programmes: ProgrammeDao,
    private val syncs: GuideSyncDao,
    private val transactions: TransactionRunner,
    private val clock: Clock,
) : GuideRepository {

    private val log = LoggerFactory.getLogger(GuideRepositoryImpl::class.java)

    override fun observe(channels: Set<ChannelId>, window: TimeWindow): Flow<List<Programme>> =
        programmes.observe(channels.map { it.value }, window.from.toEpochMilli(), window.until.toEpochMilli())
            .map { rows -> rows.map { it.toDomain() } }

    override suspend fun lastRefresh(channels: Set<ChannelId>): Instant? {
        val synced = syncs.get(channels.map { it.value })
        if (synced.isEmpty() || synced.size < channels.size) return null
        return Instant.ofEpochMilli(synced.minOf { it.fetchedAtMillis })
    }

    override suspend fun refresh(channels: Set<ChannelId>, window: TimeWindow): Outcome<Unit> = coroutineScope {
        val etags = syncs.get(channels.map { it.value }).associate { it.channelId to it.etag }
        val gate = Semaphore(MAX_PARALLEL_DOWNLOADS)
        val errors = channels
            .map { channel -> async { gate.withPermit { refreshChannel(channel.value, etags[channel.value]) } } }
            .awaitAll()
            .filterNotNull()
        programmes.deleteEndedBefore(window.from.toEpochMilli())
        errors.firstOrNull()?.let { Outcome.Failure(it) } ?: Outcome.Success(Unit)
    }

    private suspend fun refreshChannel(channelId: String, etag: String?): DomainError? =
        catchingDomainErrors(
            block = {
                val fetchedAt = clock.instant().toEpochMilli()
                when (val fetch = remote.guide(channelId, etag)) {
                    is GuideFetch.Fetched -> {
                        val rows = GuideMapper.toEntities(fetch.file)
                        transactions.run {
                            programmes.deleteChannel(channelId)
                            programmes.insertAll(rows)
                            syncs.upsert(GuideSyncEntity(channelId, fetch.etag, fetchedAt))
                        }
                    }
                    GuideFetch.NotModified -> syncs.upsert(GuideSyncEntity(channelId, etag, fetchedAt))
                    GuideFetch.Missing -> transactions.run {
                        programmes.deleteChannel(channelId)
                        syncs.upsert(GuideSyncEntity(channelId, null, fetchedAt))
                    }
                }
                null
            },
            onError = { error ->
                log.warn("Guide refresh failed for channel {}", channelId, error)
                error.toDomainError()
            },
        )

    private companion object {
        const val MAX_PARALLEL_DOWNLOADS = 8
    }
}
