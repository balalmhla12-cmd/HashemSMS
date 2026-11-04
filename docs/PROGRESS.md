# HashemSMS — Progress

Last reviewed: 2026-11-05
Active branch: `phase1-ui`
PR #6: https://github.com/balalmhla12-cmd/HashemSMS/pull/6 (open, not merged)
Repository visibility: Private.

## Current verification evidence

The code state `2f4a750c99e94318d2bc66f96b425f4cc91b51d5` passed:
- Push Android workflow: https://github.com/balalmhla12-cmd/HashemSMS/actions/runs/41927704438
- Pull-request Android workflow: https://github.com/balalmhla12-cmd/HashemSMS/actions/runs/41927715236
- Build: PASS
- Unit tests (including database migration/lifecycle): PASS
- Android Lint: PASS, 14 warnings, 0 errors (baseline 15 warnings, 0 errors)
- Java hard-coded color check: PASS
- Light/dark WCAG contrast check: PASS

The current branch later advanced to `125df6f861df19b9d68ac7e1ba4c81a7cb3439fd`, which adds a test asserting that original GIF bytes are preserved in MMS PDU composition. The latest-head push and PR workflows must pass before treating that commit as verified. The project limit remains 14 Lint warnings; do not raise the baseline.

## Batch A — implementation status

Implemented:
- Six main tabs in the required order: الكل، غير مقروءة، لم يتم الإرسال، جماعية، غير معروف، مجدولة.
- Horizontal tab scrolling and corrected scheduled-tab mapping.
- Hidden standalone starred/routing-rules destinations while preserving star and routing data/actions.
- Home title/logo and conversation-row typography/spacing refinements.
- Failed-message search/contact details/preview/status/time/retry/conditional routing.
- Conversation grouping and latest-failed-message ordering restored.
- Unrequested README.md and README-TERMUX.md edits reverted.

Sign-off is not yet final because screenshot evidence remains unresolved and the latest head still needs CI verification.

### HashemUi audit

Commit `12f5036933cc31285acc749651b782b0fa3332b3` removes the unrequested header theme toggle and tab count badges/periodic count refresh, and restores the header search icon. Theme selection remains in Settings.

Remaining visual treatments retained for final review: search-field styling, bottom-navigation styling, floating compose-button appearance/placement, avatar fallback/ring styling, and conversation-row styling. Do not claim these are separately specified functions.

## Screenshot evidence

Two emulator attempts have been made; neither produced screenshots:
1. Stable emulator configuration: emulator booted, then ADB disconnected (Broken pipe/protocol fault).
2. KVM-enabled configuration: KVM and emulator boot succeeded, but the script failed before instrumentation because `pipefail` was not supported by the POSIX shell.

The workflow was corrected to use `set -eu`. One corrected retry of the second attempt is permitted because the previous run never reached screenshot tests. If it fails, stop and wait for the user's choice of Robolectric or Paparazzi; do not silently defer screenshots and do not start a third independent emulator attempt.

## Batch B — in progress

- Composer text field has no short eight-line cap.
- Plus/clear-text behavior is implemented.
- Quick replies support defaults, add/edit/delete, unlimited saved entries, and insertion without sending.
- Attachment menu covers gallery, camera, GIF, stickers/emoji, files, live-location notice, contacts, scheduling, and one-time location.
- GIF warning and original MIME/payload preservation are implemented; a PDU unit test verifies original GIF bytes.
- Per-recipient normalized SIM preference is used for conversation sends and MMS retry; unavailable saved subscriptions trigger a fresh selection.
- Long-press SIM picker displays subscription labels/numbers when available.
- Pinch scaling updates message text and action buttons.
- Translation replaces the star icon; star remains in More.

Still to verify in B: visual/accessibility details, SMS/MMS regressions, quick-reply persistence, SIM cancellation/permission paths, and latest CI.

## Batches C and D

- C: bulk-message screen, groups, history, sidebar, settings sections, unified color picker, backup/restore safety.
- Before C, present the user with the on-device versus online translation comparison and wait for the user's choice.
- D: performance, permissions, background sync, final regression tests and real-device checklist.

## Non-negotiable constraints

- Preserve the existing Java application, package, data, SMS/MMS/scheduling behavior, and existing features.
- No unsafe DB schema changes.
- Do not modify `android.yml` or `Db.java` for the audited UI changes.
- Keep the repository Private; work only on `phase1-ui`.
- Do not merge PR #6 or related PRs into `main` without explicit approval.
- No final APK/Release/install request before all four batches are complete and verified.
- No real-device test is claimed until it is actually performed.
