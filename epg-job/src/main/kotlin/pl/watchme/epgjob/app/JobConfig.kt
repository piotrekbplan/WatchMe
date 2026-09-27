package pl.watchme.epgjob.app

import java.nio.file.Path
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl

class JobConfig(
    val outDir: Path,
    val catalogPath: Path,
    val operatorsPath: Path,
    val sourceUrl: HttpUrl,
    val tmdbApiKey: String?,
    val omdbApiKey: String?,
) {
    override fun toString(): String =
        "JobConfig(outDir=$outDir, catalog=$catalogPath, operators=$operatorsPath, source=$sourceUrl, " +
            "tmdbKey=${tmdbApiKey != null}, omdbKey=${omdbApiKey != null})"

    companion object {
        const val DEFAULT_SOURCE = "https://epg.ovh/pltv.xml"
        private const val USAGE =
            "Usage: epg-job --out <dir> --catalog <catalog.yaml> --operators <operators.yaml> [--source-url <url>]"

        fun from(args: Array<String>, env: Map<String, String>): JobConfig {
            val options = args.toList().chunked(2).associate { chunk ->
                require(chunk.size == 2 && chunk[0].startsWith("--")) { USAGE }
                chunk[0].removePrefix("--") to chunk[1]
            }
            fun path(name: String): Path = Path.of(requireNotNull(options[name]) { "Missing --$name. $USAGE" })
            fun key(name: String): String? = env[name]?.takeIf { it.isNotBlank() }

            return JobConfig(
                outDir = path("out"),
                catalogPath = path("catalog"),
                operatorsPath = path("operators"),
                sourceUrl = (options["source-url"] ?: DEFAULT_SOURCE).toHttpUrl(),
                tmdbApiKey = key("TMDB_API_KEY"),
                omdbApiKey = key("OMDB_API_KEY"),
            )
        }
    }
}
