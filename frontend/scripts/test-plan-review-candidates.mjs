/* global globalThis */
import ts from 'typescript';

import { JSDOM } from 'jsdom';
import assert from 'node:assert/strict';
import { webcrypto } from 'node:crypto';
import { readFileSync } from 'node:fs';
import { createRequire } from 'node:module';
import { afterEach, test } from 'node:test';
import { compileScript, parse } from 'vue/compiler-sfc';

const dom = new JSDOM('<!doctype html><html><body></body></html>', { url: 'http://localhost' });
[
  'window',
  'document',
  'navigator',
  'HTMLElement',
  'Element',
  'SVGElement',
  'Node',
  'HTMLInputElement',
  'MutationObserver',
  'getComputedStyle',
].forEach((name) => Object.defineProperty(globalThis, name, { configurable: true, value: dom.window[name] }));
globalThis.ResizeObserver = class {
  observe() {
    return undefined;
  }

  unobserve() {
    return undefined;
  }

  disconnect() {
    return undefined;
  }
};
window.ResizeObserver = globalThis.ResizeObserver;
window.matchMedia = () => ({
  matches: false,
  addListener() {
    return undefined;
  },
  removeListener() {
    return undefined;
  },
  addEventListener() {
    return undefined;
  },
  removeEventListener() {
    return undefined;
  },
});
globalThis.requestAnimationFrame = (fn) => setTimeout(fn, 0);
globalThis.cancelAnimationFrame = clearTimeout;
const require = createRequire(import.meta.url);
const Vue = require('vue');
const { mount, flushPromises } = require('@vue/test-utils');
const Arco = require('@arco-design/web-vue');

const source = readFileSync(new URL('../src/components/business/plan-review/linkCard.vue', import.meta.url), 'utf8');
const { descriptor } = parse(source);
const compiled = compileScript(descriptor, { id: 'candidate-test', inlineTemplate: true }).content;
const code = ts.transpileModule(compiled, {
  compilerOptions: { module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2022 },
}).outputText;
const mounted = [];
afterEach(async () => {
  mounted.splice(0).forEach((wrapper) => wrapper.unmount());
  await flushPromises();
  document.body.innerHTML = '';
});
const records = [
  { id: 'r1', num: 100003, name: 'First review', status: 'PREPARED' },
  { id: 'r2', num: 100004, name: 'Second review', status: 'UNDERWAY' },
];
const settle = async () => {
  await flushPromises();
  await Vue.nextTick();
};
const sleep = (ms) =>
  new Promise((resolve) => {
    setTimeout(resolve, ms);
  });
function button(text) {
  return [...document.querySelectorAll('button')].find((element) => element.textContent.trim() === text);
}
function click(element) {
  assert.ok(element);
  element.click();
}
function deferred() {
  let resolve;
  let reject;
  const promise = new Promise((done, fail) => {
    resolve = done;
    reject = fail;
  });
  return { promise, resolve, reject };
}
function harness(overrides = {}, side = 'plan') {
  const calls = [];
  const app = Vue.reactive({ currentProjectId: 'project' });
  const api = {
    getPlanReview: async () => ({ relationStatus: 'NONE', restricted: false }),
    getPlanReviewCandidates: async () => ({ items: records, total: records.length }),
    previewPlanReview: async () => ({
      previewId: 'preview',
      expiresAt: Date.now() + 600000,
      difference: {},
      message: 'Preview only',
    }),
    getPlanReviewPreviewItems: async () => ({ items: [] }),
    confirmPlanReview: async () => ({}),
    ...overrides,
  };
  Object.keys(api).forEach((key) => {
    const action = api[key];
    api[key] = async (...args) => {
      calls.push({ kind: key, args: structuredClone(args) });
      return action(...args);
    };
  });
  const exports = {};
  // Execute this repository's compiled SFC, with API calls replaced at the module boundary.
  // eslint-disable-next-line no-new-func
  new Function('require', 'exports', 'crypto', code)(
    (name) => {
      if (name === 'vue') return Vue;
      if (name === '@arco-design/web-vue') return Arco;
      if (name === 'vue-router')
        return {
          useRouter: () => ({
            push() {
              return undefined;
            },
          }),
        };
      if (name === '@/api/modules/plan-review') return api;
      if (name === '@/store/modules/app') return { default: () => app };
      if (name === '@/utils/permission') return { hasAnyPermission: () => true };
      if (name === '@/enums/routeEnum') return { TestPlanRouteEnum: {} };
      if (name.startsWith('./')) return { default: { render: () => null } };
      throw new Error(`Unexpected dependency ${name}`);
    },
    exports,
    webcrypto
  );
  const wrapper = mount(exports.default, {
    props: { side, objectId: side === 'plan' ? 'plan' : 'review', objectStatus: 'PREPARED' },
    attachTo: document.body,
    global: { plugins: [Arco.default] },
  });
  mounted.push(wrapper);
  return { wrapper, calls, app };
}
async function open(h, title = '关联评审计划') {
  await settle();
  click(button(title));
  await settle();
  assert.equal(h.calls.filter((call) => call.kind === 'getPlanReviewCandidates').length, 1);
}
test('actual Arco radio and row name select exactly one candidate; preview and cancel never confirm', async () => {
  const h = harness();
  await open(h);
  const next = button('下一步：预览差异');
  assert.equal(next.disabled, true);
  const radio = document.querySelector('.arco-modal input[type=radio]');
  assert.equal(radio.labels[0].textContent.trim(), '选择 100003 First review');
  click(radio);
  await settle();
  assert.equal(next.disabled, false);
  assert.equal(radio.checked, true);
  click([...document.querySelectorAll('.arco-modal td')].find((cell) => cell.textContent.includes('Second review')));
  await settle();
  assert.equal(radio.checked, false);
  assert.equal(document.querySelectorAll('.arco-modal input[type=radio]:checked').length, 1);
  click(next);
  await settle();
  assert.equal(h.calls.find((call) => call.kind === 'previewPlanReview').args[0].reviewId, 'r2');
  assert.equal(
    h.calls.some((call) => call.kind === 'confirmPlanReview'),
    false
  );
  click(button('取消'));
  await settle();
  assert.equal(
    h.calls.some((call) => call.kind === 'confirmPlanReview'),
    false
  );
});
test('review-side row selection previews the selected plan without confirming a relationship', async () => {
  const h = harness({}, 'review');
  await open(h, '关联测试计划');
  click([...document.querySelectorAll('.arco-modal td')].find((cell) => cell.textContent.includes('First review')));
  await settle();
  click(button('下一步：预览差异'));
  await settle();
  assert.deepEqual(h.calls.find((call) => call.kind === 'previewPlanReview').args[0], {
    planId: 'r1',
    reviewId: 'review',
    purpose: 'PAIR_EXISTING',
  });
  assert.equal(
    h.calls.some((call) => call.kind === 'confirmPlanReview'),
    false
  );
});
test('typing automatically searches and immediately invalidates the previous selection', async () => {
  const h = harness({
    getPlanReviewCandidates: async (request) => ({
      items: request.keyword ? [] : records,
      total: request.keyword ? 0 : records.length,
    }),
  });
  await open(h);
  click(document.querySelector('.arco-modal input[type=radio]'));
  await settle();
  const input = document.querySelector('input[placeholder="搜索名称或编号"]');
  input.value = '100006';
  input.dispatchEvent(new window.Event('input', { bubbles: true }));
  await Vue.nextTick();
  assert.equal(button('下一步：预览差异').disabled, true);
  await sleep(300);
  await settle();
  const last = h.calls.filter((call) => call.kind === 'getPlanReviewCandidates').at(-1);
  assert.equal(last.args[0].keyword, '100006');
  assert.equal(last.args[0].page, 1);
  assert.equal(document.querySelectorAll('.arco-modal input[type=radio]').length, 0);
  assert.equal(input.value, '100006');
  assert.equal(
    h.calls.some((call) => call.kind === 'previewPlanReview'),
    false
  );
});
test('a failed search keeps its input, blocks preview and does not accept a late older response', async () => {
  const old = deferred();
  let count = 0;
  const h = harness({
    getPlanReviewCandidates: async () => {
      if (++count === 1) return old.promise;
      throw new Error('network');
    },
  });
  await settle();
  click(button('关联评审计划'));
  await settle();
  const input = document.querySelector('input[placeholder="搜索名称或编号"]');
  input.value = 'missing';
  input.dispatchEvent(new window.Event('input', { bubbles: true }));
  await sleep(300);
  await settle();
  old.resolve({ items: records, total: 2 });
  await settle();
  assert.equal(input.value, 'missing');
  assert.equal(button('下一步：预览差异').disabled, true);
  assert.equal(document.querySelectorAll('.arco-modal input[type=radio]').length, 0);
  assert.ok(document.body.textContent.includes('候选加载失败'));
  assert.equal(
    h.calls.some((call) => call.kind === 'previewPlanReview'),
    false
  );
});
test('legacy occupancy shows a safe reason instead of an unassociated pairing button', async () => {
  const h = harness({
    getPlanReview: async () => ({ relationStatus: 'NONE', restricted: false, legacyBlocked: true }),
  });
  await settle();
  assert.ok(document.body.textContent.includes('历史关联待交接'));
  assert.equal(button('关联评审计划'), undefined);
  assert.equal(
    h.calls.some((call) => call.kind === 'getPlanReviewCandidates'),
    false
  );
});

function previewResult(id) {
  return { previewId: id, expiresAt: Date.now() + 600000, difference: {}, message: id };
}
function assertNoConfirmation(h) {
  assert.equal(
    h.calls.some((call) => call.kind === 'confirmPlanReview'),
    false
  );
}
['plan', 'review'].forEach((side) => {
  const title = side === 'plan' ? '关联评审计划' : '关联测试计划';
  async function startPending(h) {
    await open(h, title);
    click(document.querySelector('.arco-modal input[type=radio]'));
    await settle();
    click(button('下一步：预览差异'));
    await settle();
    assert.equal(h.calls.filter((call) => call.kind === 'previewPlanReview').length, 1);
  }
  test(`${side}: cancel discards a late preview success`, async () => {
    const pending = deferred();
    const h = harness({ previewPlanReview: () => pending.promise }, side);
    await startPending(h);
    click(button('取消'));
    await settle();
    pending.resolve(previewResult('CANCELLED_PREVIEW'));
    await settle();
    assert.equal(document.body.textContent.includes('CANCELLED_PREVIEW'), false);
    assertNoConfirmation(h);
  });
  ['success', 'failure'].forEach((outcome) => {
    test(`${side}: reopening isolates an old ${outcome} and its finally from the new busy request`, async () => {
      const old = deferred();
      const current = deferred();
      let request = 0;
      const h = harness({ previewPlanReview: () => (++request === 1 ? old.promise : current.promise) }, side);
      await startPending(h);
      click(button('取消'));
      await settle();
      click(button(title));
      await settle();
      click(
        [...document.querySelectorAll('.arco-modal td')].find((cell) => cell.textContent.includes('Second review'))
      );
      await settle();
      click(button('下一步：预览差异'));
      await settle();
      assert.equal(request, 2);
      assert.equal(button(title).disabled, true);
      if (outcome === 'success') old.resolve(previewResult('OLD_PREVIEW'));
      else old.reject(new Error('old request failed'));
      await settle();
      assert.equal(document.body.textContent.includes('OLD_PREVIEW'), false);
      assert.equal(button(title).disabled, true);
      current.resolve(previewResult('CURRENT_PREVIEW'));
      await settle();
      assert.equal(document.body.textContent.includes('CURRENT_PREVIEW'), true);
      assert.equal(button(title).disabled, false);
      const last = h.calls.filter((call) => call.kind === 'previewPlanReview').at(-1).args[0];
      assert.equal(side === 'plan' ? last.reviewId : last.planId, 'r2');
      assertNoConfirmation(h);
    });
  });
  test(`${side}: a search change invalidates an in-flight preview`, async () => {
    const pending = deferred();
    const h = harness({ previewPlanReview: () => pending.promise }, side);
    await startPending(h);
    const input = document.querySelector('input[placeholder="搜索名称或编号"]');
    input.value = 'Second';
    input.dispatchEvent(new window.Event('input', { bubbles: true }));
    await Vue.nextTick();
    pending.resolve(previewResult('OLD_SEARCH_PREVIEW'));
    await settle();
    assert.equal(document.body.textContent.includes('OLD_SEARCH_PREVIEW'), false);
    await sleep(300);
    await settle();
    assert.equal(h.calls.filter((call) => call.kind === 'getPlanReviewCandidates').at(-1).args[0].keyword, 'Second');
    assertNoConfirmation(h);
  });
  test(`${side}: changing the selected candidate invalidates its pending preview`, async () => {
    const pending = deferred();
    const h = harness({ previewPlanReview: () => pending.promise }, side);
    await startPending(h);
    click([...document.querySelectorAll('.arco-modal td')].find((cell) => cell.textContent.includes('Second review')));
    await settle();
    pending.resolve(previewResult('OLD_SELECTION_PREVIEW'));
    await settle();
    assert.equal(document.body.textContent.includes('OLD_SELECTION_PREVIEW'), false);
    assert.equal(button('下一步：预览差异').disabled, false);
    assertNoConfirmation(h);
  });
  ['project', 'unmount'].forEach((change) => {
    test(`${side}: ${change} invalidates a pending preview`, async () => {
      const pending = deferred();
      const h = harness({ previewPlanReview: () => pending.promise }, side);
      await startPending(h);
      if (change === 'project') h.app.currentProjectId = 'other-project';
      else h.wrapper.unmount();
      await settle();
      pending.resolve(previewResult('OLD_CONTEXT_PREVIEW'));
      await settle();
      assert.equal(document.body.textContent.includes('OLD_CONTEXT_PREVIEW'), false);
      assertNoConfirmation(h);
    });
  });
});
