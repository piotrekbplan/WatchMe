package pl.watchme.epgjob.data

import assertk.assertFailure
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import assertk.assertions.isNull
import java.io.IOException
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.jupiter.api.Test
import pl.watchme.epgjob.domain.ImdbScore
import pl.watchme.epgjob.domain.RatingSourceUnavailableException
import pl.watchme.epgjob.fakes.FakeHttpGetter

class OmdbClientTest {

    private val http = FakeHttpGetter()
    private val client = OmdbClient(http, "omdb-key", "https://omdb.test/".toHttpUrl())

    @Test
    fun `parses rating and votes with thousands separator`() {
        http.respond(200, """{"Response":"True","imdbRating":"8.5","imdbVotes":"950,123"}""")

        assertThat(client.rating("tt0253474")).isEqualTo(ImdbScore(8.5, 950123))
        val url = http.requested.single()
        assertThat(url.queryParameter("i")).isEqualTo("tt0253474")
        assertThat(url.queryParameter("apikey")).isEqualTo("omdb-key")
    }

    @Test
    fun `not available rating is null`() {
        http.respond(200, """{"Response":"True","imdbRating":"N/A","imdbVotes":"N/A"}""")

        assertThat(client.rating("tt1")).isNull()
    }

    @Test
    fun `unknown id is null`() {
        http.respond(200, """{"Response":"False","Error":"Incorrect IMDb ID."}""")

        assertThat(client.rating("tt1")).isNull()
    }

    @Test
    fun `request limit makes source unavailable`() {
        http.respond(401, """{"Response":"False","Error":"Request limit reached!"}""")

        assertFailure { client.rating("tt1") }.isInstanceOf<RatingSourceUnavailableException>()
    }

    @Test
    fun `invalid key makes source unavailable`() {
        http.respond(401, """{"Response":"False","Error":"Invalid API key!"}""")

        assertFailure { client.rating("tt1") }.isInstanceOf<RatingSourceUnavailableException>()
    }

    @Test
    fun `server error is io exception`() {
        http.respond(503, "down")

        assertFailure { client.rating("tt1") }.isInstanceOf<IOException>()
    }

    @Test
    fun `malformed payload is io exception`() {
        http.respond(200, "<html>")

        assertFailure { client.rating("tt1") }.isInstanceOf<IOException>()
    }
}
