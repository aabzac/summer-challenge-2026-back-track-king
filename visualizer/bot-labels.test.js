'use strict';
const assert = require('node:assert/strict');
const {label} = require('./bot-labels.js');
const {entries} = require('../src/main/resources/btk/bot/catalog.json');

assert.equal(label({source:'local', bots:['v05','v03']},0,entries),'v05 - Disrupt Reserve');
assert.equal(label({source:'local', bots:['v03?NOISE=0.2']},0,entries),'v03 - Risk Aware [NOISE=0.2]');
assert.equal(label({source:'cg', bots:['v05','exp-income-guard']},0,entries),'v05');
assert.equal(label({source:'cg', bots:['v05','exp-income-guard']},1,entries),'exp-income-guard');
assert.equal(label({source:'local', bots:['custom-name']},0,entries),'custom-name');
assert.equal(label({source:'local', bots:['v05']},0),'v05');
assert.equal(label({source:'local', bots:['exp-income-guard']},0,entries),
  'exp-income-guard - Income Guard (experiment, not promoted)');
assert.equal(label({source:'local', bots:['v05'],botDetails:[{displayName:'Recorded title',category:'version'}]},0,entries),'Recorded title');
assert.equal(label({source:'cg', bots:['v05'],botDetails:[{displayName:'Do not use this',category:'version'}]},0,entries),'v05');
console.log('9 bot-label checks passed');
