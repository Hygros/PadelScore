# Contributing

Thank you for considering a contribution to Padel Score.

## Project Scope

The project is a standalone, offline Wear OS padel scorekeeper. Contributions should preserve that focus unless a proposed change is discussed and accepted first.

Before implementing a substantial feature, open an issue describing:

- the problem being solved;
- the proposed behavior;
- the impact on offline operation and privacy;
- the impact on Wear OS usability; and
- the tests needed to verify the change.

## Development Requirements

- Use the included Gradle Wrapper.
- Use Java 25 to run Gradle.
- Keep Java source and target compatibility at version 11 unless a separate compatibility change is justified.
- Preserve the application ID and namespace `ch.hygro.padelscore`.
- Do not add a phone component, backend, account system, advertising, analytics, crash reporting, network access, location access, health permissions, or sensor permissions without prior discussion.
- Do not update dependencies solely because newer versions exist. Dependency changes must have a documented compatibility, security, consistency, or release reason.

## Build and Test

On Windows, set `JAVA_HOME` to a compatible JDK or Android Studio's bundled JBR, then run:

```powershell
.\gradlew.bat testDebugUnitTest
.\gradlew.bat assembleDebug
.\gradlew.bat lint
.\gradlew.bat processReleaseMainManifest
.\gradlew.bat assembleRelease
```

Changes to scoring or serving behavior must include or update focused unit tests. UI, persistence, lifecycle, and ambient-mode changes should also be tested on a Wear OS emulator and, where possible, a physical watch.

## Code Guidelines

- Keep changes focused and avoid unrelated refactoring.
- Follow the existing Kotlin formatting and naming style.
- Preserve package and source-path consistency.
- Keep imports complete and remove unused imports.
- Avoid hard-coded local paths, IP addresses, device identifiers, or account details.
- Do not commit generated build files or local audit output.
- Update documentation when build, behavior, privacy, or release procedures change.

## Privacy and Security

Never commit:

- passwords, tokens, API keys, or private keys;
- keystores, upload keys, or signing passwords;
- `local.properties` or local SDK paths;
- ADB serials, wireless-debugging addresses, or local IP addresses;
- personal contact information;
- employer, school, or internal infrastructure details;
- APK, AAB, mapping, log, crash-dump, or audit files; or
- third-party assets without a clear right to redistribute them.

Review [SECURITY.md](SECURITY.md) before reporting a suspected vulnerability.

## Pull Requests

A pull request should:

1. explain the purpose and scope of the change;
2. identify behavior, privacy, permission, dependency, or release impacts;
3. include relevant tests;
4. pass the project checks;
5. avoid unrelated generated files; and
6. update `CHANGELOG.md` when the change affects users or releases.

By submitting a contribution, you agree that it may be licensed under the Apache License, Version 2.0, as described in [LICENSE](LICENSE).
