'use strict';
// Pure presentation only: never interpret CG nicknames as our local bot ids.
const BtkBotLabels = {
  label(meta, side, entries = []) {
    const spec = meta.bots?.[side] ?? `P${side}`;
    if (meta.source !== 'local') return spec;
    const recorded = meta.botDetails?.[side];
    const q = spec.indexOf('?');
    const id = q < 0 ? spec : spec.slice(0, q);
    const entry = entries.find(e => e.id === id);
    const display = recorded?.displayName || (entry
      ? `${id} - ${entry.title}${q >= 0 && q + 1 < spec.length ? ' [' + spec.slice(q + 1) + ']' : ''}` : spec);
    const category = recorded?.category || entry?.category;
    if (category === 'experiment') return `${display} (experiment, not promoted)`;
    if (category === 'reference') return `${display} (local reference)`;
    if (category === 'working') return `${display} (mutable)`;
    return display;
  }
};
if (typeof module !== 'undefined') module.exports = BtkBotLabels;
