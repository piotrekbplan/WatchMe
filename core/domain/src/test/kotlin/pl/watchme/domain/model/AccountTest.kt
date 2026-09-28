package pl.watchme.domain.model

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.junit.jupiter.api.Test

class AccountTest {

    @ParameterizedTest
    @ValueSource(strings = ["jan@example.com", "jan.kowalski+tv@poczta.onet.pl", "  jan@example.com  "])
    fun `valid emails are accepted and trimmed`(raw: String) {
        assertThat(Email.of(raw)?.value).isNotNull().isEqualTo(raw.trim())
    }

    @ParameterizedTest
    @ValueSource(strings = ["", "   ", "jan", "jan@", "@example.com", "jan@example", "jan kowalski@example.com"])
    fun `invalid emails are rejected`(raw: String) {
        assertThat(Email.of(raw)).isNull()
    }

    @Test
    fun `password needs at least six characters`() {
        assertThat(Password.of("12345")).isNull()
        assertThat(Password.of("123456")?.value).isEqualTo("123456")
    }
}
