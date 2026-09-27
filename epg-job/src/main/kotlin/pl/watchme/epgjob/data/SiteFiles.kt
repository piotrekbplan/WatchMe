package pl.watchme.epgjob.data

import pl.watchme.epg.contract.ChannelGuideFile
import pl.watchme.epg.contract.ChannelsFile
import pl.watchme.epg.contract.EpgContract
import pl.watchme.epg.contract.OperatorsFile
import pl.watchme.epgjob.app.SiteContent

object SiteFiles {

    fun render(content: SiteContent): Map<String, String> {
        val json = EpgContract.json
        return buildMap {
            put(EpgContract.CHANNELS_FILE, json.encodeToString(ChannelsFile.serializer(), content.channels))
            put(EpgContract.OPERATORS_FILE, json.encodeToString(OperatorsFile.serializer(), content.operators))
            content.guides.forEach { guide ->
                put(EpgContract.guideFile(guide.channelId), json.encodeToString(ChannelGuideFile.serializer(), guide))
            }
        }
    }

    fun staleGuides(existingGuideFiles: Collection<String>, content: SiteContent): List<String> {
        val current = content.guides.mapTo(HashSet()) { EpgContract.guideFile(it.channelId) }
        return existingGuideFiles.filterNot { it in current }
    }
}
