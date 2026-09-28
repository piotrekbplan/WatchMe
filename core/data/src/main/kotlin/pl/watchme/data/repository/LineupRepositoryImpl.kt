package pl.watchme.data.repository

import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.slf4j.LoggerFactory
import pl.watchme.data.auth.SessionStore
import pl.watchme.data.local.LineupDao
import pl.watchme.data.local.TransactionRunner
import pl.watchme.data.mapper.toDomain
import pl.watchme.data.mapper.toEntity
import pl.watchme.data.sync.LineupMerge
import pl.watchme.data.sync.LineupRemoteSource
import pl.watchme.data.sync.LineupSyncScheduler
import pl.watchme.data.sync.MergeAction
import pl.watchme.domain.Outcome
import pl.watchme.domain.model.ChannelLineup
import pl.watchme.domain.repository.LineupRepository

class LineupRepositoryImpl @Inject constructor(
    private val dao: LineupDao,
    private val remote: LineupRemoteSource,
    private val sessions: SessionStore,
    private val scheduler: LineupSyncScheduler,
    private val transactions: TransactionRunner,
) : LineupRepository {

    private val log = LoggerFactory.getLogger(LineupRepositoryImpl::class.java)

    override fun observe(): Flow<ChannelLineup?> = dao.observe().map { it?.toDomain() }

    override suspend fun save(lineup: ChannelLineup) {
        dao.upsert(lineup.toEntity().copy(dirty = true))
        scheduler.schedule()
    }

    override suspend fun sync(): Outcome<Unit> {
        val uid = sessions.current()?.uid ?: return Outcome.Success(Unit)
        return catchingDomainErrors(
            block = {
                val local = dao.get()?.toDomain()
                when (val action = LineupMerge.resolve(local, remote.fetch(uid))) {
                    is MergeAction.Push -> {
                        remote.push(uid, action.lineup)
                        dao.markClean(action.lineup.updatedAt.toEpochMilli())
                    }
                    is MergeAction.TakeRemote -> transactions.run {
                        if (dao.get()?.updatedAtMillis == local?.updatedAt?.toEpochMilli()) {
                            dao.upsert(action.lineup.toEntity())
                        }
                    }
                    MergeAction.None -> local?.let { dao.markClean(it.updatedAt.toEpochMilli()) }
                }
                Outcome.Success(Unit)
            },
            onError = { error ->
                log.warn("Lineup sync failed for uid {}", uid, error)
                Outcome.Failure(error.toDomainError())
            },
        )
    }

    override suspend fun clear() = dao.clear()
}
