"""Verify the production Launcher locator on Android, without installing an app."""
import argparse
import os
from pathlib import Path
import subprocess
import uuid
import zipfile

ROOT = Path(__file__).resolve().parents[1]
FIXTURE = '''package fixture;
import android.content.Context;
class Features {
    static boolean export, second, breeno, search;
    static void init(Context context) {
        System.out.println("initFeature. The pm is null!");
        export = false; second = false;
        System.out.println("IS_EXP_VERSION:");
    }
    static void initBreeno(Context context) {
        System.out.println("initSupportBreenoEntryFeature: Export, sIsSupportBreenoEntry = false");
        breeno = !export;
    }
    static void initSearch(Context context) {
        System.out.println("initSupportSearchEntryFeature: search entry disable, sIsSupportSearchEntry = false");
        search = export;
    }
    static boolean query() { return false; }
}
class BottomSearch {
    void update(Context context) {
        System.out.println("dealBottomSearchHostUpdate: qsb is enabled?");
        System.out.println(eligible(context));
    }
    boolean eligible(Context context) { return Features.query(); }
}
'''


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
    local = ROOT / 'build/search-bar-dex-test'
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
        run(java / 'javac.exe', '-encoding', 'UTF-8', '-cp', os.pathsep.join(map(str, [android, dexkit, kotlin])),
            '-d', classes, *sources)
        jar = local / (name + '.jar')
        with zipfile.ZipFile(jar, 'w') as archive:
            for path in classes.rglob('*.class'):
                archive.write(path, path.relative_to(classes).as_posix())
        output = local / (name + '.zip')
        run(java / 'java.exe', '-cp', sdk / 'build-tools/36.0.0/lib/d8.jar', 'com.android.tools.r8.D8',
            '--min-api', '35', '--lib', android, '--output', output, jar, *libraries, capture_output=True)
        return output

    files = [local / 'libdexkit.so']
    for name in ('valid', 'duplicate-init', 'ambiguous-export', 'missing-query', 'wrong-field'):
        source = FIXTURE
        if name == 'duplicate-init':
            source += 'class Duplicate { static void init(Context c) { System.out.println("initFeature. The pm is null!"); System.out.println("IS_EXP_VERSION:"); } }'
        elif name == 'ambiguous-export':
            source = source.replace('breeno = !export', 'breeno = !export && second').replace('search = export', 'search = export && second')
        elif name == 'missing-query':
            source = source.replace('return Features.query()', 'return false')
        elif name == 'wrong-field':
            source = source.replace('second, breeno, search;', 'second, search; static int breeno;').replace('breeno = !export', 'breeno = export ? 1 : 0')
        path = local / (name + '.java')
        path.write_text(source, encoding='utf-8')
        files.append(compile_dex(name, [path]))
    production = ROOT / 'app/src/main/java/io/github/zopulus/coogle'
    sources = [production / (name + '.java') for name in ('AppDexTargets', 'SearchBarBinding', 'CameraLensBinding', 'LensBinding', 'FeaturePolicy', 'ActionButtonLensBinding')]
    files.append(compile_dex('runner', [*sources, ROOT / 'tests/SearchBarDexTest.java'], [dexkit, kotlin, flat]))
    adb = [args.adb, '-s', args.serial]
    remote = '/data/local/tmp/coogle-search-test-' + uuid.uuid4().hex
    run(*adb, 'shell', 'mkdir', remote)
    remote_files = []
    try:
        for path in [*files, *args.apk]:
            target = remote + '/' + path.name
            run(*adb, 'push', path, target, capture_output=True)
            remote_files.append(target)
        run(*adb, 'shell', 'chmod 0444 ' + ' '.join(remote_files))
        command = f'CLASSPATH={remote}/runner.zip app_process -Xmx512m -Djava.library.path={remote} /system/bin io.github.zopulus.coogle.SearchBarDexTest {remote} '
        run(*adb, 'shell', command + ' '.join(remote + '/' + apk.name for apk in args.apk))
    finally:
        run(*adb, 'shell', 'rm', '-f', *remote_files, remote + '/coogle_app_targets_v1.cache')
        run(*adb, 'shell', 'rmdir', remote)


if __name__ == '__main__':
    main()
