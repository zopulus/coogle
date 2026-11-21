package io.github.zopulus.coogle;

/** Restores the retained Oplus Lens presenter; menu and click handling remain native. */
final class CameraLensHooks {
    static void install(HookInstaller hooks, ClassLoader loader, String apkPath, String dataDir) {
        if (!FeatureConfig.enabled(FeaturePolicy.GALLERY_LENS)) return;
        int mark = hooks.mark();
        try (AppDexTargets targets = new AppDexTargets(loader, apkPath, dataDir)) {
            var found = targets.resolve(FeaturePolicy.CAMERA_LENS);
            if (found.isEmpty()) { hooks.info("CAMERA LENS skip: native presenter binding not unique"); return; }
            var binding = CameraLensBinding.bind(found);
            hooks.install(binding.eligibility, chain -> {
                if (!FeatureConfig.enabled(FeaturePolicy.GALLERY_LENS) || !binding.isCommon(chain.getThisObject())) return chain.proceed();
                return binding.allowed(chain.getThisObject());
            });
            for (int i = CameraLensBinding.TARGET_COUNT; i < found.size(); i++) hooks.deoptimize(found.get(i));
            hooks.info("CAMERA LENS native presenter restored: " + binding.eligibility);
        } catch (Throwable failure) {
            hooks.unhookSince(mark);
            hooks.error("CAMERA LENS incompatible; original camera retained", failure);
        }
    }
    private CameraLensHooks() {}
}
