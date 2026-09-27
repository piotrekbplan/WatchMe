package pl.watchme.epgjob.data

import java.io.InputStream
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import javax.xml.stream.XMLInputFactory
import javax.xml.stream.XMLStreamConstants.END_ELEMENT
import javax.xml.stream.XMLStreamConstants.START_ELEMENT
import javax.xml.stream.XMLStreamReader
import pl.watchme.epgjob.domain.GuideParser
import pl.watchme.epgjob.domain.GuideWindow
import pl.watchme.epgjob.domain.ParsedGuide
import pl.watchme.epgjob.domain.XmltvChannel
import pl.watchme.epgjob.domain.XmltvProgramme

class XmltvParser : GuideParser {

    private val timeFormat = DateTimeFormatter.ofPattern("yyyyMMddHHmmss Z")

    private val factory: XMLInputFactory = XMLInputFactory.newFactory().apply {
        setProperty(XMLInputFactory.SUPPORT_DTD, false)
        setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false)
    }

    override fun parse(input: InputStream, window: GuideWindow, keep: (XmltvProgramme) -> Boolean): ParsedGuide {
        val reader = factory.createXMLStreamReader(input, Charsets.UTF_8.name())
        val channels = mutableListOf<XmltvChannel>()
        val programmes = mutableListOf<XmltvProgramme>()
        var inWindow = 0
        try {
            while (reader.hasNext()) {
                if (reader.next() != START_ELEMENT) continue
                when (reader.localName) {
                    "channel" -> channels += readChannel(reader)
                    "programme" -> {
                        val programme = readProgramme(reader) ?: continue
                        if (!window.overlaps(programme.start, programme.stop)) continue
                        inWindow++
                        if (keep(programme)) programmes += programme
                    }
                }
            }
        } finally {
            reader.close()
        }
        return ParsedGuide(channels, programmes, inWindow)
    }

    private fun readChannel(reader: XMLStreamReader): XmltvChannel {
        val id = reader.getAttributeValue(null, "id")
        var name: String? = null
        var icon: String? = null
        while (reader.hasNext()) {
            when (reader.next()) {
                START_ELEMENT -> when (reader.localName) {
                    "display-name" -> {
                        val text = reader.elementText.trim()
                        if (name == null) name = text
                    }
                    "icon" -> icon = reader.getAttributeValue(null, "src")
                }
                END_ELEMENT -> if (reader.localName == "channel") break
            }
        }
        return XmltvChannel(id, name ?: id, icon)
    }

    private fun readProgramme(reader: XMLStreamReader): XmltvProgramme? {
        val channel = reader.getAttributeValue(null, "channel")
        val start = reader.getAttributeValue(null, "start")?.let(::parseTime)
        val stop = reader.getAttributeValue(null, "stop")?.let(::parseTime)
        var title: String? = null
        var category: String? = null
        var year: Int? = null
        var icon: String? = null
        while (reader.hasNext()) {
            when (reader.next()) {
                START_ELEMENT -> when (reader.localName) {
                    "title" -> {
                        val text = reader.elementText.trim()
                        if (title == null) title = text
                    }
                    "category" -> {
                        val text = reader.elementText.trim()
                        if (category == null) category = text
                    }
                    "date" -> year = reader.elementText.trim().take(4).toIntOrNull()
                    "icon" -> icon = reader.getAttributeValue(null, "src")
                }
                END_ELEMENT -> if (reader.localName == "programme") break
            }
        }
        val resolvedTitle = title?.takeIf { it.isNotBlank() } ?: return null
        if (channel == null || start == null || stop == null) return null
        return XmltvProgramme(channel, resolvedTitle, start, stop, category, year, icon)
    }

    private fun parseTime(value: String): OffsetDateTime? =
        try {
            OffsetDateTime.parse(value.trim(), timeFormat)
        } catch (e: DateTimeParseException) {
            null
        }
}
