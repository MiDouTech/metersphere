import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { webcrypto } from 'node:crypto';
import test from 'node:test';
import ts from 'typescript';
import { ref } from 'vue';

// Execute the component's actual setup/functions, following the existing modal-contract harness.
// This exercises request and asynchronous boundaries; it is not browser acceptance.
const source = readFileSync(
  new URL('../src/components/business/plan-review/newReviewDialog.vue', import.meta.url),
  'utf8'
);
const script = source.match(/<script setup lang="ts">([\s\S]*?)<\/script>/)[1].replace(/^\s*import[\s\S]*?;/gm, '');
const code = ts.transpileModule(script, { compilerOptions: { target: ts.ScriptTarget.ES2022 } }).outputText;
const tick = () => new Promise((resolve) => setImmediate(resolve));
function deferred() {
  let resolve;
  const promise = new Promise((done) => {
    resolve = done;
  });
  return { promise, resolve };
}
function harness(overrides = {}) {
  const store = { currentProjectId: 'project-a' },
    watchers = [],
    unmounts = [],
    calls = [];
  const defaults = {
    candidates: async () => ({ items: [{ id: 'plan-a' }], total: 1 }),
    preview: async () => ({
      previewId: 'preview',
      operationId: 'operation',
      expiresAt: Date.now() + 600000,
      difference: { added: ['case'] },
    }),
    items: async () => ({ items: [{ caseId: 'case', num: 1, name: 'case-name' }] }),
    confirm: async () => ({ reviewId: 'review-a' }),
    recover: async () => recoveryPreview(),
  };
  const request =
    (kind) =>
    async (...args) => {
      calls.push({ kind, args: structuredClone(args) });
      return (overrides[kind] || defaults[kind])(...args);
    };
  const create = new Function(
    'ref',
    'watch',
    'onBeforeUnmount',
    'useAppStore',
    'getNewReviewPlanCandidates',
    'previewNewLinkedReview',
    'getPlanReviewPreviewItems',
    'confirmNewLinkedReview',
    'recoverNewLinkedReviewPreview',
    'defineExpose',
    'crypto',
    `${code};return {open,openRecovery,refreshPreview,submit,search,loadItems,visible,busy,failed,selected,preview,acknowledged,candidates,itemsReady};`
  );
  const api = create(
    ref,
    (getter, callback) => watchers.push({ getter, callback }),
    (callback) => unmounts.push(callback),
    () => store,
    request('candidates'),
    request('preview'),
    request('items'),
    request('confirm'),
    request('recover'),
    () => {},
    webcrypto
  );
  return {
    api,
    calls,
    switchProject() {
      store.currentProjectId = 'project-b';
      watchers[0].callback();
    },
    unmount() {
      for (const callback of unmounts) callback();
    },
  };
}
async function ready(h, created = () => {}) {
  h.api.open({ projectId: 'project-a', name: 'review' }, created);
  await tick();
  h.api.selected.value = 'plan-a';
  assert.equal(await h.api.submit(), false); // preview never creates a review
  h.api.acknowledged.value = true;
}
function recoveryPreview() {
  return {
    previewId: 'recovery-preview',
    operationId: 'original-operation',
    requestKey: 'original-key',
    projectId: 'project-a',
    planId: 'plan-a',
    expiresAt: Date.now() + 600000,
    difference: { added: ['case'] },
    settingsSummary: { name: 'Original review', reviewPassRule: 'SINGLE', reviewerCount: 1 },
  };
}
test('recovery opens the original preview and waits for explicit confirmation with its original key', async () => {
  const h = harness(),
    created = [];
  h.api.openRecovery(recoveryPreview(), (id) => created.push(id));
  await tick();
  assert.equal(
    h.calls.filter((call) => call.kind === 'confirm' || call.kind === 'preview' || call.kind === 'candidates').length,
    0
  );
  assert.equal(await h.api.submit(), false);
  h.api.acknowledged.value = true;
  assert.equal(await h.api.submit(), true);
  assert.equal(h.calls.find((call) => call.kind === 'confirm').args[0].requestKey, 'original-key');
  assert.deepEqual(created, ['review-a']);
});
test('refreshing recovery calls the original-operation endpoint instead of creating a new request', async () => {
  const h = harness();
  h.api.openRecovery(recoveryPreview(), () => {});
  await tick();
  await h.api.refreshPreview();
  const calls = h.calls.filter((call) => call.kind === 'recover');
  assert.equal(calls.length, 1);
  assert.deepEqual(calls[0].args, ['project-a', 'original-operation']);
  assert.equal(h.calls.filter((call) => call.kind === 'preview').length, 0);
  assert.equal(h.api.acknowledged.value, false);
});

test('preview alone never creates; explicit confirmation preserves the original request key', async () => {
  const h = harness(),
    created = [];
  await ready(h, (id) => created.push(id));
  assert.equal(h.calls.filter((call) => call.kind === 'confirm').length, 0);
  assert.equal(await h.api.submit(), true);
  const preview = h.calls.find((call) => call.kind === 'preview').args[0];
  const confirm = h.calls.find((call) => call.kind === 'confirm').args[0];
  assert.equal(confirm.requestKey, preview.requestKey);
  assert.deepEqual(created, ['review-a']);
});

test('an unknown confirmation result retries the same preview/key instead of creating another operation', async () => {
  let attempts = 0;
  const h = harness({
    confirm: async () => {
      if (++attempts === 1) throw new Error('response lost');
      return { reviewId: 'review-a' };
    },
  });
  await ready(h);
  assert.equal(await h.api.submit(), false);
  assert.equal(h.api.busy.value, false);
  assert.equal(await h.api.submit(), true);
  const confirmations = h.calls.filter((call) => call.kind === 'confirm');
  assert.deepEqual(confirmations[0].args, confirmations[1].args);
});

test('failed difference loading blocks confirmation even if a checkbox is manually checked', async () => {
  const h = harness({
    items: async () => {
      throw new Error('not readable');
    },
  });
  await ready(h);
  assert.equal(h.api.itemsReady.value, false);
  assert.equal(await h.api.submit(), false);
  assert.equal(h.calls.filter((call) => call.kind === 'confirm').length, 0);
});

test('switching project invalidates an in-flight candidate response', async () => {
  const pending = deferred(),
    h = harness({ candidates: () => pending.promise });
  h.api.open({ projectId: 'project-a' }, () => {});
  h.switchProject();
  pending.resolve({ items: [{ id: 'old-project-plan' }], total: 1 });
  await tick();
  assert.equal(h.api.visible.value, false);
  assert.deepEqual(h.api.candidates.value, []);
  assert.equal(await h.api.submit(), false);
});

test('unmount during confirmation never navigates using a stale callback', async () => {
  const pending = deferred(),
    created = [],
    h = harness({ confirm: () => pending.promise });
  await ready(h, (id) => created.push(id));
  const submitting = h.api.submit();
  h.unmount();
  pending.resolve({ reviewId: 'review-a' });
  assert.equal(await submitting, false);
  assert.deepEqual(created, []);
});

test('expired preview refuses confirmation locally', async () => {
  const h = harness();
  await ready(h);
  h.api.preview.value.expiresAt = Date.now() - 1;
  assert.equal(await h.api.submit(), false);
  assert.equal(h.calls.filter((call) => call.kind === 'confirm').length, 0);
});
