package io.jenkins.plugins.unity.core

import java.io.File

object BuildProfileScanner {
    @JvmStatic
    fun collectProfiles(root: File, relativePath: String = "Assets", maxDepth: Int = 3, currentDepth: Int = 0): List<String> {
        if (currentDepth > maxDepth || !root.isDirectory) return emptyList()
        return root.listFiles().orEmpty().flatMap { child ->
            val childPath = "$relativePath/${child.name}"
            when {
                child.isFile && child.name.endsWith(".asset", ignoreCase = true) && isBuildProfileAsset(child) -> listOf(childPath)
                child.isDirectory -> collectProfiles(child, childPath, maxDepth, currentDepth + 1)
                else -> emptyList()
            }
        }
    }

    private fun isBuildProfileAsset(file: File): Boolean =
        runCatching { file.useLines { lines -> lines.any { it.trimStart().startsWith("BuildProfile:") } } }.getOrDefault(false)
}

object CSharpStaticMethodScanner {
    private val namespaceRegex = Regex("""namespace\s+([A-Za-z_][\w.]*)""")
    private val classRegex = Regex("""\bclass\s+([A-Za-z_]\w*)""")
    private val methodRegex = Regex("""(?m)(?:\[MenuItem\("([^"]+)"\)\]\s*)?(?:(?:public|protected)\s+)?static\s+void\s+([A-Za-z_]\w*)\s*\(\s*\)""")

    @JvmStatic
    fun readStaticMethods(file: File): Map<String, String?> {
        val text = file.readText()
        val namespace = namespaceRegex.find(text)?.groupValues?.get(1)?.takeIf { it.isNotBlank() }
        val className = classRegex.find(text)?.groupValues?.get(1) ?: return emptyMap()
        return methodRegex.findAll(text).associate { match ->
            val method = match.groupValues[2]
            val reference = listOfNotNull(namespace, className, method).joinToString(".")
            reference to match.groupValues.getOrNull(1)?.takeIf { it.isNotBlank() }
        }
    }
}
