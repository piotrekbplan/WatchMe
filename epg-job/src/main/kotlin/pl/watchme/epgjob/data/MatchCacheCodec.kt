package pl.watchme.epgjob.data

import java.time.DateTimeException
import java.time.Instant
import java.time.LocalDate
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import pl.watchme.epgjob.domain.MatchCache
import pl.watchme.epgjob.domain.MatchEntry
import pl.watchme.epgjob.domain.OmdbUsage

class MatchCacheCodec {

    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }

    fun encode(cache: MatchCache): String =
        json.encodeToString(
            MatchCacheFile.serializer(),
            MatchCacheFile(
                entries = cache.entries().mapValues { (_, entry) ->
                    MatchEntryDto(
                        imdbId = entry.imdbId,
                        rating = entry.rating,
                        votes = entry.votes,
                        posterUrl = entry.posterUrl,
                        checkedAt = entry.checkedAt.toString(),
                        ratedAt = entry.ratedAt?.toString(),
                        lastSeenAt = entry.lastSeenAt.toString(),
                    )
                },
                omdbUsage = cache.omdbUsage?.let { OmdbUsageDto(it.day.toString(), it.count) },
                lastProgrammeCount = cache.lastProgrammeCount,
            ),
        )

    fun decode(text: String): MatchCache? =
        try {
            val file = json.decodeFromString(MatchCacheFile.serializer(), text)
            MatchCache(
                entries = file.entries.mapValues { (_, dto) ->
                    MatchEntry(
                        imdbId = dto.imdbId,
                        rating = dto.rating,
                        votes = dto.votes,
                        posterUrl = dto.posterUrl,
                        checkedAt = Instant.parse(dto.checkedAt),
                        ratedAt = dto.ratedAt?.let(Instant::parse),
                        lastSeenAt = Instant.parse(dto.lastSeenAt),
                    )
                },
                omdbUsage = file.omdbUsage?.let { OmdbUsage(LocalDate.parse(it.day), it.count) },
                lastProgrammeCount = file.lastProgrammeCount,
            )
        } catch (e: IllegalArgumentException) {
            null
        } catch (e: DateTimeException) {
            null
        }
}

@Serializable
private class MatchCacheFile(
    val entries: Map<String, MatchEntryDto> = emptyMap(),
    val omdbUsage: OmdbUsageDto? = null,
    val lastProgrammeCount: Int? = null,
)

@Serializable
private class MatchEntryDto(
    val imdbId: String? = null,
    val rating: Double? = null,
    val votes: Int? = null,
    val posterUrl: String? = null,
    val checkedAt: String,
    val ratedAt: String? = null,
    val lastSeenAt: String,
)

@Serializable
private class OmdbUsageDto(val day: String, val count: Int)
