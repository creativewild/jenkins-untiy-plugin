package io.jenkins.plugins.unity;

import static org.assertj.core.api.Assertions.assertThat;

import hudson.EnvVars;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.TaskListener;
import io.jenkins.plugins.unity.core.UnityEnvironment;
import io.jenkins.plugins.unity.core.UnityVersion;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.Test;

public class UnityBuildLicenseActionTest {

    @Test
    public void duplicateActivationKeyIsReused() throws Exception {
        UnityBuildLicenseAction action = new UnityBuildLicenseAction();
        UnityBuildLicenseAction.ActivationKey key = key("agent-a", "unity-a", "user-a", "serial-a");
        UnityBuildLicenseAction.ActivationRecord record = record("unity-a");
        AtomicInteger activations = new AtomicInteger();

        boolean first = action.ensureActivated(key, record, () -> activations.incrementAndGet());
        boolean second = action.ensureActivated(key, record, () -> activations.incrementAndGet());

        assertThat(first).isTrue();
        assertThat(second).isFalse();
        assertThat(activations).hasValue(1);
        assertThat(action.activationCount()).isEqualTo(1);
    }

    @Test
    public void multipleActivationRecordsAreReturned() throws Exception {
        UnityBuildLicenseAction action = new UnityBuildLicenseAction();
        action.ensureActivated(key("agent-a", "unity-a", "user-a", "serial-a"), record("unity-a"), () -> {});
        action.ensureActivated(key("agent-b", "unity-b", "user-a", "serial-a"), record("unity-b"), () -> {});
        RecordingExecutor executor = new RecordingExecutor();

        int failures = action.returnLicenses(executor, TaskListener.NULL);

        assertThat(failures).isZero();
        assertThat(executor.returnedUnityPaths).containsExactly("unity-a", "unity-b");
        assertThat(action.activationCount()).isZero();
    }

    @Test
    public void returnAttemptsContinueAfterFailure() throws Exception {
        UnityBuildLicenseAction action = new UnityBuildLicenseAction();
        action.ensureActivated(key("agent-a", "unity-a", "user-a", "serial-a"), record("unity-a"), () -> {});
        action.ensureActivated(key("agent-b", "unity-b", "user-a", "serial-a"), record("unity-b"), () -> {});
        RecordingExecutor executor = new RecordingExecutor();
        executor.failForUnityPaths.add("unity-a");

        int failures = action.returnLicenses(executor, TaskListener.NULL);

        assertThat(failures).isEqualTo(1);
        assertThat(executor.returnedUnityPaths).containsExactly("unity-a", "unity-b");
        assertThat(action.isActivated(key("agent-a", "unity-a", "user-a", "serial-a"))).isTrue();
        assertThat(action.isActivated(key("agent-b", "unity-b", "user-a", "serial-a"))).isFalse();
    }

    private static UnityBuildLicenseAction.ActivationKey key(String computerName, String unityPath, String usernameCredentialsId, String serialCredentialsId) {
        return new UnityBuildLicenseAction.ActivationKey(computerName, unityPath, usernameCredentialsId, serialCredentialsId);
    }

    private static UnityBuildLicenseAction.ActivationRecord record(String unityPath) {
        UnityConfigSecrets secrets = new UnityConfigSecrets();
        secrets.username = "unity-user";
        secrets.password = "unity-password";
        secrets.serial = "unity-serial";
        UnityEnvironment environment = new UnityEnvironment(unityPath, new UnityVersion(6000, 0, 0), "manual");
        return new UnityBuildLicenseAction.ActivationRecord(secrets, environment, null, new EnvVars(), null, TaskListener.NULL);
    }

    private static class RecordingExecutor extends UnityExecutor {
        private final List<String> returnedUnityPaths = new ArrayList<>();
        private final Set<String> failForUnityPaths = new HashSet<>();

        @Override
        void returnProfessionalLicense(UnityEnvironment unity, UnityConfigSecrets secrets, FilePath workspace, EnvVars env, Launcher launcher, TaskListener listener)
                throws IOException {
            returnedUnityPaths.add(unity.getUnityPath());
            if (failForUnityPaths.contains(unity.getUnityPath())) {
                throw new IOException("planned failure");
            }
        }
    }
}
