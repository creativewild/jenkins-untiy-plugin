package io.jenkins.plugins.unity.core

import java.io.InputStream

class UnityConfigReader {
    fun readValue(stream: InputStream, key: String): String? =
        stream.bufferedReader().useLines { lines ->
            lines
                .mapNotNull { line -> line.trim().split(":", limit = 2).takeIf { it.size == 2 } }
                .map { it[0].trim() to it[1].trim() }
                .firstOrNull { it.first == key }
                ?.second
        }
}

interface UnityProjectFilesAccessor {
    fun directory(name: String): UnityProjectFilesAccessor?
    fun file(name: String): InputStream?
}

class UnityProject(private val filesAccessor: UnityProjectFilesAccessor) {
    private val reader = UnityConfigReader()

    val unityVersion: UnityVersion? by lazy {
        val stream = filesAccessor
            .directory(PROJECT_SETTINGS)
            ?.file(PROJECT_VERSION_FILE)
            ?: return@lazy null

        reader.readValue(stream, "m_EditorVersion")?.let(UnityVersion::tryParse)
    }

    val assetPipelineVersion: AssetPipelineVersion? by lazy {
        val stream = filesAccessor
            .directory(PROJECT_SETTINGS)
            ?.file(EDITOR_SETTINGS_FILE)
            ?: return@lazy null

        AssetPipelineVersion.from(reader.readValue(stream, "m_AssetPipelineMode"))
    }

    companion object {
        const val PROJECT_SETTINGS = "ProjectSettings"
        const val PROJECT_VERSION_FILE = "ProjectVersion.txt"
        const val EDITOR_SETTINGS_FILE = "EditorSettings.asset"
    }
}
