package pl.watchme.epg.contract

import kotlinx.serialization.Serializable

@Serializable
data class ChannelsFile(
    val schemaVersion: Int,
    val items: List<ChannelDto>,
)

@Serializable
data class ChannelDto(
    val id: String,
    val name: String,
    val group: String,
    val groupName: String,
    val logoUrl: String? = null,
)

@Serializable
data class OperatorsFile(
    val schemaVersion: Int,
    val items: List<OperatorDto>,
)

@Serializable
data class OperatorDto(
    val id: String,
    val name: String,
    val logoUrl: String? = null,
    val packages: List<PackageDto>,
)

@Serializable
data class PackageDto(
    val id: String,
    val name: String,
    val channelIds: List<String>,
)

@Serializable
data class ChannelGuideFile(
    val schemaVersion: Int,
    val channelId: String,
    val generatedAt: String,
    val programmes: List<ProgrammeDto>,
)

@Serializable
data class ProgrammeDto(
    val title: String,
    val start: String,
    val stop: String,
    val kind: ProgrammeKindDto,
    val category: String,
    val year: Int? = null,
    val imdbId: String? = null,
    val imdbRating: Double? = null,
    val imdbVotes: Int? = null,
    val posterUrl: String? = null,
)

@Serializable
enum class ProgrammeKindDto { MOVIE, SERIES }
