# HashemSMS — Project State

Last reviewed: 2026-11-05
Repository: `balalmhla12-cmd/HashemSMS` (must remain Private)
Active branch: `phase1-ui`
Package invariant: `com.hashem.sms`
Main branch: unchanged by this work; PR #6 remains open and unmerged.

## Current branch and CI

- Current branch HEAD at the time of this update: `125df6f861df19b9d68ac7e1ba4c81a7cb3439fd`.
- The immediately preceding code state `2f4a750c99e94318d2bc66f96b425f4cc91b51d5` passed both push and pull-request Android workflows:
  - Push run: https://github.com/balalmhla12-cmd/HashemSMS/actions/runs/41927704438
  - Pull-request run: https://github.com/balalmhla12-cmd/HashemSMS/actions/runs/41927715236
- Both runs passed database migration/lifecycle unit tests, debug build, Android Lint, Java color-constructor check, and light/dark WCAG contrast check.
- Lint at `2f4a750`: baseline 15 warnings / 0 errors; current 14 warnings / 0 errors. The project limit is 14 warnings. No baseline was raised or regenerated.
- Commit `125df6f` adds a unit test asserting original GIF payload bytes survive MMS PDU composition unchanged. Its new push and PR workflows must pass before this latest head is considered verified.
- Do not describe any pending workflow as PASS.

## Batch A — implementation and remaining sign-off

Implemented on `phase1-ui`:
- Main tabs are ordered: الكل، غير مقروءة، لم يتم الإرسال، جماعية، غير معروف، مجدولة.
- The tabs remain horizontally scrollable; the obsolete starred tab is not shown.
- The «مميزة» and «قواعد التوجيه» standalone UI destinations are hidden; the star action and routing data/logic are retained.
- The home title/logo sizing, tab density, conversation row text sizing and spacing were adjusted.
- Failed-message list was updated for search, contact name/photo fallback, preview, state/time, retry and conditional routing.
- A follow-up fix restored grouping by conversation and latest-failure ordering.
- README.md and README-TERMUX.md edits were reverted; they are not part of the requested project-state documentation.

A sign-off remains conditional on:
1. Confirming the latest head's Actions are green with 14 or fewer Lint warnings.
2. Completing the two-attempt screenshot procedure described below or receiving the user's choice of a no-emulator alternative.
3. Final review of remaining cosmetic overlay changes against the reference UI.

### HashemUi audit

The overlay had additional UI behavior beyond the six-tab correction. The unrequested header theme toggle, hidden header search icon, and unread/failed tab count badges (including their periodic DB count refresh) were removed in commit `12f5036`. The header search icon is restored. Theme selection remains available in Settings.

Other styling retained for review: search-field treatment, bottom-navigation surface styling, floating compose-button appearance/placement, avatar fallback/ring styling, and conversation-row typography/spacing. Typography/spacing and logo/tab work correspond to the stated home-screen requirements; purely decorative treatment should not be represented as separately requested functionality.

## Screenshot attempts

Exactly two emulator attempts were made:
1. Stable emulator-runner configuration: emulator booted, then ADB disconnected with Broken pipe/protocol errors; no screenshots or artifact were produced.
2. KVM-enabled runner: KVM check and emulator boot succeeded, but the script stopped before instrumentation tests because `set -euo pipefail` was run under POSIX `sh`; no screenshots or artifact were produced.

The shell issue was corrected to POSIX-compatible `set -eu` in the screenshot workflow. The second attempt is eligible for one corrected retry because its failure occurred before screenshot tests began; do not start a third independent emulator attempt. If the corrected retry fails, stop and ask the user to choose between Robolectric screenshots and Paparazzi. No screenshot artifact currently exists.

No emulator screenshots, real-device tests, or device-level SMS/MMS/SIM verification are claimed.

## Batch B — conversation and composer

Work is underway; not yet signed off:
- The composer no longer has the former eight-line cap.
- Plus changes to clear-text (×) when text exists; tapping it clears only the text.
- Quick replies include «كيف حالك»، «السلام عليكم»، and «إضافة رد جديد»; user replies can be added, edited, deleted, and inserted without auto-send.
- The attachment menu includes gallery, camera, GIF, stickers/emoji, files, live-location notice (live location remains deferred), contacts, scheduling, and one-time location.
- GIF selection warns before sending and retains original GIF MIME/payload; a unit test checks the original GIF bytes in the MMS PDU.
- SIM selection is stored per normalized recipient number, reused for later sends and MMS retry, and re-requested if the saved subscription is no longer active. Long-press SIM picker exposes active subscription names/numbers when Android provides them.
- Pinch scaling affects message text and action buttons; the translation action replaces the star icon, while star remains in More.

Remaining Batch B checks include UI layout/accessibility, SMS/MMS composer regression tests, quick-reply persistence behavior, SIM permission/cancel paths, and final Actions verification.

## Protected project rules

- Do not rewrite the application or remove existing features/data.
- Do not modify database schema without a safe migration and tests.
- Do not change `android.yml` or `Db.java` as part of the UI workflow work. In the specifically audited commits `b9f0124`, `60dc03c`, and `b152dc4`, only `ui-screenshots.yml` changed.
- Do not merge PR #6 or any related PR into `main` without explicit approval.
- No final APK, final Release, or installation request before all four batches are complete and verified.
- Any manual phone checks must be delivered as a checklist with the final build.
