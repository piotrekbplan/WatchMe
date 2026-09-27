package pl.watchme.data.repository

import app.cash.turbine.test
import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEqualTo
import assertk.assertions.isNull
import java.time.Instant
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import pl.watchme.data.fakes.FakeEpgRemoteSource
import pl.watchme.data.fakes.FakeGuideSyncDao
import pl.watchme.data.fakes.FakeProgrammeDao
import pl.watchme.data.fakes.RecordingTransactionRunner
import pl.watchme.data.local.GuideSyncEntity
import pl.watchme.data.local.ProgrammeEntity
import pl.watchme.data.remote.GuideFetch
import pl.watchme.domain.DomainError
import pl.watchme.domain.Outcome
import pl.watchme.domain.model.ChannelId
import pl.watchme.domain.model.TimeWindow
import pl.watchme.epg.contract.ChannelGuideFile
import pl.watchme.epg.contract.ProgrammeDto
import pl.watchme.epg.contract.ProgrammeKindDto
import pl.watchme.testing.MutableClock

class GuideRepositoryImplTest {

    private val now = Instant.parse("2026-09-28T17:00:00Z")
    private val window = TimeWindow.around(now)
    private val remote = FakeEpgRemoteSource()
    private val programmes = FakeProgrammeDao()
    private val syncs = FakeGuideSyncDao()
    private val transactions = RecordingTransactionRunner()
    private val repository = GuideRepositoryImpl(remote, programmes, syncs, transactions, MutableClock(now))

    private val tvp = ChannelId("tvp-1")
    private val tvn = ChannelId("tvn")

    @Test
    fun `observe maps stored programmes inside the window`() = runTest {
        programmes.rows.value = listOf(
            entity("tvp-1", "Pianista", "2026-09-28T18:00:00Z", "2026-09-28T20:00:00Z"),
            entity("tvp-1", "Dawno", "2026-09-28T10:00:00Z", "2026-09-28T12:00:00Z"),
            entity("tvn", "Inny kanał", "2026-09-28T18:00:00Z", "2026-09-28T20:00:00Z"),
        )

        repository.observe(setOf(tvp), window).test {
            assertThat(awaitItem().map { it.title }).containsExactly("Pianista")
        }
    }

    @Test
    fun `last refresh is unknown until every channel was synced`() = runTest {
        syncs.rows["tvp-1"] = GuideSyncEntity("tvp-1", null, now.toEpochMilli())

        assertThat(repository.lastRefresh(setOf(tvp, tvn))).isNull()

        syncs.rows["tvn"] = GuideSyncEntity("tvn", null, now.minusSeconds(60).toEpochMilli())

        assertThat(repository.lastRefresh(setOf(tvp, tvn))).isEqualTo(now.minusSeconds(60))
    }

    @Test
    fun `fetched guide replaces channel programmes and stores etag in one transaction`() = runTest {
        programmes.rows.value = listOf(entity("tvp-1", "Stary", "2026-09-28T18:00:00Z", "2026-09-28T19:00:00Z"))
        remote.guides["tvp-1"] = GuideFetch.Fetched(guide("tvp-1", "Pianista"), etag = "\"v2\"")

        val outcome = repository.refresh(setOf(tvp), window)

        assertThat(outcome).isEqualTo(Outcome.Success(Unit))
        assertThat(programmes.rows.value.map { it.title }).containsExactly("Pianista")
        assertThat(syncs.rows.getValue("tvp-1")).isEqualTo(GuideSyncEntity("tvp-1", "\"v2\"", now.toEpochMilli()))
        assertThat(transactions.transactions).isEqualTo(1)
    }

    @Test
    fun `not modified keeps programmes and only bumps the sync time`() = runTest {
        programmes.rows.value = listOf(entity("tvp-1", "Pianista", "2026-09-28T18:00:00Z", "2026-09-28T20:00:00Z"))
        syncs.rows["tvp-1"] = GuideSyncEntity("tvp-1", "\"v1\"", 0)
        remote.guides["tvp-1"] = GuideFetch.NotModified

        repository.refresh(setOf(tvp), window)

        assertThat(remote.requestedEtags["tvp-1"]).isEqualTo("\"v1\"")
        assertThat(programmes.rows.value.map { it.title }).containsExactly("Pianista")
        assertThat(syncs.rows.getValue("tvp-1")).isEqualTo(GuideSyncEntity("tvp-1", "\"v1\"", now.toEpochMilli()))
    }

    @Test
    fun `missing guide clears the channel without failing`() = runTest {
        programmes.rows.value = listOf(entity("tvp-1", "Stary", "2026-09-28T18:00:00Z", "2026-09-28T20:00:00Z"))
        remote.guides["tvp-1"] = GuideFetch.Missing

        val outcome = repository.refresh(setOf(tvp), window)

        assertThat(outcome).isEqualTo(Outcome.Success(Unit))
        assertThat(programmes.rows.value).isEqualTo(emptyList())
        assertThat(syncs.rows.getValue("tvp-1").fetchedAtMillis).isEqualTo(now.toEpochMilli())
    }

    @Test
    fun `failure on one channel keeps the others and reports network error`() = runTest {
        remote.guides["tvp-1"] = GuideFetch.Fetched(guide("tvp-1", "Pianista"), etag = null)
        remote.failingChannels += "tvn"

        val outcome = repository.refresh(setOf(tvp, tvn), window)

        assertThat(outcome).isEqualTo(Outcome.Failure(DomainError.Network))
        assertThat(programmes.rows.value.map { it.title }).containsExactly("Pianista")
        assertThat(syncs.rows["tvn"]).isNull()
    }

    @Test
    fun `unsupported schema is reported`() = runTest {
        remote.guides["tvp-1"] = GuideFetch.Fetched(guide("tvp-1", "Pianista").copy(schemaVersion = 9), etag = null)

        assertThat(repository.refresh(setOf(tvp), window)).isEqualTo(Outcome.Failure(DomainError.UnsupportedData))
    }

    @Test
    fun `programmes that ended before the window are purged`() = runTest {
        programmes.rows.value = listOf(
            entity("tvn", "Dawno", "2026-09-28T10:00:00Z", "2026-09-28T13:00:00Z"),
            entity("tvn", "Trwa", "2026-09-28T13:30:00Z", "2026-09-28T15:30:00Z"),
        )

        repository.refresh(emptySet(), window)

        assertThat(programmes.rows.value.map { it.title }).containsExactly("Trwa")
    }

    @Test
    fun `at most eight channels are downloaded at once`() = runTest {
        remote.latencyMillis = 100
        val channels = (1..20).map { ChannelId("c$it") }.toSet()

        repository.refresh(channels, window)

        assertThat(remote.maxInFlight).isEqualTo(8)
    }

    private fun entity(channel: String, title: String, start: String, stop: String) = ProgrammeEntity(
        channelId = channel,
        startMillis = Instant.parse(start).toEpochMilli(),
        stopMillis = Instant.parse(stop).toEpochMilli(),
        title = title,
        kind = "MOVIE",
        category = "film",
        year = null,
        imdbId = null,
        rating = null,
        votes = null,
        posterUrl = null,
    )

    private fun guide(channel: String, title: String) = ChannelGuideFile(
        schemaVersion = 1,
        channelId = channel,
        generatedAt = "2026-09-28T19:00:00+02:00",
        programmes = listOf(
            ProgrammeDto(title, "2026-09-28T20:00:00+02:00", "2026-09-28T22:00:00+02:00", ProgrammeKindDto.MOVIE, "film"),
        ),
    )
}
