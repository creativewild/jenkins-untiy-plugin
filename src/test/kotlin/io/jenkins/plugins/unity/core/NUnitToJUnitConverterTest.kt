package io.jenkins.plugins.unity.core

import javax.xml.parsers.DocumentBuilderFactory
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test
import org.w3c.dom.Element

class NUnitToJUnitConverterTest {
    @Test
    fun `converts Unity NUnit output to Jenkins JUnit shape`() {
        val junit = NUnitToJUnitConverter().convert(
            """
            <test-run>
              <test-suite>
                <test-case fullname="Game.Tests.Passes" name="Passes" classname="Game.Tests" duration="0.12" result="Passed" />
                <test-case fullname="Game.Tests.Fails" name="Fails" classname="Game.Tests" duration="0.05" result="Failed">
                  <failure><message>boom</message></failure>
                </test-case>
                <test-case fullname="Game.Tests.Skips" name="Skips" classname="Game.Tests" duration="0" result="Skipped" />
              </test-suite>
            </test-run>
            """.trimIndent(),
        )

        val suite = DocumentBuilderFactory.newInstance()
            .newDocumentBuilder()
            .parse(junit.byteInputStream())
            .documentElement

        assertThat(suite.tagName).isEqualTo("testsuite")
        assertThat(suite.getAttribute("tests")).isEqualTo("3")
        assertThat(suite.getAttribute("failures")).isEqualTo("1")
        assertThat(suite.getAttribute("skipped")).isEqualTo("1")

        val cases = suite.getElementsByTagName("testcase")
        assertThat((cases.item(1) as Element).getElementsByTagName("failure").item(0).textContent).isEqualTo("boom")
        assertThat((cases.item(2) as Element).getElementsByTagName("skipped").length).isEqualTo(1)
    }
}
