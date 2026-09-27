package pl.watchme.epgjob.app

import java.time.Clock
import java.time.Duration
import kotlin.io.path.readText
import okhttp3.OkHttpClient
import org.slf4j.LoggerFactory
import pl.watchme.epgjob.data.FileMatchCacheRepository
import pl.watchme.epgjob.data.FileSitePublisher
import pl.watchme.epgjob.data.HttpEpgSource
import pl.watchme.epgjob.data.LineupYamlParser
import pl.watchme.epgjob.data.OkHttpGetter
import pl.watchme.epgjob.data.OmdbClient
import pl.watchme.epgjob.data.SiteContentBuilder
import pl.watchme.epgjob.data.TmdbClient
import pl.watchme.epgjob.data.XmltvParser
import pl.watchme.epgjob.domain.Catalog
import pl.watchme.epgjob.domain.OperatorResolver
import pl.watchme.epgjob.domain.TitleMatcher

object JobFactory {

    private val log = LoggerFactory.getLogger(JobFactory::class.java)

    fun create(config: JobConfig, clock: Clock = Clock.systemUTC()): EpgJob {
        val yaml = LineupYamlParser()
        val catalog = Catalog.from(yaml.parseCatalog(config.catalogPath.readText()))
        val lineup = OperatorResolver(catalog).resolve(yaml.parseLineup(config.operatorsPath.readText()))
        lineup.warnings.forEach { log.warn(it) }

        val epgHttp = OkHttpClient.Builder()
            .connectTimeout(Duration.ofSeconds(30))
            .readTimeout(Duration.ofMinutes(2))
            .callTimeout(Duration.ofMinutes(10))
            .build()
        val apiHttp = OkHttpGetter(
            epgHttp.newBuilder()
                .readTimeout(Duration.ofSeconds(20))
                .callTimeout(Duration.ofSeconds(30))
                .build(),
        )

        val tmdbKey = config.tmdbApiKey
        val omdbKey = config.omdbApiKey
        val resolver = if (tmdbKey != null && omdbKey != null) {
            TitleMatcher(TmdbClient(apiHttp, tmdbKey), OmdbClient(apiHttp, omdbKey), clock)
        } else {
            log.warn("TMDB_API_KEY or OMDB_API_KEY is missing, publishing only cached ratings")
            null
        }

        return EpgJob(
            source = HttpEpgSource(epgHttp, config.sourceUrl),
            parser = XmltvParser(),
            catalog = catalog,
            operators = lineup.operators,
            resolver = resolver,
            cacheRepository = FileMatchCacheRepository(config.outDir.resolve("cache/match-cache.json")),
            contentFactory = SiteContentBuilder(),
            publisher = FileSitePublisher(config.outDir),
            clock = clock,
        )
    }
}
