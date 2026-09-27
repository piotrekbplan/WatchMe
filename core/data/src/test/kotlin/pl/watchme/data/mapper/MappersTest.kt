package pl.watchme.data.mapper

import assertk.assertFailure
import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import assertk.assertions.isNull
import java.time.Instant
import org.junit.jupiter.api.Test
import pl.watchme.data.local.LineupEntity
import pl.watchme.data.local.ProgrammeEntity
import pl.watchme.domain.model.ChannelId
import pl.watchme.domain.model.ChannelLineup
import pl.watchme.domain.model.ImdbRating
import pl.watchme.domain.model.PackageRef
import pl.watchme.domain.model.ProgrammeKind
import pl.watchme.epg.contract.ChannelDto
import pl.watchme.epg.contract.ChannelGuideFile
import pl.watchme.epg.contract.ChannelsFile
import pl.watchme.epg.contract.OperatorDto
import pl.watchme.epg.contract.OperatorsFile
import pl.watchme.epg.contract.PackageDto
import pl.watchme.epg.contract.ProgrammeDto
import pl.watchme.epg.contract.ProgrammeKindDto

class MappersTest {

    private val channels = ChannelsFile(
        1,
        listOf(
            ChannelDto("tvp-1", "TVP 1", "ogolne", "Ogólne", "http://epg.ovh/logo/TVP+1.png"),
            ChannelDto("hbo", "HBO", "filmowe", "Filmowe", null),
        ),
    )
    private val operators = OperatorsFile(
        1,
        listOf(OperatorDto("play", "Play", null, listOf(PackageDto("play-start", "Start", listOf("tvp-1", "gone"))))),
    )

    @Test
    fun `catalog maps channels and upgrades logos to https`() {
        val catalog = CatalogMapper.toDomain(channels, operators)

        assertThat(catalog.channels.map { it.id }).containsExactly(ChannelId("tvp-1"), ChannelId("hbo"))
        assertThat(catalog.channel(ChannelId("tvp-1"))?.logoUrl).isEqualTo("https://epg.ovh/logo/TVP+1.png")
        assertThat(catalog.channel(ChannelId("hbo"))?.groupName).isEqualTo("Filmowe")
    }

    @Test
    fun `packages drop channels missing from the catalog`() {
        val pkg = CatalogMapper.toDomain(channels, operators).operators.single().packages.single()

        assertThat(pkg.channelIds).containsExactly(ChannelId("tvp-1"))
    }

    @Test
    fun `unsupported schema version is rejected`() {
        assertFailure { CatalogMapper.toDomain(channels.copy(schemaVersion = 2), operators) }
            .isInstanceOf<UnsupportedSchemaException>()
        assertFailure { GuideMapper.toEntities(ChannelGuideFile(3, "tvp-1", "2026-09-28T12:00:00+02:00", emptyList())) }
            .isInstanceOf<UnsupportedSchemaException>()
    }

    @Test
    fun `guide programmes become entities with epoch times`() {
        val file = ChannelGuideFile(
            1,
            "tvp-1",
            "2026-09-28T12:00:00+02:00",
            listOf(
                ProgrammeDto(
                    title = "Pianista",
                    start = "2026-09-28T20:00:00+02:00",
                    stop = "2026-09-28T22:00:00+02:00",
                    kind = ProgrammeKindDto.MOVIE,
                    category = "film wojenny",
                    year = 2002,
                    imdbId = "tt0253474",
                    imdbRating = 8.5,
                    imdbVotes = 950000,
                    posterUrl = "http://img.example/p.jpg",
                ),
            ),
        )

        val entity = GuideMapper.toEntities(file).single()

        assertThat(entity).isEqualTo(
            ProgrammeEntity(
                channelId = "tvp-1",
                startMillis = Instant.parse("2026-09-28T18:00:00Z").toEpochMilli(),
                stopMillis = Instant.parse("2026-09-28T20:00:00Z").toEpochMilli(),
                title = "Pianista",
                kind = "MOVIE",
                category = "film wojenny",
                year = 2002,
                imdbId = "tt0253474",
                rating = 8.5,
                votes = 950000,
                posterUrl = "https://img.example/p.jpg",
            ),
        )
    }

    @Test
    fun `programme entity maps back to the domain`() {
        val entity = ProgrammeEntity("tvn", 0, 3_600_000, "Ranczo", "SERIES", "serial", null, null, 11.0, null, null)

        val programme = entity.toDomain()

        assertThat(programme.channelId).isEqualTo(ChannelId("tvn"))
        assertThat(programme.kind).isEqualTo(ProgrammeKind.SERIES)
        assertThat(programme.stop).isEqualTo(Instant.ofEpochSecond(3600))
        assertThat(programme.rating).isNull()
    }

    @Test
    fun `lineup survives a round trip`() {
        val lineup = ChannelLineup(setOf(ChannelId("tvp-1"), ChannelId("hbo")), PackageRef("play", "play-start"), Instant.ofEpochSecond(1000))

        assertThat(lineup.toEntity().toDomain()).isEqualTo(lineup)
        assertThat(lineup.toEntity().channelIds).isEqualTo("hbo,tvp-1")
    }

    @Test
    fun `empty lineup without source survives a round trip`() {
        val lineup = ChannelLineup.empty(Instant.ofEpochSecond(5))

        val restored = lineup.toEntity().toDomain()

        assertThat(restored.channelIds).isEmpty()
        assertThat(restored.source).isNull()
        assertThat(LineupEntity(channelIds = "", operatorId = "play", packageId = null, updatedAtMillis = 5000).toDomain().source).isNull()
    }

    @Test
    fun `rating inside the scale is kept`() {
        val entity = ProgrammeEntity("tvn", 0, 1, "X", "MOVIE", "film", null, null, 7.4, 10, null)

        assertThat(entity.toDomain().rating).isEqualTo(ImdbRating.of(7.4))
    }
}
