package io.jenkins.plugins.unity;

import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.common.StandardUsernamePasswordCredentials;
import hudson.AbortException;
import hudson.EnvVars;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.Computer;
import hudson.model.Node;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.tasks.junit.JUnitResultArchiver;
import hudson.util.ArgumentListBuilder;
import io.jenkins.plugins.unity.core.DetectionMode;
import io.jenkins.plugins.unity.core.LicenseScope;
import io.jenkins.plugins.unity.core.LicenseType;
import io.jenkins.plugins.unity.core.LineStatusProvider;
import io.jenkins.plugins.unity.core.UnityBuildPlan;
import io.jenkins.plugins.unity.core.UnityCommandBuilder;
import io.jenkins.plugins.unity.core.UnityCommandContext;
import io.jenkins.plugins.unity.core.UnityConfig;
import io.jenkins.plugins.unity.core.UnityEnvironment;
import io.jenkins.plugins.unity.core.UnityInvocation;
import io.jenkins.plugins.unity.core.UnityLogProcessor;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import jenkins.model.Jenkins;
import org.jenkinsci.plugins.plaincredentials.FileCredentials;
import org.jenkinsci.plugins.plaincredentials.StringCredentials;

public class UnityExecutor {

    public void execute(UnityConfig config, Run<?, ?> run, FilePath workspace, EnvVars env, Launcher launcher, TaskListener listener)
            throws IOException, InterruptedException {
        FormValidationException.throwIfInvalid(config);

        FilePath stateDir = workspace.child(".unity-support");
        stateDir.mkdirs();

        if (DetectionMode.from(config.getDetectionMode()) == DetectionMode.AUTO
                && !UnityGlobalConfiguration.get().isAutoDetectionEnabled()) {
            throw new AbortException("Automatic Unity detection is disabled globally. Select a Jenkins Unity tool or set a manual Unity root.");
        }

        UnityWorkspaceInfo workspaceInfo = workspace.act(new UnityRemoteCallables.WorkspaceInfo(config.getProjectPath()));
        Map<String, String> toolRoots = toolRoots(workspace, env, listener);
        UnityEnvironment unity = workspace.act(new UnityRemoteCallables.DetectUnity(config, new LinkedHashMap<>(env), toolRoots, workspaceInfo.getProjectVersion()));
        listener.getLogger().println("[Unity] Using Unity " + unity.getUnityVersion() + " at " + unity.getUnityPath() + " (" + unity.getSource() + ")");

        String projectRemotePath = projectPath(workspace, config);
        String logPath = stateDir.child("unity.log").getRemote();
        String testPath = stateDir.child("unity-tests.xml").getRemote();
        UnityCommandContext context = new UnityCommandContext(
                unity.getUnityVersion(),
                projectRemotePath,
                workspaceInfo.getAssetPipelineVersion(),
                logPath,
                testPath,
                !launcher.isUnix());
        UnityBuildPlan plan = new UnityCommandBuilder().build(config, context);
        for (String warning : plan.getWarnings()) {
            listener.getLogger().println("[Unity][WARNING] " + warning);
        }

        UnityConfigSecrets secrets = resolveSecrets(config, run);
        LicenseType licenseType = LicenseType.from(config.getLicenseType());
        LicenseScope licenseScope = LicenseScope.from(config.getLicenseScope());

        if (licenseType == LicenseType.PROFESSIONAL && licenseScope == LicenseScope.BUILD) {
            ensureBuildScopedLicense(config, run, unity, secrets, workspace, env, launcher, listener);
        }

        for (UnityInvocation invocation : plan.getInvocations()) {
            if (licenseType != LicenseType.NONE && licenseScope == LicenseScope.STEP) {
                activateLicense(licenseType, unity, secrets, workspace, env, launcher, listener);
            }
            int exitCode;
            try {
                exitCode = runUnityInvocation(invocation, unity, workspace, env, launcher, listener, config);
            } finally {
                if (licenseType == LicenseType.PROFESSIONAL && licenseScope == LicenseScope.STEP) {
                    returnProfessionalLicense(unity, secrets, workspace, env, launcher, listener);
                }
            }
            if (exitCode != 0 && !invocation.getIgnoreExitCode()) {
                throw new AbortException("Unity finished with exit code " + exitCode);
            }
            if (invocation.getTestResultsPath() != null) {
                publishTestResults(invocation.getTestResultsPath(), stateDir, run, workspace, env, launcher, listener);
            }
        }
    }

    void returnProfessionalLicense(UnityEnvironment unity, UnityConfigSecrets secrets, FilePath workspace, EnvVars env, Launcher launcher, TaskListener listener)
            throws IOException, InterruptedException {
        listener.getLogger().println("[Unity] Returning Unity Pro license");
        runMaskedUnityCommand(
                "Return Unity license",
                unity.getUnityPath(),
                professionalReturnArgs(workspace, secrets),
                workspace,
                env,
                launcher,
                listener);
    }

    private void ensureBuildScopedLicense(
            UnityConfig config,
            Run<?, ?> run,
            UnityEnvironment unity,
            UnityConfigSecrets secrets,
            FilePath workspace,
            EnvVars env,
            Launcher launcher,
            TaskListener listener)
            throws IOException, InterruptedException {
        UnityBuildLicenseAction action = getOrCreateBuildLicenseAction(run);
        UnityBuildLicenseAction.ActivationKey key = buildScopedLicenseKey(config, unity, workspace);
        UnityBuildLicenseAction.ActivationRecord record =
                new UnityBuildLicenseAction.ActivationRecord(secrets, unity, workspace, env, launcher, listener);
        action.ensureActivated(
                key,
                record,
                () -> activateLicense(LicenseType.PROFESSIONAL, unity, secrets, workspace, env, launcher, listener));
    }

    private UnityBuildLicenseAction getOrCreateBuildLicenseAction(Run<?, ?> run) {
        synchronized (run) {
            UnityBuildLicenseAction action = run.getAction(UnityBuildLicenseAction.class);
            if (action == null) {
                action = new UnityBuildLicenseAction();
                run.addAction(action);
            }
            return action;
        }
    }

    private UnityBuildLicenseAction.ActivationKey buildScopedLicenseKey(UnityConfig config, UnityEnvironment unity, FilePath workspace)
            throws IOException, InterruptedException {
        Computer computer = workspace.toComputer();
        String computerName = computer == null ? "" : computer.getName();
        return new UnityBuildLicenseAction.ActivationKey(
                computerName,
                unity.getUnityPath(),
                config.getUsernamePasswordCredentialsId(),
                config.getSerialCredentialsId());
    }

    private int runUnityInvocation(
            UnityInvocation invocation,
            UnityEnvironment unity,
            FilePath workspace,
            EnvVars env,
            Launcher launcher,
            TaskListener listener,
            UnityConfig config)
            throws IOException, InterruptedException {
        FilePath lineStatuses = null;
        if (!UnityConfigSupport.isBlank(config.getLineStatusesFile())) {
            lineStatuses = workspace.child(config.getLineStatusesFile());
        }
        return runUnityCommand(
                invocation.getDisplayName(),
                unity.getUnityPath(),
                invocation.getArguments(),
                workspace,
                env,
                launcher,
                listener,
                invocation.getSuppressLogErrors(),
                lineStatuses);
    }

    private int runUnityCommand(
            String displayName,
            String unityPath,
            Iterable<String> arguments,
            FilePath workspace,
            EnvVars env,
            Launcher launcher,
            TaskListener listener,
            boolean suppressLogErrors,
            FilePath lineStatuses)
            throws IOException, InterruptedException {
        listener.getLogger().println("[Unity] " + displayName);
        LineStatusProvider provider = lineStatuses != null && lineStatuses.exists()
                ? new LineStatusProvider(lineStatuses.read())
                : new LineStatusProvider();
        UnityLogProcessor processor = new UnityLogProcessor(provider);
        UnityConsoleLogOutputStream output = new UnityConsoleLogOutputStream(listener.getLogger(), processor, suppressLogErrors);
        ArgumentListBuilder command = new ArgumentListBuilder();
        List<String> argumentList = new ArrayList<>();
        for (String arg : arguments) argumentList.add(arg);
        command.add(unityPath);
        for (String arg : argumentList) command.add(arg);
        int exitCode = launcher.launch()
                .cmds(command)
                .pwd(workspace)
                .envs(env)
                .stdout(output)
                .stderr(output)
                .join();
        output.close();
        streamUnityLogFile(argumentList, workspace, output);
        if (processor.getHasErrors() && !suppressLogErrors) {
            throw new AbortException("Unity log contained error patterns");
        }
        return exitCode;
    }

    private void streamUnityLogFile(List<String> arguments, FilePath workspace, UnityConsoleLogOutputStream output)
            throws IOException, InterruptedException {
        String logPath = commandValue(arguments, "-cleanedLogFile");
        if (logPath == null) {
            logPath = commandValue(arguments, "-logFile");
        }
        if (UnityConfigSupport.isBlank(logPath) || "-".equals(logPath)) {
            return;
        }
        FilePath logFile = remotePath(workspace, logPath);
        if (!logFile.exists()) {
            return;
        }
        try (InputStream input = logFile.read();
                BufferedReader reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                output.logLine(line);
            }
        }
    }

    private String commandValue(List<String> arguments, String name) {
        for (int index = 0; index < arguments.size() - 1; index++) {
            if (name.equalsIgnoreCase(arguments.get(index))) {
                return arguments.get(index + 1);
            }
        }
        return null;
    }

    private FilePath remotePath(FilePath workspace, String path) {
        if (isRemoteAbsolute(path)) {
            return new FilePath(workspace.getChannel(), path);
        }
        return workspace.child(path);
    }

    private boolean isRemoteAbsolute(String path) {
        return path.startsWith("/")
                || path.startsWith("\\\\")
                || path.matches("^[A-Za-z]:[\\\\/].*");
    }

    private void activateLicense(
            LicenseType licenseType,
            UnityEnvironment unity,
            UnityConfigSecrets secrets,
            FilePath workspace,
            EnvVars env,
            Launcher launcher,
            TaskListener listener)
            throws IOException, InterruptedException {
        if (licenseType == LicenseType.PROFESSIONAL) {
            listener.getLogger().println("[Unity] Activating Unity Pro license");
            runMaskedUnityCommand("Activate Unity license", unity.getUnityPath(), professionalActivationArgs(workspace, secrets), workspace, env, launcher, listener);
        } else if (licenseType == LicenseType.PERSONAL) {
            listener.getLogger().println("[Unity] Activating Unity Personal license");
            FilePath licenseFile = workspace.child(".unity-support/unity-personal-license.ulf");
            licenseFile.write(UnityConfigSupport.nvl(secrets.personalLicenseContent), StandardCharsets.UTF_8.name());
            try {
                runUnityCommand(
                        "Activate Unity Personal license",
                        unity.getUnityPath(),
                        java.util.List.of("-quit", "-batchmode", "-nographics", "-manualLicenseFile", licenseFile.getRemote(), "-logFile", workspace.child(".unity-support/activate-personal-license.log").getRemote()),
                        workspace,
                        env,
                        launcher,
                        listener,
                        false,
                        null);
            } finally {
                licenseFile.delete();
            }
        }
    }

    private void runMaskedUnityCommand(
            String displayName,
            String unityPath,
            ArgumentListBuilder command,
            FilePath workspace,
            EnvVars env,
            Launcher launcher,
            TaskListener listener)
            throws IOException, InterruptedException {
        listener.getLogger().println("[Unity] " + displayName);
        command.prepend(unityPath);
        int exitCode = launcher.launch()
                .cmds(command)
                .pwd(workspace)
                .envs(env)
                .stdout(listener.getLogger())
                .stderr(listener.getLogger())
                .join();
        if (exitCode != 0) {
            throw new AbortException(displayName + " failed with exit code " + exitCode);
        }
    }

    private ArgumentListBuilder professionalActivationArgs(FilePath workspace, UnityConfigSecrets secrets) {
        ArgumentListBuilder args = new ArgumentListBuilder();
        args.add("-quit", "-batchmode", "-nographics");
        if (!UnityConfigSupport.isBlank(secrets.serial)) args.add("-serial").addMasked(secrets.serial);
        if (!UnityConfigSupport.isBlank(secrets.username)) args.add("-username").add(secrets.username);
        if (!UnityConfigSupport.isBlank(secrets.password)) args.add("-password").addMasked(secrets.password);
        args.add("-logFile", workspace.child(".unity-support/activate-license.log").getRemote());
        return args;
    }

    private ArgumentListBuilder professionalReturnArgs(FilePath workspace, UnityConfigSecrets secrets) {
        ArgumentListBuilder args = new ArgumentListBuilder();
        args.add("-quit", "-batchmode", "-nographics", "-returnlicense");
        if (!UnityConfigSupport.isBlank(secrets.username)) args.add("-username").add(secrets.username);
        if (!UnityConfigSupport.isBlank(secrets.password)) args.add("-password").addMasked(secrets.password);
        args.add("-logFile", workspace.child(".unity-support/return-license.log").getRemote());
        return args;
    }

    private void publishTestResults(
            String nunitPath,
            FilePath stateDir,
            Run<?, ?> run,
            FilePath workspace,
            EnvVars env,
            Launcher launcher,
            TaskListener listener)
            throws IOException, InterruptedException {
        FilePath junitFile = stateDir.child("junit-" + Math.abs(nunitPath.hashCode()) + ".xml");
        workspace.act(new UnityRemoteCallables.ConvertNUnit(nunitPath, junitFile.getRemote()));
        if (junitFile.exists()) {
            new JUnitResultArchiver(".unity-support/" + junitFile.getName()).perform(run, workspace, env, launcher, listener);
        }
    }

    private UnityConfigSecrets resolveSecrets(UnityConfig config, Run<?, ?> run) throws IOException {
        UnityConfigSecrets secrets = new UnityConfigSecrets();
        if (!UnityConfigSupport.isBlank(config.getUsernamePasswordCredentialsId())) {
            StandardUsernamePasswordCredentials credential = CredentialsProvider.findCredentialById(
                    config.getUsernamePasswordCredentialsId(), StandardUsernamePasswordCredentials.class, run);
            if (credential != null) {
                secrets.username = credential.getUsername();
                secrets.password = credential.getPassword().getPlainText();
            }
        }
        if (!UnityConfigSupport.isBlank(config.getSerialCredentialsId())) {
            StringCredentials credential = CredentialsProvider.findCredentialById(config.getSerialCredentialsId(), StringCredentials.class, run);
            if (credential != null) secrets.serial = credential.getSecret().getPlainText();
        }
        if (!UnityConfigSupport.isBlank(config.getPersonalLicenseCredentialsId())) {
            FileCredentials credential = CredentialsProvider.findCredentialById(config.getPersonalLicenseCredentialsId(), FileCredentials.class, run);
            if (credential != null) {
                try (InputStream input = credential.getContent()) {
                    secrets.personalLicenseContent = new String(input.readAllBytes(), StandardCharsets.UTF_8);
                }
            }
        }
        return secrets;
    }

    private Map<String, String> toolRoots(FilePath workspace, EnvVars env, TaskListener listener) throws IOException, InterruptedException {
        Map<String, String> roots = new LinkedHashMap<>();
        UnityInstallation.DescriptorImpl descriptor = Jenkins.get().getDescriptorByType(UnityInstallation.DescriptorImpl.class);
        if (descriptor == null) return roots;
        Computer computer = workspace.toComputer();
        Node node = computer == null ? null : computer.getNode();
        for (UnityInstallation installation : descriptor.getInstallations()) {
            UnityInstallation resolved = installation.forNode(node, listener);
            roots.put(resolved.getName(), env.expand(resolved.getHome()));
        }
        return roots;
    }

    private String projectPath(FilePath workspace, UnityConfig config) {
        return UnityConfigSupport.isBlank(config.getProjectPath())
                ? workspace.getRemote()
                : workspace.child(config.getProjectPath()).getRemote();
    }

    private static class FormValidationException extends AbortException {
        FormValidationException(String message) {
            super(message);
        }

        static void throwIfInvalid(UnityConfig config) throws FormValidationException {
            hudson.util.FormValidation validation = UnityConfigSupport.validate(config);
            if (validation.kind == hudson.util.FormValidation.Kind.ERROR) {
                throw new FormValidationException(validation.getMessage());
            }
        }
    }
}
