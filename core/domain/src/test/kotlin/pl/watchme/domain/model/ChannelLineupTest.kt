package pl.watchme.domain.model

import assertk.assertThat
import assertk.assertions.containsExactlyInAnyOrder
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isTrue
import java.time.Instant
import org.junit.jupiter.api.Test

class ChannelLineupTest {

    private val created = Instant.parse("2026-09-28T08:00:00Z")
    private val later = Instant.parse("2026-09-28T09:00:00Z")
    private val tvp = ChannelId("tvp-1")
    private val tvn = ChannelId("tvn")
    private val hbo = ChannelId("hbo")
    private val start = ChannelPackage("play-start", "Play TV Start", listOf(tvp, tvn))
    private val play = TvOperator("play", "Play", listOf(start))

    @Test
    fun `empty lineup has no channels and no source`() {
        val lineup = ChannelLineup.empty(created)

        assertThat(lineup.isEmpty).isTrue()
        assertThat(lineup.source).isEqualTo(null)
        assertThat(lineup.updatedAt).isEqualTo(created)
    }

    @Test
    fun `applying a package replaces channels and records its source`() {
        val lineup = ChannelLineup(setOf(hbo), null, created).applyPackage(play, start, later)

        assertThat(lineup.channelIds).containsExactlyInAnyOrder(tvp, tvn)
        assertThat(lineup.source).isEqualTo(PackageRef("play", "play-start"))
        assertThat(lineup.updatedAt).isEqualTo(later)
        assertThat(lineup.isEmpty).isFalse()
    }

    @Test
    fun `toggling adds a missing channel and keeps the source`() {
        val lineup = ChannelLineup.empty(created).applyPackage(play, start, created).toggle(hbo, later)

        assertThat(lineup.channelIds).containsExactlyInAnyOrder(tvp, tvn, hbo)
        assertThat(lineup.source).isEqualTo(PackageRef("play", "play-start"))
        assertThat(lineup.updatedAt).isEqualTo(later)
    }

    @Test
    fun `toggling removes a selected channel`() {
        val lineup = ChannelLineup(setOf(tvp, tvn), null, created).toggle(tvp, later)

        assertThat(lineup.channelIds).containsExactlyInAnyOrder(tvn)
    }

    @Test
    fun `catalog finds channel by id`() {
        val channel = Channel(tvp, "TVP 1", "ogolne", "Ogólne", null)
        val catalog = Catalog(listOf(channel), listOf(play))

        assertThat(catalog.channel(tvp)).isEqualTo(channel)
        assertThat(catalog.channel(hbo)).isEqualTo(null)
    }
}
