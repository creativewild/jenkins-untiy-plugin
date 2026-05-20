package io.jenkins.plugins.unity.core

import java.io.BufferedReader
import java.io.File
import java.io.Serializable
import java.util.concurrent.TimeUnit

data class UnityDetectionRequest(
    val detectionMode: DetectionMode,
    val requestedVersion: UnityVersion?,
    val projectVersion: UnityVersion?,
    val unityRoot: String?,
    val unityToolName: String?,
    val unityTools: Map<String, String>,
    val env: Map<String, String>,
    val userHome: String,
    val osName: String,
) : Serializable

class UnityDetector {
    private val installDirectoryVersionRegex = Regex("""(?:^|[^\d])(\d+)\.(\d+)(?:\.(\d+))?""")


    fun select(request: UnityDetectionRequest): UnityEnvironment {
        val os = OperatingSystem.from(request.osName)
        return when (request.detectionMode) {
            DetectionMode.MANUAL -> {
                val root = request.unityRoot?.takeIf { it.isNotBlank() }
                    ?: throw IllegalStateException("Manual Unity detection requires unityRoot")
                environmentFromRoot(File(root), os, "manual")
                    ?: throw IllegalStateException("Unable to detect Unity at $root")
            }
            DetectionMode.TOOL -> {
                val name = request.unityToolName?.takeIf { it.isNotBlank() }
                    ?: throw IllegalStateException("Tool Unity detection requires unityToolName")
                val root = request.unityTools[name]
                    ?: throw IllegalStateException("Jenkins Unity tool '$name' was not found")
                environmentFromRoot(File(root), os, "tool:$name")
                    ?: throw IllegalStateException("Unable to detect Unity from Jenkins tool '$name' at $root")
            }
            DetectionMode.AUTO -> selectAuto(request, os)
        }
    }

    fun editorExecutable(root: File, os: OperatingSystem): File = when (os) {
        OperatingSystem.WINDOWS -> File(root, "Editor/Unity.exe")
        OperatingSystem.MAC -> File(root, "Unity.app/Contents/MacOS/Unity")
        OperatingSystem.LINUX -> File(root, "Editor/Unity")
    }

    private fun selectAuto(request: UnityDetectionRequest, os: OperatingSystem): UnityEnvironment {
        val environments = findInstallations(request, os).distinctBy { it.unityPath }
        if (environments.isEmpty()) {
            throw IllegalStateException("No Unity installation was found")
        }

        val requested = request.requestedVersion ?: request.projectVersion
        if (requested != null) {
            environments.firstOrNull { it.unityVersion == requested }?.let { return it }
            val upper = if (requested.minor == null) requested.nextMajor() else requested.nextMinor()
            environments
                .filter { it.unityVersion >= requested && it.unityVersion < upper }
                .maxByOrNull { it.unityVersion }
                ?.let { return it }
        }

        return environments.maxBy { it.unityVersion }
    }

    fun findInstallations(request: UnityDetectionRequest, os: OperatingSystem): List<UnityEnvironment> {
        val roots = linkedSetOf<File>()
        request.unityTools.values.mapTo(roots) { File(it) }
        request.env["UNITY_HOME"]?.split(File.pathSeparatorChar)?.filter { it.isNotBlank() }?.mapTo(roots) { File(it) }
        request.env["UNITY_HINT_PATH"]?.split(File.pathSeparatorChar)?.filter { it.isNotBlank() }?.forEach {
            roots += findUnityRoots(File(it))
        }
        request.env["PATH"]?.split(File.pathSeparatorChar)?.filter { it.isNotBlank() }?.forEach {
            val path = File(it)
            if (path.name.equals("Editor", true) || path.path.contains("Unity", true)) {
                roots += path.toUnityRootCandidate(os)
            }
        }
        roots += unityHubRoots(File(request.userHome), os)
        roots += knownRoots(request.userHome, os).flatMap(::findUnityRoots)

        return roots.mapNotNull { environmentFromRoot(it, os, "auto") }
    }

    private fun environmentFromRoot(root: File, os: OperatingSystem, source: String): UnityEnvironment? {
        val executable = editorExecutableCandidates(root, os).firstOrNull { it.exists() } ?: return null
        val version = versionFromPath(root, executable, os) ?: versionFromExecutable(executable)
        return version?.let { UnityEnvironment(executable.absolutePath, it, source) }
    }

    private fun editorExecutableCandidates(path: File, os: OperatingSystem): List<File> {
        val candidates = linkedSetOf<File>()
        when (os) {
            OperatingSystem.WINDOWS -> {
                if (path.name.equals("Unity.exe", true)) candidates += path
                if (path.name.equals("Editor", true)) candidates += File(path, "Unity.exe")
                candidates += File(path, "Editor/Unity.exe")
            }
            OperatingSystem.MAC -> {
                if (path.name.equals("Unity", true)) candidates += path
                if (path.name.equals("Unity.app", true)) candidates += File(path, "Contents/MacOS/Unity")
                if (path.name.equals("MacOS", true)) candidates += File(path, "Unity")
                candidates += File(path, "Unity.app/Contents/MacOS/Unity")
            }
            OperatingSystem.LINUX -> {
                if (path.name == "Unity") candidates += path
                if (path.name.equals("Editor", true)) candidates += File(path, "Unity")
                candidates += File(path, "Editor/Unity")
            }
        }
        return candidates.toList()
    }

    private fun versionFromPath(root: File, executable: File, os: OperatingSystem): UnityVersion? =
        versionPathCandidates(root, executable, os)
            .asSequence()
            .mapNotNull { versionFromPathName(it.name) }
            .firstOrNull()

    private fun versionFromPathName(name: String): UnityVersion? =
        UnityVersion.tryParse(name)
            ?: installDirectoryVersionRegex.find(name)?.let { match ->
                UnityVersion(
                    match.groupValues[1].toInt(),
                    match.groupValues[2].toInt(),
                    match.groupValues.getOrNull(3)?.takeIf { it.isNotBlank() }?.toInt(),
                )
            }

    private fun versionPathCandidates(root: File, executable: File, os: OperatingSystem): List<File> {
        val candidates = linkedSetOf<File>()
        candidates += root
        when (os) {
            OperatingSystem.WINDOWS, OperatingSystem.LINUX -> {
                root.parentFile?.let { candidates += it }
                root.parentFile?.parentFile?.let { candidates += it }
                executable.parentFile?.parentFile?.let { candidates += it }
            }
            OperatingSystem.MAC -> {
                root.parentFile?.let { candidates += it }
                root.parentFile?.parentFile?.let { candidates += it }
                executable.parentFile?.parentFile?.parentFile?.parentFile?.let { candidates += it }
            }
        }
        return candidates.toList()
    }

    private fun versionFromExecutable(executable: File): UnityVersion? {
        if (!executable.exists()) return null
        return try {
            val process = ProcessBuilder(executable.absolutePath, "-version").redirectErrorStream(true).start()
            if (!process.waitFor(4, TimeUnit.SECONDS)) {
                process.destroyForcibly()
                return null
            }
            val output = process.inputStream.bufferedReader().use(BufferedReader::readText)
            UnityVersion.tryParse(output)
        } catch (_: Exception) {
            null
        }
    }

    private fun findUnityRoots(base: File): List<File> {
        val result = mutableListOf<File>()
        if (!base.exists()) return result
        if (base.isDirectory && base.name.startsWith("Unity", true)) result += base
        File(base, "Unity/Hub/Editor").listFiles { file -> file.isDirectory }?.let { result += it }
        base.listFiles { file -> file.isDirectory && file.name.startsWith("Unity", true) }?.let { result += it }
        return result
    }

    private fun unityHubRoots(userHome: File, os: OperatingSystem): List<File> {
        val config = when (os) {
            OperatingSystem.WINDOWS -> File(userHome, "AppData/Roaming/UnityHub")
            OperatingSystem.MAC -> File(userHome, "Library/Application support/UnityHub")
            OperatingSystem.LINUX -> File(userHome, ".config/UnityHub")
        }
        val result = mutableListOf<File>()
        File(config, "editors.json").takeIf { it.exists() }?.readText()?.let { text ->
            Regex(""""location"\s*:\s*"([^"]+)"""").findAll(text).forEach { match ->
                result += File(match.groupValues[1]).parentFile?.parentFile ?: return@forEach
            }
        }
        File(config, "secondaryInstallPath.json").takeIf { it.exists() }?.readText()?.trim()?.trim('"')?.let {
            result += findUnityRoots(File(it))
        }
        return result
    }

    private fun knownRoots(userHome: String, os: OperatingSystem): List<File> = when (os) {
        OperatingSystem.WINDOWS -> listOfNotNull(
            System.getenv("ProgramFiles")?.let(::File),
            System.getenv("ProgramFiles(x86)")?.let(::File),
            System.getenv("ProgramW6432")?.let(::File),
        )
        OperatingSystem.MAC -> listOf(File("/Applications"))
        OperatingSystem.LINUX -> listOf(File("/opt"), File(userHome))
    }

    private fun File.toUnityRootCandidate(os: OperatingSystem): File = when (os) {
        OperatingSystem.WINDOWS, OperatingSystem.LINUX ->
            if (name.equals("Editor", true)) parentFile ?: this else this
        OperatingSystem.MAC ->
            path.substringBefore("Unity.app").let { File(it.ifEmpty { path }) }
    }
}

enum class OperatingSystem {
    WINDOWS,
    MAC,
    LINUX,
    ;

    companion object {
        fun from(osName: String): OperatingSystem = when {
            osName.contains("win", true) -> WINDOWS
            osName.contains("mac", true) || osName.contains("darwin", true) -> MAC
            else -> LINUX
        }
    }
}
