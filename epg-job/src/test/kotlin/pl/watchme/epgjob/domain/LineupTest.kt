package pl.watchme.epgjob.domain

import assertk.assertFailure
import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.hasMessage
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import assertk.assertions.messageContains
import org.junit.jupiter.api.Test

class LineupTest {

    private val catalog = Catalog.from(
        listOf(
            GroupDefinition("ogolne", "Ogólne", listOf("TVP 1", "TVP 2", "Polsat")),
            GroupDefinition("filmowe", "Filmowe", listOf("Canal+ Film", "HBO")),
        ),
    )

    @Test
    fun `catalog assigns slugs and groups`() {
        assertThat(catalog.find("Canal+ Film")).isEqualTo(CatalogChannel("Canal+ Film", "canal-plus-film", "filmowe", "Filmowe"))
        assertThat(catalog.missingIn(setOf("TVP 1", "TVP 2", "Polsat", "HBO")).map { it.epgId }).containsExactly("Canal+ Film")
    }

    @Test
    fun `catalog rejects duplicated channels`() {
        assertFailure {
            Catalog.from(listOf(GroupDefinition("a", "A", listOf("TVP 1")), GroupDefinition("b", "B", listOf("TVP 1"))))
        }.isInstanceOf<IllegalArgumentException>().messageContains("TVP 1")
    }

    @Test
    fun `catalog rejects slug collisions`() {
        assertFailure {
            Catalog.from(listOf(GroupDefinition("a", "A", listOf("TVP 1", "TVP-1"))))
        }.isInstanceOf<IllegalArgumentException>().messageContains("tvp-1")
    }

    @Test
    fun `resolves bundles includes and own channels in order without duplicates`() {
        val definition = LineupDefinition(
            bundles = mapOf("fta" to listOf("TVP 1", "TVP 2")),
            operators = listOf(
                OperatorDefinition(
                    "play", "Play",
                    listOf(
                        PackageDefinition("start", "Start", bundles = listOf("fta"), includes = emptyList(), epgIds = listOf("Polsat")),
                        PackageDefinition("max", "Max", bundles = emptyList(), includes = listOf("start"), epgIds = listOf("HBO", "TVP 1")),
                    ),
                ),
            ),
        )

        val resolution = OperatorResolver(catalog).resolve(definition)

        val packages = resolution.operators.single().packages
        assertThat(packages[0].channelSlugs).containsExactly("tvp-1", "tvp-2", "polsat")
        assertThat(packages[1].channelSlugs).containsExactly("tvp-1", "tvp-2", "polsat", "hbo")
        assertThat(resolution.warnings).isEmpty()
    }

    @Test
    fun `channel outside catalog is dropped with a warning`() {
        val definition = LineupDefinition(
            bundles = emptyMap(),
            operators = listOf(
                OperatorDefinition("play", "Play", listOf(PackageDefinition("start", "Start", emptyList(), emptyList(), listOf("TVP 1", "Ghost TV")))),
            ),
        )

        val resolution = OperatorResolver(catalog).resolve(definition)

        assertThat(resolution.operators.single().packages.single().channelSlugs).containsExactly("tvp-1")
        assertThat(resolution.warnings).containsExactly("play/start: channel 'Ghost TV' is not in catalog")
    }

    @Test
    fun `unknown include fails`() {
        val definition = LineupDefinition(
            emptyMap(),
            listOf(OperatorDefinition("play", "Play", listOf(PackageDefinition("max", "Max", emptyList(), listOf("nope"), emptyList())))),
        )

        assertFailure { OperatorResolver(catalog).resolve(definition) }
            .hasMessage("Unknown package 'nope' included by play/max")
    }

    @Test
    fun `unknown bundle fails`() {
        val definition = LineupDefinition(
            emptyMap(),
            listOf(OperatorDefinition("play", "Play", listOf(PackageDefinition("max", "Max", listOf("nope"), emptyList(), emptyList())))),
        )

        assertFailure { OperatorResolver(catalog).resolve(definition) }
            .hasMessage("Unknown bundle 'nope' in play/max")
    }

    @Test
    fun `include cycle fails`() {
        val definition = LineupDefinition(
            emptyMap(),
            listOf(
                OperatorDefinition(
                    "play", "Play",
                    listOf(
                        PackageDefinition("a", "A", emptyList(), listOf("b"), emptyList()),
                        PackageDefinition("b", "B", emptyList(), listOf("a"), emptyList()),
                    ),
                ),
            ),
        )

        assertFailure { OperatorResolver(catalog).resolve(definition) }
            .messageContains("Include cycle in play: a -> b -> a")
    }
}
