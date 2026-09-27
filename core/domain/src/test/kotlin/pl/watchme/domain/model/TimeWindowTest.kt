package pl.watchme.domain.model

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isTrue
import java.time.Instant
import org.junit.jupiter.api.Test

class TimeWindowTest {

    private val now = Instant.parse("2026-09-28T18:00:00Z")

    @Test
    fun `cache window spans three hours back and a day ahead`() {
        assertThat(TimeWindow.around(now)).isEqualTo(
            TimeWindow(Instant.parse("2026-09-28T15:00:00Z"), Instant.parse("2026-09-29T18:00:00Z")),
        )
    }

    @Test
    fun `ranking range spans from now to a day ahead`() {
        assertThat(TimeWindow.rankingRange(now)).isEqualTo(TimeWindow(now, Instant.parse("2026-09-29T18:00:00Z")))
    }

    @Test
    fun `contains is inclusive at start and exclusive at end`() {
        val window = TimeWindow(now, now.plusSeconds(60))

        assertThat(window.contains(now)).isTrue()
        assertThat(window.contains(now.plusSeconds(60))).isFalse()
    }

    @Test
    fun `clamp keeps moments inside and pulls outside ones to the edges`() {
        val range = TimeWindow.rankingRange(now)

        assertThat(range.clamp(now.plusSeconds(3600))).isEqualTo(now.plusSeconds(3600))
        assertThat(range.clamp(now.minusSeconds(3600))).isEqualTo(now)
        assertThat(range.clamp(now.plusSeconds(3 * 86400))).isEqualTo(range.until)
    }
}
