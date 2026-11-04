import { ref } from 'vue';

import ts from 'typescript';

import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import test from 'node:test';
import { compileFunction } from 'node:vm';

function componentSetup(...parameters) {
  return compileFunction(parameters.pop(), parameters);
}

const source = readFileSync(new URL('../src/components/business/plan-review/noticePanel.vue', import.meta.url), 'utf8');
const script = source.match(/<script setup lang="ts">([\s\S]*?)<\/script>/)[1].replace(/^\s*import[\s\S]*?;/gm, '');
const code = ts.transpileModule(script, { compilerOptions: { target: ts.ScriptTarget.ES2022 } }).outputText;
const tick = () =>
  new Promise((resolve) => {
    setImmediate(resolve);
  });
function deferred() {
  let resolve;
  const promise = new Promise((done) => {
    resolve = done;
  });
  return { promise, resolve };
}
function harness(overrides = {}) {
  const props = { projectId: 'project-a' };
  const calls = [];
  const watchers = [];
  const unmounts = [];
  const defaults = {
    list: async () => ({ items: [{ id: 'fault' }], canRecover: true }),
    preview: async () => ({
      previewId: 'preview',
      eligibleCount: 1,
      ineligibleCount: 0,
      expiresAt: Date.now() + 600000,
    }),
    confirm: async () => ({ state: 'PENDING' }),
  };
  const request =
    (kind) =>
    async (...args) => {
      calls.push({ kind, args });
      return (overrides[kind] || defaults[kind])(...args);
    };
  const create = componentSetup(
    'ref',
    'watch',
    'onBeforeUnmount',
    'defineProps',
    'defineExpose',
    'getPlanReviewNotices',
    'previewPlanReviewNoticeRetry',
    'confirmPlanReviewNoticeRetry',
    `${code};return {open,refresh,preview,confirm,approval,items,visible,error,busy};`
  );
  const api = create(
    ref,
    (getter, callback) => watchers.push(callback),
    (callback) => unmounts.push(callback),
    () => props,
    () => undefined,
    request('list'),
    request('preview'),
    request('confirm')
  );
  return {
    api,
    calls,
    switchProject() {
      props.projectId = 'project-b';
      watchers[0]();
    },
    unmount() {
      unmounts.forEach((callback) => callback());
    },
  };
}
test('opening or previewing never confirms notice recovery', async () => {
  const h = harness();
  h.api.open('plan', 'plan');
  await tick();
  await h.api.preview('fault');
  assert.equal(h.calls.filter((call) => call.kind === 'confirm').length, 0);
  assert.equal(h.api.approval.value.previewId, 'preview');
  await h.api.confirm();
  assert.equal(h.calls.filter((call) => call.kind === 'confirm').length, 1);
});
test('unknown confirmation preserves the original preview and warning for safe retry', async () => {
  let attempts = 0;
  const h = harness({
    confirm: async () => {
      if (++attempts === 1) throw new Error('lost response');
      return {};
    },
  });
  h.api.open('plan', 'plan');
  await tick();
  await h.api.preview('fault');
  await h.api.confirm();
  assert.equal(h.api.error.value, true);
  assert.equal(h.api.approval.value.previewId, 'preview');
  await h.api.confirm();
  const requests = h.calls.filter((call) => call.kind === 'confirm');
  assert.deepEqual(requests[0].args, requests[1].args);
});
test('missing recovery authorization prevents preview calls', async () => {
  const h = harness({ list: async () => ({ items: [], canRecover: false }) });
  h.api.open('plan', 'plan');
  await tick();
  await h.api.preview('fault');
  assert.equal(h.calls.filter((call) => call.kind === 'preview').length, 0);
});
test('expired preview cannot confirm', async () => {
  const h = harness();
  h.api.open('plan', 'plan');
  await tick();
  await h.api.preview('fault');
  h.api.approval.value.expiresAt = 1;
  await h.api.confirm();
  assert.equal(h.calls.filter((call) => call.kind === 'confirm').length, 0);
  assert.equal(h.api.error.value, true);
});
['switchProject', 'unmount'].forEach((action) =>
  test(`${action} ignores late recipient preview`, async () => {
    const pending = deferred();
    const h = harness({ preview: () => pending.promise });
    h.api.open('plan', 'plan');
    await tick();
    const call = h.api.preview('fault');
    h[action]();
    pending.resolve({ previewId: 'stale' });
    await call;
    assert.equal(h.api.approval.value, undefined);
    assert.equal(h.api.visible.value, false);
  })
);
