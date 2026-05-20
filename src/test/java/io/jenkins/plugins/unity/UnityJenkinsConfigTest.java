package io.jenkins.plugins.unity;

import static org.assertj.core.api.Assertions.assertThat;

import hudson.model.FreeStyleProject;
import hudson.util.ListBoxModel;
import java.util.List;
import java.util.Map;
import org.jenkinsci.plugins.structs.describable.DescribableModel;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;

public class UnityJenkinsConfigTest {
    @Rule
    public JenkinsRule jenkins = new JenkinsRule();

    @Test
    public void freestyleDescriptorPopulatesDropdowns() {
        UnityBuilder.DescriptorImpl descriptor = new UnityBuilder.DescriptorImpl();

        assertThat(values(descriptor.doFillDetectionModeItems())).containsExactly("auto", "tool", "manual");
        assertThat(values(descriptor.doFillTestPlatformItems())).containsExactly("", "editmode", "playmode", "all");
        assertThat(values(descriptor.doFillVerbosityItems())).containsExactly("normal", "minimal");
        assertThat(values(descriptor.doFillLicenseTypeItems())).containsExactly("none", "professional", "personal");
        assertThat(values(descriptor.doFillLicenseScopeItems())).containsExactly("step", "build");
    }

    @Test
    public void freestyleBuilderRoundTripsConfiguration() throws Exception {
        FreeStyleProject project = jenkins.createFreeStyleProject();
        UnityBuilder builder = new UnityBuilder();
        builder.setDetectionMode("manual");
        builder.setUnityRoot("/opt/unity/6000.0.23f1");
        builder.setProjectPath("Game");
        builder.setBuildProfile("Assets/Profiles/Linux.asset");
        builder.setBuildPlayerPath("Builds/Linux/Game.x86_64");
        builder.setRunEditorTests(true);
        builder.setTestPlatform("all");
        builder.setCacheServer("cache.example.test:8126");
        builder.setLicenseType("professional");
        builder.setLicenseScope("build");
        project.getBuildersList().add(builder);

        FreeStyleProject roundTripped = jenkins.configRoundtrip(project);
        UnityBuilder actual = roundTripped.getBuildersList().get(UnityBuilder.class);

        assertThat(actual.getDetectionMode()).isEqualTo("manual");
        assertThat(actual.getUnityRoot()).isEqualTo("/opt/unity/6000.0.23f1");
        assertThat(actual.getProjectPath()).isEqualTo("Game");
        assertThat(actual.getBuildProfile()).isEqualTo("Assets/Profiles/Linux.asset");
        assertThat(actual.getBuildPlayerPath()).isEqualTo("Builds/Linux/Game.x86_64");
        assertThat(actual.isRunEditorTests()).isTrue();
        assertThat(actual.getTestPlatform()).isEqualTo("all");
        assertThat(actual.getCacheServer()).isEqualTo("cache.example.test:8126");
        assertThat(actual.getLicenseType()).isEqualTo("professional");
        assertThat(actual.getLicenseScope()).isEqualTo("build");
    }

    @Test
    public void pipelineStepDatabindsAllPublicFields() throws Exception {
        DescribableModel<UnityStep> model = new DescribableModel<>(UnityStep.class);

        UnityStep step = model.instantiate(Map.ofEntries(
                Map.entry("projectPath", "Game"),
                Map.entry("detectionMode", "tool"),
                Map.entry("unityVersion", "6000.0"),
                Map.entry("unityToolName", "Unity 6"),
                Map.entry("unityRoot", "/unused"),
                Map.entry("executeMethod", "Company.Game.Editor.Builder.Build"),
                Map.entry("buildTarget", "StandaloneLinux64"),
                Map.entry("buildProfile", "Assets/Profiles/Linux.asset"),
                Map.entry("buildPlayer", "buildLinux64Player"),
                Map.entry("buildPlayerPath", "Builds/Linux/Game.x86_64"),
                Map.entry("runEditorTests", true),
                Map.entry("testPlatform", "editmode"),
                Map.entry("testCategories", "fast"),
                Map.entry("testNames", "SmokeTests"),
                Map.entry("noGraphics", true),
                Map.entry("noQuit", false),
                Map.entry("silentCrashes", true),
                Map.entry("arguments", "-customArg value"),
                Map.entry("lineStatusesFile", "unity-line-statuses.xml"),
                Map.entry("logFilePath", "Logs/unity.log"),
                Map.entry("verbosity", "minimal"),
                Map.entry("cacheServer", "cache.example.test:8126"),
                Map.entry("licenseType", "professional"),
                Map.entry("licenseScope", "step"),
                Map.entry("usernamePasswordCredentialsId", "unity-user"),
                Map.entry("serialCredentialsId", "unity-serial"),
                Map.entry("personalLicenseCredentialsId", "unity-license")));

        assertThat(step.getProjectPath()).isEqualTo("Game");
        assertThat(step.getDetectionMode()).isEqualTo("tool");
        assertThat(step.getUnityToolName()).isEqualTo("Unity 6");
        assertThat(step.getBuildProfile()).isEqualTo("Assets/Profiles/Linux.asset");
        assertThat(step.isRunEditorTests()).isTrue();
        assertThat(step.getVerbosity()).isEqualTo("minimal");
        assertThat(step.getUsernamePasswordCredentialsId()).isEqualTo("unity-user");
        assertThat(step.getSerialCredentialsId()).isEqualTo("unity-serial");
        assertThat(step.getPersonalLicenseCredentialsId()).isEqualTo("unity-license");
    }

    private static List<String> values(ListBoxModel items) {
        return items.stream().map(option -> option.value).toList();
    }
}
