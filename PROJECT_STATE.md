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


## UI work: Google Messages-inspired secondary screens

Reviewed implementation commits on `phase1-ui`:
- `0c2f1bac0c39baf04be9550b9d295e9e1782f32b`: conversation header/layout sizing, icon-oriented message selection actions, and a confirmation before deleting selected messages.
- `5ab50733bfad49d8a6172230ddb99fd40e7587aa`: contact thumbnail photo loading in the conversation header, with an initials fallback.
- `62b9a584553539e87efaf5826ea148f1668c1e9e` and `2d2545db5f54bb9f45fc5dd995abc8d9e4129b2c`: shared spacing/input sizing for secondary management screens only.
- `fa3349706def468feb3cb56a9db5dd943434049a`: CI workflow publishes a `phase1-ui-debug` release after a successful push build.

**Protected main screen:** no redesign was applied to the conversations home. The new secondary-screen styling is invoked only for starred, trash, bulk, groups, routing rules, scheduled, and failed-message screens. MainActivity's home-building code and HashemUi overlay were not changed by these UI commits.

## Verification and download

- Push Actions for implementation commit `fa3349706def468feb3cb56a9db5dd943434049a` passed: [run #413](https://github.com/balalmhla12-cmd/HashemSMS/actions/runs/39671491353).
- The run passed baseline/current Lint comparison, hard-coded Java color check, light/dark contrast check, database migration/lifecycle unit tests, APK build, APK artifact upload, and publication of the private branch release.
- The PR-triggered run for the same commit must also be checked in Actions before final sign-off. Use the newest run at the current branch HEAD; documentation-only commits also trigger CI.
- Release URL: https://github.com/balalmhla12-cmd/HashemSMS/releases/tag/phase1-ui-debug
- Direct APK asset: https://github.com/balalmhla12-cmd/HashemSMS/releases/download/phase1-ui-debug/HashemSMS-debug.apk
- The repository is private, so downloading the release requires an account with repository access. This is a debug APK, not a production-signed release.
- **No real-device test is claimed.** SMS/MMS behavior, SIM selection, contact-photo rendering, notification reply, and scheduling still need verification on a physical Android device.

## Guardrails

- Keep package `com.hashem.sms`, existing features, database and send/receive behavior intact unless separately approved.
- Do not raise or regenerate the Lint baseline to hide warnings.
- Do not merge to `main` without explicit approval.
- Build success is not proof of real-device SMS, SIM, notification, scheduling, or theme behavior.

## Guardrails

- Keep package `com.hashem.sms`, existing features, database and send/receive behavior intact unless separately approved.
- Do not raise or regenerate the Lint baseline to hide warnings.
- Do not merge to `main` without explicit approval.
- Build success is not proof of real-device SMS, SIM, notification, scheduling, or theme behavior.
