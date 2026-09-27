package pl.watchme.epgjob.domain

import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import assertk.assertions.isTrue
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import org.junit.jupiter.api.Test
import pl.watchme.epgjob.fakes.FakeMovieCatalog
import pl.watchme.epgjob.fakes.FakeRatingSource
import pl.watchme.epgjob.fakes.movieHit
import pl.watchme.epgjob.fakes.seriesHit

class TitleMatcherTest {

    private val now = Instant.parse("2026-09-27T10:00:00Z")
    private val today = LocalDate.parse("2026-09-27")
    private val catalog = FakeMovieCatalog()
    private val ratings = FakeRatingSource()
    private val cache = MatchCache()

    private fun matcher(budget: Int = 900) =
        TitleMatcher(catalog, ratings, Clock.fixed(now, ZoneOffset.UTC), dailyOmdbBudget = budget)

    private fun movie(title: String, year: Int?) = MatchRequest(MatchKey.of(title, ProgrammeKind.MOVIE, year), title, year)

    private fun series(title: String, year: Int?) = MatchRequest(MatchKey.of(title, ProgrammeKind.SERIES, year), title, year)

    @Test
    fun `new movie is matched and rated`() {
        catalog.hits["Pianista" to ProgrammeKind.MOVIE] = listOf(movieHit(1, "Pianista", 2002, "The Pianist"))
        catalog.imdbIds[1] = "tt0253474"
        ratings.scores["tt0253474"] = ImdbScore(8.5, 950000)
        val request = movie("Pianista", 2002)

        val outcome = matcher().resolve(listOf(request), cache)

        val entry = outcome.entries.getValue(request.key)
        assertThat(entry.imdbId).isEqualTo("tt0253474")
        assertThat(entry.rating).isEqualTo(8.5)
        assertThat(entry.votes).isEqualTo(950000)
        assertThat(entry.posterUrl).isEqualTo("https://img.example/1.jpg")
        assertThat(entry.ratedAt).isEqualTo(now)
        assertThat(cache.omdbCallsOn(today)).isEqualTo(1)
        assertThat(outcome.stats).isEqualTo(MatchStats(searched = 1, matched = 1, rated = 1, failures = 0, ratingsUnavailable = false))
    }

    @Test
    fun `movie with year off by more than one is rejected`() {
        catalog.hits["Diuna" to ProgrammeKind.MOVIE] = listOf(movieHit(2, "Diuna", 1984))
        catalog.imdbIds[2] = "tt0087182"
        val request = movie("Diuna", 2021)

        val entry = matcher().resolve(listOf(request), cache).entries.getValue(request.key)

        assertThat(entry.imdbId).isNull()
        assertThat(entry.checkedAt).isEqualTo(now)
        assertThat(ratings.calls).isEmpty()
    }

    @Test
    fun `original title match is accepted`() {
        catalog.hits["The Pianist" to ProgrammeKind.MOVIE] = listOf(movieHit(1, "Pianista", 2002, "The Pianist"))
        catalog.imdbIds[1] = "tt0253474"

        val entry = matcher().resolve(listOf(movie("The Pianist", 2003)), cache).entries.values.single()

        assertThat(entry.imdbId).isEqualTo("tt0253474")
    }

    @Test
    fun `series ignores year and falls back to title prefix`() {
        catalog.hits["Dom pod Dwoma Orłami" to ProgrammeKind.SERIES] = listOf(seriesHit(5, "Dom pod dwoma orłami", 2023))
        catalog.imdbIds[5] = "tt5"
        val request = series("Dom pod Dwoma Orłami. Kłamstwa Zofii. ", 2026)

        val entry = matcher().resolve(listOf(request), cache).entries.getValue(request.key)

        assertThat(request.key.year).isNull()
        assertThat(catalog.searches).containsExactly("Dom pod Dwoma Orłami. Kłamstwa Zofii", "Dom pod Dwoma Orłami")
        assertThat(entry.imdbId).isEqualTo("tt5")
    }

    @Test
    fun `same title airing many times is searched once`() {
        catalog.hits["Pianista" to ProgrammeKind.MOVIE] = listOf(movieHit(1, "Pianista", 2002))
        catalog.imdbIds[1] = "tt1"

        matcher().resolve(listOf(movie("Pianista", 2002), movie("Pianista", 2002), movie("PIANISTA", 2002)), cache)

        assertThat(catalog.searches).containsExactly("Pianista")
        assertThat(ratings.calls).containsExactly("tt1")
    }

    @Test
    fun `title without usable candidates is not searched`() {
        val request = series("odc. 5", null)

        val entry = matcher().resolve(listOf(request), cache).entries.getValue(request.key)

        assertThat(catalog.searches).isEmpty()
        assertThat(entry.imdbId).isNull()
    }

    @Test
    fun `fresh negative entry is not searched again`() {
        val request = movie("Nieznany", 2020)
        cache[request.key] = MatchEntry.notFound(now.minus(Duration.ofDays(6)))

        matcher().resolve(listOf(request), cache)

        assertThat(catalog.searches).isEmpty()
    }

    @Test
    fun `stale negative entry is searched again`() {
        val request = movie("Nieznany", 2020)
        cache[request.key] = MatchEntry.notFound(now.minus(Duration.ofDays(8)))

        matcher().resolve(listOf(request), cache)

        assertThat(catalog.searches).containsExactly("Nieznany")
    }

    @Test
    fun `fresh rating is reused and stale rating is refreshed`() {
        val fresh = movie("Pianista", 2002)
        val stale = movie("Diuna", 2021)
        cache[fresh.key] = rated("tt1", 8.5, now.minus(Duration.ofDays(2)))
        cache[stale.key] = rated("tt2", 7.0, now.minus(Duration.ofDays(8)))
        ratings.scores["tt2"] = ImdbScore(8.0, 700000)

        val outcome = matcher().resolve(listOf(fresh, stale), cache)

        assertThat(ratings.calls).containsExactly("tt2")
        assertThat(outcome.entries.getValue(fresh.key).rating).isEqualTo(8.5)
        assertThat(outcome.entries.getValue(stale.key).rating).isEqualTo(8.0)
    }

    @Test
    fun `exhausted budget keeps imdb id without rating`() {
        catalog.hits["Pianista" to ProgrammeKind.MOVIE] = listOf(movieHit(1, "Pianista", 2002))
        catalog.imdbIds[1] = "tt1"
        val usedUp = MatchCache(omdbUsage = OmdbUsage(today, 900))
        val request = movie("Pianista", 2002)

        val entry = matcher().resolve(listOf(request), usedUp).entries.getValue(request.key)

        assertThat(ratings.calls).isEmpty()
        assertThat(entry.imdbId).isEqualTo("tt1")
        assertThat(entry.ratedAt).isNull()
    }

    @Test
    fun `budget resets on a new utc day`() {
        val yesterday = MatchCache(omdbUsage = OmdbUsage(today.minusDays(1), 900))
        val request = movie("Pianista", 2002)
        yesterday[request.key] = rated("tt1", null, null)

        matcher().resolve(listOf(request), yesterday)

        assertThat(ratings.calls).containsExactly("tt1")
        assertThat(yesterday.omdbCallsOn(today)).isEqualTo(1)
    }

    @Test
    fun `new titles are rated before stale refreshes`() {
        val stale = movie("Stary", 2000)
        val fresh = movie("Nowy", 2026)
        cache[stale.key] = rated("tt-old", 6.0, now.minus(Duration.ofDays(30)))
        catalog.hits["Nowy" to ProgrammeKind.MOVIE] = listOf(movieHit(9, "Nowy", 2026))
        catalog.imdbIds[9] = "tt-new"

        matcher(budget = 1).resolve(listOf(stale, fresh), cache)

        assertThat(ratings.calls).containsExactly("tt-new")
    }

    @Test
    fun `unavailable rating source stops further calls`() {
        ratings.unavailable = true
        val first = movie("A", 2000)
        val second = movie("B", 2000)
        cache[first.key] = rated("tt-a", null, null)
        cache[second.key] = rated("tt-b", null, null)

        val outcome = matcher().resolve(listOf(first, second), cache)

        assertThat(ratings.calls).containsExactly("tt-a")
        assertThat(outcome.stats.ratingsUnavailable).isTrue()
        assertThat(outcome.entries.getValue(second.key).imdbId).isEqualTo("tt-b")
    }

    @Test
    fun `search failure leaves no entry and continues`() {
        catalog.failingQuery = "Awaria"
        catalog.hits["Pianista" to ProgrammeKind.MOVIE] = listOf(movieHit(1, "Pianista", 2002))
        catalog.imdbIds[1] = "tt1"
        val broken = movie("Awaria", 2020)

        val outcome = matcher().resolve(listOf(broken, movie("Pianista", 2002)), cache)

        assertThat(cache[broken.key]).isNull()
        assertThat(outcome.stats.failures).isEqualTo(1)
        assertThat(outcome.entries.size).isEqualTo(1)
    }

    @Test
    fun `seen entries get last seen timestamp and old ones are pruned`() {
        val seen = movie("Pianista", 2002)
        val forgotten = movie("Dawno", 1990)
        cache[seen.key] = rated("tt1", 8.5, now).copy(lastSeenAt = now.minus(Duration.ofDays(40)))
        cache[forgotten.key] = rated("tt9", 5.0, now).copy(lastSeenAt = now.minus(Duration.ofDays(40)))

        matcher().resolve(listOf(seen), cache)
        cache.prune(now.minus(Duration.ofDays(30)))

        assertThat(cache[seen.key]).isNotNull()
        assertThat(cache[forgotten.key]).isNull()
    }

    private fun rated(imdbId: String, rating: Double?, ratedAt: Instant?) =
        MatchEntry(imdbId, rating, null, null, checkedAt = now.minus(Duration.ofDays(1)), ratedAt = ratedAt, lastSeenAt = now)
}
