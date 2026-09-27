package pl.watchme.epgjob.app

import java.time.Clock
import java.time.Duration
import java.time.OffsetDateTime
import java.time.ZoneId
import org.slf4j.LoggerFactory
import pl.watchme.epgjob.domain.Catalog
import pl.watchme.epgjob.domain.ClassifiedProgramme
import pl.watchme.epgjob.domain.EpgSanityException
import pl.watchme.epgjob.domain.EpgSource
import pl.watchme.epgjob.domain.GuideParser
import pl.watchme.epgjob.domain.GuideWindow
import pl.watchme.epgjob.domain.MatchCacheRepository
import pl.watchme.epgjob.domain.MatchKey
import pl.watchme.epgjob.domain.MatchRequest
import pl.watchme.epgjob.domain.MatchStats
import pl.watchme.epgjob.domain.ProgrammeClassifier
import pl.watchme.epgjob.domain.TitleResolver
import pl.watchme.epgjob.domain.TvOperator

data class JobReport(
    val programmesInWindow: Int,
    val moviesAndSeries: Int,
    val uniqueTitles: Int,
    val ratedTitles: Int,
    val publishedChannels: Int,
    val missingChannels: Int,
    val matchStats: MatchStats?,
)

class EpgJob(
    private val source: EpgSource,
    private val parser: GuideParser,
    private val catalog: Catalog,
    private val operators: List<TvOperator>,
    private val resolver: TitleResolver?,
    private val cacheRepository: MatchCacheRepository,
    private val contentFactory: SiteContentFactory,
    private val publisher: SitePublisher,
    private val clock: Clock,
    private val zone: ZoneId = WARSAW,
) {
    private val log = LoggerFactory.getLogger(EpgJob::class.java)

    fun run(): JobReport {
        val now = OffsetDateTime.now(clock.withZone(zone))
        val cache = cacheRepository.load()
        val catalogIds = catalog.channels.mapTo(HashSet()) { it.epgId }

        val guide = source.read { input ->
            parser.parse(input, GuideWindow.around(now)) { programme ->
                programme.channelId in catalogIds && ProgrammeClassifier.classify(programme.category) != null
            }
        }
        checkSanity(guide.programmesInWindow, cache.lastProgrammeCount)

        val missing = catalog.missingIn(guide.channels.mapTo(HashSet()) { it.id })
        missing.forEach { log.warn("Catalog channel '{}' is missing in EPG", it.epgId) }

        val classified = guide.programmes.mapNotNull { programme ->
            ProgrammeClassifier.classify(programme.category)?.let { kind ->
                ClassifiedProgramme(programme, kind, MatchKey.of(programme.title, kind, programme.year))
            }
        }
        val outcome = resolver?.resolve(classified.map { MatchRequest(it.key, it.programme.title, it.programme.year) }, cache)
        val matches = outcome?.entries
            ?: classified.mapNotNull { item -> cache[item.key]?.let { item.key to it } }.toMap()

        cache.lastProgrammeCount = guide.programmesInWindow
        cache.prune(now.toInstant().minus(CACHE_RETENTION))

        val content = contentFactory.build(now, catalog, operators, guide.channels, classified, matches)
        publisher.publish(content)
        cacheRepository.save(cache)

        return JobReport(
            programmesInWindow = guide.programmesInWindow,
            moviesAndSeries = classified.size,
            uniqueTitles = classified.distinctBy { it.key }.size,
            ratedTitles = matches.values.count { it.rating != null },
            publishedChannels = content.guides.size,
            missingChannels = missing.size,
            matchStats = outcome?.stats,
        )
    }

    private fun checkSanity(count: Int, previous: Int?) {
        if (count == 0) throw EpgSanityException("EPG has no programmes in the guide window")
        if (previous != null && count < previous * MIN_COUNT_RATIO) {
            throw EpgSanityException("EPG shrank from $previous to $count programmes in the guide window")
        }
    }

    companion object {
        val WARSAW: ZoneId = ZoneId.of("Europe/Warsaw")
        private val CACHE_RETENTION: Duration = Duration.ofDays(30)
        private const val MIN_COUNT_RATIO = 0.5
    }
}
