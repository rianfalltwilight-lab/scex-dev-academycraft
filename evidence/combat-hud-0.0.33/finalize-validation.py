import argparse
import hashlib
import json
from pathlib import Path
import zipfile

parser = argparse.ArgumentParser()
parser.add_argument('--tested-dir', required=True)
parser.add_argument('--server-tested-dir', required=True)
parser.add_argument('--expected-tests', required=True, type=int)
parser.add_argument('--expected-client-checks', required=True, type=int)
args = parser.parse_args()
root = Path(__file__).resolve().parents[2]
out = Path(__file__).resolve().parent
version = '0.0.33-scex-combat-hud'
stem = 'AcademyCraft-neoforge-1.21.1-' + version
def read(path):
    return json.loads(path.read_text(encoding='utf-8-sig'))
def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()
def entries(path):
    with zipfile.ZipFile(path) as archive:
        return {n: hashlib.sha256(archive.read(n)).hexdigest() for n in archive.namelist() if not n.endswith('/')}

junit = read(out / 'junit-result.json')
server = read(out / 'gametests-final/result.json')
client = read(out / 'client-final/result.json')
visual = read(out / 'client-final/visual-review.json')
assert junit['tests'] >= 342 and all(junit[k] == 0 for k in ['failures', 'errors', 'skipped'])
assert server['passed'] and server['tests'] == args.expected_tests and server['failures'] == 0
assert server['exit_code'] == 0 and not server['forced'] and server['process_exited']
assert client['passed'] and client['exit_code'] == 0 and not client['forced']
assert client['process_exited'] and client['saved_after_stop']
cases = ['visible', 'f1-hidden', 'f1-restored', 'cancel-cp', 'cancel-hotbar', 'hud-disabled', 'individual-disabled', 'screen-open', 'screen-closed']
expected_images = {f'screenshots/hud-layers-scale{scale}-{case}.png' for scale in [2, 3] for case in cases}
assert visual['status'] == 'PASS' and len(visual['images']) == 18
assert {item['file'] for item in visual['images']} == expected_images
for item in visual['images']:
    assert sha(out / 'client-final' / item['file']) == item['sha256']
client_text = (out / 'client-final/academy-hud-layers-client.txt').read_text(encoding='utf-8-sig')
assert client_text.startswith('status=PASS')
checks = [line for line in client_text.splitlines() if line.startswith('PASS ')]
assert len(checks) == args.expected_client_checks
save_checkpoint = (out / 'client-final/academy-hud-layers-save-checkpoint.txt').read_text(encoding='utf-8-sig')
assert 'nativeSaveEverythingCompleted=true flush=true' in save_checkpoint

jar = root / 'build/libs' / (stem + '.jar')
probe = root / 'build/libs' / (stem + '-audit-tests.jar')
source_zip = root / 'build/distributions' / (stem + '-sources.zip')
info_bytes = (root / 'BUILD-INFO.txt').read_bytes()
info = dict(line.split('=', 1) for line in info_bytes.decode('utf-8-sig').splitlines() if '=' in line)
assert info['Mod-Version'] == version
with zipfile.ZipFile(jar) as archive:
    assert archive.read('BUILD-INFO.txt') == info_bytes
with zipfile.ZipFile(source_zip) as archive:
    assert archive.read('BUILD-INFO.txt') == info_bytes
    names = sorted(name for name in archive.namelist() if name != 'BUILD-INFO.txt')
    digest = hashlib.sha256()
    for name in names:
        blob = archive.read(name)
        assert blob == (root / name).read_bytes(), name
        digest.update((hashlib.sha256(blob).hexdigest() + '  ' + name + '\n').encode())
    assert digest.hexdigest() == info['Source-Tree-SHA256']
    assert len(names) == int(info['Source-File-Count'])

comparisons = []
for candidate in [jar, probe]:
    reference = root / args.tested_dir / candidate.name
    before, after = entries(reference), entries(candidate)
    changed = sorted(n for n in set(before) | set(after) if before.get(n) != after.get(n))
    allowed = {'BUILD-INFO.txt', 'META-INF/MANIFEST.MF', 'README.md'} if candidate == jar else set()
    assert set(changed) <= allowed, changed
    comparisons.append({'tested': str(reference), 'tested_sha256': sha(reference), 'final': str(candidate),
                        'final_sha256': sha(candidate), 'changed_entries': changed, 'runtime_entries_identical': True})
    server_reference = root / args.server_tested_dir / candidate.name
    server_before = entries(server_reference)
    client_delta = sorted(n for n in set(server_before) | set(before) if server_before.get(n) != before.get(n))
    if candidate == jar:
        client_only = allowed | {
            'com/mohistmc/academy/client/gui/CPBarOverlay.class',
            'com/mohistmc/academy/client/gui/CPBarOverlay$HudPosition.class',
            'com/mohistmc/academy/client/gui/NotifyOverlay.class',
        }
        assert set(client_delta) <= client_only, client_delta
    else:
        client_probe_only = {'com/mohistmc/academy/regression/HudLayersClientGate' + suffix + '.class'
                             for suffix in ['', '$DrawCount', '$Registration', '$TrackingGraphics']}
        assert set(client_delta) <= client_probe_only, client_delta
    comparisons[-1].update(server_tested=str(server_reference), server_tested_sha256=sha(server_reference),
                           server_common_entries_identical=True, server_changed_client_only_entries=client_delta)
    for evidence, tested in [('gametests-final', server_reference), ('client-final', reference)]:
        identity = next(x for x in read(out / evidence / 'artifacts.json') if x['name'] == candidate.name)
        assert identity['sha256'].lower() == sha(tested)

measurements = [line for line in (out / 'gametests-final/latest.log').read_text(encoding='utf-8-sig').splitlines()
                if 'NATIVE_COMBAT_' in line or 'SKILL_DAMAGE_' in line or '[academy_combat:' in line]
result = {'status': 'PASS', 'version': version, 'baseline_commit': '3423b53e0e65b47f7d47bdc30b6ec71d4cb63995',
          'source_tree_sha256': info['Source-Tree-SHA256'], 'source_file_count': int(info['Source-File-Count']),
          'junit': junit, 'gametests': server, 'client': client, 'client_behavior_checks': checks,
          'measurements': measurements, 'native_save_checkpoint': save_checkpoint, 'visual_review': visual,
          'artifact_entry_comparisons': comparisons,
          'artifacts': [{'path': str(p), 'bytes': p.stat().st_size, 'sha256': sha(p)} for p in [jar, probe, source_zip]],
          'limits': ['NeoForge 21.1.248 / Java 21 isolated runtime; not a complete third-party pack matrix.',
                     'Server GameTests used r5; final client tested r7. Exact JAR entry comparison proves changes after server validation are limited to the listed client HUD and client probe classes plus metadata.',
                     'Client input is programmatically driven through native keyboard and layer dispatch; not physical keyboard or human usability testing.',
                     'The runner visual_review=pending field is resolved by the separate image-hash-bound visual-review.json.',
                     'Combat checks cover the specified vanilla fixtures, not every third-party event handler.',
                     'Known pre-existing layout boundary: scale2 first frame shows temporary selected-item-name/charging-hint text overlap; current charging coordinates unchanged. Notification displacement requires sufficient space.',
                     'Private GitHub archival release only; no production deployment.']}
(out / 'final-validation.json').write_text(json.dumps(result, ensure_ascii=False, indent=2)+'\n', encoding='utf-8')
print(json.dumps({'status': result['status'], 'source_tree_sha256': result['source_tree_sha256'],
                  'source_file_count': result['source_file_count'], 'junit': junit['tests'],
                  'gametests': server['tests'], 'client_checks': len(checks)}))
