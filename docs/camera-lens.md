# ColorOS camera Lens integration (experimental)

The 「相机相册智能镜头」 switch under 「系统设置」→「Google 智能镜头」 controls the Camera and Gallery integrations. Upgrading from the original Lens switch copies its state into the shared switch. The required LSPosed scopes are Camera (`com.oplus.camera`), Photos (`com.coloros.gallery3d`), and the Google App. Restart these apps after changing the switch.

## Inspected native sources

International CPH2653 / V16.1.0 Camera 6.070.230 and CN PLK110 / V17.0.0 Camera 7.006.125 both retain `key_show_googlelens_icon`, the native menu descriptor, and a presenter click handler that calls `startActivityForResult` with `ACTION_VIEW`, `googleapp://lens`, and the Google app package. The floating-button show/hide code is incomplete in both inspected builds. The CN presenter also returns false for the ordinary photo mode.

DexKit anchors the native menu using the option key and `com.oplus.camera.feature.google_lens`, then finds the native Lens presenter from its unchanged `googleapp://lens` click handler and mode strings. Native listener predicates, the normal-entry delegate, lock-state getter and UI-context accessor are bound once. Both real APKs passed full discovery and reflection binding on the connected CN device. The descriptor cache uses a separate `camera_lens.native-v1` entry; former menu descriptors/misses are discarded without invalidating Google Lens or Wallet caches. Only the menu gate and direct menu/presenter callers are deoptimized.

The hook restores only the missing `common` branch of the CN native presenter. It calls the camera's existing support, children-mode, initialization, normal-entry and front-camera APIs. Lock eligibility follows the inspected international conjunction: deny when the native device-lock getter and native secure UI flag are both true. Unlocking within the same secure-camera session can therefore recover eligibility. Other mode branches execute the original presenter. Native event handlers still control visibility during capture/recording; native menu/click handling still controls launch and statistics. No separate Intent whitelist, Activity-field guessing, PackageManager lookup or module-defined mode/lock policy remains.

Google 17.66.11's inspected native Lens gateway checks OEM eligibility using `com.google.lens.feature.CAMERA_INTEGRATION` and `ro.com.google.lens.oem_camera_package`. Both are absent on the tested CN phone. With the switch enabled, Google-process-only hooks expose the feature at version 0 and append `com.oplus.camera` to the package declaration. Existing package entries retain their original values.

## Verification

Both inspected Camera APKs passed native locator and binding checks, including reuse of cached descriptors. After adding the OEM eligibility declaration and restarting Camera and Google, the user confirmed that Lens opens from Camera on the CN OnePlus 15.

Future camera builds must retain the native anchors, named presenter APIs, normal-entry instruction shape and typed secure UI field `EventMessageUIContainerContext.b`. Ambiguous lookup or incompatible binding retains original camera behavior. Google may change its OEM declaration mechanism; universal update compatibility is not claimed.
