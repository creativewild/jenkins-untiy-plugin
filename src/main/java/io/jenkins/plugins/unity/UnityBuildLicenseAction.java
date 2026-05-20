package io.jenkins.plugins.unity;

import hudson.EnvVars;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.InvisibleAction;
import hudson.model.TaskListener;
import io.jenkins.plugins.unity.core.UnityEnvironment;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

class UnityBuildLicenseAction extends InvisibleAction {
    private transient Map<ActivationKey, ActivationRecord> activations;

    synchronized boolean isActivated(ActivationKey key) {
        return activations().containsKey(key);
    }

    synchronized int activationCount() {
        return activations().size();
    }

    synchronized boolean ensureActivated(ActivationKey key, ActivationRecord record, LicenseActivator activator)
            throws IOException, InterruptedException {
        if (activations().containsKey(key)) {
            return false;
        }
        activator.activate();
        activations().put(key, record);
        return true;
    }

    int returnLicenses(UnityExecutor executor, TaskListener fallbackListener) {
        List<Map.Entry<ActivationKey, ActivationRecord>> records;
        synchronized (this) {
            records = new ArrayList<>(activations().entrySet());
        }

        int failures = 0;
        for (Map.Entry<ActivationKey, ActivationRecord> entry : records) {
            ActivationRecord record = entry.getValue();
            TaskListener listener = record.listener != null ? record.listener : fallbackListener;
            if (listener == null) {
                listener = TaskListener.NULL;
            }
            try {
                executor.returnProfessionalLicense(record.environment, record.secrets, record.workspace, record.env, record.launcher, listener);
                synchronized (this) {
                    activations().remove(entry.getKey());
                }
            } catch (Exception e) {
                failures++;
                listener.error("Failed to return Unity license for " + entry.getKey().displayName() + ": " + e.getMessage());
            }
        }
        return failures;
    }

    private Map<ActivationKey, ActivationRecord> activations() {
        if (activations == null) {
            activations = new LinkedHashMap<>();
        }
        return activations;
    }

    @FunctionalInterface
    interface LicenseActivator {
        void activate() throws IOException, InterruptedException;
    }

    static final class ActivationKey {
        private final String computerName;
        private final String unityPath;
        private final String usernamePasswordCredentialsId;
        private final String serialCredentialsId;

        ActivationKey(String computerName, String unityPath, String usernamePasswordCredentialsId, String serialCredentialsId) {
            this.computerName = UnityConfigSupport.nvl(computerName);
            this.unityPath = UnityConfigSupport.nvl(unityPath);
            this.usernamePasswordCredentialsId = UnityConfigSupport.nvl(usernamePasswordCredentialsId);
            this.serialCredentialsId = UnityConfigSupport.nvl(serialCredentialsId);
        }

        String displayName() {
            return "agent '" + (computerName.isEmpty() ? "built-in" : computerName) + "', Unity '" + unityPath + "'";
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof ActivationKey)) return false;
            ActivationKey that = (ActivationKey) o;
            return computerName.equals(that.computerName)
                    && unityPath.equals(that.unityPath)
                    && usernamePasswordCredentialsId.equals(that.usernamePasswordCredentialsId)
                    && serialCredentialsId.equals(that.serialCredentialsId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(computerName, unityPath, usernamePasswordCredentialsId, serialCredentialsId);
        }
    }

    static final class ActivationRecord {
        private final UnityConfigSecrets secrets;
        private final UnityEnvironment environment;
        private final FilePath workspace;
        private final EnvVars env;
        private final Launcher launcher;
        private final TaskListener listener;

        ActivationRecord(UnityConfigSecrets secrets, UnityEnvironment environment, FilePath workspace, EnvVars env, Launcher launcher, TaskListener listener) {
            this.secrets = copy(secrets);
            this.environment = environment;
            this.workspace = workspace;
            this.env = env == null ? null : new EnvVars(env);
            this.launcher = launcher;
            this.listener = listener;
        }

        private static UnityConfigSecrets copy(UnityConfigSecrets source) {
            UnityConfigSecrets target = new UnityConfigSecrets();
            if (source != null) {
                target.username = source.username;
                target.password = source.password;
                target.serial = source.serial;
                target.personalLicenseContent = source.personalLicenseContent;
            }
            return target;
        }
    }
}
