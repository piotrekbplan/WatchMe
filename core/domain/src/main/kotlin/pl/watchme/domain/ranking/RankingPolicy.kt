package pl.watchme.domain.ranking

import java.time.Instant
import pl.watchme.domain.model.Channel
import pl.watchme.domain.model.ChannelId
import pl.watchme.domain.model.Programme

data class RankedEntry(val programme: Programme, val channel: Channel?)

data class RankedGuide(val rated: List<RankedEntry>, val unrated: List<RankedEntry>) {
    val isEmpty: Boolean get() = rated.isEmpty() && unrated.isEmpty()

    companion object {
        val EMPTY = RankedGuide(emptyList(), emptyList())
    }
}

object RankingPolicy {

    private val byRating = compareByDescending<RankedEntry> { it.programme.rating?.value }
        .thenByDescending { it.programme.votes ?: -1 }
        .thenBy { it.programme.title }

    private val bySchedule = compareBy<RankedEntry> { it.programme.start }
        .thenBy { it.programme.title }

    fun rank(programmes: List<Programme>, channels: Map<ChannelId, Channel>, at: Instant): RankedGuide {
        val (rated, unrated) = programmes
            .filter { it.isAiringAt(at) }
            .map { RankedEntry(it, channels[it.channelId]) }
            .partition { it.programme.rating != null }
        return RankedGuide(rated.sortedWith(byRating), unrated.sortedWith(bySchedule))
    }
}
