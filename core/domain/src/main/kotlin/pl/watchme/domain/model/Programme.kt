package pl.watchme.domain.model

import java.time.Duration
import java.time.Instant

enum class ProgrammeKind { MOVIE, SERIES }

@JvmInline
value class ImdbRating private constructor(val value: Double) {
    companion object {
        private const val MIN = 0.0
        private const val MAX = 10.0

        fun of(value: Double): ImdbRating? = if (value in MIN..MAX) ImdbRating(value) else null
    }
}

data class Programme(
    val channelId: ChannelId,
    val title: String,
    val start: Instant,
    val stop: Instant,
    val kind: ProgrammeKind,
    val category: String,
    val year: Int?,
    val imdbId: String?,
    val rating: ImdbRating?,
    val votes: Int?,
    val posterUrl: String?,
) {
    fun isAiringAt(moment: Instant): Boolean = !moment.isBefore(start) && moment.isBefore(stop)

    fun progressAt(moment: Instant): Float {
        val total = Duration.between(start, stop).toMillis()
        if (total <= 0) return 1f
        val elapsed = Duration.between(start, moment).toMillis()
        return (elapsed.toFloat() / total).coerceIn(0f, 1f)
    }
}
