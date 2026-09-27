package pl.watchme.epgjob.domain

data class GroupDefinition(val id: String, val name: String, val epgIds: List<String>)

data class PackageDefinition(
    val id: String,
    val name: String,
    val bundles: List<String>,
    val includes: List<String>,
    val epgIds: List<String>,
)

data class OperatorDefinition(val id: String, val name: String, val packages: List<PackageDefinition>)

data class LineupDefinition(val bundles: Map<String, List<String>>, val operators: List<OperatorDefinition>)

data class CatalogChannel(val epgId: String, val slug: String, val groupId: String, val groupName: String)

class Catalog private constructor(val channels: List<CatalogChannel>) {

    private val byEpgId = channels.associateBy { it.epgId }

    fun find(epgId: String): CatalogChannel? = byEpgId[epgId]

    fun missingIn(epgIds: Set<String>): List<CatalogChannel> = channels.filterNot { it.epgId in epgIds }

    companion object {
        fun from(groups: List<GroupDefinition>): Catalog {
            val channels = groups.flatMap { group ->
                group.epgIds.map { CatalogChannel(it, ChannelSlug.of(it), group.id, group.name) }
            }
            val duplicatedChannels = channels.groupBy { it.epgId }.filterValues { it.size > 1 }.keys
            require(duplicatedChannels.isEmpty()) { "Channels listed more than once in catalog: $duplicatedChannels" }
            val collidingSlugs = channels.groupBy { it.slug }.filterValues { it.size > 1 }.keys
            require(collidingSlugs.isEmpty()) { "Slug collisions in catalog: $collidingSlugs" }
            return Catalog(channels)
        }
    }
}

data class ChannelPackage(val id: String, val name: String, val channelSlugs: List<String>)

data class TvOperator(val id: String, val name: String, val packages: List<ChannelPackage>)

data class LineupResolution(val operators: List<TvOperator>, val warnings: List<String>)

class OperatorResolver(private val catalog: Catalog) {

    fun resolve(definition: LineupDefinition): LineupResolution {
        val warnings = linkedSetOf<String>()
        val operators = definition.operators.map { operator ->
            val packagesById = operator.packages.associateBy { it.id }
            require(packagesById.size == operator.packages.size) { "Duplicated package id in operator ${operator.id}" }
            TvOperator(
                id = operator.id,
                name = operator.name,
                packages = operator.packages.map { pkg ->
                    val epgIds = expand(pkg, packagesById, definition.bundles, operator.id, emptyList())
                    val slugs = epgIds.mapNotNull { epgId ->
                        catalog.find(epgId)?.slug
                            ?: null.also { warnings += "${operator.id}/${pkg.id}: channel '$epgId' is not in catalog" }
                    }
                    ChannelPackage(pkg.id, pkg.name, slugs)
                },
            )
        }
        require(operators.map { it.id }.toSet().size == operators.size) { "Duplicated operator id" }
        return LineupResolution(operators, warnings.toList())
    }

    private fun expand(
        pkg: PackageDefinition,
        packagesById: Map<String, PackageDefinition>,
        bundles: Map<String, List<String>>,
        operatorId: String,
        path: List<String>,
    ): List<String> {
        require(pkg.id !in path) { "Include cycle in $operatorId: ${(path + pkg.id).joinToString(" -> ")}" }
        val included = pkg.includes.flatMap { id ->
            val child = requireNotNull(packagesById[id]) { "Unknown package '$id' included by $operatorId/${pkg.id}" }
            expand(child, packagesById, bundles, operatorId, path + pkg.id)
        }
        val fromBundles = pkg.bundles.flatMap { name ->
            requireNotNull(bundles[name]) { "Unknown bundle '$name' in $operatorId/${pkg.id}" }
        }
        return (included + fromBundles + pkg.epgIds).distinct()
    }
}
