package pl.watchme.epgjob.domain

object TitleNormalizer {
    private val episodeMarkers = listOf(
        Regex("""[\s,:-]*\bodc\.?\s*\d+.*$""", RegexOption.IGNORE_CASE),
        Regex("""[\s,:-]*\b(sezon|s\.)\s*\d+.*$""", RegexOption.IGNORE_CASE),
        Regex("""\s*\(\d+(/\d+)?\)\s*$"""),
    )
    private val trailingSeasonNumber = Regex("""\s+\d+$""")
    private val separators = listOf(". ", ": ", " - ")
    private val nonAlphanumeric = Regex("[^a-z0-9]+")

    fun clean(title: String): String =
        episodeMarkers.fold(title.trim()) { acc, marker -> acc.replace(marker, "") }
            .trim()
            .trimEnd('.', ' ')

    fun candidates(title: String): List<String> {
        val cleaned = clean(title)
        val prefix = separators
            .mapNotNull { separator -> cleaned.indexOf(separator).takeIf { it > 0 } }
            .minOrNull()
            ?.let { cleaned.substring(0, it).trim() }
        val base = listOfNotNull(cleaned, prefix)
        val withoutSeason = base.map { it.replace(trailingSeasonNumber, "") }
        return (base + withoutSeason).filter { it.isNotBlank() }.distinct()
    }

    fun key(title: String): String =
        TextNormalization.asciiFold(title).lowercase().replace(nonAlphanumeric, " ").trim()
}
