package pl.watchme.epgjob.domain

object TitleNormalizer {
    private val episodeMarkers = listOf(
        Regex("""[\s,:-]*\bodc\.?\s*\d+.*$""", RegexOption.IGNORE_CASE),
        Regex("""[\s,:-]*\b(sezon|s\.)\s*\d+.*$""", RegexOption.IGNORE_CASE),
        Regex("""\s*\(\d+(/\d+)?\)\s*$"""),
    )
    private val trailingSeasonNumber = Regex("""\s+\d+$""")
    private const val EPISODE_SEPARATOR = ". "
    private val separators = listOf(EPISODE_SEPARATOR, ": ", " - ")
    private val nonAlphanumeric = Regex("[^a-z0-9]+")

    fun clean(title: String): String =
        episodeMarkers.fold(title.trim()) { acc, marker -> acc.replace(marker, "") }
            .trim()
            .trimEnd('.', ' ')

    fun seriesName(title: String): String {
        val series = clean(title).substringBefore(EPISODE_SEPARATOR).trim()
        return series.replace(trailingSeasonNumber, "").ifBlank { series }
    }

    fun candidates(title: String, kind: ProgrammeKind): List<String> {
        val cleaned = clean(title)
        val ordered = when (kind) {
            ProgrammeKind.MOVIE -> listOfNotNull(cleaned, prefixBeforeSeparator(cleaned))
            ProgrammeKind.SERIES -> listOf(seriesName(title), cleaned)
        }
        return ordered.filter { it.isNotBlank() }.distinct()
    }

    fun key(title: String): String =
        TextNormalization.asciiFold(title).lowercase().replace(nonAlphanumeric, " ").trim()

    private fun prefixBeforeSeparator(title: String): String? =
        separators
            .mapNotNull { separator -> title.indexOf(separator).takeIf { it > 0 } }
            .minOrNull()
            ?.let { title.substring(0, it).trim() }
}
