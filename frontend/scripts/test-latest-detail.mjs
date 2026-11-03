import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import test from 'node:test';
import ts from 'typescript';

const source = readFileSync(new URL('../src/utils/latestDetail.ts', import.meta.url), 'utf8');
const js = ts.transpileModule(source, { compilerOptions: { module: ts.ModuleKind.CommonJS } }).outputText;
const exports = {};
new Function('exports', js)(exports);
const { createLatestDetailLoader } = exports;
function setup() {
  const requests = [],
    events = [];
  const loader = createLatestDetailLoader({
    fetch: (id, signal) => new Promise((resolve, reject) => requests.push({ id, signal, resolve, reject })),
    start: () => events.push('start'),
    success: (v) => events.push(v),
    failure: (e) => events.push(e),
    finish: () => events.push('finish'),
  });
  return { loader, requests, events };
}
test('same ID in flight shares one request', async () => {
  const h = setup();
  const first = h.loader.load('A');
  assert.equal(h.loader.load('A'), first);
  await Promise.resolve();
  assert.equal(h.requests.length, 1);
  h.requests[0].resolve('A loaded');
  await first;
  assert.deepEqual(h.events, ['start', 'A loaded', 'finish']);
});
for (const staleOutcome of ['resolve', 'reject']) {
  test(`late ${staleOutcome} cannot overwrite newer detail`, async () => {
    const h = setup();
    const a = h.loader.load('A');
    await Promise.resolve();
    const b = h.loader.load('B');
    await Promise.resolve();
    assert.equal(h.requests[0].signal.aborted, true);
    h.requests[1].resolve('B loaded');
    await b;
    h.requests[0][staleOutcome](new Error('old request'));
    await a;
    assert.deepEqual(h.events, ['start', 'start', 'B loaded', 'finish']);
  });
}
test('cancellation is silent but real failures remain visible and can retry', async () => {
  const h = setup();
  const a = h.loader.load('A');
  await Promise.resolve();
  h.requests[0].reject({ code: 'ERR_CANCELED' });
  await a;
  assert.deepEqual(h.events, ['start', 'finish']);
  const b = h.loader.load('A');
  await Promise.resolve();
  const failure = new Error('server error');
  h.requests[1].reject(failure);
  await b;
  assert.ok(h.events.includes(failure));
  const c = h.loader.load('A');
  await Promise.resolve();
  h.requests[2].resolve('retry success');
  await c;
  assert.ok(h.events.includes('retry success'));
});
test('close invalidates pending request, and empty ID never requests', async () => {
  const h = setup();
  await h.loader.load('');
  assert.equal(h.requests.length, 0);
  const a = h.loader.load('A');
  await Promise.resolve();
  h.loader.invalidate();
  assert.equal(h.requests[0].signal.aborted, true);
  h.requests[0].resolve('closed detail');
  await a;
  assert.ok(!h.events.includes('closed detail'));
});
