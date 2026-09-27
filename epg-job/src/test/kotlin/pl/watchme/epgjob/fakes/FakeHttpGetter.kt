package pl.watchme.epgjob.fakes

import java.io.IOException
import okhttp3.HttpUrl
import pl.watchme.epgjob.data.HttpGetter
import pl.watchme.epgjob.data.HttpResult

class FakeHttpGetter : HttpGetter {
    val responses = ArrayDeque<HttpResult>()
    val requested = mutableListOf<HttpUrl>()
    var failure: IOException? = null

    fun respond(code: Int, body: String) {
        responses += HttpResult(code, body)
    }

    override fun get(url: HttpUrl): HttpResult {
        requested += url
        failure?.let { throw it }
        return responses.removeFirst()
    }
}
