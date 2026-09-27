package pl.watchme.epgjob.domain

data class CatalogHit(
    val tmdbId: Long,
    val kind: ProgrammeKind,
    val title: String,
    val originalTitle: String,
    val year: Int?,
    val posterUrl: String?,
)

interface MovieCatalog {
    fun search(title: String, kind: ProgrammeKind): List<CatalogHit>
    fun imdbId(hit: CatalogHit): String?
}

data class ImdbScore(val rating: Double, val votes: Int?)

interface RatingSource {
    fun rating(imdbId: String): ImdbScore?
}

class RatingSourceUnavailableException(message: String) : RuntimeException(message)
