package pl.watchme.epgjob.app

import assertk.assertFailure
import assertk.assertThat
import assertk.assertions.doesNotContain
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import assertk.assertions.isNull
import assertk.assertions.messageContains
import java.nio.file.Path
import org.junit.jupiter.api.Test

class JobConfigTest {

    private val args = arrayOf("--out", "site", "--catalog", "data/catalog.yaml", "--operators", "data/operators.yaml")

    @Test
    fun `parses paths keys and default source`() {
        val config = JobConfig.from(args, mapOf("TMDB_API_KEY" to "tmdb", "OMDB_API_KEY" to "omdb"))

        assertThat(config.outDir).isEqualTo(Path.of("site"))
        assertThat(config.catalogPath).isEqualTo(Path.of("data/catalog.yaml"))
        assertThat(config.operatorsPath).isEqualTo(Path.of("data/operators.yaml"))
        assertThat(config.sourceUrl.toString()).isEqualTo(JobConfig.DEFAULT_SOURCE)
        assertThat(config.tmdbApiKey).isEqualTo("tmdb")
        assertThat(config.omdbApiKey).isEqualTo("omdb")
    }

    @Test
    fun `custom source url is used`() {
        val config = JobConfig.from(args + arrayOf("--source-url", "https://example.org/guide.xml"), emptyMap())

        assertThat(config.sourceUrl.toString()).isEqualTo("https://example.org/guide.xml")
    }

    @Test
    fun `blank keys are treated as missing`() {
        val config = JobConfig.from(args, mapOf("TMDB_API_KEY" to " ", "OMDB_API_KEY" to ""))

        assertThat(config.tmdbApiKey).isNull()
        assertThat(config.omdbApiKey).isNull()
    }

    @Test
    fun `missing option fails with usage`() {
        assertFailure { JobConfig.from(arrayOf("--out", "site"), emptyMap()) }
            .isInstanceOf<IllegalArgumentException>()
            .messageContains("--catalog")
    }

    @Test
    fun `dangling option fails`() {
        assertFailure { JobConfig.from(arrayOf("--out"), emptyMap()) }.isInstanceOf<IllegalArgumentException>()
    }

    @Test
    fun `to string never reveals keys`() {
        val text = JobConfig.from(args, mapOf("TMDB_API_KEY" to "tmdb-secret", "OMDB_API_KEY" to "omdb-secret")).toString()

        assertThat(text).doesNotContain("tmdb-secret")
        assertThat(text).doesNotContain("omdb-secret")
    }
}
