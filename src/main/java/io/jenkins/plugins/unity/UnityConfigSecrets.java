package io.jenkins.plugins.unity;

import java.io.Serializable;

class UnityConfigSecrets implements Serializable {
    String username;
    String password;
    String serial;
    String personalLicenseContent;
}
