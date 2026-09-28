package pl.watchme.domain.model

@JvmInline
value class UserId(val value: String)

@JvmInline
value class Email private constructor(val value: String) {
    companion object {
        private val pattern = Regex("""^[^@\s]+@[^@\s]+\.[^@\s]+$""")

        fun of(raw: String): Email? = raw.trim().takeIf(pattern::matches)?.let(::Email)
    }
}

@JvmInline
value class Password private constructor(val value: String) {
    companion object {
        const val MIN_LENGTH = 6

        fun of(raw: String): Password? = raw.takeIf { it.length >= MIN_LENGTH }?.let(::Password)
    }
}

data class Session(val userId: UserId, val email: Email)
