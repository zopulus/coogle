"""Combine hotword payloads with the user-supplied Google systemizer."""
from pathlib import Path
import stat
import zipfile

ROOT = Path(__file__).resolve().parent
OUT = ROOT.parent / 'build/Coogle-Companion-1.3.6.zip'
PAYLOAD_REVISION = 3

def payload():
    files = {}
    for directory in (ROOT/'module/system',):
        for path in directory.rglob('*'):
            if path.is_file():
                name = 'system/' + path.relative_to(directory).as_posix()
                assert name not in files, name
                files[name] = path.read_bytes()
    with zipfile.ZipFile(ROOT/'googlequicksearchbox-systemizer.zip') as systemizer:
        for info in systemizer.infolist():
            if not info.is_dir() and info.filename.startswith('system/'):
                assert '..' not in Path(info.filename).parts
                assert info.filename not in files
                files[info.filename] = systemizer.read(info)
    for name in ('patch-audio.awk', 'prepare-audio.sh', 'status.sh', 'hotword-status.awk'):
        path = ROOT/'integration'/name
        files[name] = path.read_bytes().replace(b'\r\n', b'\n')
    for name in ('boot-completed.sh', 'setup.sh', 'uninstall.sh', 'features.sh', 'post-fs-data.sh', 'native-translate.sh', 'copy-google.sh', 'mount-google.sh'):
        files[name] = (ROOT/'module'/name).read_bytes().replace(b'\r\n', b'\n')
    installer = '''#!/system/bin/sh
. "$MODPATH/copy-google.sh"
set_perm_recursive "$MODPATH/system" 0 0 0755 0644
for script in boot-completed.sh setup.sh uninstall.sh prepare-audio.sh status.sh features.sh post-fs-data.sh native-translate.sh copy-google.sh mount-google.sh; do
    set_perm "$MODPATH/$script" 0 0 0755
done
/system/bin/sh "$MODPATH/prepare-audio.sh"
. "$MODPATH/features.sh"
load_features /dev/null
if grep -q '^controlVersion=1$' /data/adb/modules/coogle_companion/module.prop 2>/dev/null; then
    load_features /data/adb/modules/coogle_companion/features.conf
fi
save_features "$MODPATH/features.conf" || abort "Cannot save component choices"
MODDIR=$MODPATH
apply_features || abort "Cannot prepare selected components"
. "$MODPATH/native-translate.sh"
prepare_native_translation || abort "Cannot prepare native translation configuration"
ui_print "Google system app, enrollment and DSP support combined. Reboot required."
ui_print "Hey Google follows the Coogle switch; live translation enables automatically after reboot."
'''
    files['customize.sh'] = installer.replace('\r\n', '\n').encode()
    files['module.prop'] = ('id=coogle_companion\nname=Coogle 附属模块\nversion=1.3.6\nversionCode=136\ncontrolVersion=1\n'
                            f'payloadRevision={PAYLOAD_REVISION}\nauthor=zopulus\ndescription=[⏳待重启检测]\n').encode()
    return files

def build():
    from build_contextual_overlay import build as build_overlay
    build_overlay()
    files = payload()
    OUT.parent.mkdir(parents=True, exist_ok=True)
    with zipfile.ZipFile(OUT, 'w', zipfile.ZIP_DEFLATED) as archive:
        for name,data in sorted(files.items()):
            info = zipfile.ZipInfo(name)
            info.create_system = 3
            info.external_attr = (stat.S_IFREG | (0o755 if name.endswith('.sh') else 0o644)) << 16
            info.compress_type = zipfile.ZIP_DEFLATED
            archive.writestr(info,data)
    print(f'{OUT} ({OUT.stat().st_size} bytes)')

if __name__ == '__main__':
    build()
