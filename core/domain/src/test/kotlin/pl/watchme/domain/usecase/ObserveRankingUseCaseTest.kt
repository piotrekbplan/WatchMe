package pl.watchme.domain.usecase

import app.cash.turbine.test
import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isNull
import assertk.assertions.isTrue
import java.time.Instant
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import pl.watchme.domain.Outcome
import pl.watchme.domain.model.ChannelLineup
import pl.watchme.domain.model.TimeWindow
import pl.watchme.testing.FakeCatalogRepository
import pl.watchme.testing.FakeGuideRepository
import pl.watchme.testing.FakeLineupRepository
import pl.watchme.testing.TestData

class ObserveRankingUseCaseTest {

    private val now = Instant.parse("2026-09-28T19:00:00Z")
    private val ticks = MutableStateFlow(now)
    private val lineups = FakeLineupRepository()
    private val guides = FakeGuideRepository()
    private val catalogs = FakeCatalogRepository(Outcome.Success(TestData.catalog))
    private val observeRanking = ObserveRankingUseCase(lineups, guides, catalogs)

    private val selected = ChannelLineup(setOf(TestData.tvp.id, TestData.tvn.id), null, now)

    @Test
    fun `no lineup gives an empty ranking without touching the guide`() = runTest {
        observeRanking(at = null, now = ticks).test {
            val ranking = awaitItem()

            assertThat(ranking.hasChannels).isFalse()
            assertThat(ranking.guide.isEmpty).isTrue()
            assertThat(guides.observed).isEmpty()
        }
    }

    @Test
    fun `empty lineup gives an empty ranking`() = runTest {
        lineups.lineup.value = ChannelLineup.empty(now)

        observeRanking(at = null, now = ticks).test {
            assertThat(awaitItem().hasChannels).isFalse()
        }
    }

    @Test
    fun `ranks selected channels now within the cache window`() = runTest {
        lineups.lineup.value = selected
        guides.programmes.value = listOf(
            TestData.programme("Pianista", rating = 8.5),
            TestData.programme("Ranczo", channel = TestData.tvn, rating = 8.6),
            TestData.programme("Obcy kanał", channel = TestData.hbo, rating = 9.9),
        )

        observeRanking(at = null, now = ticks).test {
            val ranking = awaitItem()

            assertThat(ranking.at).isEqualTo(now)
            assertThat(ranking.isNow).isTrue()
            assertThat(ranking.hasChannels).isTrue()
            assertThat(ranking.guide.rated.map { it.programme.title }).containsExactly("Ranczo", "Pianista")
            assertThat(ranking.guide.rated[0].channel).isEqualTo(TestData.tvn)
            assertThat(guides.observed.single()).isEqualTo(selected.channelIds to TimeWindow.around(now))
        }
    }

    @Test
    fun `now ranking follows the clock`() = runTest {
        lineups.lineup.value = selected
        guides.programmes.value = listOf(
            TestData.programme("Pianista", rating = 8.5),
            TestData.programme(
                "Nocny film",
                start = Instant.parse("2026-09-28T21:00:00Z"),
                stop = Instant.parse("2026-09-28T23:00:00Z"),
                rating = 7.0,
            ),
        )

        observeRanking(at = null, now = ticks).test {
            assertThat(awaitItem().guide.rated.map { it.programme.title }).containsExactly("Pianista")

            ticks.value = Instant.parse("2026-09-28T22:00:00Z")

            val later = awaitItem()
            assertThat(later.at).isEqualTo(Instant.parse("2026-09-28T22:00:00Z"))
            assertThat(later.guide.rated.map { it.programme.title }).containsExactly("Nocny film")
        }
    }

    @Test
    fun `chosen moment is clamped to the next day`() = runTest {
        lineups.lineup.value = selected

        observeRanking(at = now.plusSeconds(3 * 86_400), now = ticks).test {
            val ranking = awaitItem()
            assertThat(ranking.at).isEqualTo(now.plusSeconds(86_400))
            assertThat(ranking.isNow).isFalse()
        }
        observeRanking(at = now.minusSeconds(3_600), now = ticks).test {
            assertThat(awaitItem().at).isEqualTo(now)
        }
    }

    @Test
    fun `ranking reacts to lineup changes`() = runTest {
        lineups.lineup.value = selected
        guides.programmes.value = listOf(TestData.programme("Na HBO", channel = TestData.hbo, rating = 7.0))

        observeRanking(at = null, now = ticks).test {
            assertThat(awaitItem().guide.isEmpty).isTrue()

            lineups.lineup.value = selected.toggle(TestData.hbo.id, now)

            assertThat(awaitItem().guide.rated.map { it.programme.title }).containsExactly("Na HBO")
        }
    }

    @Test
    fun `ranking reports when the guide was last refreshed`() = runTest {
        lineups.lineup.value = selected
        guides.lastRefreshAt = now.minusSeconds(1_800)

        observeRanking(at = null, now = ticks).test {
            assertThat(awaitItem().updatedAt).isEqualTo(now.minusSeconds(1_800))
        }
    }

    @Test
    fun `channel details come from the cached catalog without network`() = runTest {
        catalogs.cached.value = null
        lineups.lineup.value = selected
        guides.programmes.value = listOf(TestData.programme("Pianista", rating = 8.5))

        observeRanking(at = null, now = ticks).test {
            assertThat(awaitItem().guide.rated.single().channel).isNull()

            catalogs.cached.value = TestData.catalog

            assertThat(awaitItem().guide.rated.single().channel).isEqualTo(TestData.tvp)
        }
        assertThat(catalogs.calls).isEqualTo(0)
    }
}
