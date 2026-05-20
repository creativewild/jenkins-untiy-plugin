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
        action.returnLicenses(new UnityExecutor(), listener);
    }
}
