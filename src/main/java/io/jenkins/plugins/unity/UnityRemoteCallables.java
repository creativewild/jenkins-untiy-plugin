package io.jenkins.plugins.unity;

import hudson.FilePath;
import hudson.remoting.VirtualChannel;
import io.jenkins.plugins.unity.core.AssetPipelineVersion;
import io.jenkins.plugins.unity.core.DetectionMode;
import io.jenkins.plugins.unity.core.UnityConfig;
import io.jenkins.plugins.unity.core.UnityConfigReader;
import io.jenkins.plugins.unity.core.UnityDetectionRequest;
import io.jenkins.plugins.unity.core.UnityDetector;
import io.jenkins.plugins.unity.core.UnityEnvironment;
import io.jenkins.plugins.unity.core.UnityVersion;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import jenkins.MasterToSlaveFileCallable;

final class UnityRemoteCallables {
    private UnityRemoteCallables() {}

    static class WorkspaceInfo extends MasterToSlaveFileCallable<UnityWorkspaceInfo> {
        private static final long serialVersionUID = 1L;
        private final String projectPath;

        WorkspaceInfo(String projectPath) {
            this.projectPath = UnityConfigSupport.nvl(projectPath);
        }

        @Override
        public UnityWorkspaceInfo invoke(File workspace, VirtualChannel channel) throws IOException {
            File project = projectPath.isBlank() ? workspace : new File(workspace, projectPath);
            UnityConfigReader reader = new UnityConfigReader();
            UnityVersion version = null;
            AssetPipelineVersion pipeline = null;
            File versionFile = new File(project, "ProjectSettings/ProjectVersion.txt");
            if (versionFile.isFile()) {
                try (InputStream input = java.nio.file.Files.newInputStream(versionFile.toPath())) {
                    version = UnityVersion.tryParse(reader.readValue(input, "m_EditorVersion"));
                }
            }
            File editorSettings = new File(project, "ProjectSettings/EditorSettings.asset");
            if (editorSettings.isFile()) {
                try (InputStream input = java.nio.file.Files.newInputStream(editorSettings.toPath())) {
                    pipeline = AssetPipelineVersion.from(reader.readValue(input, "m_AssetPipelineMode"));
                }
            }
            return new UnityWorkspaceInfo(version, pipeline);
        }
    }

    static class DetectUnity extends MasterToSlaveFileCallable<UnityEnvironment> {
        private static final long serialVersionUID = 1L;
        private final UnityConfig config;
        private final Map<String, String> env;
        private final Map<String, String> toolRoots;
        private final UnityVersion projectVersion;

        DetectUnity(UnityConfig config, Map<String, String> env, Map<String, String> toolRoots, UnityVersion projectVersion) {
            this.config = config;
            this.env = env;
            this.toolRoots = toolRoots;
            this.projectVersion = projectVersion;
        }

        @Override
        public UnityEnvironment invoke(File workspace, VirtualChannel channel) {
            String root = config.getUnityRoot();
            if (root != null && !root.isBlank() && !new File(root).isAbsolute()) {
                root = new File(workspace, root).getAbsolutePath();
            }
            UnityDetectionRequest request = new UnityDetectionRequest(
                    DetectionMode.from(config.getDetectionMode()),
                    UnityVersion.tryParse(config.getUnityVersion()),
                    projectVersion,
                    root,
                    config.getUnityToolName(),
                    toolRoots,
                    env,
                    System.getProperty("user.home", ""),
                    System.getProperty("os.name", ""));
            return new UnityDetector().select(request);
        }
    }

    static class ConvertNUnit extends MasterToSlaveFileCallable<Void> {
        private static final long serialVersionUID = 1L;
        private final String nunitPath;
        private final String junitPath;

        ConvertNUnit(String nunitPath, String junitPath) {
            this.nunitPath = nunitPath;
            this.junitPath = junitPath;
        }

        @Override
        public Void invoke(File workspace, VirtualChannel channel) throws IOException {
            File input = new File(nunitPath);
            if (!input.isFile()) return null;
            String converted = new io.jenkins.plugins.unity.core.NUnitToJUnitConverter().convert(java.nio.file.Files.readString(input.toPath()));
            java.nio.file.Files.writeString(new File(junitPath).toPath(), converted);
            return null;
        }
    }
}
