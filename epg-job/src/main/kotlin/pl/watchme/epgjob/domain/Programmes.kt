package pl.watchme.epgjob.domain

import java.io.InputStream
import java.time.OffsetDateTime

enum class ProgrammeKind { MOVIE, SERIES }

object ProgrammeClassifier {
    fun classify(category: String?): ProgrammeKind? {
        val normalized = category?.trim()?.lowercase() ?: return null
        return when {
            normalized.startsWith("film") -> ProgrammeKind.MOVIE
            normalized.startsWith("serial") -> ProgrammeKind.SERIES
            else -> null
        }
    }
}

data class GuideWindow(val from: OffsetDateTime, val until: OffsetDateTime) {

    fun overlaps(start: OffsetDateTime, stop: OffsetDateTime): Boolean =
        stop.isAfter(from) && start.isBefore(until)

    companion object {
        private const val HOURS_BACK = 3L
        private const val HOURS_AHEAD = 48L

        fun around(now: OffsetDateTime): GuideWindow =
            GuideWindow(now.minusHours(HOURS_BACK), now.plusHours(HOURS_AHEAD))
    }
}

data class XmltvChannel(val id: String, val displayName: String, val iconUrl: String?)

data class XmltvProgramme(
    val channelId: String,
    val title: String,
    val start: OffsetDateTime,
    val stop: OffsetDateTime,
    val category: String?,
    val year: Int?,
    val iconUrl: String?,
)

data class ParsedGuide(
    val channels: List<XmltvChannel>,
    val programmes: List<XmltvProgramme>,
    val programmesInWindow: Int,
)

interface GuideParser {
    fun parse(input: InputStream, window: GuideWindow, keep: (XmltvProgramme) -> Boolean): ParsedGuide
}
