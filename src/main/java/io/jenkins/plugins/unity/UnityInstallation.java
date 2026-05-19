package io.jenkins.plugins.unity;

import edu.umd.cs.findbugs.annotations.CheckForNull;
import hudson.Extension;
import hudson.model.Node;
import hudson.model.TaskListener;
import hudson.slaves.NodeSpecific;
import hudson.tools.ToolDescriptor;
import hudson.tools.ToolInstallation;
import hudson.tools.ToolProperty;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import org.jenkinsci.Symbol;
import org.kohsuke.stapler.DataBoundConstructor;

public class UnityInstallation extends ToolInstallation implements NodeSpecific<UnityInstallation> {

    @DataBoundConstructor
    public UnityInstallation(String name, String home, List<? extends ToolProperty<?>> properties) {
        super(name, home, properties == null ? Collections.emptyList() : properties);
    }

    @Override
    public UnityInstallation forNode(@CheckForNull Node node, TaskListener log) throws IOException, InterruptedException {
        return new UnityInstallation(getName(), translateFor(node, log), getProperties().toList());
    }

    @Extension
    @Symbol("unityInstallation")
    public static class DescriptorImpl extends ToolDescriptor<UnityInstallation> {
        @Override
        public String getDisplayName() {
            return "Unity Editor";
        }
    }
}
