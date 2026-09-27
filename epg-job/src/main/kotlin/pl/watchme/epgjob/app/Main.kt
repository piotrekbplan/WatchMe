package pl.watchme.epgjob.app

import kotlin.system.exitProcess
import org.slf4j.LoggerFactory

fun main(args: Array<String>) {
    val log = LoggerFactory.getLogger("pl.watchme.epgjob.Main")
    val exitCode = try {
        val report = JobFactory.create(JobConfig.from(args, System.getenv())).run()
        log.info(
            "Published {} channels: {} programmes in window, {} movies/series, rated {} of {} unique titles, {} catalog channels missing",
            report.publishedChannels,
            report.programmesInWindow,
            report.moviesAndSeries,
            report.ratedTitles,
            report.uniqueTitles,
            report.missingChannels,
        )
        report.matchStats?.let {
            log.info(
                "Matching: searched={}, matched={}, rated={}, failures={}, ratingsUnavailable={}",
                it.searched, it.matched, it.rated, it.failures, it.ratingsUnavailable,
            )
        }
        0
    } catch (e: Exception) {
        log.error("EPG job failed, nothing was published", e)
        1
    }
    exitProcess(exitCode)
}
