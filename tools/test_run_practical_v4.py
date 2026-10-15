"""Offline regression checks for practical-v4 exclusion-index construction."""
import contextlib
import io
import json
from pathlib import Path
import shutil
import unittest
from unittest.mock import Mock, patch
import uuid

import run_practical_v4 as runner


class PracticalV4ExclusionTest(unittest.TestCase):
    def setUp(self):
        self.test_root = Path(__file__).resolve().parents[1] / 'build/test-run-practical-v4'
        self.test_root.mkdir(parents=True, exist_ok=True)
        self.root = self.test_root / ('case-' + uuid.uuid4().hex)
        self.root.mkdir()
        self.addCleanup(self.cleanup)
        self.bench = self.root / 'build/bench'
        self.folder = self.bench / 'candidate'
        self.folder.mkdir(parents=True)
        self.addCleanup(patch.stopall)
        patch.object(runner, 'ROOT', self.root).start()
        patch.object(runner, 'BENCH', self.bench).start()

    def cleanup(self):
        if self.root.resolve().parent != self.test_root.resolve():
            raise AssertionError('Test cleanup escaped workspace directory')
        shutil.rmtree(self.root)

    def report(self, name, maps):
        path = self.bench / name
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(json.dumps({'gamesPerMap': maps}), encoding='utf-8')
        return path

    def build(self):
        with contextlib.redirect_stdout(io.StringIO()):
            return runner.build_exclusion_index(self.folder, 'block2')

    def test_nested_overlapping_indices_preserve_union_and_sources_with_unit_counts(self):
        a, b, c, d = (character * 64 for character in 'abcd')
        paths = [self.report('old/block1.json', {a: 2, b: 2}),
                 self.report('old/nested/block2-excluded.json', {a: 2 ** 40, b: 2 ** 40}),
                 self.report('older/deeper/block3-excluded.json', {a: 2 ** 60, c: 2 ** 50}),
                 self.report('candidate/block1.json', {b: 2, d: 2})]
        # This run's old exclusion output must not become another source report.
        self.report('candidate/block1-excluded.json', {'e' * 64: 2 ** 62})
        original_bytes = {path: path.read_bytes() for path in paths}

        result = json.loads(self.build().read_text(encoding='utf-8'))

        self.assertEqual(result['gamesPerMap'], {a: 1, b: 1, c: 1, d: 1})
        self.assertEqual(result['sourceFiles'],
                         {path.relative_to(self.root).as_posix(): 2 for path in paths})
        self.assertEqual({path: path.read_bytes() for path in paths}, original_bytes)

    def test_invalid_original_counts_are_rejected_before_output_is_written(self):
        for count in (0, -1, -(2 ** 40), True, 1.5, '2', None):
            with self.subTest(count=count):
                self.report('old/block1.json', {'a' * 64: count})
                with self.assertRaisesRegex(SystemExit, 'expected positive integer map count'):
                    self.build()
                self.assertFalse((self.folder / 'block2-excluded.json').exists())

    def test_completed_block_uses_v4_budget_validator(self):
        process = Mock(stdout=[], returncode=0)
        validated = object()
        pool = self.root / 'bench/pools/fixed.json'
        excluded = self.folder / 'block1-excluded.json'
        with patch.object(runner, 'ensure_pool', return_value=pool), \
                patch.object(runner, 'build_exclusion_index', return_value=excluded), \
                patch.object(runner.subprocess, 'Popen', return_value=process), \
                patch.object(runner.gate, 'read_report', return_value=validated) as read, \
                contextlib.redirect_stdout(io.StringIO()):
            actual = runner.run_block('candidate', 'current?TURN_BUDGET_MS=38',
                                      'current?TURN_BUDGET_MS=36', 'block1', 300, 150, 1)
        self.assertIs(validated, actual)
        read.assert_called_once_with(self.folder / 'block1.json', 300)
        process.wait.assert_called_once_with()


if __name__ == '__main__':
    unittest.main()
