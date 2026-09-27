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

class ObserveRankingUseCaseTest {

    private val now = Instant.parse("2026-09-28T19:00:00Z")
    private val clock = MutableClock(now)
    private val lineups = FakeLineupRepository()
    private val guides = FakeGuideRepository()
    private val catalogs = FakeCatalogRepository(Outcome.Success(TestData.catalog))
    private val observeRanking = ObserveRankingUseCase(lineups, guides, catalogs, clock)

    private val selected = ChannelLineup(setOf(TestData.tvp.id, TestData.tvn.id), null, now)

    @Test
    fun `no lineup gives an empty ranking without touching the guide`() = runTest {
        observeRanking(at = null).test {
            val ranking = awaitItem()

            assertThat(ranking.hasChannels).isFalse()
            assertThat(ranking.guide.isEmpty).isTrue()
            assertThat(guides.observed).isEmpty()
        }
    }

    @Test
    fun `empty lineup gives an empty ranking`() = runTest {
        lineups.lineup.value = ChannelLineup.empty(now)

        observeRanking(at = null).test {
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

        observeRanking(at = null).test {
            val ranking = awaitItem()

            assertThat(ranking.at).isEqualTo(now)
            assertThat(ranking.hasChannels).isTrue()
            assertThat(ranking.guide.rated.map { it.programme.title }).containsExactly("Ranczo", "Pianista")
            assertThat(ranking.guide.rated[0].channel).isEqualTo(TestData.tvn)
            assertThat(guides.observed.single()).isEqualTo(selected.channelIds to TimeWindow.around(now))
        }
    }

    @Test
    fun `chosen moment is clamped to the next day`() = runTest {
        lineups.lineup.value = selected

        observeRanking(at = now.plusSeconds(3 * 86_400)).test {
            assertThat(awaitItem().at).isEqualTo(now.plusSeconds(86_400))
        }
        observeRanking(at = now.minusSeconds(3_600)).test {
            assertThat(awaitItem().at).isEqualTo(now)
        }
    }

    @Test
    fun `ranking reacts to lineup changes`() = runTest {
        lineups.lineup.value = selected
        guides.programmes.value = listOf(TestData.programme("Na HBO", channel = TestData.hbo, rating = 7.0))

        observeRanking(at = null).test {
            assertThat(awaitItem().guide.isEmpty).isTrue()

            lineups.lineup.value = selected.toggle(TestData.hbo.id, now)

            assertThat(awaitItem().guide.rated.map { it.programme.title }).containsExactly("Na HBO")
        }
    }

    @Test
    fun `catalog failure still ranks without channel details`() = runTest {
        catalogs.outcome = Outcome.Failure(DomainError.Network)
        lineups.lineup.value = selected
        guides.programmes.value = listOf(TestData.programme("Pianista", rating = 8.5))

        observeRanking(at = null).test {
            val entry = awaitItem().guide.rated.single()

            assertThat(entry.programme.title).isEqualTo("Pianista")
            assertThat(entry.channel).isNull()
        }
    }
}
