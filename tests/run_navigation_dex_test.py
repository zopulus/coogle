"""Exercise native navigation discovery against renamed DEX and a real Launcher APK."""
import argparse
import os
from pathlib import Path
import subprocess
import uuid
import zipfile

ROOT = Path(__file__).resolve().parents[1]
FIXTURES = {
    'fixture/Features.java': '''package fixture;
public class Features {
    public static boolean circle() { return false; }
    public static boolean hidden() { return false; }
    public static boolean gemini() { return false; }
    public static boolean decoy() { return false; }
}''',
    'fixture/Navigation.java': '''package fixture;
public class Navigation {
    public static final Holder state = new Holder();
    public static boolean gestureEnabled=true, hiddenEnabled=true, circleEnabled=true;
    public boolean gesture() { return gestureEnabled; }
    public boolean hidden() { return hiddenEnabled; }
    public boolean circle() { return circleEnabled; }
    public static class Holder { public Navigation get() { return new Navigation(); } }
}''',
    'fixture/Dispatch.java': '''package fixture;
import android.content.Context;
public class Dispatch {
    public static void start(int type, Context context) {
        if (Features.circle()) System.out.println("startCircleToSearch, type: " + type + " isRecentsAnimRealFinish: " + context);
    }
    public int corner(Context context) {
        System.out.println("circle_to_search_corner_assist_enable_navi");
        return Features.gemini() ? 1 : 0;
    }
}''',
    'com/android/launcher3/circlesearch/OplusHideNavHandleController.java': '''package com.android.launcher3.circlesearch;
import fixture.Features;
import fixture.Navigation;
public class OplusHideNavHandleController {
    public boolean enabled;
    public OplusHideNavHandleController() { enabled = INSTANCE.isSupportCircleToSearchHandle(); }
    public static class INSTANCE {
        public static boolean isSupportCircleToSearchHandle() {
            return Features.hidden() && Navigation.state.get().gesture()
                && Navigation.state.get().hidden() && Navigation.state.get().circle();
        }
    }
}'''
}

def run(*args, **kwargs):
    return subprocess.run(list(map(str, args)), check=True, **kwargs)

def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--adb', default='adb')
    parser.add_argument('--serial', required=True)
    parser.add_argument('--apk', type=Path, action='append', default=[])
    args = parser.parse_args()
    java = Path(os.environ['JAVA_HOME']) / 'bin'
    sdk = Path(os.environ['ANDROID_HOME'])
    android = sdk / 'platforms/android-37.0/android.jar'
    cache = Path(os.environ.get('GRADLE_USER_HOME', Path.home() / '.gradle')) / 'caches/modules-2/files-2.1'
    local = ROOT / 'build/navigation-dex-test'
    local.mkdir(parents=True, exist_ok=True)
    with zipfile.ZipFile(next((cache / 'org.luckypray/dexkit/2.3.0').rglob('*.aar'))) as archive:
        dexkit = local / 'dexkit.jar'
        dexkit.write_bytes(archive.read('classes.jar'))
        (local / 'libdexkit.so').write_bytes(archive.read('jni/arm64-v8a/libdexkit.so'))
    kotlin = next((cache / 'org.jetbrains.kotlin/kotlin-stdlib/2.4.20').rglob('*.jar'))
    flat = next((cache / 'com.google.flatbuffers/flatbuffers-java/23.5.26').rglob('*.jar'))

    def compile_dex(name, sources, libraries=()):
        classes = local / (name + '-classes')
        classes.mkdir(exist_ok=True)
        run(java / 'javac.exe', '-encoding', 'UTF-8', '-cp', os.pathsep.join(map(str, [android, dexkit, kotlin])), '-d', classes, *sources)
        jar = local / (name + '.jar')
        with zipfile.ZipFile(jar, 'w') as archive:
            for path in classes.rglob('*.class'):
                archive.write(path, path.relative_to(classes).as_posix())
        output = local / (name + '.zip')
        run(java / 'java.exe', '-cp', sdk / 'build-tools/36.0.0/lib/d8.jar', 'com.android.tools.r8.D8',
            '--min-api', '35', '--lib', android, '--output', output, jar, *libraries, capture_output=True)
        return output

    files = [local / 'libdexkit.so']
    for name in ('valid', 'renamed', 'missing-dispatch', 'ambiguous-circle', 'static-predicate', 'duplicate-corner', 'static-accessor'):
        sources = []
        for relative, source in FIXTURES.items():
            if name == 'renamed':
                source = source.replace('circle()', 'a()').replace('hidden()', 'b()').replace('gemini()', 'c()').replace('gesture()', 'd()').replace(' state', ' changed').replace('.state', '.changed')
            elif name == 'missing-dispatch':
                source = source.replace('startCircleToSearch, type:', 'unrelated:')
            elif name == 'ambiguous-circle':
                source = source.replace('if (Features.circle())', 'if (Features.circle() && Features.decoy())')
            elif name == 'static-predicate':
                source = source.replace('public boolean gesture()', 'public static boolean gesture()')
            elif name == 'duplicate-corner' and relative.endswith('Dispatch.java'):
                source += 'class Duplicate { public int read(android.content.Context c) { System.out.println("circle_to_search_corner_assist_enable_navi"); return Features.gemini() ? 1 : 0; } }'
            elif name == 'static-accessor':
                source = source.replace('public Navigation get()', 'public static Navigation get()')
            path = local / name / relative
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_text(source, encoding='utf-8')
            sources.append(path)
        files.append(compile_dex(name, sources))
    production = ROOT / 'app/src/main/java/io/github/zopulus/coogle'
    sources = [production / (name + '.java') for name in ('AppDexTargets', 'NavigationBinding', 'GateBinding', 'SearchBarBinding', 'CameraLensBinding', 'LensBinding', 'FeaturePolicy', 'ActionButtonLensBinding')]
    files.append(compile_dex('runner', [*sources, ROOT / 'tests/NavigationDexTest.java'], [dexkit, kotlin, flat]))
    adb = [args.adb, '-s', args.serial]
    remote = '/data/local/tmp/coogle-navigation-test-' + uuid.uuid4().hex
    run(*adb, 'shell', 'mkdir', remote)
    remote_files = []
    try:
        for path in [*files, *args.apk]:
            target = remote + '/' + path.name
            run(*adb, 'push', path, target, capture_output=True)
            remote_files.append(target)
        run(*adb, 'shell', 'chmod 0444 ' + ' '.join(remote_files))
        command = f'CLASSPATH={remote}/runner.zip app_process -Xmx512m -Djava.library.path={remote} /system/bin io.github.zopulus.coogle.NavigationDexTest {remote} '
        run(*adb, 'shell', command + ' '.join(remote + '/' + apk.name for apk in args.apk))
    finally:
        run(*adb, 'shell', 'rm', '-f', *remote_files, remote + '/coogle_app_targets_v1.cache')
        run(*adb, 'shell', 'rmdir', remote)

if __name__ == '__main__':
    main()
