package pl.watchme.epgjob.fakes

import java.io.IOException
import pl.watchme.epgjob.domain.CatalogHit
import pl.watchme.epgjob.domain.ImdbScore
import pl.watchme.epgjob.domain.MovieCatalog
import pl.watchme.epgjob.domain.ProgrammeKind
import pl.watchme.epgjob.domain.RatingSource
import pl.watchme.epgjob.domain.RatingSourceUnavailableException

class FakeMovieCatalog : MovieCatalog {
    val hits = mutableMapOf<Pair<String, ProgrammeKind>, List<CatalogHit>>()
    val imdbIds = mutableMapOf<Long, String>()
    val searches = mutableListOf<String>()
    var failingQuery: String? = null

    override fun search(title: String, kind: ProgrammeKind): List<CatalogHit> {
        searches += title
        if (title == failingQuery) throw IOException("search failed")
        return hits[title to kind].orEmpty()
    }

    override fun imdbId(hit: CatalogHit): String? = imdbIds[hit.tmdbId]
}

class FakeRatingSource : RatingSource {
    val scores = mutableMapOf<String, ImdbScore>()
    val calls = mutableListOf<String>()
    var unavailable = false

    override fun rating(imdbId: String): ImdbScore? {
        calls += imdbId
        if (unavailable) throw RatingSourceUnavailableException("limit reached")
        return scores[imdbId]
    }
}

fun movieHit(id: Long, title: String, year: Int?, originalTitle: String = title) =
    CatalogHit(id, ProgrammeKind.MOVIE, title, originalTitle, year, "https://img.example/$id.jpg")

fun seriesHit(id: Long, title: String, year: Int?) =
    CatalogHit(id, ProgrammeKind.SERIES, title, title, year, null)
