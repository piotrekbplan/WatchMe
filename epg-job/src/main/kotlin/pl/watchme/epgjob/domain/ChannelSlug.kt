package pl.watchme.epgjob.domain

object ChannelSlug {
    private val nonAlphanumeric = Regex("[^a-z0-9]+")

    fun of(name: String): String =
        TextNormalization.asciiFold(name.replace("+", " plus "))
            .lowercase()
            .replace(nonAlphanumeric, "-")
            .trim('-')
}
