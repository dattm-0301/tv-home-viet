#!/usr/bin/env python3
"""Fetch a pinned project release, verify SHA-256, then use the guarded installer."""
import argparse, hashlib, json, re, subprocess, urllib.request
from pathlib import Path

REPO='dattm-0301/tv-home-viet'
ROOT=Path(__file__).resolve().parents[1]

def get(url,limit):
    req=urllib.request.Request(url,headers={'User-Agent':'TV-Home-Viet-updater','Accept':'application/vnd.github+json'})
    with urllib.request.urlopen(req,timeout=30) as response:
        data=response.read(limit+1)
    if len(data)>limit: raise ValueError('Download exceeded size limit')
    return data

def select(releases,tag=None):
    if not isinstance(releases,list): raise ValueError('Unexpected release API response')
    def version(release):
        match=re.fullmatch(r'tv-home-viet-v(\d+)\.(\d+)\.(\d+)',release.get('tag_name',''))
        return tuple(map(int,match.groups())) if match else (0,0,0)
    for release in sorted(releases,key=version,reverse=True):
        t=release.get('tag_name','')
        if release.get('draft') or not re.fullmatch(r'tv-home-viet-v\d+\.\d+\.\d+',t): continue
        if tag and t!=tag: continue
        version=t.removeprefix('tv-home-viet-v')
        assets={a.get('name'):a.get('browser_download_url','') for a in release.get('assets',[])}
        apk=f'TV-Home-Viet-{version}.apk'; sums=f'TV-Home-Viet-{version}-SHA256SUMS.txt'
        prefix=f'https://github.com/{REPO}/releases/download/{t}/'
        if assets.get(apk)!=prefix+apk or assets.get(sums)!=prefix+sums: continue
        return t,apk,assets[apk],assets[sums]
    raise ValueError('No matching complete project release found')

def verify(data,sums,name):
    matches=[line.split()[0] for line in sums.splitlines() if len(line.split())==2 and line.split()[1].lstrip('*')==name]
    if len(matches)!=1 or not re.fullmatch(r'[0-9a-fA-F]{64}',matches[0]): raise ValueError('Missing or ambiguous APK checksum')
    if hashlib.sha256(data).hexdigest()!=matches[0].lower(): raise ValueError('APK checksum mismatch; refusing install')

def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('serial',nargs='?',help='Explicit ADB device serial, e.g. TV_IP:5555')
    parser.add_argument('--tag',help='Pin a release tag')
    parser.add_argument('--check',action='store_true',help='Only show the release; do not download or install')
    args=parser.parse_args()
    if not args.check and not args.serial: parser.error('Provide SERIAL, or use --check')
    releases=json.loads(get(f'https://api.github.com/repos/{REPO}/releases?per_page=100',2_000_000))
    tag,name,url,sums_url=select(releases,args.tag)
    print('Project release:',tag,flush=True)
    if args.check:return
    data=get(url,20_000_000); verify(data,get(sums_url,65536).decode('utf-8'),name)
    folder=ROOT/'.local'/'downloads'/tag; folder.mkdir(parents=True,exist_ok=True)
    apk=folder/name; apk.write_bytes(data)
    print('SHA-256 verified. Installing with APK/config backup; existing app data is retained.',flush=True)
    subprocess.run(['bash',str(ROOT/'scripts'/'tv.sh'),args.serial,'install',str(apk)],check=True)

if __name__=='__main__':
    try: main()
    except (ValueError,OSError,subprocess.CalledProcessError) as e: raise SystemExit(str(e))
