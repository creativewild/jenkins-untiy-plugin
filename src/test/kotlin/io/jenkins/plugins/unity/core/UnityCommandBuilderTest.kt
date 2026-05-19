package io.jenkins.plugins.unity.core

import org.assertj.core.api.Assertions.assertThat
import org.junit.Test

class UnityCommandBuilderTest {
    private val context = UnityCommandContext(
        unityVersion = UnityVersion(6000, 0, 23),
        projectPath = "/workspace/game",
        assetPipelineVersion = AssetPipelineVersion.V2,
        logFilePath = "/workspace/.unity-support/unity.log",
        testResultsPath = "/workspace/.unity-support/unity-tests.xml",
        isWindows = false,
    )

    @Test
    fun `build profile prewarms once then clears build target for actual build`() {
        val config = UnityConfig().apply {
            buildProfile = "Assets/Profiles/Linux.asset"
            buildTarget = "StandaloneLinux64"
            buildPlayerPath = "Builds/Linux/Game.x86_64"
        }

        val plan = UnityCommandBuilder().build(config, context)

        assertThat(plan.invocations).hasSize(2)
        assertThat(plan.invocations[0].arguments).containsSubsequence("-buildTarget", "StandaloneLinux64")
        assertThat(plan.invocations[0].suppressLogErrors).isTrue()
        assertThat(plan.invocations[1].arguments).containsSubsequence("-activeBuildProfile", "Assets/Profiles/Linux.asset")
        assertThat(plan.invocations[1].arguments).doesNotContain("-buildTarget", "StandaloneLinux64")
        assertThat(plan.invocations[1].arguments).containsSubsequence("-build", "Builds/Linux/Game.x86_64")
    }

    @Test
    fun `test platform all generates editmode and playmode invocations with distinct result files`() {
        val config = UnityConfig().apply {
            runEditorTests = true
            testPlatform = "all"
            testCategories = "fast, smoke"
            testNames = "PlayerTests; EditorTests"
        }

        val plan = UnityCommandBuilder().build(config, context)

        assertThat(plan.invocations).hasSize(2)
        assertThat(plan.invocations[0].arguments).containsSubsequence("-runTests", "-testPlatform", "editmode")
        assertThat(plan.invocations[0].testResultsPath).endsWith("unity-tests-editmode.xml")
        assertThat(plan.invocations[1].arguments).containsSubsequence("-runTests", "-testPlatform", "playmode")
        assertThat(plan.invocations[1].testResultsPath).endsWith("unity-tests-playmode.xml")
        assertThat(plan.invocations[1].arguments).containsSubsequence("-editorTestsCategories", "fast;smoke")
        assertThat(plan.invocations[1].arguments).containsSubsequence("-editorTestsFilter", "PlayerTests;EditorTests")
    }

    @Test
    fun `cache server arguments follow asset pipeline version`() {
        val config = UnityConfig().apply {
            cacheServer = "cache.example.test:8126"
        }

        val v2 = UnityCommandBuilder().build(config, context).invocations.single().arguments
        val v1 = UnityCommandBuilder()
            .build(config, context.copy(assetPipelineVersion = AssetPipelineVersion.V1))
            .invocations
            .single()
            .arguments

        assertThat(v2).containsSubsequence("-EnableCacheServer", "-cacheServerEndpoint", "cache.example.test:8126")
        assertThat(v1).containsSubsequence("-CacheServerIPAddress", "cache.example.test:8126")
    }

    @Test
    fun `splits extra arguments with quotes and escapes`() {
        assertThat(UnityCommandBuilder.splitCommandLine("""-flag "two words" 'more words' escaped\ value"""))
            .containsExactly("-flag", "two words", "more words", "escaped value")
    }
}
