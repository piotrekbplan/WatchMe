package pl.watchme.data.repository

import java.io.IOException
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.serialization.SerializationException
import pl.watchme.data.mapper.UnsupportedSchemaException
import pl.watchme.domain.DomainError
import retrofit2.HttpException

internal fun Throwable.toDomainError(): DomainError = when (this) {
    is IOException, is HttpException -> DomainError.Network
    is UnsupportedSchemaException, is SerializationException -> DomainError.UnsupportedData
    else -> DomainError.Unknown(message)
}

internal inline fun <T> catchingDomainErrors(block: () -> T, onError: (Exception) -> T): T =
    try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        onError(e)
    }
