package pl.watchme.epgjob.domain

import java.io.InputStream

interface EpgSource {
    fun <T> read(block: (InputStream) -> T): T
}

class EpgSanityException(message: String) : RuntimeException(message)
