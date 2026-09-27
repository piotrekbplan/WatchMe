package pl.watchme.epgjob.app

import java.time.OffsetDateTime
import pl.watchme.epg.contract.ChannelGuideFile
import pl.watchme.epg.contract.ChannelsFile
import pl.watchme.epg.contract.OperatorsFile
import pl.watchme.epgjob.domain.Catalog
import pl.watchme.epgjob.domain.ClassifiedProgramme
import pl.watchme.epgjob.domain.MatchEntry
import pl.watchme.epgjob.domain.MatchKey
import pl.watchme.epgjob.domain.TvOperator
import pl.watchme.epgjob.domain.XmltvChannel

data class SiteContent(
    val channels: ChannelsFile,
    val operators: OperatorsFile,
    val guides: List<ChannelGuideFile>,
)

interface SiteContentFactory {
    fun build(
        generatedAt: OffsetDateTime,
        catalog: Catalog,
        operators: List<TvOperator>,
        epgChannels: List<XmltvChannel>,
        programmes: List<ClassifiedProgramme>,
        matches: Map<MatchKey, MatchEntry>,
    ): SiteContent
}

interface SitePublisher {
    fun publish(content: SiteContent)
}
