package pl.watchme.epgjob.fakes

import java.io.InputStream
import java.time.OffsetDateTime
import pl.watchme.epg.contract.ChannelGuideFile
import pl.watchme.epg.contract.ChannelsFile
import pl.watchme.epg.contract.OperatorsFile
import pl.watchme.epgjob.app.SiteContent
import pl.watchme.epgjob.app.SiteContentFactory
import pl.watchme.epgjob.app.SitePublisher
import pl.watchme.epgjob.domain.Catalog
import pl.watchme.epgjob.domain.ClassifiedProgramme
import pl.watchme.epgjob.domain.EpgSource
import pl.watchme.epgjob.domain.GuideParser
import pl.watchme.epgjob.domain.GuideWindow
import pl.watchme.epgjob.domain.MatchCache
import pl.watchme.epgjob.domain.MatchCacheRepository
import pl.watchme.epgjob.domain.MatchEntry
import pl.watchme.epgjob.domain.MatchKey
import pl.watchme.epgjob.domain.MatchOutcome
import pl.watchme.epgjob.domain.MatchRequest
import pl.watchme.epgjob.domain.MatchStats
import pl.watchme.epgjob.domain.ParsedGuide
import pl.watchme.epgjob.domain.TitleResolver
import pl.watchme.epgjob.domain.TvOperator
import pl.watchme.epgjob.domain.XmltvChannel
import pl.watchme.epgjob.domain.XmltvProgramme

class StubEpgSource(private val failure: Exception? = null) : EpgSource {
    override fun <T> read(block: (InputStream) -> T): T {
        failure?.let { throw it }
        return block(InputStream.nullInputStream())
    }
}

class StubGuideParser(
    private val channels: List<XmltvChannel>,
    private val programmes: List<XmltvProgramme>,
    private val programmesInWindow: Int = programmes.size,
) : GuideParser {
    var window: GuideWindow? = null

    override fun parse(input: InputStream, window: GuideWindow, keep: (XmltvProgramme) -> Boolean): ParsedGuide {
        this.window = window
        return ParsedGuide(channels, programmes.filter(keep), programmesInWindow)
    }
}

class StubTitleResolver(private val entries: Map<MatchKey, MatchEntry>) : TitleResolver {
    val requests = mutableListOf<MatchRequest>()
    val stats = MatchStats(searched = 2, matched = 1, rated = 1, failures = 0, ratingsUnavailable = false)

    override fun resolve(requests: List<MatchRequest>, cache: MatchCache): MatchOutcome {
        this.requests += requests
        return MatchOutcome(requests.mapNotNull { request -> entries[request.key]?.let { request.key to it } }.toMap(), stats)
    }
}

class InMemoryMatchCacheRepository(private val events: MutableList<String>) : MatchCacheRepository {
    var stored: MatchCache? = null

    override fun load(): MatchCache = stored ?: MatchCache()

    override fun save(cache: MatchCache) {
        events += "save"
        stored = cache
    }
}

class RecordingSiteContentFactory : SiteContentFactory {
    val result = SiteContent(
        channels = ChannelsFile(1, emptyList()),
        operators = OperatorsFile(1, emptyList()),
        guides = listOf(ChannelGuideFile(1, "tvp-1", "2026-09-27T12:00:00+02:00", emptyList())),
    )
    var generatedAt: OffsetDateTime? = null
    var operators: List<TvOperator> = emptyList()
    var epgChannels: List<XmltvChannel> = emptyList()
    var programmes: List<ClassifiedProgramme> = emptyList()
    var matches: Map<MatchKey, MatchEntry> = emptyMap()

    override fun build(
        generatedAt: OffsetDateTime,
        catalog: Catalog,
        operators: List<TvOperator>,
        epgChannels: List<XmltvChannel>,
        programmes: List<ClassifiedProgramme>,
        matches: Map<MatchKey, MatchEntry>,
    ): SiteContent {
        this.generatedAt = generatedAt
        this.operators = operators
        this.epgChannels = epgChannels
        this.programmes = programmes
        this.matches = matches
        return result
    }
}

class RecordingSitePublisher(private val events: MutableList<String>) : SitePublisher {
    val published = mutableListOf<SiteContent>()

    override fun publish(content: SiteContent) {
        events += "publish"
        published += content
    }
}
