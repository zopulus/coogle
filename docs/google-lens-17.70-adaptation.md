# Google Lens Talk / Create compatibility — 2026-10-27

The PLK110 device updated Google to 17.70.10.ve.arm64 (301827554) on October 26. Both Lens entries disappeared with Coogle 1.3.5. Running the unchanged production scanner against that installed APK found the diagnostic anchor and both candidates, but returned no binding pair.

The Talk controller now has three leading references, ten boolean flags and one trailing reference (14 constructor arguments). The previous layouts have two or six flags (6 or 10 arguments). Direct inspection of the defining `classes7.dex` confirms the current `dunh.f()` predicate still calls the shared `cjuk.b(boolean)` gate and retains its unique no-argument long getter. Create remains the account/gate/boolean/long controller (`cxus.c()`). No downstream implementation was used.

The selector now accepts the inspected 14-argument layout alongside the older two layouts. It retains boolean flag validation, reference positions, unique mode getter, shared gate candidates, ambiguity rejection and paired hook installation. Unknown shapes remain rejected. The feature remains independent of the hardware shortcut switch.

The Lens cache key advances to `lens.controllers-v3`; both the obsolete `lens` and `lens.controllers-v2` entries are removed when resolving Lens. This prevents a deterministic miss from Coogle 1.3.5 suppressing the corrected locator for the same Google APK. Other feature caches are unchanged.

## Verification

- Added tests first: the old implementation failed both the 17.70 structural test and the same-APK `controllers-v2` negative cache migration test (`build/lens-diagnostic-2026-10-27/red.log`).
- Eleven synthetic DEX fixtures pass on Android, including the latest layout, a non-boolean final flag, ambiguous controllers, unsupported layout and missing Talk. Actual Google 17.67.17 / 17.68.17 / 17.70.10 APKs resolve 70 / 68 / 69 targets and reuse the corrected cache after replacing seeded older negative caches (`dex-check.log`). This does not imply visual testing of the older Google versions.
- An isolated mutation removing 17.70 layout acceptance is rejected by the new regression test. The original source passes (`review-mutation/results.json`).
- Full lifecycle regression, release assembly and lint pass (`check.log`): zero errors and unchanged 30 warnings. Diff review found no blocking issue. The signing certificate and embedded companion ZIP match the published 1.3.5 build.
- Installed the separate test APK at device time 2026-10-27 01:11:01, then restarted only Google. Runtime logs confirm paired hook installation with 67 callers in `:googleapp`, `:search` and `:interactor` (`runtime-reload.log`). No feature switches, scopes or phone reboot were changed.
- The user confirmed: **两个入口均恢复且能打开**.

Test APK: `Coogle-1.3.5-google-17.70.10-fix.apk`, 2,886,538 bytes, SHA-256 `b3b9172d816607cad634ab38db6f3c253032e6b0f11dff161915bfe2a5adb3d1`. Version remains 1.3.5 / 135 for the local test. Artifact metadata and diagnostic evidence are in `build/lens-diagnostic-2026-10-27/`.

No background service, receiver, timer, fallback launch route or additional hooks are introduced. This is a local adaptation; no publication or version bump is part of this change.
