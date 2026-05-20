package io.jenkins.plugins.unity.core

import java.nio.file.Files
import java.nio.file.Path
import org.assertj.core.api.Assertions.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class UnityDetectorTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private val tempDir: Path
        get() = temporaryFolder.root.toPath()

    @Test
    fun `manual detection resolves editor under provided root`() {
        val root = fakeLinuxUnity("2022.3.16f1")

        val selected = UnityDetector().select(
            request(
                detectionMode = DetectionMode.MANUAL,
                unityRoot = root.toString(),
            ),
        )

        assertThat(selected.unityVersion).isEqualTo(UnityVersion(2022, 3, 16))
        assertThat(selected.unityPath).endsWith("Editor${java.io.File.separator}Unity")
        assertThat(selected.source).isEqualTo("manual")
    }

    @Test
    fun `auto detection prefers requested minor stream over latest installed`() {
        val older = fakeLinuxUnity("2022.3.1f1")
        val requestedStream = fakeLinuxUnity("2022.3.16f1")
        val newer = fakeLinuxUnity("2023.1.4f1")

        val selected = UnityDetector().select(
            request(
                requestedVersion = UnityVersion(2022, 3),
                unityTools = mapOf(
                    "older" to older.toString(),
                    "requested" to requestedStream.toString(),
                    "newer" to newer.toString(),
                ),
            ),
        )

        assertThat(selected.unityVersion).isEqualTo(UnityVersion(2022, 3, 16))
    }

    @Test
    fun `tool detection uses named Jenkins tool`() {
        val selectedRoot = fakeLinuxUnity("6000.0.23f1")
        fakeLinuxUnity("2022.3.16f1")

        val selected = UnityDetector().select(
            request(
                detectionMode = DetectionMode.TOOL,
                unityToolName = "Unity 6",
                unityTools = mapOf("Unity 6" to selectedRoot.toString()),
            ),
        )

        assertThat(selected.unityVersion).isEqualTo(UnityVersion(6000, 0, 23))
        assertThat(selected.source).isEqualTo("tool:Unity 6")
    }

    @Test
    fun `tool detection accepts Windows editor directory as Jenkins tool home`() {
        val selectedRoot = fakeWindowsUnity("Editor-6000.3.5f2")
        val editor = selectedRoot.resolve("Editor")

        val selected = UnityDetector().select(
            request(
                detectionMode = DetectionMode.TOOL,
                unityToolName = "Unity.exe",
                unityTools = mapOf("Unity.exe" to editor.toString()),
                osName = "Windows",
            ),
        )

        assertThat(selected.unityVersion).isEqualTo(UnityVersion(6000, 3, 5))
        assertThat(selected.unityPath).endsWith("Editor${java.io.File.separator}Unity.exe")
        assertThat(selected.source).isEqualTo("tool:Unity.exe")
    }

    @Test
    fun `tool detection accepts Windows executable as Jenkins tool home`() {
        val selectedRoot = fakeWindowsUnity("Editor-6000.3.5f2")
        val executable = selectedRoot.resolve("Editor").resolve("Unity.exe")

        val selected = UnityDetector().select(
            request(
                detectionMode = DetectionMode.TOOL,
                unityToolName = "Unity.exe",
                unityTools = mapOf("Unity.exe" to executable.toString()),
                osName = "Windows",
            ),
        )

        assertThat(selected.unityVersion).isEqualTo(UnityVersion(6000, 3, 5))
        assertThat(selected.unityPath).endsWith("Editor${java.io.File.separator}Unity.exe")
        assertThat(selected.source).isEqualTo("tool:Unity.exe")
    }

    private fun fakeLinuxUnity(versionDirectory: String): Path {
        val root = tempDir.resolve(versionDirectory)
        Files.createDirectories(root.resolve("Editor"))
        Files.writeString(root.resolve("Editor").resolve("Unity"), "#!/bin/sh\n")
        return root
    }

    private fun fakeWindowsUnity(versionDirectory: String): Path {
        val root = tempDir.resolve(versionDirectory)
        Files.createDirectories(root.resolve("Editor"))
        Files.writeString(root.resolve("Editor").resolve("Unity.exe"), "")
        return root
    }

    private fun request(
        detectionMode: DetectionMode = DetectionMode.AUTO,
        requestedVersion: UnityVersion? = null,
        projectVersion: UnityVersion? = null,
        unityRoot: String? = null,
        unityToolName: String? = null,
        unityTools: Map<String, String> = emptyMap(),
        osName: String = "Linux",
    ) = UnityDetectionRequest(
        detectionMode = detectionMode,
        requestedVersion = requestedVersion,
        projectVersion = projectVersion,
        unityRoot = unityRoot,
        unityToolName = unityToolName,
        unityTools = unityTools,
        env = emptyMap(),
        userHome = tempDir.toString(),
        osName = osName,
    )
}
