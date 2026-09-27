package pl.watchme.domain.usecase

import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import pl.watchme.domain.Outcome
import pl.watchme.domain.model.Catalog
import pl.watchme.domain.model.ChannelLineup
import pl.watchme.domain.repository.CatalogRepository
import pl.watchme.domain.repository.LineupRepository

class GetCatalogUseCase @Inject constructor(private val catalogs: CatalogRepository) {
    suspend operator fun invoke(): Outcome<Catalog> = catalogs.catalog()
}

class ObserveLineupUseCase @Inject constructor(private val lineups: LineupRepository) {
    operator fun invoke(): Flow<ChannelLineup?> = lineups.observe()
}

class SaveLineupUseCase @Inject constructor(private val lineups: LineupRepository) {
    suspend operator fun invoke(lineup: ChannelLineup) = lineups.save(lineup)
}
