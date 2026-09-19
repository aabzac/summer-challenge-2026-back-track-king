"""Prove that seven unused Params additions are the only cached-v07 source difference."""
import argparse
import hashlib
import json
from pathlib import Path
import re

FIELDS = {
    'USE_ROLLOUT_RHEA': ('boolean', 'false'),
    'ROLLOUT_RHEA_MUTATIONS': ('int', '12'),
    'ROLLOUT_RHEA_ELITES': ('int', '2'),
    'USE_PROTECTED_OPENING': ('boolean', 'false'),
    'OPENING_TURNS': ('int', '3'),
    'OPENING_CITY_QUOTA': ('int', '1'),
    'OPENING_COLLISION_FACTOR': ('double', '0.25'),
}
MARKER = re.compile(r'public static final String BUILD_MARKER = "[^"\r\n]*";')


def require(condition, message):
    if not condition:
        raise ValueError(message)


def digest(data):
    return hashlib.sha256(data).hexdigest()


def normalize(source):
    require(len(MARKER.findall(source)) == 1, 'Expected exactly one marker')
    return MARKER.sub('public static final String BUILD_MARKER = "";', source)


def reconstruct(current, cached, current_params, cached_params):
    expected = {key: value for key, (_, value) in FIELDS.items()}
    require(set(current_params) - set(cached_params) == set(FIELDS), 'Unexpected added parameter set')
    require(all(current_params.get(k) == v for k, v in cached_params.items()), 'Cached parameter changed')
    require(all(current_params.get(k) == v for k, v in expected.items()), 'Inactive default changed')
    source = normalize(current)
    for name, (kind, value) in FIELDS.items():
        # Exactly declaration and one embedded configuration entry: no strategy/runtime reads.
        require(len(re.findall(r'\b' + re.escape(name) + r'\b', source)) == 2,
                'New field is referenced outside declaration/configuration: ' + name)
        declaration = f'public {kind} {name} = {value};\n'
        require(source.count(declaration) == 1, 'Changed field declaration: ' + name)
        source = source.replace(declaration, '', 1)
    params = re.compile(r'new Params\("([^"\r\n]*)"\)')
    matches = list(params.finditer(source))
    require(len(matches) == 1, 'Expected one embedded complete configuration')
    match = matches[0]
    entries = match.group(1).split(',')
    require(len(entries) == len(current_params), 'Incomplete embedded configuration')
    require(dict(entry.split('=', 1) for entry in entries) == current_params, 'Embedded config differs')
    kept = [entry for entry in entries if entry.split('=', 1)[0] not in FIELDS]
    source = source[:match.start(1)] + ','.join(kept) + source[match.end(1):]
    require(source == normalize(cached), 'Other shipped source changed; cached control cannot be reused')
    return digest(source.encode('utf-8'))


def certificate(current_dir, cached_dir):
    files = {}
    values = []
    for directory in (current_dir, cached_dir):
        source = directory / 'Player.java'
        manifest = directory / 'manifest.json'
        raw = source.read_bytes()
        data = json.loads(manifest.read_bytes())
        require(data['strategy'] == 'v07', 'Only selected frozen v07 is supported')
        require(data['sha256'] == digest(raw), 'Artifact source hash mismatch')
        for path in (source, manifest):
            files[str(path.resolve())] = digest(path.read_bytes())
        values.append((raw.decode('utf-8'), data))
    current, cached = values
    reconstructed = reconstruct(current[0], cached[0], current[1]['params'], cached[1]['params'])
    return {
        'schemaVersion': 1, 'method': 'exact-source-removal-of-seven-unused-params-v1',
        'spec': 'v07', 'files': files,
        'currentDirectory': str(current_dir.resolve()), 'cachedDirectory': str(cached_dir.resolve()),
        'addedUnusedFields': {key: value for key, (_, value) in FIELDS.items()},
        'currentParams': current[1]['params'], 'cachedParams': cached[1]['params'],
        'currentBuildId': current[1]['buildId'], 'cachedBuildId': cached[1]['buildId'],
        'currentSourceSha256': current[1]['sha256'], 'cachedSourceSha256': cached[1]['sha256'],
        'reconstructedNormalizedSha256': reconstructed,
        'cachedNormalizedSha256': digest(normalize(cached[0]).encode('utf-8')),
        'equivalentShippedSourceExceptUnusedFields': True,
        'limitation': 'Different artifact bytes and reflective Params-copy work; this proves identical remaining shipped source and historical parameter values, not identical host timing or a new process pass. Original reports and cached artifacts remain unchanged.'
    }


def verify_certificate(path):
    saved = json.loads(path.read_bytes())
    fresh = certificate(Path(saved['currentDirectory']), Path(saved['cachedDirectory']))
    require(saved == fresh, 'Compatibility certificate no longer matches source/artifacts')
    return fresh


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--current', required=True, type=Path)
    parser.add_argument('--cached', required=True, type=Path)
    parser.add_argument('--out', required=True, type=Path)
    args = parser.parse_args()
    result = certificate(args.current, args.cached)
    args.out.parent.mkdir(parents=True, exist_ok=True)
    with args.out.open('x', encoding='utf-8') as stream:
        json.dump(result, stream, indent=2)
        stream.write('\n')
    print('Verified identical remaining shipped v07 source:', result['reconstructedNormalizedSha256'])


if __name__ == '__main__':
    main()
