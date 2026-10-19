#!/usr/bin/env python3
"""Check local submission metadata and assets; does not contact App Store Connect."""
import json
import plistlib
import re
import struct
from pathlib import Path

root = Path(__file__).resolve().parents[1]
metadata = root / 'app-store/metadata/en-US'
app = json.loads((root / 'app-store/app.json').read_text())
for name, limit in {'name': 30, 'subtitle': 30, 'promotional_text': 170, 'keywords': 100, 'description': 4000}.items():
    value = (metadata / f'{name}.txt').read_text().strip()
    assert value and len(value) <= limit, f'{name}: {len(value)}/{limit} characters'
    assert not re.search(r'\b(TODO|TBD|YOURTEAMID)\b', value), f'Placeholder in {name}'
    print(f'{name}: {len(value)}/{limit} characters')
for name in ('privacy_url', 'support_url', 'marketing_url'):
    assert (metadata / f'{name}.txt').read_text().strip().startswith('https://'), f'{name}: HTTPS required'
project = (root / 'iosApp/iosApp.xcodeproj/project.pbxproj').read_text()
assert set(re.findall(r'MARKETING_VERSION = ([^;]+);', project)) == {app['version']}, 'Metadata/project version mismatch'
assert set(re.findall(r'PRODUCT_BUNDLE_IDENTIFIER = ([^;]+);', project)) == {app['bundle_id']}, 'Bundle identifier mismatch'
with (root / 'iosApp/ExportOptions-AppStore.plist').open('rb') as stream:
    export = plistlib.load(stream)
assert export['method'] == 'app-store-connect'
assert export['destination'] == 'export', 'Preparation must not upload automatically'
assert export['manageAppVersionAndBuildNumber'] is False
with (root / 'iosApp/iosApp/Info.plist').open('rb') as stream:
    info = plistlib.load(stream)
assert info['ITSAppUsesNonExemptEncryption'] is False
with (root / 'iosApp/iosApp/PrivacyInfo.xcprivacy').open('rb') as stream:
    privacy = plistlib.load(stream)
assert privacy['NSPrivacyTracking'] is False and privacy['NSPrivacyCollectedDataTypes'] == []

notices = (root / 'iosApp/iosApp/ThirdPartyNotices.txt').read_text()
assert notices == (root / 'NOTICE').read_text(), 'Bundled notices must match NOTICE'
for component in ('Apache License', 'Skiko NOTICE', 'Skia, revision', 'harfbuzz', 'icu', 'libjpeg-turbo'):
    assert component in notices, f'Missing runtime notice: {component}'

def png_header(path):
    data = path.read_bytes()
    assert data[:8] == b'\x89PNG\r\n\x1a\n', f'Not a PNG: {path}'
    width, height, depth, color = struct.unpack('>IIBB', data[16:26])
    assert color == 2, f'Expected RGB without an alpha channel: {path}'
    offset = 8
    while offset + 12 <= len(data):
        length = struct.unpack('>I', data[offset:offset + 4])[0]
        assert data[offset + 4:offset + 8] != b'tRNS', f'Transparency is not permitted: {path}'
        offset += length + 12
    return width, height, color

icon = root / 'iosApp/iosApp/Assets.xcassets/AppIcon.appiconset/AppIcon.png'
assert png_header(icon) == (1024, 1024, 2), 'Expected opaque RGB 1024px icon'
def screenshot_size(path):
    if path.suffix == '.png':
        width, height, _ = png_header(path)
        return width, height
    # JPEG cannot carry an alpha channel. Read its native frame dimensions without
    # requiring Pillow in CI or decoding/re-encoding the screenshot.
    data = path.read_bytes()
    assert data[:2] == b'\xff\xd8', f'Not a JPEG: {path}'
    offset = 2
    while offset + 4 <= len(data):
        assert data[offset] == 0xFF, f'Invalid JPEG marker: {path}'
        while data[offset] == 0xFF:
            offset += 1
        marker = data[offset]
        offset += 1
        if marker in (0xD8, 0xD9) or 0xD0 <= marker <= 0xD7:
            continue
        length = struct.unpack('>H', data[offset:offset + 2])[0]
        if marker in (0xC0, 0xC1, 0xC2):
            height, width = struct.unpack('>HH', data[offset + 3:offset + 7])
            return width, height
        assert marker != 0xDA and length >= 2, f'Missing JPEG frame: {path}'
        offset += length
    raise AssertionError(f'Missing JPEG dimensions: {path}')

# Native captures only; no resizing Android captures or disguising sample camera UI.
sizes = {'iphone': {(1260, 2736), (1290, 2796), (1320, 2868)}, 'ipad': {(2064, 2752), (2048, 2732)}}
for family, allowed in sizes.items():
    shots = sorted(p for p in (root / 'app-store/screenshots' / family).glob('*') if p.suffix in ('.png', '.jpg', '.jpeg'))
    assert 1 <= len(shots) <= 10, f'Expected 1–10 {family} screenshots, found {len(shots)}'
    for shot in shots:
        width, height = screenshot_size(shot)
        assert (width, height) in allowed, f'Unexpected screenshot: {shot} ({width}x{height})'
    print(f'{family}: {len(shots)} native-size screenshots')
print('Local App Store preparation checks passed. Signing, Apple validation, questionnaires, and physical camera tests remain external gates.')
