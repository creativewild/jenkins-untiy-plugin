package io.jenkins.plugins.unity;

import hudson.util.FormValidation;
import io.jenkins.plugins.unity.core.LicenseScope;
import io.jenkins.plugins.unity.core.LicenseType;
import io.jenkins.plugins.unity.core.UnityConfig;

final class UnityConfigSupport {
    private UnityConfigSupport() {}

    static FormValidation validate(UnityConfig config) {
        if ("manual".equalsIgnoreCase(config.getDetectionMode()) && isBlank(config.getUnityRoot())) {
            return FormValidation.error("Manual Unity detection requires a Unity root.");
        }
        if ("tool".equalsIgnoreCase(config.getDetectionMode()) && isBlank(config.getUnityToolName())) {
            return FormValidation.error("Tool Unity detection requires a Jenkins Unity tool name.");
        }
        if (!isBlank(config.getBuildProfile()) && !isParameterized(config.getBuildProfile())
                && !config.getBuildProfile().trim().endsWith(".asset")) {
            return FormValidation.error("Build Profile path should point to a .asset file.");
        }
        if (LicenseType.from(config.getLicenseType()) == LicenseType.PERSONAL
                && LicenseScope.from(config.getLicenseScope()) == LicenseScope.BUILD) {
            return FormValidation.error("Unity Personal license activation is supported only with step scope.");
        }
        return FormValidation.ok();
    }

    static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    static boolean isParameterized(String value) {
        return value != null && (value.contains("$") || value.contains("%"));
    }

    static UnityConfig copy(UnityConfig source) {
        UnityConfig target = new UnityConfig();
        target.setProjectPath(nvl(source.getProjectPath()));
        target.setDetectionMode(nvl(source.getDetectionMode(), "auto"));
        target.setUnityVersion(nvl(source.getUnityVersion()));
        target.setUnityToolName(nvl(source.getUnityToolName()));
        target.setUnityRoot(nvl(source.getUnityRoot()));
        target.setExecuteMethod(nvl(source.getExecuteMethod()));
        target.setBuildTarget(nvl(source.getBuildTarget()));
        target.setBuildProfile(nvl(source.getBuildProfile()));
        target.setBuildPlayer(nvl(source.getBuildPlayer()));
        target.setBuildPlayerPath(nvl(source.getBuildPlayerPath()));
        target.setRunEditorTests(source.getRunEditorTests());
        target.setTestPlatform(nvl(source.getTestPlatform()));
        target.setTestCategories(nvl(source.getTestCategories()));
        target.setTestNames(nvl(source.getTestNames()));
        target.setNoGraphics(source.getNoGraphics());
        target.setNoQuit(source.getNoQuit());
        target.setSilentCrashes(source.getSilentCrashes());
        target.setArguments(nvl(source.getArguments()));
        target.setLineStatusesFile(nvl(source.getLineStatusesFile()));
        target.setLogFilePath(nvl(source.getLogFilePath()));
        target.setVerbosity(nvl(source.getVerbosity(), "normal"));
        target.setCacheServer(nvl(source.getCacheServer()));
        target.setLicenseType(nvl(source.getLicenseType(), "none"));
        target.setLicenseScope(nvl(source.getLicenseScope(), "step"));
        target.setUsernamePasswordCredentialsId(nvl(source.getUsernamePasswordCredentialsId()));
        target.setSerialCredentialsId(nvl(source.getSerialCredentialsId()));
        target.setPersonalLicenseCredentialsId(nvl(source.getPersonalLicenseCredentialsId()));
        return target;
    }

    static String nvl(String value) {
        return nvl(value, "");
    }

    static String nvl(String value, String fallback) {
        return value == null ? fallback : value;
    }
}
