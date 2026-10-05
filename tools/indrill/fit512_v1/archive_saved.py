"""Package the completed fit512 audit with bounded directory canonicalization.

This post-training operational amendment imports no learner and changes no frozen
module. It preserves the original archive membership, bytes, order and tar/gzip
layout. Run only after the saved-state audit has published PASS.
"""
from dataclasses import dataclass
import gzip
import hashlib
import io
import json
import os
from pathlib import Path
import stat
import tarfile

ROOT = Path(__file__).resolve().parents[3]
EXPERIMENT = 'indrill-fit512-v1'
AUDIT_SOURCE = 'tools/indrill/fit512_v1/audit_result.py'
AUDIT_SHA256 = 'c2d726415cec985a880193fc532ff87b615c9f68701eacac6795301813991e4d'
METRIC_STEPS = (0, 3000, 12000, 24000, 48000)
PRIORS = ('indrill-capacity-v1', 'indrill-fit-one-v1', 'indrill-build64-v1',
          'indrill-build64-long-v1', 'indrill-build64-12k-v1',
          'indrill-joint64-12k-v1', 'indrill-generalization-v1')


def need(condition, message):
    if not condition:
        raise ValueError(message)


def key(path):
    return os.path.normcase(os.fspath(path))


def identity(value):
    # Directory size/mtime legitimately change when the write-once outputs appear.
    return (value.st_dev, value.st_ino, value.st_mode,
            getattr(value, 'st_file_attributes', 0), getattr(value, 'st_reparse_tag', 0))


def opened_identity(value):
    # Windows path stat adds executable bits from an .exe filename; fstat of the
    # same handle does not. Compare object identity/type across those APIs, while
    # identity() still compares the complete mode for repeated lstat calls.
    return (value.st_dev, value.st_ino, stat.S_IFMT(value.st_mode),
            getattr(value, 'st_file_attributes', 0), getattr(value, 'st_reparse_tag', 0))


def link(value):
    return stat.S_ISLNK(value.st_mode) or bool(getattr(value, 'st_file_attributes', 0) & 0x400)


@dataclass(frozen=True)
class Directory:
    lexical: Path
    canonical: Path
    stamp: tuple
    has_link: bool


@dataclass(frozen=True)
class File:
    lexical: Path
    canonical: Path
    leaf_stamp: tuple
    target_stamp: tuple
    has_link: bool


class Files:
    """Resolve each ancestor once, but still read and hash every required input.

    Every read checks the leaf identity and the opened file identity. Ancestors
    are revalidated at phase boundaries, including their resolved destination.
    Links/reparse points in archive paths are rejected, not hidden by resolve().
    """
    def __init__(self, root):
        self.root = Path(root).resolve(strict=True)
        self.directories, self.files = {}, {}

    def lexical(self, value):
        path = Path(value)
        if not path.is_absolute():
            path = self.root / path
        return Path(os.path.abspath(path))

    def directory(self, value):
        path = self.lexical(value)
        cache_key = key(path)
        if cache_key in self.directories:
            return self.directories[cache_key]
        parent = None if path.parent == path else self.directory(path.parent)
        stamp = path.lstat()
        canonical = path.resolve(strict=True)
        need(canonical.is_dir(), 'Non-directory ancestor')
        entry = Directory(path, canonical, identity(stamp),
                          link(stamp) or (parent is not None and parent.has_link))
        self.directories[cache_key] = entry
        return entry

    def file(self, value):
        path = self.lexical(value)
        cache_key = key(path)
        if cache_key in self.files:
            return self.files[cache_key]
        parent = self.directory(path.parent)
        stamp = path.lstat()
        canonical = path.resolve(strict=True) if link(stamp) else parent.canonical / path.name
        target = canonical.stat() if link(stamp) else stamp
        need(stat.S_ISREG(target.st_mode), 'Input is not a regular file')
        entry = File(path, canonical, identity(stamp), opened_identity(target), link(stamp) or parent.has_link)
        self.files[cache_key] = entry
        return entry

    def name(self, value):
        path = self.file(value).canonical
        return path.relative_to(self.root).as_posix() if path.is_relative_to(self.root) else str(path)

    def read(self, value, expected=None, contents=False):
        entry = self.file(value)
        need(identity(entry.lexical.lstat()) == entry.leaf_stamp, 'Input path identity changed')
        digest, size, parts = hashlib.sha256(), 0, []
        with entry.canonical.open('rb') as stream:
            need(opened_identity(os.fstat(stream.fileno())) == entry.target_stamp, 'Opened input identity changed')
            while block := stream.read(1 << 20):
                digest.update(block)
                size += len(block)
                if contents:
                    parts.append(block)
        observed = digest.hexdigest()
        if expected is not None:
            need(observed == expected, 'Pinned input bytes changed: ' + self.name(value))
        return observed, size, b''.join(parts) if contents else None

    def pin(self, value):
        digest, _, _ = self.read(value)
        return {'path': self.name(value), 'sha256': digest}

    def json(self, value):
        return json.loads(self.read(value, contents=True)[2].decode('utf-8'))

    def validate_directories(self):
        for entry in tuple(self.directories.values()):
            need(identity(entry.lexical.lstat()) == entry.stamp
                 and entry.lexical.resolve(strict=True) == entry.canonical,
                 'Cached ancestor identity/destination changed')

    def output(self, value):
        path = self.lexical(value)
        parent = self.directory(path.parent)
        need(not parent.has_link and parent.canonical.is_relative_to(self.root), 'Linked/external output ancestor')
        target = parent.canonical / path.name
        need(not os.path.lexists(target), 'Preserve existing output: ' + path.name)
        return target


def metadata_inventory(files, folder):
    found = set()
    for directory, subdirs, names in os.walk(folder, followlinks=False):
        need(not files.directory(directory).has_link, 'Linked production directory')
        for name in subdirs:
            need(not files.directory(Path(directory) / name).has_link, 'Linked production subdirectory')
        for name in names:
            if name.endswith('.json'):
                entry = files.file(Path(directory) / name)
                need(not entry.has_link, 'Linked production metadata')
                found.add(key(entry.canonical))
    return found


class HashingReader:
    """Hash compressed bytes during the one complete tar+gzip verification pass."""
    def __init__(self, raw):
        self.raw, self.digest, self.size = raw, hashlib.sha256(), 0

    def read(self, size=-1):
        block = self.raw.read(size)
        self.digest.update(block)
        self.size += len(block)
        return block


def verify_archive(path, rows):
    expected, observed = {item['path']: item for item in rows}, set()
    with Path(path).open('rb') as raw:
        hashed = HashingReader(raw)
        with gzip.GzipFile(fileobj=hashed, mode='rb') as compressed:
            with tarfile.open(fileobj=compressed, mode='r|') as packed:
                for member in packed:
                    need(member.isfile() and member.name in expected and member.name not in observed,
                         'Unexpected/repeated/non-file tar member')
                    item = expected[member.name]
                    stream = packed.extractfile(member)
                    need(stream is not None and member.size == item['bytes'], 'Archived member size differs')
                    digest, size = hashlib.sha256(), 0
                    with stream:
                        while block := stream.read(1 << 20):
                            digest.update(block)
                            size += len(block)
                    need(size == item['bytes'] and digest.hexdigest() == item['sha256'], 'Archived member hash differs')
                    observed.add(member.name)
            # Tar stops at its padding; continue through every gzip trailer/CRC.
            while compressed.read(1 << 20):
                pass
        need(observed == set(expected), 'Archive inventory is incomplete')
        need(hashed.size == os.fstat(raw.fileno()).st_size, 'Compressed bytes were not read completely')
        return hashed.digest.hexdigest(), hashed.size


def package(root=ROOT):
    files = Files(root)
    out = files.root / 'build/analysis/indrill-fit512-v1'
    final_path = files.root / 'docs/evidence/indrill-fit512-v1.json'
    chain_path = out / 'chain-manifest.json'
    archive_path = files.output('docs/evidence/indrill-fit512-v1-artifacts.tar.gz')
    index_path = files.output('docs/evidence/indrill-fit512-v1-artifacts.json')
    packager = files.pin(__file__)
    audit_source = files.pin(AUDIT_SOURCE)
    need(audit_source == {'path': AUDIT_SOURCE, 'sha256': AUDIT_SHA256}, 'Frozen audit source differs')
    final, chain = files.json(final_path), files.json(chain_path)
    final_pin, chain_pin = files.pin(final_path), files.pin(chain_path)
    need(final['experiment'] == EXPERIMENT and final['complete'] is True and final['auditStatus'] == 'PASS'
         and chain['complete'] is True and chain['auditStatus'] == 'PASS' and final['checkpointChain'] == chain_pin
         and final['freeze'] == chain['freeze'] and chain['helper'] == audit_source,
         'Completed audit/source binding differs')
    verified = {}
    for item in chain['auditedPins']:
        need(set(item) == {'path', 'sha256'}, 'Invalid audited pin')
        entry = files.file(item['path'])
        canonical = key(entry.canonical)
        need(canonical not in verified or verified[canonical] == item['sha256'], 'Conflicting audited pin/alias')
        files.read(item['path'], item['sha256'])
        verified[canonical] = item['sha256']
    metadata = metadata_inventory(files, out / 'run')
    need(metadata == {key(files.file(item['path']).canonical) for item in chain['productionMetadataPins']},
         'Production metadata inventory changed')
    rows, seen = [], set()
    for item in chain['archiveInputs'] + [chain_pin, final_pin]:
        need(set(item) == {'path', 'sha256'}, 'Invalid archive pin')
        entry = files.file(item['path'])
        path, canonical = entry.canonical, key(entry.canonical)
        need(path.is_relative_to(files.root) and canonical not in seen, 'External/duplicate archive input')
        seen.add(canonical)
        relative = path.relative_to(files.root).as_posix()
        need(item['path'] == relative and not entry.has_link and not relative.startswith('replays/')
             and not any(part.lower() in ('private', 'sessions', 'session') for part in path.parts),
             'Noncanonical/linked/private/raw archive input')
        need(path.suffix in ('.py', '.md', '.json', '.npz'), 'Raw corpus/runtime binary or unexpected archive type')
        if path not in (chain_path, final_path):
            need(verified.get(canonical) == item['sha256'], 'Archive input has no completed byte audit')
        _, size, _ = files.read(item['path'], item['sha256'])
        rows.append({**item, 'bytes': size})
    need({item['path'] for item in rows if item['path'].endswith('.npz')} ==
         {f'build/analysis/indrill-fit512-v1/run/checkpoints/step-{step:05d}.npz' for step in METRIC_STEPS},
         'Archive must retain exactly five scheduled full states')
    priors = []
    for name in PRIORS:
        previous_path = files.root / f'docs/evidence/{name}-artifacts.json'
        previous = files.json(previous_path)
        path = files.file(previous['archive']).canonical
        need(previous['complete'] is True and previous['experiment'] == name and path.is_relative_to(files.root),
             'Prior archive/index differs')
        digest, size, _ = files.read(path, previous['sha256'])
        need(size == previous['bytes'], 'Prior archive size differs')
        priors.append({'archive': {'path': files.name(path), 'sha256': digest}, 'index': files.pin(previous_path)})
    rows.sort(key=lambda item: item['path'])
    files.validate_directories()
    with archive_path.open('xb') as raw:
        with gzip.GzipFile(filename='', mode='wb', fileobj=raw, compresslevel=6, mtime=0) as compressed:
            with tarfile.open(fileobj=compressed, mode='w|', format=tarfile.PAX_FORMAT) as packed:
                for item in rows:
                    _, size, content = files.read(item['path'], item['sha256'], contents=True)
                    need(size == item['bytes'], 'Snapshot bytes differ from completed audit')
                    info = tarfile.TarInfo(item['path'])
                    info.size, info.mtime, info.mode = len(content), 0, 0o644
                    packed.addfile(info, io.BytesIO(content))
    archive_sha, archive_size = verify_archive(archive_path, rows)
    files.validate_directories()
    need(files.pin(__file__) == packager and files.pin(AUDIT_SOURCE) == audit_source
         and files.pin(final_path) == final_pin and files.pin(chain_path) == chain_pin,
         'Source/final evidence changed during packaging')
    index = {'schemaVersion': 1, 'experiment': EXPERIMENT, 'complete': True,
        'format': 'tar.gz', 'finalEvidence': final_pin, 'checkpointChain': chain_pin,
        'archive': archive_path.relative_to(files.root).as_posix(), 'sha256': archive_sha, 'bytes': archive_size,
        'files': rows, 'priorArchives': priors, 'auditedInputPinsVerified': len(verified),
        'productionMetadataFilesVerified': len(metadata), 'allTarEntryBytesVerified': True,
        'scope': 'Frozen sources/protocol/proof/selection, all production metadata and saved metrics, full checkpoint hash chain and five scheduled full NPZ states.',
        'retainedOutsideArchive': ['Other 47996 full NPZ states remain locally retained and individually hash-pinned.',
            'Original corpus, raw replays and runtime binaries remain locally retained and pinned through the input freezes.'],
        'newGames': 0, 'newModelEvaluations': 0, 'newGradients': 0, 'promotion': False,
        'packager': packager,
        'packagingAmendment': {'kind': 'post-training-packaging-only', 'frozenAuditHelper': audit_source,
            'optimization': 'Cache canonical ancestors with identity revalidation; preserve every required content hash and frozen tar layout. Verify all members and the gzip trailer in one complete stream.',
            'frozenArchiveInputsChanged': False, 'learnerOrAuditRestarted': False}}
    with index_path.open('x', encoding='utf-8') as stream:
        json.dump(index, stream, indent=2, allow_nan=False)
        stream.write('\n')
    print(json.dumps({'archive': index['archive'], 'sha256': archive_sha, 'files': len(rows), 'bytes': archive_size}))
    return index


if __name__ == '__main__':
    package()
