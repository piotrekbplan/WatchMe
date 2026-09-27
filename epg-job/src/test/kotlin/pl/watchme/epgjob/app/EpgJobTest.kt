package pl.watchme.epgjob.app

import assertk.assertFailure
import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import assertk.assertions.isNull
import java.io.IOException
import java.io.InputStream
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset
import javax.xml.stream.XMLStreamException
import org.junit.jupiter.api.Test
import pl.watchme.epgjob.domain.Catalog
import pl.watchme.epgjob.domain.ChannelPackage
import pl.watchme.epgjob.domain.EpgSanityException
import pl.watchme.epgjob.domain.EpgSource
import pl.watchme.epgjob.domain.GroupDefinition
import pl.watchme.epgjob.domain.GuideParser
import pl.watchme.epgjob.domain.GuideWindow
import pl.watchme.epgjob.domain.MatchCache
import pl.watchme.epgjob.domain.MatchEntry
import pl.watchme.epgjob.domain.MatchKey
import pl.watchme.epgjob.domain.ParsedGuide
import pl.watchme.epgjob.domain.ProgrammeKind
import pl.watchme.epgjob.domain.TitleResolver
import pl.watchme.epgjob.domain.TvOperator
import pl.watchme.epgjob.domain.XmltvChannel
import pl.watchme.epgjob.domain.XmltvProgramme
import pl.watchme.epgjob.fakes.InMemoryMatchCacheRepository
import pl.watchme.epgjob.fakes.RecordingSiteContentFactory
import pl.watchme.epgjob.fakes.RecordingSitePublisher
import pl.watchme.epgjob.fakes.StubEpgSource
import pl.watchme.epgjob.fakes.StubGuideParser
import pl.watchme.epgjob.fakes.StubTitleResolver

class EpgJobTest {

    private val clock = Clock.fixed(Instant.parse("2026-09-27T10:00:00Z"), ZoneOffset.UTC)
    private val now = OffsetDateTime.parse("2026-09-27T12:00:00+02:00")
    private val catalog = Catalog.from(
        listOf(
            GroupDefinition("ogolne", "Ogólne", listOf("TVP 1")),
            GroupDefinition("filmowe", "Filmowe", listOf("Canal+ Film", "Ghost Channel")),
        ),
    )
    private val operators = listOf(TvOperator("play", "Play", listOf(ChannelPackage("play-start", "Start", listOf("tvp-1")))))
    private val channels = listOf(
        XmltvChannel("TVP 1", "TVP 1", null),
        XmltvChannel("Canal+ Film", "Canal+ Film", null),
        XmltvChannel("Foreign X", "Foreign X", null),
    )
    private val pianista = programme("TVP 1", "Pianista", "film wojenny", 2002)
    private val news = programme("TVP 1", "Wiadomości", "program informacyjny", null)
    private val ranczo = programme("Canal+ Film", "Ranczo odc. 12", "serial komediowy", 2009)
    private val foreign = programme("Foreign X", "Foreign Movie", "film", null)
    private val pianistaKey = MatchKey.of("Pianista", ProgrammeKind.MOVIE, 2002)
    private val pianistaEntry = MatchEntry("tt0253474", 8.5, 950000, null, clock.instant(), clock.instant(), clock.instant())

    private val events = mutableListOf<String>()
    private val parser = StubGuideParser(channels, listOf(pianista, news, ranczo, foreign), programmesInWindow = 40)
    private val resolver = StubTitleResolver(mapOf(pianistaKey to pianistaEntry))
    private val cacheRepository = InMemoryMatchCacheRepository(events)
    private val contentFactory = RecordingSiteContentFactory()
    private val publisher = RecordingSitePublisher(events)

    private fun job(
        resolver: TitleResolver? = this.resolver,
        source: EpgSource = StubEpgSource(),
        parser: GuideParser = this.parser,
    ) = EpgJob(source, parser, catalog, operators, resolver, cacheRepository, contentFactory, publisher, clock)

    @Test
    fun `parses guide window around now in Warsaw`() {
        job().run()

        assertThat(parser.window).isEqualTo(GuideWindow.around(now))
    }

    @Test
    fun `keeps only catalog movies and series`() {
        job().run()

        assertThat(contentFactory.programmes.map { it.programme.title to it.kind })
            .containsExactly("Pianista" to ProgrammeKind.MOVIE, "Ranczo odc. 12" to ProgrammeKind.SERIES)
    }

    @Test
    fun `resolves titles and passes matches to content`() {
        job().run()

        assertThat(resolver.requests.map { it.title }).containsExactly("Pianista", "Ranczo odc. 12")
        assertThat(contentFactory.matches).isEqualTo(mapOf(pianistaKey to pianistaEntry))
        assertThat(contentFactory.generatedAt).isEqualTo(now)
        assertThat(contentFactory.operators).isEqualTo(operators)
        assertThat(contentFactory.epgChannels).isEqualTo(channels)
    }

    @Test
    fun `publishes content before saving cache`() {
        job().run()

        assertThat(publisher.published).containsExactly(contentFactory.result)
        assertThat(events).containsExactly("publish", "save")
        assertThat(cacheRepository.stored?.lastProgrammeCount).isEqualTo(40)
    }

    @Test
    fun `source failure publishes nothing`() {
        assertFailure { job(source = StubEpgSource(IOException("epg.ovh down"))).run() }.isInstanceOf<IOException>()

        assertThat(events).isEmpty()
    }

    @Test
    fun `parser failure publishes nothing`() {
        val broken = object : GuideParser {
            override fun parse(input: InputStream, window: GuideWindow, keep: (XmltvProgramme) -> Boolean): ParsedGuide =
                throw XMLStreamException("truncated")
        }

        assertFailure { job(parser = broken).run() }.isInstanceOf<XMLStreamException>()

        assertThat(events).isEmpty()
    }

    @Test
    fun `empty guide fails sanity check`() {
        assertFailure { job(parser = StubGuideParser(channels, emptyList(), programmesInWindow = 0)).run() }
            .isInstanceOf<EpgSanityException>()

        assertThat(events).isEmpty()
    }

    @Test
    fun `sharp drop in programme count fails sanity check`() {
        cacheRepository.stored = MatchCache(lastProgrammeCount = 100)

        assertFailure { job().run() }.isInstanceOf<EpgSanityException>()

        assertThat(events).isEmpty()
    }

    @Test
    fun `drop to exactly half is accepted`() {
        cacheRepository.stored = MatchCache(lastProgrammeCount = 80)

        job().run()

        assertThat(events).containsExactly("publish", "save")
    }

    @Test
    fun `without resolver cached entries are used`() {
        cacheRepository.stored = MatchCache().also { it[pianistaKey] = pianistaEntry }

        val report = job(resolver = null).run()

        assertThat(contentFactory.matches).isEqualTo(mapOf(pianistaKey to pianistaEntry))
        assertThat(report.matchStats).isNull()
    }

    @Test
    fun `entries unseen for thirty days are pruned`() {
        val oldKey = MatchKey.of("Dawno", ProgrammeKind.MOVIE, 1990)
        cacheRepository.stored = MatchCache().also { it[oldKey] = MatchEntry.notFound(clock.instant().minus(Duration.ofDays(31))) }

        job().run()

        assertThat(cacheRepository.stored?.get(oldKey)).isNull()
    }

    @Test
    fun `report summarises the run`() {
        val report = job().run()

        assertThat(report).isEqualTo(
            JobReport(
                programmesInWindow = 40,
                moviesAndSeries = 2,
                uniqueTitles = 2,
                ratedTitles = 1,
                publishedChannels = 1,
                missingChannels = 1,
                matchStats = resolver.stats,
            ),
        )
    }

    private fun programme(channel: String, title: String, category: String, year: Int?) =
        XmltvProgramme(channel, title, now.plusHours(1), now.plusHours(3), category, year, null)
}
