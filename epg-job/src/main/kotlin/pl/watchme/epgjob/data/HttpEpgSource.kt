package pl.watchme.epgjob.data

import java.io.IOException
import java.io.InputStream
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import pl.watchme.epgjob.domain.EpgSource

class HttpEpgSource(private val http: OkHttpClient, private val url: HttpUrl) : EpgSource {

    override fun <T> read(block: (InputStream) -> T): T =
        http.newCall(Request.Builder().url(url).build()).execute().use { response ->
            if (!response.isSuccessful) throw IOException("EPG source ${url.host} returned ${response.code}")
            response.body.byteStream().use(block)
        }
}
