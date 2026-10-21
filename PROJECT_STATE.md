# HashemSMS — Project State

Last reviewed: 2026-10-21
Branch: `phase1-ui`
Package invariant: `com.hashem.sms`
Main branch: not modified; PR #6 remains open and unmerged.

## Settings screen

- Settings screen is implemented in `app/src/main/java/com/hashem/sms/SettingsActivity.java`, introduced by commit `6164497ede5246dfff6eb9871c4067361a215671` (`Add internal HashemSMS settings screen`).
- `MainActivity.openApplicationSettings()` now opens the internal settings activity.
- Implemented UI sections: live default-SMS status, permission summary, active SIM count/details, settings search, appearance choices (system/light/dark) with message-bubble preview, notification settings shortcut, exact-alarm/scheduling shortcut, privacy/system app settings, expandable advanced shortcuts, and About dialog using the installed version and package ID.
- SIM information is read from Android's active subscriptions when the required phone-state permission is granted. This screen does not silently select or change the sending SIM; sending selection remains in the conversation flow.
- The screen uses existing HashemSMS resources/theme and RTL layout. No database schema or SMS sending logic change was included in the settings-screen commit.
- Limitation: Android system settings shortcuts hand off to Android; they are not custom in-app permission editors. SIM display and behavior still require real-device verification. No device test is claimed.

## CI evidence at review

- Pull-request Actions run #396 for commit `6164497ede5246dfff6eb9871c4067361a215671`: completed successfully.
- Run #396 reported baseline Lint warnings: 15; current warnings: 14; errors: 0. Contrast checks, database migration/lifecycle unit tests, debug APK build and artifact uploads completed. Stable release publishing was skipped.
- Push Actions run #395 for the same commit was still in progress when this review was recorded; refresh Actions before treating the commit as fully verified across both triggers.
- Run link: https://github.com/balalmhla12-cmd/HashemSMS/actions/runs/39664271062
- Push run link: https://github.com/balalmhla12-cmd/HashemSMS/actions/runs/39664265131

## Guardrails

- Keep package `com.hashem.sms`, existing features, database and send/receive behavior intact unless separately approved.
- Do not raise or regenerate the Lint baseline to hide warnings.
- Do not merge to `main` without explicit approval.
- Build success is not proof of real-device SMS, SIM, notification, scheduling, or theme behavior.
