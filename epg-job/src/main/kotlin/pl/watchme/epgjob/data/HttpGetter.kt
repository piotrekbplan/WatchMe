package pl.watchme.epgjob.data

import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request

data class HttpResult(val code: Int, val body: String) {
    val isSuccessful: Boolean get() = code in 200..299
}

fun interface HttpGetter {
    fun get(url: HttpUrl): HttpResult
}

class OkHttpGetter(private val client: OkHttpClient) : HttpGetter {
    override fun get(url: HttpUrl): HttpResult =
        client.newCall(Request.Builder().url(url).build()).execute().use { HttpResult(it.code, it.body.string()) }
}
