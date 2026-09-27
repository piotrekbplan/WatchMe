package pl.watchme.data.remote

import java.io.IOException
import javax.inject.Inject
import pl.watchme.epg.contract.ChannelGuideFile
import pl.watchme.epg.contract.ChannelsFile
import pl.watchme.epg.contract.OperatorsFile
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Path

interface EpgApi {

    @GET("channels.json")
    suspend fun channels(): ChannelsFile

    @GET("operators.json")
    suspend fun operators(): OperatorsFile

    @GET("epg/{channelId}.json")
    suspend fun guide(
        @Path("channelId") channelId: String,
        @Header("If-None-Match") etag: String?,
    ): Response<ChannelGuideFile>
}

sealed interface GuideFetch {
    data class Fetched(val file: ChannelGuideFile, val etag: String?) : GuideFetch
    data object NotModified : GuideFetch
    data object Missing : GuideFetch
}

interface EpgRemoteSource {
    suspend fun channels(): ChannelsFile
    suspend fun operators(): OperatorsFile
    suspend fun guide(channelId: String, etag: String?): GuideFetch
}

class RetrofitEpgRemoteSource @Inject constructor(private val api: EpgApi) : EpgRemoteSource {

    override suspend fun channels(): ChannelsFile = api.channels()

    override suspend fun operators(): OperatorsFile = api.operators()

    override suspend fun guide(channelId: String, etag: String?): GuideFetch {
        val response = api.guide(channelId, etag)
        return when {
            response.code() == HTTP_NOT_MODIFIED -> GuideFetch.NotModified
            response.code() == HTTP_NOT_FOUND -> GuideFetch.Missing
            response.isSuccessful -> GuideFetch.Fetched(
                file = response.body() ?: throw IOException("Empty guide for $channelId"),
                etag = response.headers()["ETag"],
            )
            else -> throw IOException("Guide for $channelId returned ${response.code()}")
        }
    }

    private companion object {
        const val HTTP_NOT_MODIFIED = 304
        const val HTTP_NOT_FOUND = 404
    }
}
