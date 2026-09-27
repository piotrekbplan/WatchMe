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
        assertThat(TitleNormalizer.candidates("odc. 5", ProgrammeKind.SERIES)).isEmpty()
        assertThat(TitleNormalizer.candidates("odc. 5", ProgrammeKind.MOVIE)).isEmpty()
    }

    @ParameterizedTest
    @CsvSource(
        delimiter = '|',
        value = [
            "Ranczo 2. Lokalna rewolucja.|Ranczo",
            "Gra z Cieniem 2.|Gra z Cieniem",
            "Świnka Peppa 7. Zamek.|Świnka Peppa",
            "Rodzinka.pl. Sprzątanie.|Rodzinka.pl",
            "Troskliwe Misie: Uwolnić magię|Troskliwe Misie: Uwolnić magię",
            "Klan, odc. 4123|Klan",
            "2012.|2012",
        ],
    )
    fun `series name drops episode title and season number`(title: String, expected: String) {
        assertThat(TitleNormalizer.seriesName(title)).isEqualTo(expected)
    }

    @Test
    fun `movie candidates add prefix before first separator`() {
        assertThat(TitleNormalizer.candidates("Gwiezdne wojny: Nowa nadzieja", ProgrammeKind.MOVIE))
            .containsExactly("Gwiezdne wojny: Nowa nadzieja", "Gwiezdne wojny")
        assertThat(TitleNormalizer.candidates("Pianista", ProgrammeKind.MOVIE)).containsExactly("Pianista")
    }

    @Test
    fun `movie candidates keep sequel numbers`() {
        assertThat(TitleNormalizer.candidates("Shrek 2.", ProgrammeKind.MOVIE)).containsExactly("Shrek 2")
        assertThat(TitleNormalizer.candidates("2012.", ProgrammeKind.MOVIE)).containsExactly("2012")
    }

    @Test
    fun `series candidates start with series name and keep full title`() {
        assertThat(TitleNormalizer.candidates("Ranczo 2. Lokalna rewolucja.", ProgrammeKind.SERIES))
            .containsExactly("Ranczo", "Ranczo 2. Lokalna rewolucja")
        assertThat(TitleNormalizer.candidates("Szpital św. Anny", ProgrammeKind.SERIES))
            .containsExactly("Szpital św", "Szpital św. Anny")
    }

    @Test
    fun `key ignores case diacritics and punctuation`() {
        assertThat(TitleNormalizer.key("Gwiezdne Wojny: Nowa Nadzieja!")).isEqualTo("gwiezdne wojny nowa nadzieja")
        assertThat(TitleNormalizer.key("Łódź  Kaliska")).isEqualTo("lodz kaliska")
    }
}
