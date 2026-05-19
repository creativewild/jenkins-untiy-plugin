package io.jenkins.plugins.unity;

import hudson.Extension;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.model.listeners.RunListener;

@Extension
public class UnityBuildLicenseRunListener extends RunListener<Run<?, ?>> {
    @Override
    public void onCompleted(Run<?, ?> run, TaskListener listener) {
        UnityBuildLicenseAction action = run.getAction(UnityBuildLicenseAction.class);
        if (action == null) return;
        try {
            action.returnLicense(new UnityExecutor());
        } catch (Exception e) {
            listener.error("Failed to return Unity license: " + e.getMessage());
        }
    }
}
