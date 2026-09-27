package pl.watchme.epgjob.domain

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isNull
import assertk.assertions.isTrue
import java.time.OffsetDateTime
import org.junit.jupiter.api.Test

class ProgrammesTest {

    private val now = OffsetDateTime.parse("2026-09-27T12:00:00+02:00")
    private val window = GuideWindow.around(now)

    @Test
    fun `classifies movies and series by category prefix`() {
        assertThat(ProgrammeClassifier.classify("film wojenny")).isEqualTo(ProgrammeKind.MOVIE)
        assertThat(ProgrammeClassifier.classify(" Film dokumentalny")).isEqualTo(ProgrammeKind.MOVIE)
        assertThat(ProgrammeClassifier.classify("serial obyczajowy")).isEqualTo(ProgrammeKind.SERIES)
        assertThat(ProgrammeClassifier.classify("program informacyjny")).isNull()
        assertThat(ProgrammeClassifier.classify(null)).isNull()
    }

    @Test
    fun `window spans three hours back and forty eight ahead`() {
        assertThat(window.from).isEqualTo(OffsetDateTime.parse("2026-09-27T09:00:00+02:00"))
        assertThat(window.until).isEqualTo(OffsetDateTime.parse("2026-09-29T12:00:00+02:00"))
    }

    @Test
    fun `overlap excludes programmes touching window edges`() {
        assertThat(window.overlaps(at("07:00"), at("09:30"))).isTrue()
        assertThat(window.overlaps(at("07:00"), at("09:00"))).isFalse()
        assertThat(window.overlaps(OffsetDateTime.parse("2026-09-29T12:00:00+02:00"), OffsetDateTime.parse("2026-09-29T13:00:00+02:00"))).isFalse()
    }

    @Test
    fun `overlap compares instants across offsets`() {
        assertThat(window.overlaps(OffsetDateTime.parse("2026-09-27T06:30:00Z"), OffsetDateTime.parse("2026-09-27T07:30:00Z"))).isTrue()
    }

    private fun at(time: String) = OffsetDateTime.parse("2026-09-27T$time:00+02:00")
}
