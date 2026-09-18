"""Immutable local boss-replay capture and explicitly qualified build-marker attribution."""
from datetime import datetime, timezone
import hashlib
import json
from pathlib import Path, PureWindowsPath
import re
import threading

SCHEMA_VERSION = 1
IDENTITY_SCHEME = 'sha256-source-with-empty-BUILD_MARKER-v1'
EMPTY_DECLARATION = 'public static final String BUILD_MARKER = "";'
DECLARATION = re.compile(r'public static final String BUILD_MARKER = "([^"\r\n]*)";')
MARKER = re.compile(r'BTK_BUILD_V1 strategy=([a-z][a-z0-9_-]{0,39}) '
                    r'build=([0-9a-f]{64}) config=([0-9a-f]{64})(?: note=.*)?\Z')
KINDS = {'bot': 'boss', 'bot_raw': 'boss_raw', 'boss': 'boss', 'boss_raw': 'boss_raw', 'stderr': 'stderr'}
_LOCK = threading.Lock()


def sha256(content):
    return hashlib.sha256(content.encode('utf-8') if isinstance(content, str) else content).hexdigest()


def config_sha256(params):
    if not isinstance(params, dict) or not params:
        raise ValueError('Missing resolved build parameters')
    if any(not isinstance(key, str) or not re.fullmatch(r'[A-Z][A-Z0-9_]*', key) or not isinstance(value, str)
           or '\n' in value or '\r' in value for key, value in params.items()):
        raise ValueError('Invalid resolved build parameters')
    return sha256(''.join(f'{key}={params[key]}\n' for key in sorted(params)))


def validate_local_build(project_dir, build_id):
    """Verify retained source bytes and configuration; this cannot prove remote execution."""
    if not isinstance(build_id, str) or not re.fullmatch(r'[0-9a-f]{64}', build_id):
        raise ValueError('Invalid build identity')
    directory = Path(project_dir) / 'build/submission-registry' / build_id
    manifest = json.loads((directory / 'manifest.json').read_bytes())
    if not isinstance(manifest, dict):
        raise ValueError('Registered build manifest must be an object')
    source_bytes = (directory / 'Player.java').read_bytes()
    source = source_bytes.decode('utf-8')
    declarations = list(DECLARATION.finditer(source))
    if len(declarations) != 1:
        raise ValueError('Registered source has ambiguous or missing marker')
    marker = MARKER.fullmatch(declarations[0].group(1))
    if marker is None or ' note=' in marker.group(0):
        raise ValueError('Registered source marker is malformed')
    strategy, declared_build, config = marker.groups()
    normalized = DECLARATION.sub(lambda _: EMPTY_DECLARATION, source)
    if (type(manifest.get('schemaVersion')) is not int or manifest['schemaVersion'] != 2
            or manifest.get('identityScheme') != IDENTITY_SCHEME
            or manifest.get('strategyId') != strategy or declared_build != build_id
            or manifest.get('buildId') != build_id or sha256(normalized) != build_id
            or manifest.get('sha256') != sha256(source_bytes)
            or manifest.get('configSha256') != config
            or config_sha256(manifest.get('params')) != config
            or manifest.get('marker') != marker.group(0)
            or type(manifest.get('bytesUtf8')) is not int or manifest['bytesUtf8'] != len(source_bytes)
            or type(manifest.get('charactersUtf16')) is not int
            or manifest['charactersUtf16'] != len(source.encode('utf-16-le')) // 2):
        raise ValueError('Registered build identity does not match retained artifact')
    return manifest


def replay_identity(raw):
    if not isinstance(raw, dict) or not isinstance(raw.get('frames'), list) or not raw['frames']:
        raise ValueError('Canonical replay must contain frames')
    game_id = raw.get('gameId')
    if isinstance(game_id, bool) or not re.fullmatch(r'[1-9][0-9]*', str(game_id)):
        raise ValueError('Canonical replay must contain a positive gameId')
    agents = raw.get('agents')
    if (not isinstance(agents, list) or len(agents) != 2
            or any(not isinstance(agent, dict) or type(agent.get('agentId')) is not int for agent in agents)):
        raise ValueError('Expected a two-player boss replay')
    slots = [a.get('index') for a in agents if isinstance(a, dict)]
    bosses = [a.get('index') for a in agents if isinstance(a, dict)
              and (isinstance(a.get('arenaboss'), dict) or a.get('agentId') == -2)]
    if any(type(slot) is not int for slot in slots) or sorted(slots) != [0, 1] or len(bosses) != 1:
        raise ValueError('Replay must identify exactly one boss and both player slots')
    return int(game_id), 1 - bosses[0], bosses[0]


def identify_build(raw, player, project_dir):
    identity = {'status': 'unknown', 'executionIdentityIndependentlyVerified': False,
                'reason': 'No first-turn build marker', 'marker': None,
                'retainedArtifactVerified': False}
    own_lines = [frame['stdout'] for frame in raw['frames'] if isinstance(frame, dict)
                 and type(frame.get('agentId')) is int and frame['agentId'] == player
                 and isinstance(frame.get('stdout'), str)
                 and frame['stdout'].strip()]
    observed = []
    for turn, line in enumerate(own_lines):
        for command in line.strip().split(';'):
            command = command.strip()
            if command.startswith('MESSAGE BTK_BUILD_V1'):
                observed.append((turn, command[len('MESSAGE '):]))
    if not observed:
        return identity
    if len(observed) != 1 or observed[0][0] != 0:
        identity['reason'] = 'Build marker is duplicated or not on the first player output'
        return identity
    match = MARKER.fullmatch(observed[0][1])
    if match is None:
        identity['reason'] = 'Malformed first-turn build marker'
        return identity
    strategy, build_id, config = match.groups()
    text = f'BTK_BUILD_V1 strategy={strategy} build={build_id} config={config}'
    identity.update(status='unverified-marker', marker=text, strategyId=strategy,
                    buildId=build_id, configSha256=config,
                    reason='Self-reported marker has no matching verified local artifact')
    try:
        manifest = validate_local_build(project_dir, build_id)
        if manifest['marker'] != text:
            raise ValueError('Replay marker differs from retained artifact marker')
    except (OSError, ValueError, TypeError, KeyError):
        return identity
    identity.update(status='marker-matches-local-artifact', retainedArtifactVerified=True,
                    artifactSha256=manifest['sha256'],
                    reason='Self-reported replay marker matches verified locally retained source/configuration; '
                           'remote executed-source identity is not independently proven')
    return identity


def _immutable(path, content):
    path.parent.mkdir(parents=True, exist_ok=True)
    payload = content.encode('utf-8')
    if path.exists():
        if path.read_bytes() != payload:
            raise ValueError('Refusing to overwrite a different existing capture')
        return False
    with path.open('xb') as stream:
        stream.write(payload)
    return True


def _filename(value, fallback):
    filename = PureWindowsPath(str(value or fallback)).name
    if (not re.fullmatch(r'[A-Za-z0-9][A-Za-z0-9._-]{0,179}', filename)
            or filename.endswith('.')
            or filename.split('.')[0].upper() in {'CON', 'PRN', 'AUX', 'NUL',
                                                   *(f'COM{i}' for i in range(1, 10)),
                                                   *(f'LPT{i}' for i in range(1, 10))}):
        raise ValueError('Unsupported capture filename')
    return filename


def _check_metadata(metadata, project, parent, game_id, player, boss, raw_hash, identity):
    """Validate historical evidence without trusting it to establish current attribution."""
    keys = {'schemaVersion', 'gameId', 'player', 'boss', 'capturedAt', 'path', 'rawSha256', 'attribution'}
    if not isinstance(metadata, dict) or set(metadata) != keys:
        raise ValueError('Invalid existing capture metadata schema')
    for key, expected in (('schemaVersion', SCHEMA_VERSION), ('gameId', game_id), ('player', player), ('boss', boss)):
        if type(metadata[key]) is not int or metadata[key] != expected:
            raise ValueError('Existing capture metadata identity differs from replay')
    if not isinstance(metadata['capturedAt'], str):
        raise ValueError('Invalid capture timestamp')
    try:
        if datetime.fromisoformat(metadata['capturedAt']).utcoffset() is None:
            raise ValueError('Capture timestamp must have an offset')
    except (ValueError, OverflowError):
        raise ValueError('Invalid capture timestamp') from None
    relative = metadata['path']
    if not isinstance(relative, str) or '\\' in relative or ':' in relative:
        raise ValueError('Invalid existing capture path')
    existing = (project / relative).resolve()
    if (Path(relative).is_absolute() or '..' in Path(relative).parts
            or not existing.is_relative_to(parent.resolve()) or existing.name != f'{game_id}.json'
            or any(part in ('raw', '_metadata') for part in existing.relative_to(parent.resolve()).parts)):
        raise ValueError('Existing capture path escapes canonical boss replays')
    if (metadata['rawSha256'] != raw_hash or not existing.is_file()
            or sha256(existing.read_bytes()) != raw_hash):
        raise ValueError('Existing game capture conflicts with incoming replay')
    recorded = metadata['attribution']
    if not isinstance(recorded, dict) or recorded.get('executionIdentityIndependentlyVerified') is not False:
        raise ValueError('Invalid existing capture attribution')
    for key in ('marker', 'strategyId', 'buildId', 'configSha256'):
        if recorded.get(key) != identity.get(key):
            raise ValueError('Existing capture attribution differs from replay marker')
    status = recorded.get('status')
    if (status not in ('unknown', 'unverified-marker', 'marker-matches-local-artifact')
            or recorded.get('retainedArtifactVerified') is not (status == 'marker-matches-local-artifact')
            or (status == 'unknown') != (recorded.get('marker') is None)):
        raise ValueError('Invalid existing capture attribution status')
    if recorded['retainedArtifactVerified']:
        digest = recorded.get('artifactSha256')
        if not isinstance(digest, str) or not re.fullmatch(r'[0-9a-f]{64}', digest):
            raise ValueError('Invalid recorded artifact hash')
        if identity['retainedArtifactVerified'] and digest != identity['artifactSha256']:
            raise ValueError('Recorded artifact hash differs from retained source')


def save_capture(data, project_dir):
    """Save once, returning paths and qualified attribution. posted labels never assign a build."""
    if not isinstance(data, dict) or not isinstance(data.get('content'), str):
        raise ValueError('Capture content must be a string')
    requested_kind = data.get('kind', 'stderr')
    kind = KINDS.get(requested_kind) if isinstance(requested_kind, str) else None
    if kind is None:
        raise ValueError('Unsupported capture kind')
    project = Path(project_dir).resolve()
    content = data['content']
    with _LOCK:
        if kind != 'boss':
            directory = project / ('logs' if kind == 'stderr' else 'replays/cg/boss/raw/unknown')
            fallback = ('stderr_' if kind == 'stderr' else 'testsession_') + sha256(content) + ('.txt' if kind == 'stderr' else '.json')
            path = directory / _filename(data.get('filename'), fallback)
            created = _immutable(path, content)
            return {'ok': True, 'kind': kind, 'created': created, 'path': path.relative_to(project).as_posix()}
        raw = json.loads(content)
        game_id, player, boss = replay_identity(raw)
        identity = identify_build(raw, player, project)
        parent = project / 'replays/cg/boss'
        relative_group = Path(identity['strategyId']) / identity['buildId'] if identity['retainedArtifactVerified'] else Path('unknown')
        path = parent / relative_group / f'{game_id}.json'
        metadata_path = parent / '_metadata' / f'{game_id}.json'
        raw_hash = sha256(content)
        # Global game identity prevents duplicate copies during retries and preserves legacy captures.
        if metadata_path.exists():
            metadata = json.loads(metadata_path.read_bytes())
            _check_metadata(metadata, project, parent, game_id, player, boss, raw_hash, identity)
            return {'ok': True, 'kind': kind, 'created': False, **metadata,
                    'attribution': identity,
                    'metadataPath': metadata_path.relative_to(project).as_posix(),
                    'expectedBuildMatches': _expected(data, identity)}
        legacy = [p for p in parent.rglob(f'{game_id}.json')
                  if not any(part in ('raw', '_metadata') for part in p.relative_to(parent).parts)] if parent.exists() else []
        if legacy:
            if len(legacy) != 1 or legacy[0].read_bytes() != content.encode('utf-8'):
                raise ValueError('Existing game capture conflicts with incoming replay')
            path = legacy[0]
        created = _immutable(path, content)
        metadata = {'schemaVersion': SCHEMA_VERSION, 'gameId': game_id, 'player': player, 'boss': boss,
                    'capturedAt': datetime.now(timezone.utc).isoformat(),
                    'path': path.relative_to(project).as_posix(), 'rawSha256': raw_hash,
                    'attribution': identity}
        _immutable(metadata_path, json.dumps(metadata, ensure_ascii=False, indent=2))
        return {'ok': True, 'kind': kind, 'created': created, **metadata,
                'metadataPath': metadata_path.relative_to(project).as_posix(),
                'expectedBuildMatches': _expected(data, identity)}


def _expected(data, identity):
    expected = data.get('expectedBuildId')
    return None if expected is None else identity['retainedArtifactVerified'] and identity.get('buildId') == expected
