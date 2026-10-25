"""Reproduce the whole-tree audit inventory; flags are review hints, not verdicts."""
from pathlib import Path
import csv
import hashlib
import json
import re

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / 'docs/audit-20261025'
OUT.mkdir(parents=True, exist_ok=True)
patterns = {
    'placeholder': r'\b(?:TODO|FIXME|stub|UnsupportedOperationException)\b',
    'static_collection': r'static\s+(?:final\s+)?(?:[\w.]*Map|[\w.]*Set|[\w.]*List|[\w.]*Cache)\b',
    'world_access': r'\b(?:getBlockState|getBlockEntity|getChunk|clip|pick)\(',
    'mutation': r'\b(?:destroyBlock|removeBlock|setBlock|setChanged|sendBlockUpdated)\(',
    'packet': r'\b(?:sendToServer|sendToPlayer|sendToPlayersTrackingEntity|sendToAllPlayers)\(',
    'render_buffer': r'\b(?:getBuffer|pushPose|popPose|begin|endBatch)\(',
    'source_contract': r'Files\.readString|\.contains\(',
}
rows = []
hits = []
for path in sorted((ROOT / 'src').rglob('*.java')):
    text = path.read_text(encoding='utf-8-sig')
    rel = path.relative_to(ROOT).as_posix()
    package = re.search(r'^package\s+([^;]+);', text, re.M)
    rows.append({'path': rel, 'package': package.group(1) if package else '',
                 'lines': len(text.splitlines()), 'sha256': hashlib.sha256(path.read_bytes()).hexdigest(),
                 'junit': len(re.findall(r'@Test\b', text)),
                 'gametest': len(re.findall(r'@GameTest\b', text))})
    for line_no, line in enumerate(text.splitlines(), 1):
        for label, pattern in patterns.items():
            if re.search(pattern, line):
                hits.append({'path': rel, 'line': line_no, 'review_hint': label})
with (OUT / 'source-inventory.csv').open('w', encoding='utf-8', newline='') as stream:
    writer = csv.DictWriter(stream, fieldnames=rows[0].keys())
    writer.writeheader()
    writer.writerows(rows)
with (OUT / 'review-hints.csv').open('w', encoding='utf-8', newline='') as stream:
    writer = csv.DictWriter(stream, fieldnames=['path', 'line', 'review_hint'])
    writer.writeheader()
    writer.writerows(hits)
langs = {p.stem: json.loads(p.read_text(encoding='utf-8-sig'))
         for p in (ROOT / 'src/main/resources/assets/academy/lang').glob('*.json')}
all_keys = set().union(*(set(v) for v in langs.values()))
summary = {'java_files': len(rows), 'java_lines': sum(r['lines'] for r in rows),
           'junit_annotations': sum(r['junit'] for r in rows),
           'gametest_annotations': sum(r['gametest'] for r in rows),
           'packages': sorted(set(r['package'] for r in rows)),
           'language_keys': {name: len(langs.get(name, {})) for name in ('zh_cn', 'en_us', 'ja_jp')},
           'language_missing_union': {name: sorted(all_keys - set(langs.get(name, {})))
                                      for name in ('zh_cn', 'en_us', 'ja_jp')},
           'warning': 'Static hints and annotation counts do not prove coverage or behavior.'}
(OUT / 'scan-summary.json').write_text(json.dumps(summary, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
print(json.dumps({k: v for k, v in summary.items() if k not in ('packages', 'language_missing_union')}, ensure_ascii=False))
