# Maintainership

This repository uses the Jenkins Maven HPI build.

## Release Checklist

1. Update `CHANGELOG.md`.
2. Run `mvn -B -ntp verify`.
3. Build the plugin artifact from `target/unity-support.hpi`.
4. For public distribution, follow the Jenkins plugin hosting and release process.
