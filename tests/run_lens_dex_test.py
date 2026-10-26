"""Run real DexKit queries on an ADB device; no app installation or root access.

Requires the normal build's JDK, SDK and cached DexKit/Kotlin dependencies.
"""
import argparse
import os
from pathlib import Path
import subprocess
import uuid
import zipfile

ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / 'app/src/main/java/io/github/zopulus/coogle'
FIXTURE = '''package fixture;
import com.google.apps.tiktok.account.AccountId;
class Anchor {
    Object diagnostic(Object a, Object b, Object c, Object d) {
        System.out.println(EVENT); return String.format("Checked: %s", a);
    }
    boolean gate(boolean value) { return value; }
}
class RenamedCreate {
    private final Anchor anchor;
    RenamedCreate(AccountId account, Anchor anchor, boolean enabled, long mode) { this.anchor = anchor; }
    boolean eligible() { return anchor.gate(true); }
}
class RenamedTalk {
    private final Anchor anchor;
    RenamedTalk(Object a, Object b, Object c, boolean one, boolean two, Object state) { anchor = (Anchor)a; }
    boolean eligible() { return anchor.gate(false); }
    long mode() { return 1L; }
}
class Caller {
    boolean call(RenamedCreate create, RenamedTalk talk) { return create.eligible() || talk.eligible(); }
}
'''


def run(*args, **options):
    return subprocess.run([str(arg) for arg in args], check=True, **options)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--adb', default='adb')
    parser.add_argument('--serial', required=True)
    parser.add_argument('--verify-regression', action='store_true', help='Confirm the obsolete numeric matcher fails')
    parser.add_argument('--apk', type=Path, action='append', default=[], help='Also verify an actual Google APK')
    args = parser.parse_args()
    java = Path(os.environ['JAVA_HOME']) / 'bin'
    sdk = Path(os.environ['ANDROID_HOME'])
    android = sdk / 'platforms/android-37.0/android.jar'
    d8 = sdk / 'build-tools/36.0.0/lib/d8.jar'
    cache = Path(os.environ.get('GRADLE_USER_HOME', Path.home() / '.gradle')) / 'caches/modules-2/files-2.1'
    aar = next((cache / 'org.luckypray/dexkit/2.3.0').rglob('*.aar'))
    kotlin = next((cache / 'org.jetbrains.kotlin/kotlin-stdlib/2.4.20').rglob('*.jar'))
    flatbuffers = next((cache / 'com.google.flatbuffers/flatbuffers-java/23.5.26').rglob('*.jar'))
    local = ROOT / 'build/lens-dex-test'
    local.mkdir(parents=True, exist_ok=True)
    with zipfile.ZipFile(aar) as archive:
        dexkit = local / 'dexkit.jar'
        dexkit.write_bytes(archive.read('classes.jar'))
        (local / 'libdexkit.so').write_bytes(archive.read('jni/arm64-v8a/libdexkit.so'))

    def compile_dex(name, sources, libraries=()):
        classes = local / name
        classes.mkdir(exist_ok=True)
        run(java/'javac.exe', '-encoding', 'UTF-8', '-source', '17', '-target', '17',
            '-cp', os.pathsep.join(map(str, [android, dexkit, kotlin])), '-d', classes, *sources)
        jar = local / (name + '-classes.jar')
        with zipfile.ZipFile(jar, 'w') as archive:
            for path in classes.rglob('*.class'):
                archive.write(path, path.relative_to(classes).as_posix())
        output = local / (name + '.zip')
        run(java/'java.exe', '-cp', d8, 'com.android.tools.r8.D8', '--min-api', '35',
            '--lib', android, '--output', output, jar, *libraries, capture_output=True)
        return output

    files = [local / 'libdexkit.so']
    account = ROOT / 'tests/lens-stubs/com/google/apps/tiktok/account/AccountId.java'
    for name in ('old-event', 'new-event', 'new-layout', 'latest-layout', 'ambiguous', 'ambiguous-talk',
                 'wrong-shape', 'wrong-talk-shape', 'latest-wrong-talk-shape', 'unknown-talk-layout', 'missing-talk'):
        fixture = FIXTURE.replace('EVENT', '27390' if name == 'old-event' else '27507')
        if name in ('new-layout', 'latest-layout', 'ambiguous-talk', 'wrong-talk-shape', 'latest-wrong-talk-shape', 'unknown-talk-layout'):
            fixture = fixture.replace('boolean one, boolean two, Object state',
                                      'boolean one, boolean two, boolean three, boolean four, boolean five, boolean six, Object state')
        if name in ('latest-layout', 'latest-wrong-talk-shape'):
            fixture = fixture.replace('boolean six, Object state',
                                      'boolean six, boolean seven, boolean eight, boolean nine, boolean ten, Object state')
        if name == 'latest-wrong-talk-shape':
            fixture = fixture.replace('boolean ten', 'int ten')
        if name == 'ambiguous-talk':
            fixture += '''class DuplicateTalk {
                private final Anchor anchor;
                DuplicateTalk(Object a, Object b, Object c, boolean one, boolean two, Object state) { anchor = (Anchor)a; }
                boolean eligible() { return anchor.gate(false); }
                long mode() { return 1L; }
            }'''
        if name == 'wrong-talk-shape':
            fixture = fixture.replace('boolean four', 'int four')
        if name == 'unknown-talk-layout':
            fixture = fixture.replace('boolean five, boolean six, ', '')
        if name == 'ambiguous':
            fixture += 'class Duplicate { Object diagnostic(Object a,Object b,Object c,Object d) { return String.format("Checked: %s",a); } }'
        if name == 'wrong-shape':
            fixture = fixture.replace('RenamedCreate(AccountId account', 'RenamedCreate(Object account')
        if name == 'missing-talk':
            fixture = fixture.replace('return anchor.gate(false)', 'return false')
        source = local / (name + '.java')
        source.write_text(fixture, encoding='utf-8')
        files.append(compile_dex(name, [source, account]))
    production = [SOURCE / (name + '.java') for name in ('AppDexTargets', 'CameraLensBinding', 'LensBinding', 'FeaturePolicy', 'WalletPreviewCandidates', 'ActionButtonLensBinding')]
    test = ROOT / 'tests/LensDexTest.java'
    files.append(compile_dex('runner', [*production, test], [dexkit, kotlin, flatbuffers]))
    if args.verify_regression:
        mutation = local / 'AppDexTargets.java'
        original = production[0].read_text(encoding='utf-8')
        anchor = '.returnType("java.lang.Object").paramCount(4)'
        assert original.count(anchor) == 1
        mutation.write_text(original.replace(anchor, anchor + '.usingNumbers(27390)'), encoding='utf-8')
        files.append(compile_dex('obsolete-runner', [mutation, *production[1:], test], [dexkit, kotlin, flatbuffers]))

    adb = [args.adb, '-s', args.serial]
    remote = '/data/local/tmp/coogle-lens-test-' + uuid.uuid4().hex
    run(*adb, 'shell', 'mkdir', remote)
    try:
        for path in files:
            run(*adb, 'push', path, remote + '/' + path.name, capture_output=True)
        apk_paths = []
        for index, apk in enumerate(args.apk):
            path = remote + f'/google-{index}.apk'
            run(*adb, 'push', apk, path, capture_output=True)
            apk_paths.append(path)
        run(*adb, 'shell', 'chmod 0444 ' + ' '.join([*(remote + '/' + p.name for p in files), *apk_paths]))
        def execute(name):
            command = f'CLASSPATH={remote}/{name}.zip app_process -Xmx768m -Djava.library.path={remote} /system/bin io.github.zopulus.coogle.LensDexTest {remote} ' + ' '.join(apk_paths)
            return subprocess.run([*adb, 'shell', command], capture_output=True, text=True)
        good = execute('runner')
        print(good.stdout, end='')
        if good.returncode != 0:
            raise RuntimeError(good.stderr)
        if args.verify_regression:
            bad = execute('obsolete-runner')
            if bad.returncode == 0 or 'new-event: eligibility pair/callers missing' not in bad.stderr:
                raise RuntimeError('Obsolete matcher was not rejected: ' + bad.stdout + bad.stderr)
            print('PASS: obsolete numeric matcher rejected by new-event fixture')
    finally:
        run(*adb, 'shell', 'rm', '-f', *(remote + '/' + p.name for p in files),
            *(remote + f'/google-{index}.apk' for index in range(len(args.apk))), remote + '/coogle_app_targets_v1.cache')
        run(*adb, 'shell', 'rmdir', remote)


if __name__ == '__main__':
    main()
