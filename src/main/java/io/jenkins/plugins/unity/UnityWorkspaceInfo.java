package io.jenkins.plugins.unity;

import io.jenkins.plugins.unity.core.AssetPipelineVersion;
import io.jenkins.plugins.unity.core.UnityVersion;
import java.io.Serializable;

class UnityWorkspaceInfo implements Serializable {
    private final UnityVersion projectVersion;
    private final AssetPipelineVersion assetPipelineVersion;

    UnityWorkspaceInfo(UnityVersion projectVersion, AssetPipelineVersion assetPipelineVersion) {
        this.projectVersion = projectVersion;
        this.assetPipelineVersion = assetPipelineVersion;
    }

    UnityVersion getProjectVersion() {
        return projectVersion;
    }

    AssetPipelineVersion getAssetPipelineVersion() {
        return assetPipelineVersion;
    }
}
