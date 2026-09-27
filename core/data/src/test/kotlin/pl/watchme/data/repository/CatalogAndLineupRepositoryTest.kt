package pl.watchme.data.repository

import app.cash.turbine.test
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import java.io.IOException
import java.time.Duration
import java.time.Instant
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import pl.watchme.data.fakes.FakeCatalogDao
import pl.watchme.data.fakes.FakeEpgRemoteSource
import pl.watchme.data.fakes.FakeLineupDao
import pl.watchme.domain.DomainError
import pl.watchme.domain.Outcome
import pl.watchme.domain.model.Catalog
import pl.watchme.domain.model.ChannelId
import pl.watchme.domain.model.ChannelLineup
import pl.watchme.domain.model.PackageRef
import pl.watchme.epg.contract.ChannelDto
import pl.watchme.epg.contract.ChannelsFile
import pl.watchme.epg.contract.OperatorsFile
import pl.watchme.testing.MutableClock

class CatalogAndLineupRepositoryTest {

    private val now = Instant.parse("2026-09-28T17:00:00Z")
    private val clock = MutableClock(now)
    private val remote = FakeEpgRemoteSource().apply {
        channelsFile = ChannelsFile(1, listOf(ChannelDto("tvp-1", "TVP 1", "ogolne", "Ogólne")))
        operatorsFile = OperatorsFile(1, emptyList())
    }
    private val catalogDao = FakeCatalogDao()
    private val catalogs = CatalogRepositoryImpl(remote, catalogDao, clock)

    @Test
    fun `downloads and caches the catalog`() = runTest {
        val outcome = catalogs.catalog()

        assertThat(channelNames(outcome)).isEqualTo(listOf("TVP 1"))
        assertThat(catalogDao.row?.fetchedAtMillis).isEqualTo(now.toEpochMilli())
    }

    @Test
    fun `fresh cache is used without network`() = runTest {
        catalogs.catalog()
        remote.channelsFile = ChannelsFile(1, listOf(ChannelDto("tvn", "TVN", "ogolne", "Ogólne")))
        clock.now = now.plus(Duration.ofHours(23))

        val outcome = catalogs.catalog()

        assertThat(channelNames(outcome)).isEqualTo(listOf("TVP 1"))
        assertThat(remote.catalogCalls).isEqualTo(1)
    }

    @Test
    fun `stale cache is refreshed`() = runTest {
        catalogs.catalog()
        remote.channelsFile = ChannelsFile(1, listOf(ChannelDto("tvn", "TVN", "ogolne", "Ogólne")))
        clock.now = now.plus(Duration.ofHours(25))

        assertThat(channelNames(catalogs.catalog())).isEqualTo(listOf("TVN"))
    }

    @Test
    fun `network failure falls back to the stale cache`() = runTest {
        catalogs.catalog()
        remote.catalogFailure = IOException("offline")
        clock.now = now.plus(Duration.ofDays(3))

        assertThat(channelNames(catalogs.catalog())).isEqualTo(listOf("TVP 1"))
    }

    @Test
    fun `network failure without cache is reported`() = runTest {
        remote.catalogFailure = IOException("offline")

        assertThat(catalogs.catalog()).isEqualTo(Outcome.Failure(DomainError.Network))
    }

    @Test
    fun `lineup is saved and observed`() = runTest {
        val lineups = LineupRepositoryImpl(FakeLineupDao())
        val lineup = ChannelLineup(setOf(ChannelId("tvp-1")), PackageRef("play", "play-start"), now)

        lineups.observe().test {
            assertThat(awaitItem()).isNull()

            lineups.save(lineup)

            assertThat(awaitItem()).isNotNull().isEqualTo(lineup)
        }
    }

    private fun channelNames(outcome: Outcome<Catalog>): List<String> {
        assertThat(outcome).isInstanceOf<Outcome.Success<Catalog>>()
        return (outcome as Outcome.Success).value.channels.map { it.name }
    }
}
