# Changelog

All notable changes to this project will be documented in this file.

The format is based on Keep a Changelog, and the project intends to use Semantic Versioning after its first public release.

## [Unreleased]

### Added

- Standalone Wear OS padel scoring application.
- Classic advantage, Golden Point, and Star Point scoring modes.
- Normal and short-set formats.
- One-set, best-of-three, and two-sets-plus-match-tie-break formats.
- Set tie-break and match tie-break scoring.
- Server rotation and 1-2-2 tie-break service sequence.
- Undo history and persistent in-progress match state.
- Local completed-match history.
- Match duration, point count, game count, and completed-set results.
- Haptic feedback and long-press reset.
- Ambient display support.
- Unit tests for the scoring engine and serve calculator.

### Changed

- Application ID and namespace changed to `ch.hygro.padelscore` before public release.
- Compose BOM aligned with Compose 1.9.0.
- Activity Compose declaration aligned with version 1.10.0.

### Removed

- Unused Play Services Wearable dependency.
- Unused `WAKE_LOCK` permission.
- Android system backup for locally stored match data.

### Security

- Added repository ignore rules for local SDK configuration, build output, audit reports, signing material, credentials, logs, and generated packages.
- Verified that the publishable project tree contains no known personal identifiers, credentials, signing keys, local device identifiers, or absolute local paths.

