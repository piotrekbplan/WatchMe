package pl.watchme.domain.model

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import assertk.assertions.isTrue
import java.time.Instant
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

class ProgrammeTest {

    private val start = Instant.parse("2026-09-28T18:00:00Z")
    private val stop = Instant.parse("2026-09-28T20:00:00Z")
    private val programme = Programme(
        channelId = ChannelId("tvp-1"),
        title = "Pianista",
        start = start,
        stop = stop,
        kind = ProgrammeKind.MOVIE,
        category = "film wojenny",
        year = 2002,
        imdbId = "tt0253474",
        rating = ImdbRating.of(8.5),
        votes = 950000,
        posterUrl = null,
    )

    @Test
    fun `airs from start inclusive to stop exclusive`() {
        assertThat(programme.isAiringAt(start)).isTrue()
        assertThat(programme.isAiringAt(stop.minusSeconds(1))).isTrue()
        assertThat(programme.isAiringAt(stop)).isFalse()
        assertThat(programme.isAiringAt(start.minusSeconds(1))).isFalse()
    }

    @Test
    fun `progress is clamped to the programme duration`() {
        assertThat(programme.progressAt(start.minusSeconds(60))).isEqualTo(0f)
        assertThat(programme.progressAt(Instant.parse("2026-09-28T19:00:00Z"))).isEqualTo(0.5f)
        assertThat(programme.progressAt(stop.plusSeconds(60))).isEqualTo(1f)
    }

    @ParameterizedTest
    @ValueSource(doubles = [0.0, 5.5, 10.0])
    fun `rating accepts values from zero to ten`(value: Double) {
        assertThat(ImdbRating.of(value)?.value).isNotNull().isEqualTo(value)
    }

    @ParameterizedTest
    @ValueSource(doubles = [-0.1, 10.1, Double.NaN])
    fun `rating rejects values outside the scale`(value: Double) {
        assertThat(ImdbRating.of(value)).isNull()
    }
}
