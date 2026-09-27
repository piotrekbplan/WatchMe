package pl.watchme.epgjob.data

import assertk.assertFailure
import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.doesNotContain
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import assertk.assertions.isNull
import java.io.IOException
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.jupiter.api.Test
import pl.watchme.epgjob.domain.CatalogHit
import pl.watchme.epgjob.domain.ProgrammeKind
import pl.watchme.epgjob.fakes.FakeHttpGetter

class TmdbClientTest {

    private val http = FakeHttpGetter()
    private val client = TmdbClient(http, "secret-key", "https://tmdb.test/3/".toHttpUrl())

    @Test
    fun `searches movies in polish and maps hits`() {
        http.respond(
            200,
            """{"results":[
              {"id":423,"title":"Pianista","original_title":"The Pianist","release_date":"2002-09-24","poster_path":"/p.jpg"},
              {"id":9,"title":"Bez daty","original_title":"No date","release_date":""}
            ]}""",
        )

        val hits = client.search("Pianista", ProgrammeKind.MOVIE)

        assertThat(hits).containsExactly(
            CatalogHit(423, ProgrammeKind.MOVIE, "Pianista", "The Pianist", 2002, "https://image.tmdb.org/t/p/w342/p.jpg"),
            CatalogHit(9, ProgrammeKind.MOVIE, "Bez daty", "No date", null, null),
        )
        val url = http.requested.single()
        assertThat(url.encodedPath).isEqualTo("/3/search/movie")
        assertThat(url.queryParameter("query")).isEqualTo("Pianista")
        assertThat(url.queryParameter("language")).isEqualTo("pl-PL")
        assertThat(url.queryParameter("api_key")).isEqualTo("secret-key")
    }

    @Test
    fun `searches tv series`() {
        http.respond(200, """{"results":[{"id":77,"name":"Ranczo","original_name":"Ranczo","first_air_date":"2006-03-05"}]}""")

        val hits = client.search("Ranczo", ProgrammeKind.SERIES)

        assertThat(hits).containsExactly(CatalogHit(77, ProgrammeKind.SERIES, "Ranczo", "Ranczo", 2006, null))
        assertThat(http.requested.single().encodedPath).isEqualTo("/3/search/tv")
    }

    @Test
    fun `reads imdb id from external ids`() {
        http.respond(200, """{"id":423,"imdb_id":"tt0253474"}""")
        http.respond(200, """{"id":77,"imdb_id":null}""")

        assertThat(client.imdbId(hit(423, ProgrammeKind.MOVIE))).isEqualTo("tt0253474")
        assertThat(client.imdbId(hit(77, ProgrammeKind.SERIES))).isNull()
        assertThat(http.requested.map { it.encodedPath }).containsExactly("/3/movie/423/external_ids", "/3/tv/77/external_ids")
    }

    @Test
    fun `http error becomes io exception without leaking key`() {
        http.respond(500, "")

        assertFailure { client.search("Pianista", ProgrammeKind.MOVIE) }
            .isInstanceOf<IOException>()
            .transform { it.message.orEmpty() }
            .doesNotContain("secret-key")
    }

    @Test
    fun `malformed payload becomes io exception`() {
        http.respond(200, "<html>")

        assertFailure { client.search("Pianista", ProgrammeKind.MOVIE) }.isInstanceOf<IOException>()
    }

    @Test
    fun `network failure propagates`() {
        http.failure = IOException("offline")

        assertFailure { client.search("Pianista", ProgrammeKind.MOVIE) }.isInstanceOf<IOException>()
    }

    private fun hit(id: Long, kind: ProgrammeKind) = CatalogHit(id, kind, "", "", null, null)
}
