package pl.watchme.epgjob.data

import java.io.IOException
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import pl.watchme.epgjob.domain.CatalogHit
import pl.watchme.epgjob.domain.MovieCatalog
import pl.watchme.epgjob.domain.ProgrammeKind

class TmdbClient(
    private val http: HttpGetter,
    private val apiKey: String,
    private val baseUrl: HttpUrl = DEFAULT_BASE_URL,
) : MovieCatalog {

    private val json = Json { ignoreUnknownKeys = true }

    override fun search(title: String, kind: ProgrammeKind): List<CatalogHit> {
        val url = baseUrl.newBuilder()
            .addPathSegments(if (kind == ProgrammeKind.MOVIE) "search/movie" else "search/tv")
            .addQueryParameter("query", title)
            .addQueryParameter("language", "pl-PL")
            .addQueryParameter("api_key", apiKey)
            .build()
        val body = get(url)
        return when (kind) {
            ProgrammeKind.MOVIE -> decode(MovieSearch.serializer(), body).results.map {
                CatalogHit(it.id, kind, it.title, it.originalTitle, year(it.releaseDate), poster(it.posterPath))
            }
            ProgrammeKind.SERIES -> decode(TvSearch.serializer(), body).results.map {
                CatalogHit(it.id, kind, it.name, it.originalName, year(it.firstAirDate), poster(it.posterPath))
            }
        }
    }

    override fun imdbId(hit: CatalogHit): String? {
        val segment = if (hit.kind == ProgrammeKind.MOVIE) "movie" else "tv"
        val url = baseUrl.newBuilder()
            .addPathSegments("$segment/${hit.tmdbId}/external_ids")
            .addQueryParameter("api_key", apiKey)
            .build()
        return decode(ExternalIds.serializer(), get(url)).imdbId?.takeIf { it.startsWith("tt") }
    }

    private fun get(url: HttpUrl): String {
        val result = http.get(url)
        if (!result.isSuccessful) throw IOException("TMDB returned ${result.code} for ${url.encodedPath}")
        return result.body
    }

    private fun <T> decode(deserializer: DeserializationStrategy<T>, body: String): T =
        try {
            json.decodeFromString(deserializer, body)
        } catch (e: SerializationException) {
            throw IOException("TMDB returned unexpected payload", e)
        }

    private fun year(date: String?): Int? = date?.take(4)?.toIntOrNull()

    private fun poster(path: String?): String? = path?.let { "$POSTER_BASE$it" }

    companion object {
        val DEFAULT_BASE_URL: HttpUrl = "https://api.themoviedb.org/3/".toHttpUrl()
        private const val POSTER_BASE = "https://image.tmdb.org/t/p/w342"
    }
}

@Serializable
private class MovieSearch(val results: List<MovieResult> = emptyList())

@Serializable
private class MovieResult(
    val id: Long,
    val title: String = "",
    @SerialName("original_title") val originalTitle: String = "",
    @SerialName("release_date") val releaseDate: String? = null,
    @SerialName("poster_path") val posterPath: String? = null,
)

@Serializable
private class TvSearch(val results: List<TvResult> = emptyList())

@Serializable
private class TvResult(
    val id: Long,
    val name: String = "",
    @SerialName("original_name") val originalName: String = "",
    @SerialName("first_air_date") val firstAirDate: String? = null,
    @SerialName("poster_path") val posterPath: String? = null,
)

@Serializable
private class ExternalIds(@SerialName("imdb_id") val imdbId: String? = null)
