import unittest
from diagnostics.strategy_control_compatibility import FIELDS, reconstruct


class ControlCompatibilityTest(unittest.TestCase):
    def fixture(self):
        cached_params = {'SEED': '1'}
        current_params = dict(cached_params, **{k: v for k, (_, v) in FIELDS.items()})
        marker = 'public static final String BUILD_MARKER = "identity";\n'
        cached = marker + 'public long SEED = 1;\nnew Params("SEED=1")\n'
        declarations = ''.join(f'public {kind} {name} = {value};\n' for name, (kind, value) in FIELDS.items())
        query = ','.join(f'{key}={value}' for key, value in sorted(current_params.items()))
        current = marker + declarations + 'public long SEED = 1;\nnew Params("' + query + '")\n'
        return current, cached, current_params, cached_params

    def test_only_known_unused_declarations_and_config_entries_are_accepted(self):
        self.assertEqual(64, len(reconstruct(*self.fixture())))

    def test_strategy_read_of_new_field_is_rejected(self):
        args = list(self.fixture()); args[0] += 'if (USE_ROLLOUT_RHEA) attack();\n'
        with self.assertRaisesRegex(ValueError, 'referenced outside'):
            reconstruct(*args)

    def test_other_shipped_code_drift_is_rejected(self):
        args = list(self.fixture()); args[0] += 'attack();\n'
        with self.assertRaisesRegex(ValueError, 'Other shipped source'):
            reconstruct(*args)

    def test_cached_parameters_and_new_defaults_are_checked(self):
        for key, value in [('SEED', '2'), ('OPENING_TURNS', '2')]:
            args = list(self.fixture()); args[2][key] = value
            with self.assertRaises(ValueError):
                reconstruct(*args)

    def test_unknown_metadata_additions_are_rejected(self):
        args = list(self.fixture()); args[2]['NEW'] = '1'
        with self.assertRaisesRegex(ValueError, 'added parameter set'):
            reconstruct(*args)


if __name__ == '__main__':
    unittest.main()
