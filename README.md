# Unity Support Jenkins Plugin

Unity Support is a Jenkins plugin for running Unity Editor builds and tests on Jenkins agents. It provides a native Pipeline step, a Freestyle build step, Jenkins Tool integration, credential-backed licensing, Unity test publishing, cache server arguments, and Unity log classification.

## Features

- Pipeline step: `unity(...)`
- Freestyle build step: **Unity**
- Jenkins Tool support for named Unity Editor installations
- Automatic Unity detection from tools, environment variables, `PATH`, Unity Hub config, and common install paths
- Manual Unity root selection for deterministic jobs
- Unity Build Profile support for Unity 6 and newer
- Edit Mode and Play Mode test execution
- Unity NUnit-style test result conversion to Jenkins JUnit reports
- Unity Professional and Personal license activation through Jenkins Credentials
- Cache server and Accelerator command-line arguments
- Unity log streaming, warning/error classification, and build failure on error patterns

## Documentation

Read the full plugin documentation:

[docs/usage.md](docs/usage.md)

The usage guide covers installation, Jenkins setup, Pipeline examples, Freestyle configuration, credentials, Unity selection, all parameters, generated files, logs, and troubleshooting.

## Quick Pipeline Example

```groovy
pipeline {
  agent any

  stages {
    stage('Unity') {
      steps {
        unity(
          detectionMode: 'auto',
          unityVersion: '6000.0',
          projectPath: '',
          runEditorTests: true,
          testPlatform: 'all',
          buildProfile: 'Assets/Settings/Build Profiles/Linux.asset',
          buildPlayerPath: 'build/linux/Game.x86_64',
          noGraphics: true
        )
      }
    }
  }
}
```

## Build From Source

Requirements:

- JDK 17 or newer
- Maven 3.9 or newer

Build and test:

```shell
mvn -B -ntp verify
```

The plugin artifact is generated at:

```text
target/unity-support.hpi
```

Run a local Jenkins with the plugin loaded:

```shell
mvn hpi:run
```

## GitHub Releases

The repository includes a GitHub Actions workflow that builds the plugin on pushes, pull requests, and manual dispatches. It uploads `target/unity-support.hpi` as a workflow artifact.

To publish a GitHub Release with the `.hpi` attached, push a version tag:

```shell
git tag v1.0.0
git push origin v1.0.0
```

For release tags, the workflow sets the Maven version from the tag, runs `mvn -B -ntp verify`, creates a SHA-256 checksum, and uploads both files to the GitHub Release.

## Repository

Source: https://github.com/creativewild/jenkins-untiy
