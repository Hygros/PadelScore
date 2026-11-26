# Padel Score repository notes

## Product boundaries
- `:app` is the standalone Wear OS app. Scoring and local history must remain usable without a phone.
- `:mobile` is optional and only handles watch sync, statistics viewing, and Health Connect import.
- Do not add backend services, accounts, analytics, crash reporting, direct network features, location access, new permissions, or expanded companion scope without prior discussion.

## Architecture
- Scoring and serving rules: `app/src/main/java/ch/hygro/padelscore/logic`
- Persistence, history, codecs: `app/src/main/java/ch/hygro/padelscore/storage`
- Wear UI: `app/src/main/java/ch/hygro/padelscore/presentation`
- Sync protocol changes must preserve revision handling, watch-generation IDs, stale-message protection, and durable workout/deletion processing.

## Toolchain
- Run Gradle with Java 25.
- Java source/target compatibility stays at 11 unless intentionally changing platform requirements.
- Android modules target API 37. Wear minSdk 30, mobile minSdk 28.
- Repositories are controlled in `settings.gradle.kts` (`RepositoriesMode.FAIL_ON_PROJECT_REPOS`).

## Verification
- Single test class: `.\gradlew.bat :app:testDebugUnitTest --tests "fully.qualified.TestClass"`
- Full JVM tests: `.\gradlew.bat :app:testDebugUnitTest :mobile:testDebugUnitTest`
- Fast compile check: `.\gradlew.bat :app:assembleDebug :mobile:assembleDebug`
- CI release validation:
  - `.\gradlew.bat :app:lintRelease :mobile:lintRelease`
  - `.\gradlew.bat :app:bundleRelease :mobile:bundleRelease`
- Scoring or serving changes require focused unit tests.
- UI, persistence, lifecycle, gesture, fitness, or ambient-mode changes may require Wear OS emulator validation.

## Privacy and release constraints
- Treat the absence of app-declared `INTERNET` permission as a product requirement. After dependency or manifest changes run:
  - `.\gradlew.bat :app:processReleaseMainManifest :mobile:processReleaseMainManifest`
- Current Play build is not F-Droid compatible. Do not describe it as such.
- Do not update dependencies without a compatibility, security, consistency, or release reason.
- Release signing uses only:
  - `PADELSCORE_UPLOAD_STORE_FILE`
  - `PADELSCORE_UPLOAD_STORE_PASSWORD`
  - `PADELSCORE_UPLOAD_KEY_ALIAS`
  - `PADELSCORE_UPLOAD_KEY_PASSWORD`

## Never commit
- `build/` outputs, APKs, AABs, mappings, audit files
- `local.properties`
- keystores, signing material, credentials, tokens
- device identifiers, health records, purchase tokens
