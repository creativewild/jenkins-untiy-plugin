package io.jenkins.plugins.unity.core

import org.w3c.dom.Element
import org.w3c.dom.NodeList
import java.io.File
import java.io.InputStream
import javax.xml.XMLConstants
import javax.xml.parsers.DocumentBuilderFactory

enum class LineStatus {
    NORMAL,
    WARNING,
    ERROR,
}

class LineStatusProvider {
    private val patterns = mutableListOf<Pair<Regex, LineStatus>>()

    constructor() {
        patterns += Regex(".*?warning CS\\d+.*?") to LineStatus.WARNING
        patterns += Regex("WARNING.*") to LineStatus.WARNING
        patterns += Regex(".*?error CS\\d+.*?") to LineStatus.ERROR
        patterns += Regex("Compilation failed:.*") to LineStatus.ERROR
        patterns += Regex("Scripts have compiler errors\\..*") to LineStatus.ERROR
        patterns += Regex("Fatal Error!.*") to LineStatus.ERROR
        patterns += Regex("executeMethod method .+ threw exception") to LineStatus.ERROR
        patterns += Regex("executeMethod class .+ could not be found") to LineStatus.ERROR
        patterns += Regex("Couldn't set project path to:.+") to LineStatus.ERROR
        patterns += Regex("Failed to activate/update license") to LineStatus.ERROR
        patterns += Regex("Error building player .+") to LineStatus.ERROR
    }

    constructor(lineStatusesFile: File) : this(lineStatusesFile.inputStream())

    constructor(stream: InputStream) {
        val factory = DocumentBuilderFactory.newInstance()
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false)
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false)
        factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false)
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "")
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "")
        val document = stream.use { factory.newDocumentBuilder().parse(it) }
        document.documentElement.normalize()
        document.getElementsByTagName("line").nodes<Element>().forEach {
            val status = when (it.getAttribute("level").lowercase()) {
                "warning" -> LineStatus.WARNING
                "error" -> LineStatus.ERROR
                else -> LineStatus.NORMAL
            }
            val message = it.getAttribute("message")
            if (message.isNotBlank()) patterns += Regex(message) to status
        }
    }

    fun status(text: String): LineStatus =
        patterns.firstOrNull { it.first.containsMatchIn(text) }?.second ?: LineStatus.NORMAL

    companion object {
        inline fun <reified T> NodeList.nodes(): Sequence<T> = sequence {
            for (index in 0 until length) yield(item(index))
        }.filterIsInstance<T>()
    }
}

data class LogLine(val text: String, val status: LineStatus, val block: String? = null)

class UnityLogProcessor(private val provider: LineStatusProvider = LineStatusProvider()) {
    var hasErrors: Boolean = false
        private set

    fun process(line: String): LogLine {
        val block = blockName(line)
        val status = provider.status(line)
        if (status == LineStatus.ERROR) hasErrors = true
        return LogLine(line, status, block)
    }

    private fun blockName(line: String): String? = when {
        line.contains("Build Report") -> "Build Report"
        line.contains("CompilerOutput") -> "Compile"
        line.contains("Starting script compilation") -> "Script Compilation"
        line.contains("Package Manager") -> "Package Manager"
        line.contains("RefreshInfo") -> "Refresh"
        else -> null
    }
}
