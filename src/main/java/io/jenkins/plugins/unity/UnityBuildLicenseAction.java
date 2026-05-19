package io.jenkins.plugins.unity;

import hudson.EnvVars;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.InvisibleAction;
import hudson.model.TaskListener;
import io.jenkins.plugins.unity.core.UnityEnvironment;

class UnityBuildLicenseAction extends InvisibleAction {
    private transient boolean activated;
    private transient UnityConfigSecrets secrets;
    private transient UnityEnvironment environment;
    private transient FilePath workspace;
    private transient EnvVars env;
    private transient Launcher launcher;
    private transient TaskListener listener;

    synchronized boolean isActivated() {
        return activated;
    }

    synchronized void markActivated(UnityConfigSecrets secrets, UnityEnvironment environment, FilePath workspace, EnvVars env, Launcher launcher, TaskListener listener) {
        this.activated = true;
        this.secrets = secrets;
        this.environment = environment;
        this.workspace = workspace;
        this.env = env;
        this.launcher = launcher;
        this.listener = listener;
    }

    synchronized void returnLicense(UnityExecutor executor) throws Exception {
        if (!activated || secrets == null || environment == null || workspace == null || launcher == null || listener == null) return;
        executor.returnProfessionalLicense(environment, secrets, workspace, env, launcher, listener);
        activated = false;
    }
}
