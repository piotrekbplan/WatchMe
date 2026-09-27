package pl.watchme.epgjob.domain

import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

class TitleNormalizerTest {

    @ParameterizedTest
    @CsvSource(
        delimiter = '|',
        value = [
            "Ranczo odc. 12|Ranczo",
            "Klan, odc. 4123|Klan",
            "M jak miłość (1834)|M jak miłość",
            "Na Wspólnej sezon 2 odc. 5|Na Wspólnej",
            "'Dom pod Dwoma Orłami. Kłamstwa Zofii. '|Dom pod Dwoma Orłami. Kłamstwa Zofii",
            "Pianista|Pianista",
            "Kodeks 5|Kodeks 5",
        ],
    )
    fun `clean removes episode markers`(title: String, expected: String) {
        assertThat(TitleNormalizer.clean(title)).isEqualTo(expected)
    }

    @Test
    fun `title made only of episode marker cleans to empty`() {
        assertThat(TitleNormalizer.clean("odc. 5")).isEqualTo("")
        assertThat(TitleNormalizer.candidates("odc. 5")).isEmpty()
    }

    @Test
    fun `candidates add prefix before first separator`() {
        assertThat(TitleNormalizer.candidates("Dom pod Dwoma Orłami. Kłamstwa Zofii. "))
            .containsExactly("Dom pod Dwoma Orłami. Kłamstwa Zofii", "Dom pod Dwoma Orłami")
        assertThat(TitleNormalizer.candidates("Gwiezdne wojny: Nowa nadzieja"))
            .containsExactly("Gwiezdne wojny: Nowa nadzieja", "Gwiezdne wojny")
        assertThat(TitleNormalizer.candidates("Pianista")).containsExactly("Pianista")
    }

    @Test
    fun `key ignores case diacritics and punctuation`() {
        assertThat(TitleNormalizer.key("Gwiezdne Wojny: Nowa Nadzieja!")).isEqualTo("gwiezdne wojny nowa nadzieja")
        assertThat(TitleNormalizer.key("Łódź  Kaliska")).isEqualTo("lodz kaliska")
    }
}
