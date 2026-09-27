package pl.watchme.epgjob.domain

import java.text.Normalizer

object TextNormalization {
    private val combiningMarks = Regex("""\p{Mn}+""")

    fun asciiFold(value: String): String =
        Normalizer.normalize(value.replace('ł', 'l').replace('Ł', 'L'), Normalizer.Form.NFD)
            .replace(combiningMarks, "")
}
