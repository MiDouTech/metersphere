import { ref } from 'vue';

import ts from 'typescript';

import assert from 'node:assert/strict';
import { webcrypto } from 'node:crypto';
import { readFileSync } from 'node:fs';
import test from 'node:test';
import { compileFunction } from 'node:vm';

// Compile only the local component setup, using explicit test dependencies.
function componentSetup(...parameters) {
  return compileFunction(parameters.pop(), parameters);
}

const source = readFileSync(new URL('../src/components/business/plan-review/copyDialog.vue', import.meta.url), 'utf8');
const script = source.match(/<script setup lang="ts">([\s\S]*?)<\/script>/)[1].replace(/^\s*import[\s\S]*?;/gm, '');
const code = ts.transpileModule(script, { compilerOptions: { target: ts.ScriptTarget.ES2022 } }).outputText;
const result = (failedCount = 0) => ({ createdCount: 2, failedCount, items: [] });
function deferred() {
  let resolve;
  const promise = new Promise((done) => {
    resolve = done;
  });
  return { promise, resolve };
}
function harness() {
  const store = { currentProjectId: 'project-a' };
  const watchers = [];
  const unmounts = [];
  const create = componentSetup(
    'ref',
    'watch',
    'onBeforeUnmount',
    'useAppStore',
    'defineExpose',
    'crypto',
    `${code};return {open,save,settingsRef,confirmed,outcome,unknown,busy,visible};`
  );
  const api = create(
    ref,
    (_, callback) => watchers.push(callback),
    (callback) => unmounts.push(callback),
    () => store,
    () => undefined,
    webcrypto
  );
  api.settingsRef.value = { validate: async () => ({ reviewers: ['reviewer'] }) };
  return {
    api,
    switchProject() {
      store.currentProjectId = 'project-b';
      watchers[0]();
    },
    unmount() {
      unmounts.forEach((callback) => callback());
    },
  };
}
test('partial result remains open and retry uses the same confirmed settings and key', async () => {
  const h = harness();
  const calls = [];
  h.api.open('project-a', 'Plan', async (request) => {
    calls.push(JSON.parse(JSON.stringify(request)));
    return result(calls.length === 1 ? 1 : 0);
  });
  assert.equal(await h.api.save(), false);
  assert.equal(h.api.outcome.value.failedCount, 1);
  assert.equal(h.api.visible.value, true);
  h.api.settingsRef.value.validate = async () => {
    throw new Error('must not revalidate fixed input');
  };
  assert.equal(await h.api.save(), true);
  assert.deepEqual(calls[1], calls[0]);
});
test('unknown retry preserves earlier confirmed facts without claiming success', async () => {
  const h = harness();
  let attempts = 0;
  h.api.open('project-a', 'Plan', async () => {
    if (++attempts === 2) throw new Error('lost response');
    return result(1);
  });
  await h.api.save();
  assert.equal(await h.api.save(), false);
  assert.equal(h.api.unknown.value, true);
  assert.equal(h.api.outcome.value.failedCount, 1);
});
test('empty group requires no review form and submits no review settings', async () => {
  const h = harness();
  let request;
  h.api.settingsRef.value = undefined;
  h.api.open(
    'project-a',
    'Group',
    async (value) => {
      request = value;
      return result();
    },
    false
  );
  assert.equal(await h.api.save(), true);
  assert.equal(request.reviewSettings, undefined);
  assert.ok(request.requestKey);
});
test('validation in progress blocks a second submission', async () => {
  const h = harness();
  const pending = deferred();
  let calls = 0;
  h.api.settingsRef.value.validate = () => pending.promise;
  h.api.open('project-a', 'Plan', async () => {
    calls++;
    return result();
  });
  const first = h.api.save();
  assert.equal(await h.api.save(), false);
  pending.resolve({ reviewers: ['reviewer'] });
  assert.equal(await first, true);
  assert.equal(calls, 1);
});
['switchProject', 'unmount'].forEach((action) => {
  test(`${action} invalidates a late copy response`, async () => {
    const h = harness();
    const pending = deferred();
    h.api.open('project-a', 'Plan', () => pending.promise, false);
    const call = h.api.save();
    h[action]();
    pending.resolve(result());
    assert.equal(await call, false);
    assert.equal(h.api.outcome.value, undefined);
    assert.equal(h.api.visible.value, false);
  });
});
