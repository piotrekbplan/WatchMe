package pl.watchme.epgjob.data

import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.containsExactlyInAnyOrder
import assertk.assertions.isEqualTo
import org.junit.jupiter.api.Test
import pl.watchme.epg.contract.ChannelDto
import pl.watchme.epg.contract.ChannelGuideFile
import pl.watchme.epg.contract.ChannelsFile
import pl.watchme.epg.contract.EpgContract
import pl.watchme.epg.contract.OperatorsFile
import pl.watchme.epgjob.app.SiteContent

class SiteFilesTest {

    private val content = SiteContent(
        channels = ChannelsFile(1, listOf(ChannelDto("tvp-1", "TVP 1", "ogolne", "Ogólne"))),
        operators = OperatorsFile(1, emptyList()),
        guides = listOf(ChannelGuideFile(1, "tvp-1", "2026-09-27T12:00:00+02:00", emptyList())),
    )

    @Test
    fun `renders contract files by relative path`() {
        val files = SiteFiles.render(content)

        assertThat(files.keys).containsExactlyInAnyOrder("channels.json", "operators.json", "epg/tvp-1.json")
        assertThat(files.getValue("operators.json")).isEqualTo("""{"schemaVersion":1,"items":[]}""")
        assertThat(EpgContract.json.decodeFromString(ChannelsFile.serializer(), files.getValue("channels.json")))
            .isEqualTo(content.channels)
        assertThat(EpgContract.json.decodeFromString(ChannelGuideFile.serializer(), files.getValue("epg/tvp-1.json")))
            .isEqualTo(content.guides.single())
    }

    @Test
    fun `guides no longer published are stale`() {
        assertThat(SiteFiles.staleGuides(listOf("epg/tvp-1.json", "epg/removed.json"), content))
            .containsExactly("epg/removed.json")
    }
}
