package pl.watchme.epgjob.domain

import java.io.IOException
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import kotlin.math.abs

data class CatalogHit(
    val tmdbId: Long,
    val kind: ProgrammeKind,
    val title: String,
    val originalTitle: String,
    val year: Int?,
    val posterUrl: String?,
)

interface MovieCatalog {
    fun search(title: String, kind: ProgrammeKind): List<CatalogHit>
    fun imdbId(hit: CatalogHit): String?
}

data class ImdbScore(val rating: Double, val votes: Int?)

interface RatingSource {
    fun rating(imdbId: String): ImdbScore?
}

class RatingSourceUnavailableException(message: String) : RuntimeException(message)

data class MatchKey(val kind: ProgrammeKind, val normalizedTitle: String, val year: Int?) {

    val value: String get() = "${kind.name}|$normalizedTitle|${year ?: "-"}"

    companion object {
        fun of(title: String, kind: ProgrammeKind, year: Int?): MatchKey =
            when (kind) {
                ProgrammeKind.MOVIE -> MatchKey(kind, TitleNormalizer.key(TitleNormalizer.clean(title)), year)
                ProgrammeKind.SERIES -> MatchKey(kind, TitleNormalizer.key(TitleNormalizer.seriesName(title)), null)
            }
    }
}

data class MatchEntry(
    val imdbId: String?,
    val rating: Double?,
    val votes: Int?,
    val posterUrl: String?,
    val checkedAt: Instant,
    val ratedAt: Instant?,
    val lastSeenAt: Instant,
) {
    companion object {
        fun notFound(now: Instant) = MatchEntry(null, null, null, null, now, null, now)
    }
}

data class OmdbUsage(val day: LocalDate, val count: Int)

class MatchCache(
    entries: Map<String, MatchEntry> = emptyMap(),
    omdbUsage: OmdbUsage? = null,
    var lastProgrammeCount: Int? = null,
) {
    private val entries = entries.toMutableMap()

    var omdbUsage: OmdbUsage? = omdbUsage
        private set

    operator fun get(key: MatchKey): MatchEntry? = entries[key.value]

    operator fun set(key: MatchKey, entry: MatchEntry) {
        entries[key.value] = entry
    }

    fun entries(): Map<String, MatchEntry> = entries.toMap()

    fun omdbCallsOn(day: LocalDate): Int = omdbUsage?.takeIf { it.day == day }?.count ?: 0

    fun recordOmdbCall(day: LocalDate) {
        omdbUsage = OmdbUsage(day, omdbCallsOn(day) + 1)
    }

    fun prune(lastSeenBefore: Instant) {
        entries.values.removeIf { it.lastSeenAt.isBefore(lastSeenBefore) }
    }
}

data class ClassifiedProgramme(val programme: XmltvProgramme, val kind: ProgrammeKind, val key: MatchKey)

data class MatchRequest(val key: MatchKey, val title: String, val year: Int?)

data class MatchStats(
    val searched: Int,
    val matched: Int,
    val rated: Int,
    val failures: Int,
    val ratingsUnavailable: Boolean,
)

data class MatchOutcome(val entries: Map<MatchKey, MatchEntry>, val stats: MatchStats)

interface TitleResolver {
    fun resolve(requests: List<MatchRequest>, cache: MatchCache): MatchOutcome
}

interface MatchCacheRepository {
    fun load(): MatchCache
    fun save(cache: MatchCache)
}

class TitleMatcher(
    private val catalog: MovieCatalog,
    private val ratings: RatingSource,
    private val clock: Clock,
    private val dailyOmdbBudget: Int = DEFAULT_OMDB_BUDGET,
    private val ttl: Duration = DEFAULT_TTL,
) : TitleResolver {

    override fun resolve(requests: List<MatchRequest>, cache: MatchCache): MatchOutcome {
        val run = Run(cache, clock.instant())
        val unique = requests.distinctBy { it.key }

        unique.filter { run.needsSearch(cache[it.key]) }.forEach(run::search)
        unique.mapNotNull { request -> cache[request.key]?.takeIf(run::needsRating)?.let { request.key to it } }
            .sortedWith(compareBy({ (_, entry) -> entry.ratedAt ?: Instant.MIN }, { (key, _) -> key.kind != ProgrammeKind.MOVIE }))
            .forEach { (key, entry) -> run.rate(key, entry) }
        unique.forEach { request -> cache[request.key]?.let { cache[request.key] = it.copy(lastSeenAt = run.now) } }

        val entries = unique.mapNotNull { request -> cache[request.key]?.let { request.key to it } }.toMap()
        return MatchOutcome(entries, run.stats())
    }

    private inner class Run(private val cache: MatchCache, val now: Instant) {
        private val today = LocalDate.ofInstant(now, ZoneOffset.UTC)
        private val staleBefore = now.minus(ttl)
        private var ratingsAvailable = true
        private var searched = 0
        private var matched = 0
        private var rated = 0
        private var failures = 0

        fun needsSearch(entry: MatchEntry?): Boolean =
            entry == null || (entry.imdbId == null && entry.checkedAt.isBefore(staleBefore))

        fun needsRating(entry: MatchEntry): Boolean =
            entry.imdbId != null && (entry.ratedAt == null || entry.ratedAt.isBefore(staleBefore))

        fun search(request: MatchRequest) {
            searched++
            try {
                val found = find(request)
                if (found != null) matched++
                cache[request.key] = found ?: MatchEntry.notFound(now)
            } catch (e: IOException) {
                failures++
            }
        }

        fun rate(key: MatchKey, entry: MatchEntry) {
            val imdbId = entry.imdbId ?: return
            if (!ratingsAvailable || cache.omdbCallsOn(today) >= dailyOmdbBudget) return
            try {
                cache.recordOmdbCall(today)
                val score = ratings.rating(imdbId)
                cache[key] = entry.copy(rating = score?.rating, votes = score?.votes, ratedAt = now)
                if (score != null) rated++
            } catch (e: RatingSourceUnavailableException) {
                ratingsAvailable = false
                failures++
            } catch (e: IOException) {
                failures++
            }
        }

        fun stats() = MatchStats(searched, matched, rated, failures, !ratingsAvailable)

        private fun find(request: MatchRequest): MatchEntry? {
            for (candidate in TitleNormalizer.candidates(request.title, request.key.kind)) {
                val hit = catalog.search(candidate, request.key.kind)
                    .take(MAX_HITS)
                    .firstOrNull { accepts(it, candidate, request) }
                    ?: continue
                val imdbId = catalog.imdbId(hit) ?: continue
                return MatchEntry(imdbId, null, null, hit.posterUrl, now, null, now)
            }
            return null
        }

        private fun accepts(hit: CatalogHit, candidate: String, request: MatchRequest): Boolean {
            val wanted = TitleNormalizer.key(candidate)
            val titleMatches = TitleNormalizer.key(hit.title) == wanted || TitleNormalizer.key(hit.originalTitle) == wanted
            val yearMatches = request.key.kind == ProgrammeKind.SERIES ||
                request.year == null ||
                hit.year == null ||
                abs(hit.year - request.year) <= MAX_YEAR_DIFFERENCE
            return titleMatches && yearMatches
        }
    }

    companion object {
        const val DEFAULT_OMDB_BUDGET = 900
        val DEFAULT_TTL: Duration = Duration.ofDays(7)
        private const val MAX_HITS = 10
        private const val MAX_YEAR_DIFFERENCE = 1
    }
}
