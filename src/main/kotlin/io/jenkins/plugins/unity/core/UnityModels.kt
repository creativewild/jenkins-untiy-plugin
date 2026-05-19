package io.jenkins.plugins.unity.core

import java.io.Serializable

enum class DetectionMode(val id: String) {
    AUTO("auto"),
    TOOL("tool"),
    MANUAL("manual"),
    ;

    companion object {
        @JvmStatic
        fun from(id: String?): DetectionMode =
            entries.firstOrNull { it.id.equals(id, ignoreCase = true) } ?: AUTO
    }
}

enum class LicenseType(val id: String) {
    NONE("none"),
    PROFESSIONAL("professional"),
    PERSONAL("personal"),
    ;

    companion object {
        @JvmStatic
        fun from(id: String?): LicenseType =
            entries.firstOrNull { it.id.equals(id, ignoreCase = true) } ?: NONE
    }
}

enum class LicenseScope(val id: String) {
    STEP("step"),
    BUILD("build"),
    ;

    companion object {
        @JvmStatic
        fun from(id: String?): LicenseScope =
            entries.firstOrNull { it.id.equals(id, ignoreCase = true) } ?: STEP
    }
}

enum class Verbosity(val id: String) {
    NORMAL("normal"),
    MINIMAL("minimal"),
    ;

    companion object {
        @JvmStatic
        fun from(id: String?): Verbosity =
            entries.firstOrNull { it.id.equals(id, ignoreCase = true) } ?: NORMAL
    }
}

enum class AssetPipelineVersion {
    V1,
    V2,
    ;

    companion object {
        @JvmStatic
        fun from(value: String?): AssetPipelineVersion? = when (value) {
            "0" -> V1
            "1" -> V2
            else -> null
        }
    }
}

data class UnityVersion(
    val major: Int,
    val minor: Int? = null,
    val patch: Int? = null,
) : Comparable<UnityVersion>, Serializable {

    fun nextMajor() = UnityVersion(major + 1, 0, 0)
    fun nextMinor() = UnityVersion(major, (minor ?: 0) + 1, 0)

    override fun compareTo(other: UnityVersion): Int =
        compareValuesBy(this, other, UnityVersion::major, { it.minor ?: 0 }, { it.patch ?: 0 })

    override fun toString(): String = buildString {
        append(major)
        if (minor != null) append('.').append(minor)
        if (patch != null) append('.').append(patch)
    }

    companion object {
        private val versionRegex = Regex("""^\s*(\d+)(?:\.(\d+))?(?:\.(\d+))?""")

        @JvmStatic
        fun tryParse(value: String?): UnityVersion? {
            val match = value?.let { versionRegex.find(it) } ?: return null
            return try {
                UnityVersion(
                    match.groupValues[1].toInt(),
                    match.groupValues.getOrNull(2)?.takeIf { it.isNotBlank() }?.toInt(),
                    match.groupValues.getOrNull(3)?.takeIf { it.isNotBlank() }?.toInt(),
                )
            } catch (_: NumberFormatException) {
                null
            }
        }
    }
}

data class UnityEnvironment(
    val unityPath: String,
    val unityVersion: UnityVersion,
    val source: String,
) : Serializable

class UnityConfig : Serializable {
    var projectPath: String = ""
    var detectionMode: String = "auto"
    var unityVersion: String = ""
    var unityToolName: String = ""
    var unityRoot: String = ""
    var executeMethod: String = ""
    var buildTarget: String = ""
    var buildProfile: String = ""
    var buildPlayer: String = ""
    var buildPlayerPath: String = ""
    var runEditorTests: Boolean = false
    var testPlatform: String = ""
    var testCategories: String = ""
    var testNames: String = ""
    var noGraphics: Boolean = true
    var noQuit: Boolean = false
    var silentCrashes: Boolean = false
    var arguments: String = ""
    var lineStatusesFile: String = ""
    var logFilePath: String = ""
    var verbosity: String = "normal"
    var cacheServer: String = ""
    var licenseType: String = "none"
    var licenseScope: String = "step"
    var usernamePasswordCredentialsId: String = ""
    var serialCredentialsId: String = ""
    var personalLicenseCredentialsId: String = ""
}
