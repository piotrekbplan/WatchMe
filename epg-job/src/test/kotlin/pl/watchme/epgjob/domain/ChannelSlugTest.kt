package pl.watchme.epgjob.domain

import assertk.assertThat
import assertk.assertions.isEqualTo
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

class ChannelSlugTest {

    @ParameterizedTest
    @CsvSource(
        delimiter = '|',
        value = [
            "TVP 1|tvp-1",
            "Canal+ Film|canal-plus-film",
            "Ale Kino+|ale-kino-plus",
            "TVP 3 Łódź|tvp-3-lodz",
            "Nick Jr.|nick-jr",
            "Polsat Café|polsat-cafe",
            "E! Entertainment|e-entertainment",
            "13 Ulica|13-ulica",
        ],
    )
    fun `builds ascii slug`(name: String, expected: String) {
        assertThat(ChannelSlug.of(name)).isEqualTo(expected)
    }
}
