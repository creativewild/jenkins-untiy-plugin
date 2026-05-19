package io.jenkins.plugins.unity.core

import org.assertj.core.api.Assertions.assertThat
import org.junit.Test

class UnityLogProcessorTest {
    @Test
    fun `classifies built in Unity warnings and errors`() {
        val processor = UnityLogProcessor()

        assertThat(processor.process("Assets/Foo.cs(10,5): warning CS0618: obsolete").status)
            .isEqualTo(LineStatus.WARNING)
        assertThat(processor.process("Scripts have compiler errors.").status)
            .isEqualTo(LineStatus.ERROR)
        assertThat(processor.hasErrors).isTrue()
    }

    @Test
    fun `loads custom line statuses from XML`() {
        val provider = LineStatusProvider(
            """
            <statuses>
              <line level="warning" message="Custom warning.*" />
              <line level="error" message="Custom failure.*" />
            </statuses>
            """.trimIndent().byteInputStream(),
        )

        assertThat(provider.status("Custom warning in asset import")).isEqualTo(LineStatus.WARNING)
        assertThat(provider.status("Custom failure in build")).isEqualTo(LineStatus.ERROR)
    }
}
