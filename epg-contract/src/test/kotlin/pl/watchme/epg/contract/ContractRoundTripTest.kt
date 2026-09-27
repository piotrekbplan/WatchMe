package pl.watchme.epg.contract

import assertk.assertThat
import assertk.assertions.doesNotContain
import assertk.assertions.hasSize
import assertk.assertions.isEqualTo
import assertk.assertions.isNull
import java.time.OffsetDateTime
import java.time.ZoneOffset
import org.junit.jupiter.api.Test

class ContractRoundTripTest {

    private val json = EpgContract.json

    @Test
    fun `channels sample survives round trip`() {
        val decoded = json.decodeFromString(ChannelsFile.serializer(), ContractSamples.read(ContractSamples.CHANNELS))

        assertThat(decoded.schemaVersion).isEqualTo(EpgContract.SCHEMA_VERSION)
        assertThat(decoded.items).hasSize(2)
        assertThat(decoded.items[1].logoUrl).isNull()
        val again = json.decodeFromString(ChannelsFile.serializer(), json.encodeToString(ChannelsFile.serializer(), decoded))
        assertThat(again).isEqualTo(decoded)
    }

    @Test
    fun `operators sample survives round trip`() {
        val decoded = json.decodeFromString(OperatorsFile.serializer(), ContractSamples.read(ContractSamples.OPERATORS))

        assertThat(decoded.items.single().packages[1].channelIds).isEqualTo(listOf("tvp-1", "canal-plus-film"))
        val again = json.decodeFromString(OperatorsFile.serializer(), json.encodeToString(OperatorsFile.serializer(), decoded))
        assertThat(again).isEqualTo(decoded)
    }

    @Test
    fun `guide sample keeps optional fields absent`() {
        val decoded = json.decodeFromString(ChannelGuideFile.serializer(), ContractSamples.read(ContractSamples.GUIDE))
        val unrated = decoded.programmes.single { it.kind == ProgrammeKindDto.SERIES }

        assertThat(unrated.imdbId).isNull()
        assertThat(unrated.imdbRating).isNull()
        val encoded = json.encodeToString(ChannelGuideFile.serializer(), decoded)
        assertThat(encoded).doesNotContain("null")
        assertThat(json.decodeFromString(ChannelGuideFile.serializer(), encoded)).isEqualTo(decoded)
    }

    @Test
    fun `unknown fields are ignored`() {
        val text = """{"schemaVersion":1,"items":[],"addedInFuture":true}"""

        assertThat(json.decodeFromString(ChannelsFile.serializer(), text).items).hasSize(0)
    }

    @Test
    fun `timestamps keep offset and seconds`() {
        val summer = OffsetDateTime.of(2026, 9, 27, 20, 10, 0, 0, ZoneOffset.ofHours(2))
        val winter = OffsetDateTime.of(2026, 11, 2, 7, 0, 0, 0, ZoneOffset.ofHours(1))

        assertThat(EpgContract.formatTimestamp(summer)).isEqualTo("2026-09-27T20:10:00+02:00")
        assertThat(EpgContract.formatTimestamp(winter)).isEqualTo("2026-11-02T07:00:00+01:00")
        assertThat(EpgContract.parseTimestamp("2026-09-27T20:10:00+02:00")).isEqualTo(summer)
    }

    @Test
    fun `guide file path uses channel id`() {
        assertThat(EpgContract.guideFile("tvp-1")).isEqualTo("epg/tvp-1.json")
    }
}
