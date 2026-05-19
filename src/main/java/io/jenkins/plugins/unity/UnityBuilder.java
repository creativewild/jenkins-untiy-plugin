package io.jenkins.plugins.unity;

import hudson.EnvVars;
import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.AbstractProject;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.Builder;
import hudson.util.FormValidation;
import io.jenkins.plugins.unity.core.UnityConfig;
import java.io.IOException;
import jenkins.tasks.SimpleBuildStep;
import org.jenkinsci.Symbol;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.QueryParameter;

public class UnityBuilder extends Builder implements SimpleBuildStep {
    private final UnityConfig config = new UnityConfig();

    @DataBoundConstructor
    public UnityBuilder() {}

    @Override
    public void perform(Run<?, ?> run, FilePath workspace, EnvVars env, Launcher launcher, TaskListener listener)
            throws InterruptedException, IOException {
        new UnityExecutor().execute(UnityConfigSupport.copy(config), run, workspace, env, launcher, listener);
    }

    public UnityConfig toConfig() {
        return UnityConfigSupport.copy(config);
    }

    public String getProjectPath() { return config.getProjectPath(); }
    @DataBoundSetter public void setProjectPath(String value) { config.setProjectPath(UnityConfigSupport.nvl(value)); }
    public String getDetectionMode() { return config.getDetectionMode(); }
    @DataBoundSetter public void setDetectionMode(String value) { config.setDetectionMode(UnityConfigSupport.nvl(value, "auto")); }
    public String getUnityVersion() { return config.getUnityVersion(); }
    @DataBoundSetter public void setUnityVersion(String value) { config.setUnityVersion(UnityConfigSupport.nvl(value)); }
    public String getUnityToolName() { return config.getUnityToolName(); }
    @DataBoundSetter public void setUnityToolName(String value) { config.setUnityToolName(UnityConfigSupport.nvl(value)); }
    public String getUnityRoot() { return config.getUnityRoot(); }
    @DataBoundSetter public void setUnityRoot(String value) { config.setUnityRoot(UnityConfigSupport.nvl(value)); }
    public String getExecuteMethod() { return config.getExecuteMethod(); }
    @DataBoundSetter public void setExecuteMethod(String value) { config.setExecuteMethod(UnityConfigSupport.nvl(value)); }
    public String getBuildTarget() { return config.getBuildTarget(); }
    @DataBoundSetter public void setBuildTarget(String value) { config.setBuildTarget(UnityConfigSupport.nvl(value)); }
    public String getBuildProfile() { return config.getBuildProfile(); }
    @DataBoundSetter public void setBuildProfile(String value) { config.setBuildProfile(UnityConfigSupport.nvl(value)); }
    public String getBuildPlayer() { return config.getBuildPlayer(); }
    @DataBoundSetter public void setBuildPlayer(String value) { config.setBuildPlayer(UnityConfigSupport.nvl(value)); }
    public String getBuildPlayerPath() { return config.getBuildPlayerPath(); }
    @DataBoundSetter public void setBuildPlayerPath(String value) { config.setBuildPlayerPath(UnityConfigSupport.nvl(value)); }
    public boolean isRunEditorTests() { return config.getRunEditorTests(); }
    @DataBoundSetter public void setRunEditorTests(boolean value) { config.setRunEditorTests(value); }
    public String getTestPlatform() { return config.getTestPlatform(); }
    @DataBoundSetter public void setTestPlatform(String value) { config.setTestPlatform(UnityConfigSupport.nvl(value)); }
    public String getTestCategories() { return config.getTestCategories(); }
    @DataBoundSetter public void setTestCategories(String value) { config.setTestCategories(UnityConfigSupport.nvl(value)); }
    public String getTestNames() { return config.getTestNames(); }
    @DataBoundSetter public void setTestNames(String value) { config.setTestNames(UnityConfigSupport.nvl(value)); }
    public boolean isNoGraphics() { return config.getNoGraphics(); }
    @DataBoundSetter public void setNoGraphics(boolean value) { config.setNoGraphics(value); }
    public boolean isNoQuit() { return config.getNoQuit(); }
    @DataBoundSetter public void setNoQuit(boolean value) { config.setNoQuit(value); }
    public boolean isSilentCrashes() { return config.getSilentCrashes(); }
    @DataBoundSetter public void setSilentCrashes(boolean value) { config.setSilentCrashes(value); }
    public String getArguments() { return config.getArguments(); }
    @DataBoundSetter public void setArguments(String value) { config.setArguments(UnityConfigSupport.nvl(value)); }
    public String getLineStatusesFile() { return config.getLineStatusesFile(); }
    @DataBoundSetter public void setLineStatusesFile(String value) { config.setLineStatusesFile(UnityConfigSupport.nvl(value)); }
    public String getLogFilePath() { return config.getLogFilePath(); }
    @DataBoundSetter public void setLogFilePath(String value) { config.setLogFilePath(UnityConfigSupport.nvl(value)); }
    public String getVerbosity() { return config.getVerbosity(); }
    @DataBoundSetter public void setVerbosity(String value) { config.setVerbosity(UnityConfigSupport.nvl(value, "normal")); }
    public String getCacheServer() { return config.getCacheServer(); }
    @DataBoundSetter public void setCacheServer(String value) { config.setCacheServer(UnityConfigSupport.nvl(value)); }
    public String getLicenseType() { return config.getLicenseType(); }
    @DataBoundSetter public void setLicenseType(String value) { config.setLicenseType(UnityConfigSupport.nvl(value, "none")); }
    public String getLicenseScope() { return config.getLicenseScope(); }
    @DataBoundSetter public void setLicenseScope(String value) { config.setLicenseScope(UnityConfigSupport.nvl(value, "step")); }
    public String getUsernamePasswordCredentialsId() { return config.getUsernamePasswordCredentialsId(); }
    @DataBoundSetter public void setUsernamePasswordCredentialsId(String value) { config.setUsernamePasswordCredentialsId(UnityConfigSupport.nvl(value)); }
    public String getSerialCredentialsId() { return config.getSerialCredentialsId(); }
    @DataBoundSetter public void setSerialCredentialsId(String value) { config.setSerialCredentialsId(UnityConfigSupport.nvl(value)); }
    public String getPersonalLicenseCredentialsId() { return config.getPersonalLicenseCredentialsId(); }
    @DataBoundSetter public void setPersonalLicenseCredentialsId(String value) { config.setPersonalLicenseCredentialsId(UnityConfigSupport.nvl(value)); }

    @Extension
    @Symbol("unityBuilder")
    public static class DescriptorImpl extends BuildStepDescriptor<Builder> {
        @Override
        public boolean isApplicable(Class<? extends AbstractProject> jobType) {
            return true;
        }

        @Override
        public String getDisplayName() {
            return "Unity";
        }

        public FormValidation doCheckBuildProfile(@QueryParameter String value) {
            UnityConfig config = new UnityConfig();
            config.setBuildProfile(value);
            return UnityConfigSupport.validate(config);
        }
    }
}
