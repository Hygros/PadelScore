# Padel Score for Wear OS

Padel Score is a standalone Wear OS application for keeping score during padel matches directly on a smartwatch. It is designed for offline use and does not require a phone companion app, account, cloud service, or network connection.

The Android application ID is `ch.hygro.padelscore`.

## Features

- Standard padel scoring: 0, 15, 30, 40
- Classic advantage scoring
- Golden Point scoring
- Star Point scoring
- Normal sets to six games
- Short sets to four games
- Two-game margin and set tie-breaks
- Match tie-breaks
- One-set matches
- Best-of-three matches
- Two sets followed by a match tie-break
- Correct server rotation, including the 1-2-2 tie-break sequence
- Undo history
- 300 ms point-input lock
- Long-press reset
- Haptic feedback
- Persistent in-progress match state
- Local match history
- Match duration, points, games, and completed-set results
- Ambient display support when Always-on Display is enabled

## Privacy

Padel Score works entirely offline.

The app does not include:

- user accounts;
- advertising;
- analytics;
- crash reporting;
- cloud synchronization;
- location access;
- health or sensor access;
- a phone companion component; or
- an Internet permission.

Match state, undo history, and completed-match history are stored locally in the app's private storage. Android system backup is disabled. Uninstalling the app or changing devices therefore removes this locally stored data.

## Requirements

- Android Studio compatible with Android Gradle Plugin 9.4.1
- Gradle 9.6.0 through the included Gradle Wrapper
- Java 25 for the Gradle daemon
- Android SDK with compile SDK 37
- Wear OS device or emulator with API level 30 or newer

The app source targets Java 11 bytecode. The Java version used to run Gradle is a separate setting.

## Build on Windows

The project includes the Gradle Wrapper. Set `JAVA_HOME` to a Java 25 installation. For example, replace the clearly marked placeholder below with the path to Android Studio's bundled JBR or another compatible JDK:

```powershell
$env:JAVA_HOME = '<path-to-jdk-25>'
$env:Path = "$env:JAVA_HOME\bin;$env:Path"

.\gradlew.bat testDebugUnitTest
.\gradlew.bat assembleDebug
```
 Do not commit `local.properties`, SDK paths, device identifiers, IP addresses, signing files, or passwords.

The debug APK is generated at:

```text
app/build/outputs/apk/debug/app-debug.apk
```

## Verification

Run the main local checks with:

```powershell
.\gradlew.bat testDebugUnitTest
.\gradlew.bat assembleDebug
.\gradlew.bat lint
.\gradlew.bat processReleaseMainManifest
.\gradlew.bat assembleRelease
```

The project currently includes unit tests for the scoring engine and serve calculator.

## Install for Development

Select a connected Wear OS emulator or watch in Android Studio and run the `app` configuration.

Alternatively, install a debug APK with Android Debug Bridge. Replace `<device-serial>` with the identifier shown by `adb devices`:

```powershell
$Adb = "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe"

& $Adb -s '<device-serial>' install -r `
    'app\build\outputs\apk\debug\app-debug.apk'
```

Do not publish device serials, local IP addresses, or wireless-debugging details.

## Release Builds and Signing

The repository does not contain a keystore, signing passwords, or a signing configuration. Release signing must be configured locally or in a protected CI environment.

Never commit:

- `*.jks` or `*.keystore` files;
- upload or app-signing private keys;
- `keystore.properties` or `signing.properties`;
- passwords or tokens;
- generated APK, AAB, mapping, or audit files.

Back up the upload keystore and its credentials separately before publishing a release. Do not replace or rotate a production upload key without first reviewing the consequences for future updates.

## Project Structure

```text
app/src/main/java/ch/hygro/padelscore/
├── logic/
├── model/
├── presentation/
├── storage/
└── vibration/

app/src/test/java/ch/hygro/padelscore/logic/
```

## Contributing

See [CONTRIBUTING.md](CONTRIBUTING.md).

## Security

See [SECURITY.md](SECURITY.md) before reporting a suspected vulnerability. Do not place secrets or sensitive vulnerability details in a public issue.

## License

Licensed under the Apache License, Version 2.0. See [LICENSE](LICENSE).
