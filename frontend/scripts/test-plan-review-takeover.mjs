import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { webcrypto } from 'node:crypto';
import test from 'node:test';
import ts from 'typescript';
import { ref } from 'vue';

const source = readFileSync(
  new URL('../src/components/business/plan-review/takeoverPanel.vue', import.meta.url),
  'utf8'
);
const script = source.match(/<script setup lang="ts">([\s\S]*?)<\/script>/)[1].replace(/^\s*import[\s\S]*?;/gm, '');
const code = ts.transpileModule(script, { compilerOptions: { target: ts.ScriptTarget.ES2022 } }).outputText;
function deferred() {
  let resolve;
  const promise = new Promise((done) => {
    resolve = done;
  });
  return { promise, resolve };
}
function harness(overrides = {}, manager = false) {
  const props = { projectId: 'project-a' },
    calls = [],
    events = [],
    watchers = [],
    unmounts = [];
  const defaults = {
    records: async () => ({ items: [] }),
    users: async () => [
      { id: 'old', name: 'Original' },
      { id: 'new', name: 'New actor' },
    ],
    operations: async () => ({ items: [] }),
    preview: async () => ({ previewId: 'preview' }),
    approve: async () => ({ approvalId: 'preview' }),
    confirm: async () => ({ revision: 1 }),
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
    'defineProps',
    'defineEmits',
    'getProjectOptions',
    'getOperationTakeovers',
    'getPlanReviewOperations',
    'previewOperationTakeover',
    'approveOperationTakeover',
    'confirmOperationTakeover',
    'hasAnyPermission',
    'defineExpose',
    'crypto',
    `${code};return {open,refresh,requestPreview,approve,confirm,visible,busy,error,newActor,operation,items,users};`
  );
  const api = create(
    ref,
    (getter, callback) => watchers.push(callback),
    (callback) => unmounts.push(callback),
    () => props,
    () => (event) => events.push(event),
    request('users'),
    request('records'),
    request('operations'),
    request('preview'),
    request('approve'),
    request('confirm'),
    () => manager,
    () => {},
    webcrypto
  );
  return {
    api,
    calls,
    events,
    switchProject() {
      props.projectId = 'project-b';
      watchers[0]();
    },
    unmount() {
      unmounts.forEach((callback) => callback());
    },
  };
}
const operation = { operationId: 'operation', revision: 0, stage: 'VALIDATED', state: 'PENDING' };
const approval = { previewId: 'preview', operationId: 'operation', revision: 0, newActor: 'new' };

test('opening a takeover panel never approves or changes executing identity', async () => {
  const h = harness();
  await h.api.open(operation);
  assert.deepEqual(
    h.calls.map((call) => call.kind),
    ['records', 'users']
  );
  h.api.newActor.value = 'new';
  await h.api.requestPreview();
  assert.equal(h.calls.filter((call) => call.kind === 'preview').length, 1);
  assert.equal(h.calls.filter((call) => ['approve', 'confirm'].includes(call.kind)).length, 0);
});
test('project switching invalidates a pending record response before member lookup', async () => {
  const pending = deferred(),
    h = harness({ records: () => pending.promise });
  const opening = h.api.open(operation);
  h.switchProject();
  pending.resolve({ items: [approval] });
  await opening;
  assert.equal(h.api.visible.value, false);
  assert.deepEqual(h.api.items.value, []);
  assert.equal(h.calls.filter((call) => call.kind === 'users').length, 0);
});
test('a lost confirmation response retries the same approval revision and request key', async () => {
  let attempts = 0;
  const h = harness({
    confirm: async () => {
      if (++attempts === 1) throw new Error('response lost');
      return { revision: 1 };
    },
  });
  await h.api.open();
  await h.api.confirm(approval);
  assert.equal(h.api.error.value, true);
  await h.api.confirm(approval);
  const requests = h.calls.filter((call) => call.kind === 'confirm');
  assert.equal(requests.length, 2);
  assert.deepEqual(requests[0].args, requests[1].args);
});
test('approval requires an explicit action and records acknowledgment', async () => {
  const h = harness();
  await h.api.open();
  assert.equal(h.calls.filter((call) => call.kind === 'approve').length, 0);
  await h.api.approve(approval);
  const request = h.calls.find((call) => call.kind === 'approve').args[0];
  assert.equal(request.acknowledged, true);
  assert.equal(request.projectId, 'project-a');
  assert.equal(request.previewId, 'preview');
});
test('unmount during approval suppresses stale refresh and parent notifications', async () => {
  const pending = deferred(),
    h = harness({ approve: () => pending.promise });
  await h.api.open();
  const approving = h.api.approve(approval);
  h.unmount();
  pending.resolve({ approvalId: 'preview' });
  await approving;
  assert.deepEqual(h.events, []);
  assert.equal(h.calls.filter((call) => call.kind === 'records').length, 1);
});
test('project-wide operation browsing is requested only for the approval role', async () => {
  const participant = harness();
  await participant.api.open();
  assert.equal(participant.calls.filter((call) => call.kind === 'operations').length, 0);
  const approver = harness({}, true);
  await approver.api.open();
  assert.deepEqual(approver.calls.find((call) => call.kind === 'operations').args, ['project-a', true]);
});
