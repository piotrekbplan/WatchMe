package pl.watchme.epg.contract

import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import kotlinx.serialization.json.Json

object EpgContract {
    const val SCHEMA_VERSION = 1
    const val CHANNELS_FILE = "channels.json"
    const val OPERATORS_FILE = "operators.json"
    const val GUIDE_DIR = "epg"

    fun guideFile(channelId: String): String = "$GUIDE_DIR/$channelId.json"

    val json: Json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }

    private val timestampFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssXXX")

    fun formatTimestamp(value: OffsetDateTime): String = value.format(timestampFormat)

    fun parseTimestamp(value: String): OffsetDateTime = OffsetDateTime.parse(value, timestampFormat)
}
