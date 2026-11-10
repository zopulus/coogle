"""Verify native action-button Lens binding without installing an APK or changing system settings."""
import argparse
import os
from pathlib import Path
import subprocess
import uuid
import zipfile

ROOT = Path(__file__).resolve().parents[1]

def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--adb', default='adb')
    parser.add_argument('--serial', required=True)
    parser.add_argument('apks', nargs='+', type=Path)
    args = parser.parse_args()
    java = Path(os.environ['JAVA_HOME']) / 'bin'
    sdk = Path(os.environ['ANDROID_HOME'])
    cache = Path(os.environ.get('GRADLE_USER_HOME', Path.home()/'.gradle')) / 'caches/modules-2/files-2.1'
    aar = next((cache/'org.luckypray/dexkit/2.3.0').rglob('*.aar'))
    kotlin = next((cache/'org.jetbrains.kotlin/kotlin-stdlib/2.4.20').rglob('*.jar'))
    flat = next((cache/'com.google.flatbuffers/flatbuffers-java/23.5.26').rglob('*.jar'))
    local = ROOT/'build/action-lens-dex-test'
    classes = local/'classes'
    classes.mkdir(parents=True, exist_ok=True)
    with zipfile.ZipFile(aar) as archive:
        dexkit = local/'dexkit.jar'
        dexkit.write_bytes(archive.read('classes.jar'))
        (local/'libdexkit.so').write_bytes(archive.read('jni/arm64-v8a/libdexkit.so'))
    source = ROOT/'app/src/main/java/io/github/zopulus/coogle'
    production = [source/(name+'.java') for name in ('AppDexTargets','CameraLensBinding','FeaturePolicy','LensBinding','ActionButtonLensBinding','ActionButtonLensLaunch','ActionButtonLensCaller','SearchBarBinding')]
    def run(*command, **kwargs):
        return subprocess.run(list(map(str, command)), check=True, **kwargs)
    run(java/'javac.exe', '-encoding','UTF-8','-source','17','-target','17',
        '-cp',os.pathsep.join(map(str,[sdk/'platforms/android-37.0/android.jar',dexkit,kotlin])),
        '-d',classes,*production,ROOT/'tests/ActionButtonLensDexTest.java')
    jar = local/'runner-classes.jar'
    with zipfile.ZipFile(jar,'w') as archive:
        for path in classes.rglob('*.class'): archive.write(path,path.relative_to(classes).as_posix())
    output = local/'runner.zip'
    run(java/'java.exe','-cp',sdk/'build-tools/36.0.0/lib/d8.jar','com.android.tools.r8.D8','--min-api','35',
        '--lib',sdk/'platforms/android-37.0/android.jar','--output',output,jar,dexkit,kotlin,flat,capture_output=True)
    adb = [args.adb,'-s',args.serial]
    remote = '/data/local/tmp/coogle-action-lens-' + uuid.uuid4().hex
    run(*adb,'shell','mkdir',remote)
    try:
        for path in (output,local/'libdexkit.so'): run(*adb,'push',path,remote+'/'+path.name,capture_output=True)
        for index, apk in enumerate(args.apks):
            path = remote+f'/gesture-{index}.apk'
            run(*adb,'push',apk,path,capture_output=True)
            run(*adb,'shell',f'chmod 0444 {path} {remote}/runner.zip')
            print(f'Scanning {apk}',flush=True)
            run(*adb,'shell',f'CLASSPATH={remote}/runner.zip app_process -Xmx768m -Djava.library.path={remote} /system/bin io.github.zopulus.coogle.ActionButtonLensDexTest {path} {remote}')
    finally:
        # Only the fresh UUID directory owned by this test is removed.
        run(*adb,'shell','rm -rf '+remote)

if __name__ == '__main__':
    main()
