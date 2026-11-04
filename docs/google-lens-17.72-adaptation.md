# Google Lens Talk / Create compatibility — 2026-11-04

The PLK110 device runs Google 17.72.13.ve.arm64 (301831010), updated on November 3, and Coogle 1.3.6. The unchanged production scanner resolves the diagnostic anchor and eligibility candidates but returns no pair. The Talk controller constructor has gained a dependency: four leading references, ten boolean flags and one trailing reference (15 parameters). Older inspected layouts have three leading references and two, six or ten flags.

Direct inspection of the defining `classes7.dex` confirms `dvzt.f()` still calls `ckra.b(boolean)` and the controller retains a single no-argument long getter. Create remains the account/gate/boolean/long controller, `cyui.c()`. No downstream implementation was used.

The matcher now accepts the inspected 15-parameter layout and validates its four-reference prefix and complete boolean block. It retains older supported layouts, shared gate discovery, unique candidate checks, mode getter validation and paired hook installation. Unknown shapes remain rejected.

The Lens cache advances to `lens.controllers-v4`, removing the obsolete base key and v2/v3 locator entries. This permits rescanning the unchanged Google APK after installing the fix. Other feature caches, scope requirements and switches are unchanged. No additional hooks, background service, receiver, timer or fallback launch route is introduced.

## Verification

- Tests added before production changes reproduced both the 17.72 layout failure and the retained v3 negative cache (`build/lens-diagnostic-2026-11-04/red.log`).
- Fourteen synthetic DEX fixtures pass on Android, including a primitive in the extra dependency position, a non-boolean final flag, ambiguous candidates and unsupported layouts. Actual Google 17.67.17 / 17.68.17 / 17.70.10 / 17.72.13 APKs resolve 70 / 68 / 69 / 71 targets respectively, replace seeded obsolete misses and reuse the updated cache (`dex-check.log`). Older-version results are APK discovery checks, not new visual tests.
- An isolated mutation restoring the old three-reference prefix is rejected by the 17.72 regression; the original source passes (`review-mutation/results.json`).
- Full lifecycle regression, release assembly and lint pass (`check.log`): zero errors and unchanged 30 warnings. Final diff review found no blocking issue. The signing certificate and embedded companion match the published 1.3.6 artifact.
- Installed the separate test APK at device time 2026-11-04 23:51:08 and restarted only Google. Runtime logs confirm paired eligibility hook installation with 69 callers in `:googleapp`, `:interactor` and `:search` (`runtime-reload.log`). No phone reboot, feature toggle or scope change was performed.
- The user confirmed: **两个入口均恢复且能打开**.

Test APK: `Coogle-1.3.6-google-17.72.13-fix.apk`, 2,886,538 bytes, SHA-256 `1156ac95fb4cb60e863158baa3985e01ed83474727b210b4432dcf28228518b6`. Version remains 1.3.6 / 136 for the local test. Diagnostic and artifact records are under `build/lens-diagnostic-2026-11-04/`.

This is a local adaptation. No release, remote push or version bump is authorized by this change.
