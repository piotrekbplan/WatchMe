package pl.watchme.domain.model

import java.time.Duration
import java.time.Instant

data class TimeWindow(val from: Instant, val until: Instant) {

    fun contains(moment: Instant): Boolean = !moment.isBefore(from) && moment.isBefore(until)

    fun clamp(moment: Instant): Instant = when {
        moment.isBefore(from) -> from
        moment.isAfter(until) -> until
        else -> moment
    }

    companion object {
        private val HISTORY: Duration = Duration.ofHours(3)
        private val HORIZON: Duration = Duration.ofHours(24)

        fun around(now: Instant) = TimeWindow(now.minus(HISTORY), now.plus(HORIZON))

        fun rankingRange(now: Instant) = TimeWindow(now, now.plus(HORIZON))
    }
}
