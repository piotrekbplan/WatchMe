package pl.watchme.epgjob.data

import com.charleskorn.kaml.Yaml
import kotlinx.serialization.Serializable
import pl.watchme.epgjob.domain.GroupDefinition
import pl.watchme.epgjob.domain.LineupDefinition
import pl.watchme.epgjob.domain.OperatorDefinition
import pl.watchme.epgjob.domain.PackageDefinition

class LineupYamlParser(private val yaml: Yaml = Yaml.default) {

    fun parseCatalog(text: String): List<GroupDefinition> =
        yaml.decodeFromString(CatalogYaml.serializer(), text)
            .groups
            .map { GroupDefinition(it.id, it.name, it.channels) }

    fun parseLineup(text: String): LineupDefinition {
        val document = yaml.decodeFromString(OperatorsYaml.serializer(), text)
        return LineupDefinition(
            bundles = document.bundles,
            operators = document.operators.map { operator ->
                OperatorDefinition(
                    id = operator.id,
                    name = operator.name,
                    packages = operator.packages.map { PackageDefinition(it.id, it.name, it.bundles, it.includes, it.channels) },
                )
            },
        )
    }
}

@Serializable
private class CatalogYaml(val groups: List<GroupYaml>)

@Serializable
private class GroupYaml(val id: String, val name: String, val channels: List<String>)

@Serializable
private class OperatorsYaml(val bundles: Map<String, List<String>> = emptyMap(), val operators: List<OperatorYaml>)

@Serializable
private class OperatorYaml(val id: String, val name: String, val packages: List<PackageYaml>)

@Serializable
private class PackageYaml(
    val id: String,
    val name: String,
    val bundles: List<String> = emptyList(),
    val includes: List<String> = emptyList(),
    val channels: List<String> = emptyList(),
)
