package pl.watchme.data.mapper

import java.time.Instant
import pl.watchme.data.local.LineupEntity
import pl.watchme.data.local.ProgrammeEntity
import pl.watchme.domain.model.Catalog
import pl.watchme.domain.model.Channel
import pl.watchme.domain.model.ChannelId
import pl.watchme.domain.model.ChannelLineup
import pl.watchme.domain.model.ChannelPackage
import pl.watchme.domain.model.ImdbRating
import pl.watchme.domain.model.PackageRef
import pl.watchme.domain.model.Programme
import pl.watchme.domain.model.ProgrammeKind
import pl.watchme.domain.model.TvOperator
import pl.watchme.epg.contract.ChannelGuideFile
import pl.watchme.epg.contract.ChannelsFile
import pl.watchme.epg.contract.EpgContract
import pl.watchme.epg.contract.OperatorsFile
import pl.watchme.epg.contract.ProgrammeDto

class UnsupportedSchemaException(version: Int) : RuntimeException("Unsupported EPG schema version $version")

object CatalogMapper {

    fun toDomain(channels: ChannelsFile, operators: OperatorsFile): Catalog {
        requireSupported(channels.schemaVersion)
        requireSupported(operators.schemaVersion)
        val domainChannels = channels.items.map {
            Channel(ChannelId(it.id), it.name, it.group, it.groupName, it.logoUrl?.let(::secureUrl))
        }
        val known = domainChannels.mapTo(HashSet()) { it.id }
        val domainOperators = operators.items.map { operator ->
            TvOperator(
                id = operator.id,
                name = operator.name,
                packages = operator.packages.map { pkg ->
                    ChannelPackage(pkg.id, pkg.name, pkg.channelIds.map(::ChannelId).filter { it in known })
                },
            )
        }
        return Catalog(domainChannels, domainOperators)
    }
}

object GuideMapper {

    fun toEntities(file: ChannelGuideFile): List<ProgrammeEntity> {
        requireSupported(file.schemaVersion)
        return file.programmes.map { it.toEntity(file.channelId) }
    }

    private fun ProgrammeDto.toEntity(channelId: String) = ProgrammeEntity(
        channelId = channelId,
        startMillis = EpgContract.parseTimestamp(start).toInstant().toEpochMilli(),
        stopMillis = EpgContract.parseTimestamp(stop).toInstant().toEpochMilli(),
        title = title,
        kind = kind.name,
        category = category,
        year = year,
        imdbId = imdbId,
        rating = imdbRating,
        votes = imdbVotes,
        posterUrl = posterUrl?.let(::secureUrl),
    )
}

fun ProgrammeEntity.toDomain() = Programme(
    channelId = ChannelId(channelId),
    title = title,
    start = Instant.ofEpochMilli(startMillis),
    stop = Instant.ofEpochMilli(stopMillis),
    kind = ProgrammeKind.valueOf(kind),
    category = category,
    year = year,
    imdbId = imdbId,
    rating = rating?.let(ImdbRating::of),
    votes = votes,
    posterUrl = posterUrl,
)

fun LineupEntity.toDomain(): ChannelLineup {
    val source = if (operatorId != null && packageId != null) PackageRef(operatorId, packageId) else null
    val ids = channelIds.split(CHANNEL_SEPARATOR).filter { it.isNotBlank() }.map(::ChannelId).toSet()
    return ChannelLineup(ids, source, Instant.ofEpochMilli(updatedAtMillis))
}

fun ChannelLineup.toEntity() = LineupEntity(
    channelIds = channelIds.map { it.value }.sorted().joinToString(CHANNEL_SEPARATOR),
    operatorId = source?.operatorId,
    packageId = source?.packageId,
    updatedAtMillis = updatedAt.toEpochMilli(),
)

private const val CHANNEL_SEPARATOR = ","

private fun requireSupported(version: Int) {
    if (version != EpgContract.SCHEMA_VERSION) throw UnsupportedSchemaException(version)
}

private fun secureUrl(url: String): String =
    if (url.startsWith("http://")) "https://" + url.removePrefix("http://") else url
