package pl.watchme.domain.ranking

import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEqualTo
import assertk.assertions.isNull
import assertk.assertions.isTrue
import java.time.Instant
import org.junit.jupiter.api.Test
import pl.watchme.domain.model.Channel
import pl.watchme.domain.model.ChannelId
import pl.watchme.domain.model.ImdbRating
import pl.watchme.domain.model.Programme
import pl.watchme.domain.model.ProgrammeKind

class RankingPolicyTest {

    private val at = Instant.parse("2026-09-28T19:00:00Z")
    private val tvp = Channel(ChannelId("tvp-1"), "TVP 1", "ogolne", "Ogólne", null)
    private val channels = mapOf(tvp.id to tvp)

    @Test
    fun `only programmes airing at the moment are ranked`() {
        val airing = programme("Pianista", rating = 8.5)
        val later = programme("Później", rating = 9.9, start = "2026-09-28T21:00:00Z", stop = "2026-09-28T23:00:00Z")
        val ended = programme("Wcześniej", rating = 9.0, start = "2026-09-28T16:00:00Z", stop = "2026-09-28T19:00:00Z")

        val guide = RankingPolicy.rank(listOf(airing, later, ended), channels, at)

        assertThat(guide.rated.map { it.programme.title }).containsExactly("Pianista")
        assertThat(guide.unrated.map { it.programme.title }).containsExactly()
    }

    @Test
    fun `rated programmes are sorted by rating then votes then title`() {
        val guide = RankingPolicy.rank(
            listOf(
                programme("Bravo", rating = 7.0, votes = 100),
                programme("Alfa", rating = 7.0, votes = 100),
                programme("Mniej głosów", rating = 7.0, votes = 10),
                programme("Bez głosów", rating = 7.0, votes = null),
                programme("Najlepszy", rating = 8.9, votes = 5),
            ),
            channels,
            at,
        )

        assertThat(guide.rated.map { it.programme.title })
            .containsExactly("Najlepszy", "Alfa", "Bravo", "Mniej głosów", "Bez głosów")
    }

    @Test
    fun `unrated programmes are sorted by start then title`() {
        val guide = RankingPolicy.rank(
            listOf(
                programme("Zeta", start = "2026-09-28T18:00:00Z"),
                programme("Beta", start = "2026-09-28T17:00:00Z"),
                programme("Alfa", start = "2026-09-28T18:00:00Z"),
            ),
            channels,
            at,
        )

        assertThat(guide.unrated.map { it.programme.title }).containsExactly("Beta", "Alfa", "Zeta")
        assertThat(guide.rated.isEmpty()).isTrue()
    }

    @Test
    fun `entries carry their channel when known`() {
        val known = programme("Pianista", rating = 8.5)
        val unknown = programme("Obcy", rating = 8.0, channel = "ghost")

        val guide = RankingPolicy.rank(listOf(known, unknown), channels, at)

        assertThat(guide.rated[0].channel).isEqualTo(tvp)
        assertThat(guide.rated[1].channel).isNull()
    }

    @Test
    fun `nothing airing gives an empty guide`() {
        assertThat(RankingPolicy.rank(emptyList(), channels, at).isEmpty).isTrue()
    }

    private fun programme(
        title: String,
        rating: Double? = null,
        votes: Int? = null,
        start: String = "2026-09-28T18:00:00Z",
        stop: String = "2026-09-28T20:00:00Z",
        channel: String = "tvp-1",
    ) = Programme(
        channelId = ChannelId(channel),
        title = title,
        start = Instant.parse(start),
        stop = Instant.parse(stop),
        kind = ProgrammeKind.MOVIE,
        category = "film",
        year = null,
        imdbId = null,
        rating = rating?.let(ImdbRating::of),
        votes = votes,
        posterUrl = null,
    )
}
