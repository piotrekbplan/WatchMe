package pl.watchme.data.auth

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import pl.watchme.domain.DomainError
import pl.watchme.domain.Field

enum class AuthOperation { SIGN_IN, SIGN_UP, RESET, REFRESH }

object FirebaseAuthErrors {

    fun map(message: String?, operation: AuthOperation): DomainError {
        val code = message?.substringBefore(" ")?.substringBefore(":")?.trim()?.takeIf { it.isNotEmpty() }
        return when (code) {
            "EMAIL_EXISTS" -> DomainError.EmailAlreadyUsed
            "WEAK_PASSWORD" -> DomainError.WeakPassword
            "TOO_MANY_ATTEMPTS_TRY_LATER" -> DomainError.TooManyAttempts
            "INVALID_EMAIL" -> DomainError.Validation(Field.EMAIL)
            "TOKEN_EXPIRED", "INVALID_REFRESH_TOKEN", "INVALID_ID_TOKEN", "USER_NOT_FOUND" -> DomainError.Unauthorized
            "EMAIL_NOT_FOUND" -> if (operation == AuthOperation.RESET) DomainError.NotFound else DomainError.InvalidCredentials
            "USER_DISABLED" -> if (operation == AuthOperation.REFRESH) DomainError.Unauthorized else DomainError.InvalidCredentials
            "INVALID_LOGIN_CREDENTIALS", "INVALID_PASSWORD" -> DomainError.InvalidCredentials
            else -> DomainError.Unknown(code)
        }
    }
}

object FirebaseErrorParser {

    private val json = Json { ignoreUnknownKeys = true }

    fun code(body: String?): String? =
        body?.let {
            try {
                json.decodeFromString(FirebaseErrorEnvelope.serializer(), it).error.message
            } catch (e: SerializationException) {
                null
            } catch (e: IllegalArgumentException) {
                null
            }
        }

    @Serializable
    private class FirebaseErrorEnvelope(val error: FirebaseError)

    @Serializable
    private class FirebaseError(val message: String? = null)
}
