package pl.watchme.epgjob.data

import assertk.assertThat
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isNull
import java.time.Instant
import java.time.LocalDate
import org.junit.jupiter.api.Test
import pl.watchme.epgjob.domain.MatchCache
import pl.watchme.epgjob.domain.MatchEntry
import pl.watchme.epgjob.domain.MatchKey
import pl.watchme.epgjob.domain.OmdbUsage
import pl.watchme.epgjob.domain.ProgrammeKind

class MatchCacheCodecTest {

    private val codec = MatchCacheCodec()

    @Test
    fun `round trips entries usage and programme count`() {
        val key = MatchKey.of("Pianista", ProgrammeKind.MOVIE, 2002)
        val entry = MatchEntry(
            "tt1", 8.5, 100, "https://p",
            Instant.parse("2026-09-20T10:00:00Z"), Instant.parse("2026-09-21T10:00:00Z"), Instant.parse("2026-09-27T10:00:00Z"),
        )
        val notFoundKey = MatchKey.of("Nic", ProgrammeKind.SERIES, null)
        val cache = MatchCache(omdbUsage = OmdbUsage(LocalDate.parse("2026-09-27"), 42), lastProgrammeCount = 31000)
        cache[key] = entry
        cache[notFoundKey] = MatchEntry.notFound(Instant.parse("2026-09-27T10:00:00Z"))

        val decoded = requireNotNull(codec.decode(codec.encode(cache)))

        assertThat(decoded[key]).isEqualTo(entry)
        assertThat(decoded[notFoundKey]).isEqualTo(MatchEntry.notFound(Instant.parse("2026-09-27T10:00:00Z")))
        assertThat(decoded.omdbUsage).isEqualTo(OmdbUsage(LocalDate.parse("2026-09-27"), 42))
        assertThat(decoded.lastProgrammeCount).isEqualTo(31000)
    }

    @Test
    fun `empty document is an empty cache`() {
        val decoded = requireNotNull(codec.decode("{}"))

        assertThat(decoded.entries()).isEmpty()
        assertThat(decoded.lastProgrammeCount).isNull()
    }

    @Test
    fun `malformed json is rejected`() {
        assertThat(codec.decode("{not json")).isNull()
    }

    @Test
    fun `invalid timestamp is rejected`() {
        assertThat(codec.decode("""{"entries":{"MOVIE|x|-":{"checkedAt":"yesterday","lastSeenAt":"today"}}}""")).isNull()
    }
}
