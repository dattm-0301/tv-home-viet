#!/usr/bin/env python3
"""Seed only sample app grouping in a disposable Android emulator; never inject widget prices."""
import json, os, subprocess, sys
from pathlib import Path
from xml.sax.saxutils import escape

if len(sys.argv) != 2 or not sys.argv[1].startswith('emulator-'):
    raise SystemExit('Usage: seed.py emulator-SERIAL (emulators only)')
adb = os.environ.get('ADB', 'adb')
serial = sys.argv[1]
pkg = 'com.tvhomeviet.launcher'
def run(*args, **kwargs):
    return subprocess.run([adb, '-s', serial, *args], check=True, **kwargs)
if run('shell', 'getprop', 'ro.kernel.qemu', capture_output=True, text=True).stdout.strip() != '1':
    raise SystemExit('Not an emulator; refusing')
run('shell', 'am', 'force-stop', pkg)
base='com.tvhomeviet.demo.'
pins=[base+'library', base+'cinema', 'com.google.android.youtube.tv', base+'music', base+'web', base+'files']
assign={base+'library':'film', base+'cinema':'film', base+'music':'music', base+'radio':'music', base+'cast':'cast', base+'web':'web', base+'games':'other', base+'files':'tools'}
values={'pins':json.dumps(pins), 'assign':json.dumps(assign), 'sort':'alpha'}
xml='<map>'+''.join(f'<string name="{k}">{escape(v)}</string>' for k,v in values.items())+'</map>'
run('shell', f"run-as {pkg} sh -c 'mkdir -p shared_prefs; cat > shared_prefs/home_rails_v1.xml'", input=xml.encode())
config=json.loads((Path(__file__).resolve().parents[1]/'themes/default/config.json').read_text())
config['home_idle_clock_minutes']=1
run('shell', 'content', 'write', '--uri', f'content://{pkg}.theme/config.json', input=json.dumps(config).encode())
run('shell', 'cmd', 'locale', 'set-app-locales', pkg, '--user', '0', '--locales', 'vi-VN')
run('shell', 'am', 'start', '-W', '-n', pkg+'/com.gathin.tvhome.MainActivity')
print('Seeded sample apps only; widget data still comes from the actual network feeds.')
