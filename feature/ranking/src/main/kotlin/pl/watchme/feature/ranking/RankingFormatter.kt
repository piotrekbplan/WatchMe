package pl.watchme.feature.ranking

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

internal class RankingFormatter(zone: ZoneId) {

    private val time = DateTimeFormatter.ofPattern("HH:mm").withZone(zone)
    private val dayTime = DateTimeFormatter.ofPattern("dd.MM HH:mm").withZone(zone)

    fun time(moment: Instant): String = time.format(moment)

    fun range(start: Instant, stop: Instant): String = "${time.format(start)}–${time.format(stop)}"

    fun dayTime(moment: Instant): String = dayTime.format(moment)

    fun rating(value: Double): String = String.format(Locale.ROOT, "%.1f", value)
}
