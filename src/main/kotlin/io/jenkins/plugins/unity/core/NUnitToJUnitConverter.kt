package io.jenkins.plugins.unity.core

import org.w3c.dom.Document
import org.w3c.dom.Element
import java.io.ByteArrayInputStream
import java.io.StringWriter
import javax.xml.XMLConstants
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.transform.OutputKeys
import javax.xml.transform.TransformerFactory
import javax.xml.transform.dom.DOMSource
import javax.xml.transform.stream.StreamResult

class NUnitToJUnitConverter {
    fun convert(nunitXml: String): String {
        val source = parse(nunitXml)
        val target = DocumentBuilderFactory.newInstance().newDocumentBuilder().newDocument()
        val suite = target.createElement("testsuite")
        val cases = source.getElementsByTagName("test-case")
        suite.setAttribute("name", "Unity")
        suite.setAttribute("tests", cases.length.toString())
        var failures = 0
        var skipped = 0
        for (index in 0 until cases.length) {
            val nunitCase = cases.item(index) as Element
            val junitCase = target.createElement("testcase")
            junitCase.setAttribute("name", nunitCase.getAttribute("name").ifBlank { nunitCase.getAttribute("fullname") })
            junitCase.setAttribute("classname", nunitCase.getAttribute("classname").ifBlank { nunitCase.getAttribute("fullname").substringBeforeLast('.', "Unity") })
            junitCase.setAttribute("time", nunitCase.getAttribute("duration").ifBlank { "0" })
            when (nunitCase.getAttribute("result").lowercase()) {
                "failed" -> {
                    failures++
                    val failure = target.createElement("failure")
                    failure.textContent = nunitCase.getElementsByTagName("message").item(0)?.textContent ?: "Unity test failed"
                    junitCase.appendChild(failure)
                }
                "skipped", "ignored" -> {
                    skipped++
                    junitCase.appendChild(target.createElement("skipped"))
                }
            }
            suite.appendChild(junitCase)
        }
        suite.setAttribute("failures", failures.toString())
        suite.setAttribute("skipped", skipped.toString())
        target.appendChild(suite)
        return write(target)
    }

    private fun parse(xml: String): Document {
        val factory = DocumentBuilderFactory.newInstance()
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false)
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false)
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "")
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "")
        return factory.newDocumentBuilder().parse(ByteArrayInputStream(xml.toByteArray(Charsets.UTF_8)))
    }

    private fun write(document: Document): String {
        val transformerFactory = TransformerFactory.newInstance()
        transformerFactory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "")
        transformerFactory.setAttribute(XMLConstants.ACCESS_EXTERNAL_STYLESHEET, "")
        val transformer = transformerFactory.newTransformer()
        transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "no")
        transformer.setOutputProperty(OutputKeys.INDENT, "yes")
        val writer = StringWriter()
        transformer.transform(DOMSource(document), StreamResult(writer))
        return writer.toString()
    }
}
