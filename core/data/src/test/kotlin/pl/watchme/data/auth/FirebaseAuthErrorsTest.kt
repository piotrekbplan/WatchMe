package pl.watchme.data.auth

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import pl.watchme.domain.DomainError
import pl.watchme.domain.Field

class FirebaseAuthErrorsTest {

    @ParameterizedTest
    @CsvSource(
        delimiter = '|',
        value = [
            "INVALID_LOGIN_CREDENTIALS|SIGN_IN|InvalidCredentials",
            "EMAIL_NOT_FOUND|SIGN_IN|InvalidCredentials",
            "INVALID_PASSWORD|SIGN_IN|InvalidCredentials",
            "USER_DISABLED|SIGN_IN|InvalidCredentials",
            "EMAIL_EXISTS|SIGN_UP|EmailAlreadyUsed",
            "WEAK_PASSWORD : Password should be at least 6 characters|SIGN_UP|WeakPassword",
            "TOO_MANY_ATTEMPTS_TRY_LATER : Access to this account has been temporarily disabled|SIGN_IN|TooManyAttempts",
            "EMAIL_NOT_FOUND|RESET|NotFound",
            "TOKEN_EXPIRED|REFRESH|Unauthorized",
            "INVALID_REFRESH_TOKEN|REFRESH|Unauthorized",
            "USER_NOT_FOUND|REFRESH|Unauthorized",
            "USER_DISABLED|REFRESH|Unauthorized",
        ],
    )
    fun `maps firebase codes to domain errors`(message: String, operation: AuthOperation, expected: String) {
        assertThat(FirebaseAuthErrors.map(message, operation)::class.simpleName).isEqualTo(expected)
    }

    @Test
    fun `invalid email is a validation error`() {
        assertThat(FirebaseAuthErrors.map("INVALID_EMAIL", AuthOperation.SIGN_UP))
            .isEqualTo(DomainError.Validation(Field.EMAIL))
    }

    @Test
    fun `unknown code keeps only the code`() {
        assertThat(FirebaseAuthErrors.map("OPERATION_NOT_ALLOWED : Password sign-in is disabled", AuthOperation.SIGN_IN))
            .isEqualTo(DomainError.Unknown("OPERATION_NOT_ALLOWED"))
        assertThat(FirebaseAuthErrors.map(null, AuthOperation.SIGN_IN)).isEqualTo(DomainError.Unknown(null))
    }

    @Test
    fun `error code is read from the firebase error body`() {
        val body = """{"error":{"code":400,"message":"EMAIL_EXISTS","errors":[{"message":"EMAIL_EXISTS"}]}}"""

        assertThat(FirebaseErrorParser.code(body)).isEqualTo("EMAIL_EXISTS")
        assertThat(FirebaseErrorParser.code("<html>")).isNull()
        assertThat(FirebaseErrorParser.code(null)).isNull()
    }

    @Test
    fun `sign in response becomes tokens`() {
        val tokens = SignInResponse(localId = "uid-1", email = "jan@example.com", idToken = "id", refreshToken = "refresh", expiresIn = "3600")
            .toTokens()

        assertThat(tokens).isEqualTo(AuthTokens("uid-1", "jan@example.com", "id", "refresh", 3600))
    }

    @Test
    fun `refresh response becomes tokens without email`() {
        val tokens = RefreshResponse(userId = "uid-1", idToken = "id2", refreshToken = "refresh2", expiresIn = "3600").toTokens()

        assertThat(tokens).isEqualTo(AuthTokens("uid-1", null, "id2", "refresh2", 3600))
    }
}
