package pl.watchme.data.sync

import java.io.IOException
import java.time.Instant
import java.time.format.DateTimeParseException
import javax.inject.Inject
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import pl.watchme.data.di.FirebaseProjectId
import pl.watchme.domain.model.ChannelId
import pl.watchme.domain.model.ChannelLineup
import pl.watchme.domain.model.PackageRef
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.Path

val FirestoreJson = Json {
    ignoreUnknownKeys = true
    explicitNulls = false
}

@Serializable
data class FirestoreDocument(val fields: Map<String, FirestoreValue> = emptyMap())

@Serializable
data class FirestoreValue(
    val stringValue: String? = null,
    val timestampValue: String? = null,
    val arrayValue: FirestoreArray? = null,
)

@Serializable
data class FirestoreArray(val values: List<FirestoreValue> = emptyList())

object FirestoreLineupMapper {

    private const val CHANNEL_IDS = "channelIds"
    private const val OPERATOR_ID = "operatorId"
    private const val PACKAGE_ID = "packageId"
    private const val UPDATED_AT = "updatedAt"

    fun toDocument(lineup: ChannelLineup): FirestoreDocument = FirestoreDocument(
        buildMap {
            val ids = lineup.channelIds.map { it.value }.sorted().map { FirestoreValue(stringValue = it) }
            put(CHANNEL_IDS, FirestoreValue(arrayValue = FirestoreArray(ids)))
            lineup.source?.let {
                put(OPERATOR_ID, FirestoreValue(stringValue = it.operatorId))
                put(PACKAGE_ID, FirestoreValue(stringValue = it.packageId))
            }
            put(UPDATED_AT, FirestoreValue(timestampValue = lineup.updatedAt.toString()))
        },
    )

    fun toDomain(document: FirestoreDocument): ChannelLineup? {
        val fields = document.fields
        val updatedAt = fields[UPDATED_AT]?.timestampValue?.let(::parseInstant) ?: return null
        val ids = fields[CHANNEL_IDS]?.arrayValue?.values.orEmpty().mapNotNull { it.stringValue }.map(::ChannelId).toSet()
        val operatorId = fields[OPERATOR_ID]?.stringValue
        val packageId = fields[PACKAGE_ID]?.stringValue
        val source = if (operatorId != null && packageId != null) PackageRef(operatorId, packageId) else null
        return ChannelLineup(ids, source, updatedAt)
    }

    private fun parseInstant(value: String): Instant? =
        try {
            Instant.parse(value)
        } catch (e: DateTimeParseException) {
            null
        }
}

class FirestoreRejectedException(val httpCode: Int) : IOException("Firestore rejected the request with $httpCode")

interface LineupRemoteSource {
    suspend fun fetch(uid: String): ChannelLineup?
    suspend fun push(uid: String, lineup: ChannelLineup)
}

interface FirestoreApi {

    @GET("v1/projects/{project}/databases/(default)/documents/users/{uid}/settings/lineup")
    suspend fun lineup(@Path("project") project: String, @Path("uid") uid: String): Response<FirestoreDocument>

    @PATCH("v1/projects/{project}/databases/(default)/documents/users/{uid}/settings/lineup")
    suspend fun saveLineup(
        @Path("project") project: String,
        @Path("uid") uid: String,
        @Body document: FirestoreDocument,
    ): Response<FirestoreDocument>
}

class FirestoreLineupRemoteSource @Inject constructor(
    private val api: FirestoreApi,
    @param:FirebaseProjectId private val projectId: String,
) : LineupRemoteSource {

    override suspend fun fetch(uid: String): ChannelLineup? {
        val response = api.lineup(projectId, uid)
        if (response.code() == HTTP_NOT_FOUND) return null
        response.requireSuccess()
        return response.body()?.let(FirestoreLineupMapper::toDomain)
    }

    override suspend fun push(uid: String, lineup: ChannelLineup) {
        api.saveLineup(projectId, uid, FirestoreLineupMapper.toDocument(lineup)).requireSuccess()
    }

    private fun Response<*>.requireSuccess() {
        if (isSuccessful) return
        if (code() == HTTP_UNAUTHORIZED || code() == HTTP_FORBIDDEN) throw FirestoreRejectedException(code())
        throw IOException("Firestore returned ${code()}")
    }

    private companion object {
        const val HTTP_UNAUTHORIZED = 401
        const val HTTP_FORBIDDEN = 403
        const val HTTP_NOT_FOUND = 404
    }
}
