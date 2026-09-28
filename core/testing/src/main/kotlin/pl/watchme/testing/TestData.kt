package pl.watchme.testing

import java.time.Instant
import pl.watchme.domain.model.Catalog
import pl.watchme.domain.model.Channel
import pl.watchme.domain.model.ChannelId
import pl.watchme.domain.model.ChannelPackage
import pl.watchme.domain.model.Email
import pl.watchme.domain.model.ImdbRating
import pl.watchme.domain.model.Programme
import pl.watchme.domain.model.ProgrammeKind
import pl.watchme.domain.model.Session
import pl.watchme.domain.model.TvOperator
import pl.watchme.domain.model.UserId

object TestData {
    val tvp = Channel(ChannelId("tvp-1"), "TVP 1", "ogolne", "Ogólne", "https://logo.example/tvp1.png")
    val tvn = Channel(ChannelId("tvn"), "TVN", "ogolne", "Ogólne", null)
    val hbo = Channel(ChannelId("hbo"), "HBO", "filmowe", "Filmowe", null)
    val playStart = ChannelPackage("play-start", "Play TV Start", listOf(tvp.id, tvn.id))
    val playMax = ChannelPackage("play-max", "Play TV Max", listOf(tvp.id, tvn.id, hbo.id))
    val play = TvOperator("play", "Play", listOf(playStart, playMax))
    val catalog = Catalog(listOf(tvp, tvn, hbo), listOf(play))
    val session = Session(UserId("uid-1"), checkNotNull(Email.of("jan@example.com")))

    fun programme(
        title: String,
        channel: Channel = tvp,
        start: Instant = Instant.parse("2026-09-28T18:00:00Z"),
        stop: Instant = Instant.parse("2026-09-28T20:00:00Z"),
        rating: Double? = null,
        votes: Int? = null,
        kind: ProgrammeKind = ProgrammeKind.MOVIE,
        year: Int? = 2002,
    ) = Programme(
        channelId = channel.id,
        title = title,
        start = start,
        stop = stop,
        kind = kind,
        category = if (kind == ProgrammeKind.MOVIE) "film" else "serial",
        year = year,
        imdbId = null,
        rating = rating?.let(ImdbRating::of),
        votes = votes,
        posterUrl = null,
    )
}
