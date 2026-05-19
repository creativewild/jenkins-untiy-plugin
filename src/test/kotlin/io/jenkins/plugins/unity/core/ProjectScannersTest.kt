package io.jenkins.plugins.unity.core

import java.nio.file.Files
import java.nio.file.Path
import org.assertj.core.api.Assertions.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ProjectScannersTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private val tempDir: Path
        get() = temporaryFolder.root.toPath()

    @Test
    fun `collects build profile assets under Assets`() {
        val assets = tempDir.resolve("Assets")
        Files.createDirectories(assets.resolve("Profiles"))
        Files.writeString(assets.resolve("Profiles").resolve("Linux.asset"), "%YAML\nBuildProfile:\n")
        Files.writeString(assets.resolve("Profiles").resolve("Other.asset"), "%YAML\nMonoBehaviour:\n")

        assertThat(BuildProfileScanner.collectProfiles(assets.toFile()))
            .containsExactly("Assets/Profiles/Linux.asset")
    }

    @Test
    fun `reads static editor method references and menu item names`() {
        val script = tempDir.resolve("Builder.cs")
        Files.writeString(
            script,
            """
            namespace Company.Game.Editor {
              public class Builder {
                [MenuItem("Build/Linux")]
                public static void BuildLinux() {}
                static void Hidden() {}
              }
            }
            """.trimIndent(),
        )

        assertThat(CSharpStaticMethodScanner.readStaticMethods(script.toFile()))
            .containsEntry("Company.Game.Editor.Builder.BuildLinux", "Build/Linux")
            .containsEntry("Company.Game.Editor.Builder.Hidden", null)
    }
}
