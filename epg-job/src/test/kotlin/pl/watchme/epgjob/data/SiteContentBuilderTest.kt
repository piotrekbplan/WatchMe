package pl.watchme.epgjob.data

import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isNull
import java.time.Instant
import java.time.OffsetDateTime
import org.junit.jupiter.api.Test
import pl.watchme.epg.contract.ProgrammeKindDto
import pl.watchme.epgjob.domain.Catalog
import pl.watchme.epgjob.domain.ChannelPackage
import pl.watchme.epgjob.domain.ClassifiedProgramme
import pl.watchme.epgjob.domain.GroupDefinition
import pl.watchme.epgjob.domain.MatchEntry
import pl.watchme.epgjob.domain.MatchKey
import pl.watchme.epgjob.domain.ProgrammeKind
import pl.watchme.epgjob.domain.TvOperator
import pl.watchme.epgjob.domain.XmltvChannel
import pl.watchme.epgjob.domain.XmltvProgramme

class SiteContentBuilderTest {

    private val generatedAt = OffsetDateTime.parse("2026-09-27T12:00:00+02:00")
    private val catalog = Catalog.from(
        listOf(
            GroupDefinition("ogolne", "Ogólne", listOf("TVP 1", "TVN")),
            GroupDefinition("filmowe", "Filmowe", listOf("Canal+ Film", "Ghost Channel")),
        ),
    )
    private val operators = listOf(
        TvOperator("play", "Play", listOf(ChannelPackage("play-start", "Start", listOf("tvp-1", "canal-plus-film", "ghost-channel")))),
    )
    private val epgChannels = listOf(
        XmltvChannel("TVP 1", "TVP 1", "https://logo.example/tvp1.png"),
        XmltvChannel("TVN", "TVN", null),
        XmltvChannel("Canal+ Film", "Canal+ Film", null),
        XmltvChannel("Foreign X", "Foreign X", null),
    )
    private val pianista = classified("TVP 1", "Pianista", at("20:00"), at("22:00"), ProgrammeKind.MOVIE, 2002, "https://img.example/icon.jpg")
    private val ranczo = classified("TVP 1", "Ranczo odc. 12", at("18:00"), at("18:50"), ProgrammeKind.SERIES, 2009, "https://img.example/ranczo.jpg")
    private val nocny = classified(
        "Canal+ Film", "Nocny film",
        OffsetDateTime.parse("2026-09-28T23:00:00+01:00"), OffsetDateTime.parse("2026-09-29T01:00:00+01:00"),
        ProgrammeKind.MOVIE, 1999, null,
    )
    private val matches = mapOf(
        pianista.key to MatchEntry("tt0253474", 8.5, 950000, "https://image.tmdb.org/p.jpg", Instant.EPOCH, Instant.EPOCH, Instant.EPOCH),
    )

    private val content = SiteContentBuilder().build(generatedAt, catalog, operators, epgChannels, listOf(pianista, nocny, ranczo), matches)

    @Test
    fun `publishes only catalog channels present in epg`() {
        assertThat(content.channels.items.map { it.id }).containsExactly("tvp-1", "tvn", "canal-plus-film")
        val tvp = content.channels.items.first()
        assertThat(tvp.name).isEqualTo("TVP 1")
        assertThat(tvp.group).isEqualTo("ogolne")
        assertThat(tvp.groupName).isEqualTo("Ogólne")
        assertThat(tvp.logoUrl).isEqualTo("https://logo.example/tvp1.png")
    }

    @Test
    fun `packages drop channels that are not published`() {
        assertThat(content.operators.items.single().packages.single().channelIds).containsExactly("tvp-1", "canal-plus-film")
    }

    @Test
    fun `guides are sorted by start and carry ratings`() {
        val tvp = content.guides.single { it.channelId == "tvp-1" }

        assertThat(tvp.generatedAt).isEqualTo("2026-09-27T12:00:00+02:00")
        assertThat(tvp.programmes.map { it.title }).containsExactly("Ranczo odc. 12", "Pianista")
        val movie = tvp.programmes[1]
        assertThat(movie.kind).isEqualTo(ProgrammeKindDto.MOVIE)
        assertThat(movie.start).isEqualTo("2026-09-27T20:00:00+02:00")
        assertThat(movie.imdbId).isEqualTo("tt0253474")
        assertThat(movie.imdbRating).isEqualTo(8.5)
        assertThat(movie.imdbVotes).isEqualTo(950000)
        assertThat(movie.posterUrl).isEqualTo("https://image.tmdb.org/p.jpg")
    }

    @Test
    fun `unmatched programme falls back to epg icon`() {
        val series = content.guides.single { it.channelId == "tvp-1" }.programmes[0]

        assertThat(series.kind).isEqualTo(ProgrammeKindDto.SERIES)
        assertThat(series.imdbRating).isNull()
        assertThat(series.posterUrl).isEqualTo("https://img.example/ranczo.jpg")
    }

    @Test
    fun `guide keeps winter offset`() {
        val night = content.guides.single { it.channelId == "canal-plus-film" }.programmes.single()

        assertThat(night.start).isEqualTo("2026-09-28T23:00:00+01:00")
        assertThat(night.year).isEqualTo(1999)
    }

    @Test
    fun `published channel without programmes gets an empty guide`() {
        assertThat(content.guides.single { it.channelId == "tvn" }.programmes).isEmpty()
    }

    private fun classified(
        channel: String,
        title: String,
        start: OffsetDateTime,
        stop: OffsetDateTime,
        kind: ProgrammeKind,
        year: Int?,
        icon: String?,
    ) = ClassifiedProgramme(
        XmltvProgramme(channel, title, start, stop, if (kind == ProgrammeKind.MOVIE) "film" else "serial", year, icon),
        kind,
        MatchKey.of(title, kind, year),
    )

    private fun at(time: String) = OffsetDateTime.parse("2026-09-27T$time:00+02:00")
}
