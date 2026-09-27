package pl.watchme.epgjob.data

import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.io.path.writeText
import org.slf4j.LoggerFactory
import pl.watchme.epgjob.domain.MatchCache
import pl.watchme.epgjob.domain.MatchCacheRepository

class FileMatchCacheRepository(
    private val file: Path,
    private val codec: MatchCacheCodec = MatchCacheCodec(),
) : MatchCacheRepository {

    private val log = LoggerFactory.getLogger(FileMatchCacheRepository::class.java)

    override fun load(): MatchCache {
        if (!file.exists()) return MatchCache()
        return codec.decode(file.readText())
            ?: MatchCache().also { log.warn("Match cache {} is unreadable, starting empty", file) }
    }

    override fun save(cache: MatchCache) {
        file.parent?.createDirectories()
        file.writeText(codec.encode(cache))
    }
}
