package io.jenkins.plugins.unity;

import hudson.Extension;
import jenkins.model.GlobalConfiguration;
import org.jenkinsci.Symbol;
import org.kohsuke.stapler.DataBoundSetter;

@Extension
@Symbol("unitySupport")
public class UnityGlobalConfiguration extends GlobalConfiguration {
    private boolean autoDetectionEnabled = true;

    public UnityGlobalConfiguration() {
        load();
    }

    public static UnityGlobalConfiguration get() {
        return GlobalConfiguration.all().get(UnityGlobalConfiguration.class);
    }

    public boolean isAutoDetectionEnabled() {
        return autoDetectionEnabled;
    }

    @DataBoundSetter
    public void setAutoDetectionEnabled(boolean autoDetectionEnabled) {
        this.autoDetectionEnabled = autoDetectionEnabled;
        save();
    }
}
