package pl.watchme.epgjob.data

import assertk.assertThat
import assertk.assertions.containsExactlyInAnyOrder
import assertk.assertions.isEqualTo
import assertk.assertions.isNull
import java.time.OffsetDateTime
import org.junit.jupiter.api.Test
import pl.watchme.epgjob.domain.GuideWindow
import pl.watchme.epgjob.domain.ParsedGuide
import pl.watchme.epgjob.domain.ProgrammeClassifier
import pl.watchme.epgjob.domain.XmltvChannel
import pl.watchme.epgjob.domain.XmltvProgramme

class XmltvParserTest {

    private val window = GuideWindow.around(OffsetDateTime.parse("2026-09-27T12:00:00+02:00"))

    private fun parse(keep: (XmltvProgramme) -> Boolean = { true }): ParsedGuide =
        requireNotNull(javaClass.classLoader.getResourceAsStream("xmltv/sample.xml")).use {
            XmltvParser().parse(it, window, keep)
        }

    @Test
    fun `reads channels with first display name and icon`() {
        assertThat(parse().channels).containsExactlyInAnyOrder(
            XmltvChannel("TVP 1", "TVP 1", "https://logo.example/tvp1.png"),
            XmltvChannel("Canal+ Film", "Canal+ Film", null),
            XmltvChannel("Foreign X", "Foreign X", null),
        )
    }

    @Test
    fun `counts every programme in window and skips malformed ones`() {
        val guide = parse()

        assertThat(guide.programmesInWindow).isEqualTo(6)
        assertThat(guide.programmes.map { it.title }).containsExactlyInAnyOrder(
            "Pianista", "Ranczo odc. 12", "Wiadomości", "Diuna", "Nocny film", "Foreign Movie",
        )
    }

    @Test
    fun `keep predicate filters programmes but not the window count`() {
        val guide = parse { ProgrammeClassifier.classify(it.category) != null }

        assertThat(guide.programmesInWindow).isEqualTo(6)
        assertThat(guide.programmes.map { it.title }).containsExactlyInAnyOrder(
            "Pianista", "Ranczo odc. 12", "Diuna", "Nocny film", "Foreign Movie",
        )
    }

    @Test
    fun `programme airing at window start is kept`() {
        assertThat(parse().programmes.single { it.title == "Diuna" }.stop)
            .isEqualTo(OffsetDateTime.parse("2026-09-27T09:30:00+02:00"))
    }

    @Test
    fun `reads first category year and icon`() {
        val pianista = parse().programmes.single { it.title == "Pianista" }

        assertThat(pianista.category).isEqualTo("film wojenny")
        assertThat(pianista.year).isEqualTo(2002)
        assertThat(pianista.iconUrl).isEqualTo("https://img.example/pianista.jpg")
        assertThat(parse().programmes.single { it.title == "Diuna" }.iconUrl).isNull()
    }

    @Test
    fun `keeps original offsets`() {
        val night = parse().programmes.single { it.title == "Nocny film" }

        assertThat(night.start).isEqualTo(OffsetDateTime.parse("2026-09-28T23:00:00+01:00"))
        assertThat(night.start.offset.totalSeconds).isEqualTo(3600)
        assertThat(night.year).isEqualTo(1999)
    }
}
