package pl.watchme.data.repository

import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import pl.watchme.data.local.LineupDao
import pl.watchme.data.mapper.toDomain
import pl.watchme.data.mapper.toEntity
import pl.watchme.domain.model.ChannelLineup
import pl.watchme.domain.repository.LineupRepository

class LineupRepositoryImpl @Inject constructor(private val dao: LineupDao) : LineupRepository {

    override fun observe(): Flow<ChannelLineup?> = dao.observe().map { it?.toDomain() }

    override suspend fun save(lineup: ChannelLineup) = dao.upsert(lineup.toEntity())
}
