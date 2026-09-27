package pl.watchme.domain.usecase

import assertk.assertThat
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import java.time.Duration
import java.time.Instant
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import pl.watchme.domain.DomainError
import pl.watchme.domain.Outcome
import pl.watchme.domain.model.ChannelLineup
import pl.watchme.domain.model.TimeWindow
import pl.watchme.testing.FakeCatalogRepository
import pl.watchme.testing.FakeGuideRepository
import pl.watchme.testing.FakeLineupRepository
import pl.watchme.testing.MutableClock
import pl.watchme.testing.TestData

class RefreshGuideUseCaseTest {

    private val now = Instant.parse("2026-09-28T19:00:00Z")
    private val lineups = FakeLineupRepository(ChannelLineup(setOf(TestData.tvp.id), null, now))
    private val guides = FakeGuideRepository()
    private val catalogs = FakeCatalogRepository(Outcome.Success(TestData.catalog))
    private val refreshGuide = RefreshGuideUseCase(lineups, guides, catalogs, MutableClock(now))

    @Test
    fun `refreshing the guide also refreshes the catalog`() = runTest {
        refreshGuide(force = true)

        assertThat(catalogs.calls).isEqualTo(1)
    }

    @Test
    fun `catalog failure does not fail the guide refresh`() = runTest {
        catalogs.outcome = Outcome.Failure(DomainError.Network)

        assertThat(refreshGuide(force = true)).isEqualTo(Outcome.Success(RefreshResult.REFRESHED))
    }

    @Test
    fun `up to date guide does not touch the catalog`() = runTest {
        guides.lastRefreshAt = now

        refreshGuide(force = false)

        assertThat(catalogs.calls).isEqualTo(0)
    }

    @Test
    fun `no channels means nothing to refresh`() = runTest {
        lineups.lineup.value = null

        assertThat(refreshGuide(force = true)).isEqualTo(Outcome.Success(RefreshResult.NO_CHANNELS))
        assertThat(guides.refreshed).isEmpty()
    }

    @Test
    fun `fresh data is not downloaded again`() = runTest {
        guides.lastRefreshAt = now.minus(Duration.ofHours(5))

        assertThat(refreshGuide(force = false)).isEqualTo(Outcome.Success(RefreshResult.UP_TO_DATE))
        assertThat(guides.refreshed).isEmpty()
    }

    @Test
    fun `stale data is refreshed for the cache window`() = runTest {
        guides.lastRefreshAt = now.minus(Duration.ofHours(7))

        assertThat(refreshGuide(force = false)).isEqualTo(Outcome.Success(RefreshResult.REFRESHED))
        assertThat(guides.refreshed.single()).isEqualTo(setOf(TestData.tvp.id) to TimeWindow.around(now))
    }

    @Test
    fun `missing data is refreshed`() = runTest {
        guides.lastRefreshAt = null

        assertThat(refreshGuide(force = false)).isEqualTo(Outcome.Success(RefreshResult.REFRESHED))
    }

    @Test
    fun `forced refresh ignores freshness`() = runTest {
        guides.lastRefreshAt = now

        assertThat(refreshGuide(force = true)).isEqualTo(Outcome.Success(RefreshResult.REFRESHED))
    }

    @Test
    fun `repository failure is passed on`() = runTest {
        guides.refreshOutcome = Outcome.Failure(DomainError.Network)

        assertThat(refreshGuide(force = true)).isEqualTo(Outcome.Failure(DomainError.Network))
    }
}
