package pl.watchme.epgjob.data

import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import org.junit.jupiter.api.Test
import pl.watchme.epgjob.domain.GroupDefinition
import pl.watchme.epgjob.domain.PackageDefinition

class LineupYamlParserTest {

    private val parser = LineupYamlParser()

    @Test
    fun `parses catalog groups`() {
        val groups = parser.parseCatalog(
            """
            groups:
              - id: ogolne
                name: Ogólne
                channels: ["TVP 1", "Canal+ Film"]
            """.trimIndent(),
        )

        assertThat(groups).containsExactly(GroupDefinition("ogolne", "Ogólne", listOf("TVP 1", "Canal+ Film")))
    }

    @Test
    fun `parses bundles and packages with optional lists`() {
        val lineup = parser.parseLineup(
            """
            bundles:
              fta: ["TVP 1", "TVP 2"]
            operators:
              - id: play
                name: Play
                packages:
                  - id: play-start
                    name: Play TV Start
                    bundles: [fta]
                  - id: play-max
                    name: Play TV Max
                    includes: [play-start]
                    channels: ["HBO"]
            """.trimIndent(),
        )

        assertThat(lineup.bundles).isEqualTo(mapOf("fta" to listOf("TVP 1", "TVP 2")))
        assertThat(lineup.operators.single().packages).containsExactly(
            PackageDefinition("play-start", "Play TV Start", listOf("fta"), emptyList(), emptyList()),
            PackageDefinition("play-max", "Play TV Max", emptyList(), listOf("play-start"), listOf("HBO")),
        )
    }

    @Test
    fun `missing bundles section means no bundles`() {
        val lineup = parser.parseLineup(
            """
            operators:
              - id: play
                name: Play
                packages: []
            """.trimIndent(),
        )

        assertThat(lineup.bundles).isEmpty()
        assertThat(lineup.operators.single().packages).isEmpty()
    }
}
