package pl.watchme.epgjob.data

import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.deleteExisting
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.name
import kotlin.io.path.writeText
import pl.watchme.epg.contract.EpgContract
import pl.watchme.epgjob.app.SiteContent
import pl.watchme.epgjob.app.SitePublisher

class FileSitePublisher(private val root: Path) : SitePublisher {

    override fun publish(content: SiteContent) {
        val guideDir = root.resolve(EpgContract.GUIDE_DIR).createDirectories()
        SiteFiles.render(content).forEach { (path, text) -> root.resolve(path).writeText(text) }
        val existing = guideDir.listDirectoryEntries("*.json").map { "${EpgContract.GUIDE_DIR}/${it.name}" }
        SiteFiles.staleGuides(existing, content).forEach { root.resolve(it).deleteExisting() }
    }
}
