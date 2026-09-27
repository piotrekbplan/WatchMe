package pl.watchme.domain.usecase

import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import pl.watchme.domain.DomainError
import pl.watchme.domain.Field
import pl.watchme.domain.Outcome
import pl.watchme.domain.model.Email
import pl.watchme.domain.model.Password
import pl.watchme.domain.model.Session
import pl.watchme.domain.repository.AuthRepository
import pl.watchme.domain.repository.LineupRepository

class ObserveSessionUseCase @Inject constructor(private val auth: AuthRepository) {
    operator fun invoke(): Flow<Session?> = auth.observeSession()
}

class SignInUseCase @Inject constructor(
    private val auth: AuthRepository,
    private val lineups: LineupRepository,
) {
    suspend operator fun invoke(email: String, password: String): Outcome<Session> {
        val validEmail = Email.of(email) ?: return invalid(Field.EMAIL)
        if (password.isBlank()) return invalid(Field.PASSWORD)
        return auth.signIn(validEmail, password).also { if (it is Outcome.Success) lineups.sync() }
    }
}

class SignUpUseCase @Inject constructor(
    private val auth: AuthRepository,
    private val lineups: LineupRepository,
) {
    suspend operator fun invoke(email: String, password: String, confirmation: String): Outcome<Session> {
        val validEmail = Email.of(email) ?: return invalid(Field.EMAIL)
        val validPassword = Password.of(password) ?: return invalid(Field.PASSWORD)
        if (confirmation != password) return invalid(Field.PASSWORD_CONFIRMATION)
        return auth.signUp(validEmail, validPassword).also { if (it is Outcome.Success) lineups.sync() }
    }
}

class SendPasswordResetUseCase @Inject constructor(private val auth: AuthRepository) {
    suspend operator fun invoke(email: String): Outcome<Unit> {
        val validEmail = Email.of(email) ?: return invalid(Field.EMAIL)
        return when (val outcome = auth.sendPasswordReset(validEmail)) {
            is Outcome.Failure -> if (outcome.error == DomainError.NotFound) Outcome.Success(Unit) else outcome
            is Outcome.Success -> outcome
        }
    }
}

class SignOutUseCase @Inject constructor(
    private val auth: AuthRepository,
    private val lineups: LineupRepository,
) {
    suspend operator fun invoke() {
        auth.signOut()
        lineups.clear()
    }
}

private fun invalid(field: Field) = Outcome.Failure(DomainError.Validation(field))
