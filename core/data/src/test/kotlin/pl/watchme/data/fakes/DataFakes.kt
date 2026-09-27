package pl.watchme.data.fakes

import java.io.IOException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import pl.watchme.data.local.CatalogCacheEntity
import pl.watchme.data.local.CatalogDao
import pl.watchme.data.local.GuideSyncDao
import pl.watchme.data.local.GuideSyncEntity
import pl.watchme.data.local.LineupDao
import pl.watchme.data.local.LineupEntity
import pl.watchme.data.local.ProgrammeDao
import pl.watchme.data.local.ProgrammeEntity
import pl.watchme.data.local.TransactionRunner
import pl.watchme.data.remote.EpgRemoteSource
import pl.watchme.data.remote.GuideFetch
import pl.watchme.epg.contract.ChannelsFile
import pl.watchme.epg.contract.OperatorsFile

class FakeProgrammeDao : ProgrammeDao {
    val rows = MutableStateFlow<List<ProgrammeEntity>>(emptyList())

    override fun observe(channelIds: List<String>, fromMillis: Long, untilMillis: Long): Flow<List<ProgrammeEntity>> =
        rows.map { all ->
            all.filter { it.channelId in channelIds && it.stopMillis > fromMillis && it.startMillis < untilMillis }
                .sortedBy { it.startMillis }
        }

    override suspend fun deleteChannel(channelId: String) {
        rows.value = rows.value.filterNot { it.channelId == channelId }
    }

    override suspend fun insertAll(programmes: List<ProgrammeEntity>) {
        rows.value = rows.value + programmes
    }

    override suspend fun deleteEndedBefore(cutoffMillis: Long) {
        rows.value = rows.value.filterNot { it.stopMillis <= cutoffMillis }
    }
}

class FakeGuideSyncDao : GuideSyncDao {
    val rows = mutableMapOf<String, GuideSyncEntity>()

    override suspend fun get(channelIds: List<String>): List<GuideSyncEntity> = channelIds.mapNotNull { rows[it] }

    override suspend fun upsert(sync: GuideSyncEntity) {
        rows[sync.channelId] = sync
    }
}

class FakeLineupDao : LineupDao {
    val row = MutableStateFlow<LineupEntity?>(null)

    override fun observe(): Flow<LineupEntity?> = row

    override suspend fun upsert(lineup: LineupEntity) {
        row.value = lineup
    }

    override suspend fun clear() {
        row.value = null
    }
}

class FakeCatalogDao : CatalogDao {
    private val state = MutableStateFlow<CatalogCacheEntity?>(null)
    val row: CatalogCacheEntity? get() = state.value

    override fun observe(): Flow<CatalogCacheEntity?> = state

    override suspend fun get(): CatalogCacheEntity? = state.value

    override suspend fun upsert(cache: CatalogCacheEntity) {
        state.value = cache
    }
}

class RecordingTransactionRunner : TransactionRunner {
    var transactions = 0

    override suspend fun <T> run(block: suspend () -> T): T {
        transactions++
        return block()
    }
}

class FakeEpgRemoteSource : EpgRemoteSource {
    val guides = mutableMapOf<String, GuideFetch>()
    val failingChannels = mutableSetOf<String>()
    val requestedEtags = mutableMapOf<String, String?>()
    var channelsFile: ChannelsFile? = null
    var operatorsFile: OperatorsFile? = null
    var catalogFailure: Exception? = null
    var catalogCalls = 0
    var latencyMillis = 0L
    var inFlight = 0
    var maxInFlight = 0

    override suspend fun channels(): ChannelsFile {
        catalogCalls++
        catalogFailure?.let { throw it }
        return checkNotNull(channelsFile)
    }

    override suspend fun operators(): OperatorsFile {
        catalogFailure?.let { throw it }
        return checkNotNull(operatorsFile)
    }

    override suspend fun guide(channelId: String, etag: String?): GuideFetch {
        requestedEtags[channelId] = etag
        inFlight++
        maxInFlight = maxOf(maxInFlight, inFlight)
        try {
            if (latencyMillis > 0) delay(latencyMillis)
            if (channelId in failingChannels) throw IOException("offline")
            return guides[channelId] ?: GuideFetch.Missing
        } finally {
            inFlight--
        }
    }
}
