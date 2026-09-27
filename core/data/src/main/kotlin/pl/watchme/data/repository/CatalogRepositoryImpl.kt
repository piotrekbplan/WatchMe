package pl.watchme.data.repository

import java.time.Clock
import java.time.Duration
import java.time.Instant
import javax.inject.Inject
import kotlinx.serialization.SerializationException
import org.slf4j.LoggerFactory
import pl.watchme.data.local.CatalogCacheEntity
import pl.watchme.data.local.CatalogDao
import pl.watchme.data.mapper.CatalogMapper
import pl.watchme.data.mapper.UnsupportedSchemaException
import pl.watchme.data.remote.EpgRemoteSource
import pl.watchme.domain.Outcome
import pl.watchme.domain.model.Catalog
import pl.watchme.domain.repository.CatalogRepository
import pl.watchme.epg.contract.ChannelsFile
import pl.watchme.epg.contract.EpgContract
import pl.watchme.epg.contract.OperatorsFile

class CatalogRepositoryImpl @Inject constructor(
    private val remote: EpgRemoteSource,
    private val cache: CatalogDao,
    private val clock: Clock,
) : CatalogRepository {

    private val log = LoggerFactory.getLogger(CatalogRepositoryImpl::class.java)
    private val json = EpgContract.json

    override suspend fun catalog(): Outcome<Catalog> {
        val now = clock.instant()
        val cached = cache.get()
        if (cached != null && cached.isFresh(now)) {
            decode(cached)?.let { return Outcome.Success(it) }
        }
        return catchingDomainErrors(
            block = { Outcome.Success(download(now)) },
            onError = { error ->
                log.warn("Catalog download failed", error)
                cached?.let(::decode)?.let { Outcome.Success(it) } ?: Outcome.Failure(error.toDomainError())
            },
        )
    }

    private suspend fun download(now: Instant): Catalog {
        val channels = remote.channels()
        val operators = remote.operators()
        val catalog = CatalogMapper.toDomain(channels, operators)
        cache.upsert(
            CatalogCacheEntity(
                channelsJson = json.encodeToString(ChannelsFile.serializer(), channels),
                operatorsJson = json.encodeToString(OperatorsFile.serializer(), operators),
                fetchedAtMillis = now.toEpochMilli(),
            ),
        )
        return catalog
    }

    private fun decode(entity: CatalogCacheEntity): Catalog? =
        try {
            CatalogMapper.toDomain(
                json.decodeFromString(ChannelsFile.serializer(), entity.channelsJson),
                json.decodeFromString(OperatorsFile.serializer(), entity.operatorsJson),
            )
        } catch (e: SerializationException) {
            null
        } catch (e: UnsupportedSchemaException) {
            null
        }

    private fun CatalogCacheEntity.isFresh(now: Instant): Boolean =
        Duration.between(Instant.ofEpochMilli(fetchedAtMillis), now) < MAX_AGE

    private companion object {
        val MAX_AGE: Duration = Duration.ofHours(24)
    }
}
