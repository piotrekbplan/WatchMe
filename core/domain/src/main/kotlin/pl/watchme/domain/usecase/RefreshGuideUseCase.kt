package pl.watchme.domain.usecase

import java.time.Clock
import java.time.Duration
import javax.inject.Inject
import kotlinx.coroutines.flow.first
import pl.watchme.domain.Outcome
import pl.watchme.domain.map
import pl.watchme.domain.model.TimeWindow
import pl.watchme.domain.repository.GuideRepository
import pl.watchme.domain.repository.LineupRepository

enum class RefreshResult { REFRESHED, UP_TO_DATE, NO_CHANNELS }

class RefreshGuideUseCase @Inject constructor(
    private val lineups: LineupRepository,
    private val guides: GuideRepository,
    private val clock: Clock,
) {
    suspend operator fun invoke(force: Boolean): Outcome<RefreshResult> {
        val lineup = lineups.observe().first()
        if (lineup == null || lineup.isEmpty) return Outcome.Success(RefreshResult.NO_CHANNELS)

        val now = clock.instant()
        val lastRefresh = guides.lastRefresh(lineup.channelIds)
        if (!force && lastRefresh != null && Duration.between(lastRefresh, now) < STALE_AFTER) {
            return Outcome.Success(RefreshResult.UP_TO_DATE)
        }
        return guides.refresh(lineup.channelIds, TimeWindow.around(now)).map { RefreshResult.REFRESHED }
    }

    private companion object {
        val STALE_AFTER: Duration = Duration.ofHours(6)
    }
}
