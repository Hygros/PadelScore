---
title: Privacy Policy for Padel Score
---

# Privacy Policy for Padel Score

**Last updated: 5 November 2026**

Padel Score is a Wear OS application for keeping score during padel matches. It can optionally record workout information on the watch and transfer match history, statistics and completed workouts to the PadelScore Sync companion app on a paired Android phone.

This policy explains what information the apps process, where that information is stored, and which optional platform services are involved.

## Summary

Padel Score:

- does not require a user account;
- does not include advertising;
- does not include analytics or developer-operated telemetry;
- does not include crash-reporting services;
- does not upload match or health information to a server operated by the developer;
- does not request the Android Internet permission;
- stores match information locally on the Wear OS device;
- can optionally record health and fitness information on the watch;
- can optionally synchronise match history and statistics directly to a paired Android phone;
- can optionally transfer completed workout information directly to a paired Android phone;
- can optionally write transferred workouts to Android Health Connect; and
- can optionally use Google Play Billing for a one-time supporter purchase.

The scoring functions can be used without enabling fitness tracking, installing the companion app, enabling match-history or Health Connect synchronisation, or making a supporter purchase.

## Information stored on the Wear OS device

Padel Score stores information required for scoring and match history in the app's private local storage.

This may include:

- the current match state;
- match configuration;
- point and undo history;
- completed-set and tie-break results;
- early-stopped match state;
- match start and end times;
- match duration;
- numbers of points and games;
- completed-match history; and
- timestamps associated with matches.

This information is used to provide scoring, match resumption, undo, statistics and match-history functions.

It is not transmitted to the developer.

## Optional fitness tracking

Fitness tracking is optional and must be enabled by the user.

When enabled and when the required Android permissions have been granted, the Wear OS app may process:

- heart-rate measurements;
- average heart rate;
- maximum heart rate;
- step counts;
- active-calorie information when supported by the device;
- workout start and end times; and
- match duration.

Padel Score uses the AndroidX Health Services APIs and available device sensors to record this information during a match.

Availability and accuracy depend on the watch, Wear OS version, sensor hardware, permissions and other workout applications running on the device.

Fitness information is stored locally in the Wear OS app's private storage. It is not sent to the developer.

## Required and optional permissions

Depending on the Android and Wear OS version and the features selected by the user, Padel Score may request the following permissions:

- body sensor or heart-rate access for optional heart-rate recording;
- activity recognition for optional workout and step tracking;
- foreground-service health access so workout recording can continue while the match screen is not in the foreground;
- notification access for the ongoing workout notification;
- notification-policy access for the optional Match Quiet Mode; and
- Health Connect write permissions in the companion app.

A permission can be declined. Features that require the declined permission may then be unavailable, while the core scoring functions remain usable.

## Foreground workout service

While fitness tracking is active, the Wear OS app uses a foreground service and displays an ongoing system notification.

The foreground service allows workout recording to continue during an active match when the app is not continuously visible on screen.

The service stops when the workout is ended or discarded, subject to Android and Wear OS lifecycle behaviour.

## Optional companion app and watch-to-phone transfer

PadelScore Sync is an optional Android companion app. Watch-to-phone transfers use the Google Play services Wearable Data Layer between the user's paired devices. Padel Score does not send this information to a developer-operated server.

### Match history and statistics synchronisation

When **Match history on phone** is enabled in the watch settings, the Wear OS app transfers a watch-authoritative snapshot of up to 100 completed or early-stopped matches to the companion app. This synchronisation is independent of Health Connect consent and stores the snapshot in the companion app's private local storage so the phone app can display match history, overall statistics, set and tie-break breakdowns, game score charts and rally-by-rally point history.

Transferred match-history snapshots may include:

- match identifiers, start and finish timestamps, and duration;
- match configuration (scoring mode, match format and set length);
- set, tie-break and match tie-break scores, plus early-stop state where applicable;
- rally-by-rally point history and total games and points played;
- per-match summary metrics recorded on the watch (average heart rate, maximum heart rate and step count); and
- snapshot revision and enabled state.

Per-match step counts included in match-history snapshots are used only for display within the companion app and are not exported to Health Connect.

Users can disable **Match history on phone** in the watch settings at any time. Turning it off publishes a disabled snapshot that clears the synced match-history copy stored in the companion app.

### Workout synchronisation for Health Connect

When **Health Sync** is enabled, completed workout information may be transferred directly between the watch and the paired phone for import into Health Connect.

Transferred workout information may include:

- workout start and end times;
- heart-rate samples;
- match duration;
- revision and synchronisation information; and
- identifiers and hashes used to prevent duplicate or stale imports.

The apps may temporarily retain pending transfer data and synchronisation receipts so that interrupted transfers can be retried and duplicate imports can be avoided.

Users can disable health synchronisation. Disabling synchronisation prevents new workout transfers, although deletion instructions for previously transferred workouts may still be exchanged where necessary to honour an explicit deletion request.

## Health Connect

The optional PadelScore Sync companion app can write completed workouts to Android Health Connect after the user:

1. installs the companion app;
2. enables synchronisation; and
3. grants the requested Health Connect permissions.

The companion app may write:

- exercise-session records; and
- heart-rate records.

Match step counts remain in local app storage and are not written to Health Connect.

Health Connect stores and manages these records on the Android device under the user's Health Connect settings.

Padel Score does not operate Health Connect and does not receive Health Connect data on a developer-controlled server.

Users can inspect or revoke the companion app's Health Connect permissions through Android settings. Revoking permissions prevents further Health Connect writes but may not automatically delete records already stored in Health Connect.

## Match and workout deletion

When a locally stored match is deleted on the watch, Padel Score removes the match from local watch history, purges its local workout and step data, and retains a local deletion marker (reconciling any interrupted two-phase deletions on startup or history load). This marker prevents an older queued copy of the workout from being imported again.

If **Match history on phone** is enabled, the updated match-history snapshot without the deleted match is also published to the companion app.

If the companion app is installed and reachable, the workout deletion request may be transmitted to the paired phone so that corresponding Health Connect records can be deleted.

Deletion may remain pending if:

- the phone is not reachable;
- the companion app is unavailable;
- Health Connect is unavailable;
- the required Health Connect permissions are missing; or
- the record identifier required for deletion is unavailable.

Users can also manage Health Connect records directly through Android's Health Connect settings.

## Optional supporter purchase

The Wear OS app may offer an optional one-time supporter purchase through Google Play Billing.

Google Play processes the purchase and payment information according to Google's own terms and privacy policies. Payment-card information is not provided to the developer through Padel Score.

To provide the supporter feature, the app receives information from Google Play that is required to:

- display the available product and localised price;
- start the purchase flow;
- determine whether a purchase is pending or completed;
- acknowledge a completed purchase; and
- recognise an existing supporter purchase.

The supporter purchase is optional and does not affect the core scoring functions.

Padel Score does not operate a developer-controlled billing server.

## Google Play and Google Play services

Some optional features rely on services supplied by Google:

- Google Play Billing for the optional supporter purchase;
- Google Play services Wearable APIs for watch-to-phone communication;
- Google Play for app installation and updates when the app is installed through Google Play; and
- a Play Store link that the companion setup function may open on a paired device.

Google may process information independently when users interact with these services. Such processing is governed by Google's own terms and privacy policies and is not controlled by Padel Score.

## Internet access

Neither the Wear OS app nor the Android companion app requests the Android Internet permission.

The apps do not connect directly to a developer-operated Internet service.

Google Play, Google Play services, Health Connect and the Android operating system are separate platform components and may perform their own communications independently of the app.

## Advertising, analytics and crash reporting

Padel Score does not integrate:

- advertising SDKs;
- analytics SDKs;
- developer-operated telemetry;
- third-party crash-reporting SDKs;
- social-network SDKs; or
- developer-operated cloud storage.

Diagnostic information may still be recorded by Android, Wear OS, device manufacturers, Google Play or other system components according to their own settings and policies.

## Data backup

Android system backup is disabled for both the Wear OS app and the companion app.

Padel Score does not back up match, workout or synchronisation information to a server operated by the developer.

Uninstalling an app normally removes the information stored in that app's private local storage. Records already written to Health Connect are managed separately by Health Connect and may remain until they are deleted through the companion app or Health Connect settings.

## Data retention

Match and workout information remains in local app storage until it is:

- deleted within the app where a deletion function is available;
- cleared on the phone by disabling **Match history on phone** on the watch;
- cleared through Android application settings; or
- removed by uninstalling the app.

Pending synchronisation data and local synchronisation receipts may remain until a transfer or deletion has been confirmed, the relevant app storage is cleared, or the app is uninstalled.

Health Connect records are retained according to the user's Health Connect settings and Android platform behaviour.

Google Play purchase records are controlled by Google and are not removed by clearing Padel Score's local application storage.

## Data deletion

Users can delete locally stored Padel Score information by:

- deleting individual supported records within the app;
- disabling **Match history on phone** on the watch to clear the synced match-history copy in the companion app;
- clearing the app's storage through Android or Wear OS settings; or
- uninstalling the app.

Users can manage or delete records written to Health Connect through:

- the PadelScore Sync companion app where supported; or
- Android Health Connect settings.

Clearing or uninstalling only the Wear OS app does not necessarily delete records already imported into Health Connect on the phone.

## Children's privacy

Padel Score is a general sports utility and is not specifically directed at children.

The developer does not knowingly collect personal information from children or other users through a developer-operated server.

## Security

Match and fitness information is stored in application-private storage and, when explicitly enabled, transferred between paired devices through the Wearable Data Layer.

Padel Score uses bounded payload sizes, integrity hashes and synchronisation receipts to reduce accidental duplicate, incomplete or stale workout imports.

No method of electronic storage or processing can be guaranteed to be completely secure. Users should keep their watch, paired phone, Android system and Google Play services updated and protect their devices with appropriate screen-lock settings.

## Changes to this policy

This policy may be updated when Padel Score's functionality, permissions, distribution methods or data practices change.

Material changes will be reflected by updating the date at the top of this document.

## Contact

Privacy questions and suspected privacy issues can be submitted through the project's GitHub issue tracker:

https://github.com/Hygros/PadelScore/issues

Do not include health records, purchase tokens, device identifiers, account credentials or other sensitive information in a public issue.