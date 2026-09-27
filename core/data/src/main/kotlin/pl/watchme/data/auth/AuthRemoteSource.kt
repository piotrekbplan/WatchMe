package pl.watchme.data.auth

import java.io.IOException
import javax.inject.Inject
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import pl.watchme.data.di.FirebaseApiKey
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.Field
import retrofit2.http.FormUrlEncoded
import retrofit2.http.POST
import retrofit2.http.Query

data class AuthTokens(
    val uid: String,
    val email: String?,
    val idToken: String,
    val refreshToken: String,
    val expiresInSeconds: Long,
)

class AuthRemoteException(val code: String?) : RuntimeException("Firebase auth rejected the request: $code")

interface AuthRemoteSource {
    suspend fun signIn(email: String, password: String): AuthTokens
    suspend fun signUp(email: String, password: String): AuthTokens
    suspend fun sendPasswordReset(email: String)
    suspend fun sendEmailVerification(idToken: String)
    suspend fun refresh(refreshToken: String): AuthTokens
}

interface IdentityToolkitApi {

    @POST("v1/accounts:signInWithPassword")
    suspend fun signIn(@Query("key") key: String, @Body body: CredentialsRequest): Response<SignInResponse>

    @POST("v1/accounts:signUp")
    suspend fun signUp(@Query("key") key: String, @Body body: CredentialsRequest): Response<SignInResponse>

    @POST("v1/accounts:sendOobCode")
    suspend fun sendOobCode(@Query("key") key: String, @Body body: OobCodeRequest): Response<Unit>
}

interface SecureTokenApi {

    @FormUrlEncoded
    @POST("v1/token")
    suspend fun refresh(
        @Query("key") key: String,
        @Field("refresh_token") refreshToken: String,
        @Field("grant_type") grantType: String = "refresh_token",
    ): Response<RefreshResponse>
}

@Serializable
data class CredentialsRequest(val email: String, val password: String, val returnSecureToken: Boolean = true)

@Serializable
data class OobCodeRequest(val requestType: String, val email: String? = null, val idToken: String? = null)

@Serializable
data class SignInResponse(
    val localId: String,
    val email: String? = null,
    val idToken: String,
    val refreshToken: String,
    val expiresIn: String,
)

@Serializable
data class RefreshResponse(
    @SerialName("user_id") val userId: String,
    @SerialName("id_token") val idToken: String,
    @SerialName("refresh_token") val refreshToken: String,
    @SerialName("expires_in") val expiresIn: String,
)

fun SignInResponse.toTokens() = AuthTokens(localId, email, idToken, refreshToken, expiresIn.toLong())

fun RefreshResponse.toTokens() = AuthTokens(userId, null, idToken, refreshToken, expiresIn.toLong())

class RetrofitAuthRemoteSource @Inject constructor(
    private val identity: IdentityToolkitApi,
    private val secureToken: SecureTokenApi,
    @param:FirebaseApiKey private val apiKey: String,
) : AuthRemoteSource {

    override suspend fun signIn(email: String, password: String): AuthTokens =
        identity.signIn(apiKey, CredentialsRequest(email, password)).bodyOrThrow().toTokens()

    override suspend fun signUp(email: String, password: String): AuthTokens =
        identity.signUp(apiKey, CredentialsRequest(email, password)).bodyOrThrow().toTokens()

    override suspend fun sendPasswordReset(email: String) {
        identity.sendOobCode(apiKey, OobCodeRequest(requestType = "PASSWORD_RESET", email = email)).requireSuccess()
    }

    override suspend fun sendEmailVerification(idToken: String) {
        identity.sendOobCode(apiKey, OobCodeRequest(requestType = "VERIFY_EMAIL", idToken = idToken)).requireSuccess()
    }

    override suspend fun refresh(refreshToken: String): AuthTokens =
        secureToken.refresh(apiKey, refreshToken).bodyOrThrow().toTokens()

    private fun <T> Response<T>.bodyOrThrow(): T {
        requireSuccess()
        return body() ?: throw IOException("Firebase returned an empty body")
    }

    private fun Response<*>.requireSuccess() {
        if (isSuccessful) return
        if (code() >= HTTP_SERVER_ERROR) throw IOException("Firebase returned ${code()}")
        throw AuthRemoteException(FirebaseErrorParser.code(errorBody()?.string()))
    }

    private companion object {
        const val HTTP_SERVER_ERROR = 500
    }
}
