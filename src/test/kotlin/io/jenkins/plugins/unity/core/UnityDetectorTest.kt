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

    private fun fakeLinuxUnity(versionDirectory: String): Path {
        val root = tempDir.resolve(versionDirectory)
        Files.createDirectories(root.resolve("Editor"))
        Files.writeString(root.resolve("Editor").resolve("Unity"), "#!/bin/sh\n")
        return root
    }

    private fun request(
        detectionMode: DetectionMode = DetectionMode.AUTO,
        requestedVersion: UnityVersion? = null,
        projectVersion: UnityVersion? = null,
        unityRoot: String? = null,
        unityToolName: String? = null,
        unityTools: Map<String, String> = emptyMap(),
    ) = UnityDetectionRequest(
        detectionMode = detectionMode,
        requestedVersion = requestedVersion,
        projectVersion = projectVersion,
        unityRoot = unityRoot,
        unityToolName = unityToolName,
        unityTools = unityTools,
        env = emptyMap(),
        userHome = tempDir.toString(),
        osName = "Linux",
    )
}
