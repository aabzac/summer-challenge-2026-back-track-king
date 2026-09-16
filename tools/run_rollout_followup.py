"""Run one independent preregistered rollout follow-up, without retries or promotion."""
import argparse
from run_quick_followup import run_variant

FLAGS = {'diverse': 'USE_ROLLOUT_DIVERSE_REPLIES', 'replan': 'USE_ROLLOUT_REPLAN',
         'target': 'USE_ROLLOUT_SECOND_TARGET'}

if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('variant', choices=FLAGS)
    args = parser.parse_args()
    run_variant('rollout-followup-' + args.variant + '-v1',
                'current?' + FLAGS[args.variant] + '=1', 'docs/rollout-followups.md')
