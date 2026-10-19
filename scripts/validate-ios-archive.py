#!/usr/bin/env python3
"""Validate a generated iPhone archive before handing it off for signing/testing."""
import json
import plistlib
import re
import subprocess
import sys
from pathlib import Path

archive = Path(sys.argv[1]).resolve()
with (archive / 'Info.plist').open('rb') as stream:
    metadata = plistlib.load(stream)
app = archive / 'Products' / metadata['ApplicationProperties']['ApplicationPath']
with (app / 'Info.plist').open('rb') as stream:
    info = plistlib.load(stream)
assert info['CFBundleSupportedPlatforms'] == ['iPhoneOS'], 'Expected a physical-device build'
assert info['CFBundleDisplayName'] == 'Rubik Buddy'
assert re.fullmatch(r'\d+(\.\d+){0,2}', info['CFBundleVersion']), 'Invalid build number'
assert info.get('CADisableMinimumFrameDurationOnPhone') is True
assert re.fullmatch(r'\d+(\.\d+){0,2}', info['CFBundleShortVersionString']), 'Invalid marketing version'
assert info.get('ITSAppUsesNonExemptEncryption') is False
assert info.get('NSCameraUsageDescription'), 'Missing camera permission explanation'
assert (app / 'Assets.car').is_file(), 'Missing icon catalog'
assert info.get('CFBundleIcons', {}).get('CFBundlePrimaryIcon'), 'Missing primary app icon'
assert (app / 'ThirdPartyNotices.txt').is_file(), 'Missing license notices'
with (app / 'PrivacyInfo.xcprivacy').open('rb') as stream:
    privacy = plistlib.load(stream)
assert privacy['NSPrivacyTracking'] is False
assert privacy['NSPrivacyCollectedDataTypes'] == []
architectures = subprocess.check_output(['xcrun', 'lipo', '-archs', str(app / info['CFBundleExecutable'])], text=True).strip()
assert architectures == 'arm64', f'Expected arm64 device executable, got {architectures}'
dsym = archive / 'dSYMs' / (app.name + '.dSYM')
assert dsym.is_dir(), 'Missing app debug symbols'
def uuids(path):
    output = subprocess.check_output(['xcrun', 'dwarfdump', '--uuid', str(path)], text=True)
    return set(re.findall(r'UUID: ([A-Fa-f0-9-]+) \(arm64\)', output))
assert uuids(app / info['CFBundleExecutable']) == uuids(dsym) != set(), 'App/dSYM UUID mismatch'
print(json.dumps({
    'archive': str(archive), 'bundle_identifier': info['CFBundleIdentifier'],
    'version': info['CFBundleShortVersionString'], 'build': info['CFBundleVersion'],
    'architectures': architectures, 'minimum_ios': info['MinimumOSVersion'],
    'matching_debug_symbols': True, 'provisioned': (app / 'embedded.mobileprovision').is_file(), 'validation': 'passed',
}, indent=2))
