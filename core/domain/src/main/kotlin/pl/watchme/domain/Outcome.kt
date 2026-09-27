package pl.watchme.domain

sealed interface DomainError {
    data object Network : DomainError
    data object NotFound : DomainError
    data object UnsupportedData : DomainError
    data class Unknown(val message: String?) : DomainError
}

sealed interface Outcome<out T> {
    data class Success<out T>(val value: T) : Outcome<T>
    data class Failure(val error: DomainError) : Outcome<Nothing>
}

inline fun <T, R> Outcome<T>.map(transform: (T) -> R): Outcome<R> = when (this) {
    is Outcome.Success -> Outcome.Success(transform(value))
    is Outcome.Failure -> this
}

fun <T> Outcome<T>.valueOrNull(): T? = (this as? Outcome.Success)?.value
