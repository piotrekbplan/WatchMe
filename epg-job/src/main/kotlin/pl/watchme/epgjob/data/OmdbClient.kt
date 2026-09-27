package pl.watchme.epgjob.data

import java.io.IOException
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import pl.watchme.epgjob.domain.ImdbScore
import pl.watchme.epgjob.domain.RatingSource
import pl.watchme.epgjob.domain.RatingSourceUnavailableException

class OmdbClient(
    private val http: HttpGetter,
    private val apiKey: String,
    private val baseUrl: HttpUrl = DEFAULT_BASE_URL,
) : RatingSource {

    private val json = Json { ignoreUnknownKeys = true }

    override fun rating(imdbId: String): ImdbScore? {
        val url = baseUrl.newBuilder()
            .addQueryParameter("i", imdbId)
            .addQueryParameter("apikey", apiKey)
            .build()
        val result = http.get(url)
        if (result.code >= 500) throw IOException("OMDb returned ${result.code}")
        val payload = try {
            json.decodeFromString(OmdbResponse.serializer(), result.body)
        } catch (e: SerializationException) {
            throw IOException("OMDb returned unexpected payload (${result.code})", e)
        }
        if (payload.response != "True") {
            val error = payload.error.orEmpty()
            if (error.contains("limit", ignoreCase = true) || error.contains("API key", ignoreCase = true)) {
                throw RatingSourceUnavailableException("OMDb unavailable: $error")
            }
            return null
        }
        val rating = payload.imdbRating?.toDoubleOrNull() ?: return null
        return ImdbScore(rating, payload.imdbVotes?.replace(",", "")?.toIntOrNull())
    }

    companion object {
        val DEFAULT_BASE_URL: HttpUrl = "https://www.omdbapi.com/".toHttpUrl()
    }
}

@Serializable
private class OmdbResponse(
    @SerialName("Response") val response: String = "False",
    @SerialName("Error") val error: String? = null,
    val imdbRating: String? = null,
    val imdbVotes: String? = null,
)
