package pl.watchme.epgjob.data

import java.time.OffsetDateTime
import pl.watchme.epg.contract.ChannelDto
import pl.watchme.epg.contract.ChannelGuideFile
import pl.watchme.epg.contract.ChannelsFile
import pl.watchme.epg.contract.EpgContract
import pl.watchme.epg.contract.OperatorDto
import pl.watchme.epg.contract.OperatorsFile
import pl.watchme.epg.contract.PackageDto
import pl.watchme.epg.contract.ProgrammeDto
import pl.watchme.epg.contract.ProgrammeKindDto
import pl.watchme.epgjob.app.SiteContent
import pl.watchme.epgjob.app.SiteContentFactory
import pl.watchme.epgjob.domain.Catalog
import pl.watchme.epgjob.domain.ClassifiedProgramme
import pl.watchme.epgjob.domain.MatchEntry
import pl.watchme.epgjob.domain.MatchKey
import pl.watchme.epgjob.domain.TvOperator
import pl.watchme.epgjob.domain.XmltvChannel

class SiteContentBuilder : SiteContentFactory {

    override fun build(
        generatedAt: OffsetDateTime,
        catalog: Catalog,
        operators: List<TvOperator>,
        epgChannels: List<XmltvChannel>,
        programmes: List<ClassifiedProgramme>,
        matches: Map<MatchKey, MatchEntry>,
    ): SiteContent {
        val epgById = epgChannels.associateBy { it.id }
        val published = catalog.channels.filter { it.epgId in epgById }
        val publishedSlugs = published.mapTo(HashSet()) { it.slug }
        val programmesByChannel = programmes.groupBy { it.programme.channelId }

        val channels = published.map { channel ->
            val epg = epgById.getValue(channel.epgId)
            ChannelDto(channel.slug, epg.displayName, channel.groupId, channel.groupName, epg.iconUrl)
        }
        val operatorDtos = operators.map { operator ->
            OperatorDto(
                id = operator.id,
                name = operator.name,
                packages = operator.packages.map { pkg ->
                    PackageDto(pkg.id, pkg.name, pkg.channelSlugs.filter { it in publishedSlugs })
                },
            )
        }
        val guides = published.map { channel ->
            ChannelGuideFile(
                schemaVersion = EpgContract.SCHEMA_VERSION,
                channelId = channel.slug,
                generatedAt = EpgContract.formatTimestamp(generatedAt),
                programmes = programmesByChannel[channel.epgId].orEmpty()
                    .sortedBy { it.programme.start.toInstant() }
                    .map { toDto(it, matches[it.key]) },
            )
        }
        return SiteContent(
            channels = ChannelsFile(EpgContract.SCHEMA_VERSION, channels),
            operators = OperatorsFile(EpgContract.SCHEMA_VERSION, operatorDtos),
            guides = guides,
        )
    }

    private fun toDto(classified: ClassifiedProgramme, match: MatchEntry?): ProgrammeDto {
        val programme = classified.programme
        return ProgrammeDto(
            title = programme.title,
            start = EpgContract.formatTimestamp(programme.start),
            stop = EpgContract.formatTimestamp(programme.stop),
            kind = ProgrammeKindDto.valueOf(classified.kind.name),
            category = programme.category.orEmpty(),
            year = programme.year,
            imdbId = match?.imdbId,
            imdbRating = match?.rating,
            imdbVotes = match?.votes,
            posterUrl = match?.posterUrl ?: programme.iconUrl,
        )
    }
}
