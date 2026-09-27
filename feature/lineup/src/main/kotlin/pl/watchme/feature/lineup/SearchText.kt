package pl.watchme.feature.lineup

import java.text.Normalizer

internal object SearchText {
    private val combiningMarks = Regex("""\p{Mn}+""")

    fun normalize(value: String): String =
        Normalizer.normalize(value.replace('ł', 'l').replace('Ł', 'L'), Normalizer.Form.NFD)
            .replace(combiningMarks, "")
            .lowercase()
            .trim()

    fun matches(candidate: String, query: String): Boolean =
        query.isBlank() || normalize(candidate).contains(normalize(query))
}
