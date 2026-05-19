package io.jenkins.plugins.unity.core

import java.io.Serializable

data class UnityCommandContext(
    val unityVersion: UnityVersion,
    val projectPath: String,
    val assetPipelineVersion: AssetPipelineVersion?,
    val logFilePath: String,
    val testResultsPath: String,
    val isWindows: Boolean,
) : Serializable

data class UnityInvocation(
    val displayName: String,
    val arguments: List<String>,
    val prewarm: Boolean = false,
    val ignoreExitCode: Boolean = false,
    val suppressLogErrors: Boolean = false,
    val testResultsPath: String? = null,
) : Serializable

data class UnityBuildPlan(
    val invocations: List<UnityInvocation>,
    val warnings: List<String>,
) : Serializable

class UnityCommandBuilder {

    fun build(config: UnityConfig, context: UnityCommandContext): UnityBuildPlan {
        val warnings = mutableListOf<String>()
        val invocations = mutableListOf<UnityInvocation>()
        val buildProfile = config.buildProfile.trim().takeIf { it.isNotEmpty() }

        if (buildProfile != null) {
            warnings += buildProfileWarnings(config, context.unityVersion)
            invocations += prewarmInvocation(config, context, buildProfile)
        }

        val variants = if (config.testPlatform.equals("all", ignoreCase = true)) {
            listOf("editmode", "playmode")
        } else {
            listOf(config.testPlatform.trim())
        }

        for (platform in variants) {
            invocations += mainInvocation(config, context, buildProfile, platform)
        }

        return UnityBuildPlan(invocations, warnings)
    }

    private fun prewarmInvocation(config: UnityConfig, context: UnityCommandContext, buildProfile: String): UnityInvocation {
        val args = mutableListOf("-batchmode")
        args += projectPathArgs(context)
        if (config.buildTarget.isNotBlank()) {
            args += listOf("-buildTarget", config.buildTarget.trim())
        } else {
            args += listOf("-activeBuildProfile", buildProfile)
        }
        if (config.noGraphics) args += "-nographics"
        args += "-quit"
        addLogArgs(args, config, context)
        return UnityInvocation(
            displayName = "Unity build profile prewarm",
            arguments = args,
            prewarm = true,
            ignoreExitCode = true,
            suppressLogErrors = true,
        )
    }

    private fun mainInvocation(
        config: UnityConfig,
        context: UnityCommandContext,
        buildProfile: String?,
        platform: String,
    ): UnityInvocation {
        val args = mutableListOf("-batchmode")
        args += projectPathArgs(context)

        if (buildProfile != null) {
            args += listOf("-activeBuildProfile", buildProfile)
            if (config.buildPlayerPath.isNotBlank()) {
                args += listOf("-build", config.buildPlayerPath.trim())
            }
        } else {
            if (config.buildTarget.isNotBlank()) args += listOf("-buildTarget", config.buildTarget.trim())
            if (config.buildPlayer.isNotBlank() && config.buildPlayerPath.isNotBlank()) {
                args += listOf("-${config.buildPlayer.trim()}", config.buildPlayerPath.trim())
            }
        }

        if (config.noGraphics) args += "-nographics"
        if (config.silentCrashes) args += "-silent-crashes"
        if (config.executeMethod.isNotBlank()) args += listOf("-executeMethod", config.executeMethod.trim())
        args += splitCommandLine(config.arguments)

        val testResultPath = addTestArgs(args, config, context, platform)
        if (testResultPath == null && !config.noQuit && !hasArg(args, "-quit")) args += "-quit"

        addCacheServerArgs(args, config, context.assetPipelineVersion)
        addLogArgs(args, config, context)

        return UnityInvocation(
            displayName = if (platform.isBlank()) "Unity build" else "Unity tests ($platform)",
            arguments = args,
            testResultsPath = testResultPath,
        )
    }

    private fun projectPathArgs(context: UnityCommandContext): List<String> =
        if (context.unityVersion > UnityVersion(2018, 2, 0)) {
            listOf("-projectPath", context.projectPath)
        } else {
            listOf("-projectPath=${context.projectPath}")
        }

    private fun addTestArgs(
        args: MutableList<String>,
        config: UnityConfig,
        context: UnityCommandContext,
        platform: String,
    ): String? {
        if (!config.runEditorTests && !hasArg(args, "-runTests") && !hasArg(args, "-runEditorTests")) return null

        if (!hasArg(args, "-runTests") && !hasArg(args, "-runEditorTests")) {
            if (platform.isBlank()) {
                args += "-runEditorTests"
            } else {
                args += listOf("-runTests", "-testPlatform", platform)
            }
        }

        val resultPath = if (platform.isBlank()) {
            context.testResultsPath
        } else {
            context.testResultsPath.removeSuffix(".xml") + "-$platform.xml"
        }
        val resultArg = if (platform.isBlank()) "-editorTestsResultFile" else "-testResults"
        if (!hasArg(args, "-editorTestsResultFile") && !hasArg(args, "-testResults")) {
            args += listOf(resultArg, resultPath)
        }

        if (config.testCategories.isNotBlank()) {
            args += listOf("-editorTestsCategories", config.testCategories.split(',', ';', '\n').map { it.trim() }.filter { it.isNotEmpty() }.joinToString(";"))
        }
        if (config.testNames.isNotBlank()) {
            args += listOf("-editorTestsFilter", config.testNames.split(',', ';', '\n').map { it.trim() }.filter { it.isNotEmpty() }.joinToString(";"))
        }

        return resultPath
    }

    private fun addCacheServerArgs(args: MutableList<String>, config: UnityConfig, assetPipelineVersion: AssetPipelineVersion?) {
        val cacheServer = config.cacheServer.trim()
        if (cacheServer.isEmpty()) return
        when (assetPipelineVersion) {
            AssetPipelineVersion.V2 -> args += listOf("-EnableCacheServer", "-cacheServerEndpoint", cacheServer)
            null, AssetPipelineVersion.V1 -> args += listOf("-CacheServerIPAddress", cacheServer)
        }
    }

    private fun addLogArgs(args: MutableList<String>, config: UnityConfig, context: UnityCommandContext) {
        if (hasArg(args, "-logFile") || hasArg(args, "-cleanedLogFile")) return
        args += if (Verbosity.from(config.verbosity) == Verbosity.MINIMAL) "-cleanedLogFile" else "-logFile"
        args += config.logFilePath.trim().takeIf { it.isNotEmpty() } ?: context.logFilePath
    }

    private fun buildProfileWarnings(config: UnityConfig, unityVersion: UnityVersion): List<String> {
        val warnings = mutableListOf<String>()
        if (unityVersion.major < 6000) {
            warnings += "Build Profiles require Unity 6 (6000.x) or later. The current Unity version is $unityVersion."
        }
        if (config.buildPlayerPath.isBlank() && config.executeMethod.isBlank() && !config.runEditorTests) {
            warnings += "Build Profile is set but no player output path, execute method, or test run is configured."
        }
        return warnings
    }

    private fun hasArg(args: List<String>, name: String): Boolean =
        args.any { it.equals(name, ignoreCase = true) }

    companion object {
        @JvmStatic
        fun splitCommandLine(value: String?): List<String> {
            if (value.isNullOrBlank()) return emptyList()
            val result = mutableListOf<String>()
            val current = StringBuilder()
            var quote: Char? = null
            var escaped = false
            for (ch in value) {
                when {
                    escaped -> {
                        current.append(ch)
                        escaped = false
                    }
                    ch == '\\' -> escaped = true
                    quote != null && ch == quote -> quote = null
                    quote == null && (ch == '"' || ch == '\'') -> quote = ch
                    quote == null && ch.isWhitespace() -> {
                        if (current.isNotEmpty()) {
                            result += current.toString()
                            current.clear()
                        }
                    }
                    else -> current.append(ch)
                }
            }
            if (escaped) current.append('\\')
            if (current.isNotEmpty()) result += current.toString()
            return result
        }
    }
}
