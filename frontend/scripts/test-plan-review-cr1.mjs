/* global globalThis */
import { cloneDeep } from 'lodash-es';

import ts from 'typescript';

import { JSDOM } from 'jsdom';
import assert from 'node:assert/strict';
import { webcrypto } from 'node:crypto';
import { readFileSync } from 'node:fs';
import { createRequire } from 'node:module';
import { afterEach, test } from 'node:test';
import vm from 'node:vm';
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

// Author regression, adapted from CR1.0 independent reproductions. APIs are replaced;
// LinkCard, NoticePanel, TakeoverPanel, NewReviewDialog, Toolbar and the table render are real SFCs.
const mounted = [];
afterEach(async () => {
  mounted.splice(0).forEach((w) => w.unmount());
  await flushPromises();
  document.body.innerHTML = '';
});
const settle = async () => {
  await flushPromises();
  await Vue.nextTick();
};
globalThis.CSS = { escape: (value) => String(value).replace(/[^a-zA-Z0-9_-]/g, '') };
window.scrollTo = () => undefined;
const enums = {};
/* eslint-disable-next-line no-new-func -- Compile real SFCs in the isolated author test harness. */
new Function(
  'exports',
  ts.transpileModule(readFileSync(new URL('../src/enums/tableEnum.ts', import.meta.url), 'utf8'), {
    compilerOptions: { module: ts.ModuleKind.CommonJS },
  }).outputText
)(enums);
function deferred() {
  let resolve;
  const promise = new Promise((done) => {
    resolve = done;
  });
  return { promise, resolve };
}
function button(text) {
  return [...document.querySelectorAll('button')].find((x) => x.textContent.trim() === text);
}
function click(element) {
  assert.ok(element);
  element.click();
}
function compile(file, api, app, cache = new Map()) {
  if (cache.has(file)) return cache.get(file);
  let raw = readFileSync(new URL(`../src/${file}`, import.meta.url), 'utf8');
  if (file.endsWith('base-table.vue'))
    raw = raw.replace(
      "import { getCurrentInstance } from 'vue';",
      "import { getCurrentInstance, ref, computed, watch, watchEffect, nextTick, useAttrs, onMounted, onBeforeUnmount, onUnmounted, onActivated } from 'vue';"
    );
  const { descriptor } = parse(raw);
  const script = compileScript(descriptor, {
    id: `cr1-${file.replace(/[^a-zA-Z0-9]/g, '-')}`,
    inlineTemplate: true,
    templateOptions: { compilerOptions: { expressionPlugins: ['typescript'], isTS: true } },
  }).content;
  const code = ts.transpileModule(script, {
    compilerOptions: { module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2022 },
  }).outputText;
  const exports = {};
  const empty = {
    render() {
      return null;
    },
  };
  const load = (name) => {
    if (name === 'vue') return Vue;
    if (name === '@arco-design/web-vue') return Arco;
    if (name === '@/api/modules/plan-review') return api;
    if (name === '@/store/modules/app') return { default: () => app };
    if (name === '@/store') return { useAppStore: () => app, useTableStore: () => ({}) };
    if (name === '@/utils/permission') return { hasAnyPermission: () => true };
    if (name === '@/hooks/useI18n') return { useI18n: () => ({ t: (x) => x }) };
    if (name === 'vue-router') return { useRouter: () => ({ push() { return undefined; } }) };
    if (name === '@/enums/routeEnum') return { TestPlanRouteEnum: {} };
    if (name === '@/enums/tableEnum') return enums;
    if (name === 'lodash-es') return { debounce: (fn) => fn, throttle: (fn) => fn };
    if (name === './utils')
      return {
        getCurrentRecordChildrenIds: (rows) => rows.flatMap((x) => [x.id, ...(x.children || []).map((y) => y.id)]),
      };
    if (name === '@/api/modules/project-management/projectMember') return { getProjectOptions: async () => [] };
    if (name === './batchAction.vue')
      return {
        default: {
          setup(_props, { slots }) {
            return () => Vue.h('div', [slots.count?.(), slots.leading?.()]);
          },
        },
      };
    if (name === '../ms-checkbox/MsCheckbox.vue')
      return { default: compile('components/pure/ms-checkbox/MsCheckbox.vue', api, app, cache) };
    if (name.startsWith('./') && file.includes('/plan-review/'))
      return { default: compile(`components/business/plan-review/${name.slice(2)}`, api, app, cache) };
    return { default: empty };
  };
  /* eslint-disable-next-line no-new-func -- Compile real SFCs in the isolated author test harness. */
  new Function('require', 'exports', 'crypto', code)(load, exports, webcrypto);
  cache.set(file, exports.default);
  return exports.default;
}
const linked = (generation = 'own-g') => ({
  relationStatus: 'ACTIVE',
  restricted: false,
  relationId: 'rel',
  generation,
  target: { id: 'other', name: 'Counterpart', status: 'UNDERWAY' },
  sync: { state: 'WAITING_RETRY', blockers: [] },
  canUnlink: true,
});
function harness(overrides = {}) {
  const app = Vue.reactive({ currentProjectId: 'project' });
  const calls = [];
  const api = {
    getPlanReview: async () => linked(),
    getPlanReviewNotices: async () => ({
      items: [{ id: 'fault-secret', reason: 'DELAYED', fault_round: 987654, detected_at: 1, deliveries: [] }],
      canRecover: true,
    }),
    previewPlanReviewNoticeRetry: async () => ({
      previewId: 'approved-secret',
      eligibleCount: 654321,
      ineligibleCount: 0,
      expiresAt: Date.now() + 600000,
    }),
    confirmPlanReviewNoticeRetry: async () => ({}),
    getOperationTakeovers: async () => ({ items: [] }),
    getPlanReviewOperations: async () => ({ items: [] }),
    ...overrides,
  };
  Object.keys(api).forEach((key) => {
    const work = api[key];
    api[key] = async (...args) => {
      calls.push({ kind: key, args });
      return work(...args);
    };
  });
  const Card = compile('components/business/plan-review/linkCard.vue', api, app);
  const wrapper = mount(Card, {
    props: { side: 'plan', objectId: 'plan', objectStatus: 'PREPARED' },
    attachTo: document.body,
    global: { plugins: [Arco.default] },
  });
  mounted.push(wrapper);
  return { wrapper, api, app, calls };
}
async function openNotices() {
  await settle();
  click(button('关联管理'));
  await settle();
  click(button('通知记录'));
  await settle();
}
function noticeRefresh() {
  return [...document.querySelectorAll('button')].filter((x) => x.textContent.trim() === '刷新').at(-1);
}
['403', 'restricted', 'generation'].forEach((mode) =>
  test(`CR-D3-02 real child: ${mode} closes and clears loaded notices and approval; recovery requires a fresh read`, async () => {
    let denied = false;
    const h = harness({
      getPlanReview: async () => {
        if (!denied) return linked();
        if (mode === '403') throw Object.assign(Error('read denied'), { status: 403 });
        return mode === 'restricted' ? { relationStatus: 'RESTRICTED', restricted: true } : linked('replacement-g');
      },
    });
    await openNotices();
    assert.match(document.body.textContent, /987654/);
    click(button('预览重试未送达通知'));
    await settle();
    assert.match(document.body.textContent, /654321/);
    denied = true;
    click(noticeRefresh());
    await settle();
    assert.doesNotMatch(document.body.textContent, /987654|654321|approved-secret|fault-secret/);
    assert.equal(h.wrapper.findComponent({ name: 'noticePanel' }).exists(), false);
    assert.equal(h.calls.filter((x) => x.kind === 'confirmPlanReviewNoticeRetry').length, 0);
    denied = false;
    // A read rejection never silently restores approval or automatically opens the child.
    const topRefresh = button('刷新');
    if (topRefresh) {
      click(topRefresh);
      await settle();
    } else {
      await h.wrapper.setProps({ objectId: 'plan-new' });
      await settle();
    }
    assert.doesNotMatch(document.body.textContent, /987654|654321/);
    const before = h.calls.filter((x) => x.kind === 'getPlanReviewNotices').length;
    await openNotices();
    assert.equal(h.calls.filter((x) => x.kind === 'getPlanReviewNotices').length, before + 1);
    assert.match(document.body.textContent, /987654/);
    assert.doesNotMatch(document.body.textContent, /654321/);
  })
);
test('CR-D3-02 late notice/approval responses cannot restore invalidated child data', async () => {
  let denied = false;
  let slow = false;
  const pending = deferred();
  const h = harness({
    getPlanReview: async () => {
      if (denied) throw Error('403');
      return linked();
    },
    getPlanReviewNotices: async () =>
      slow
        ? pending.promise
        : { items: [{ id: 'fault', fault_round: 987654, detected_at: 1, deliveries: [] }], canRecover: true },
  });
  await openNotices();
  slow = true;
  click(noticeRefresh());
  await settle();
  denied = true; // Trigger parent revalidation independently while the notice read is in flight.
  click([...document.querySelectorAll('button')].find((x) => x.textContent.trim() === '刷新'));
  await settle();
  pending.resolve({ items: [{ id: 'late', fault_round: 111999, detected_at: 1, deliveries: [] }], canRecover: true });
  await settle();
  assert.doesNotMatch(document.body.textContent, /987654|111999/);
  assert.equal(h.calls.filter((x) => x.kind === 'confirmPlanReviewNoticeRetry').length, 0);
});
test('CR-D3-02 standalone notice explicit invalidation clears pending approval and requires reopening', async () => {
  const app = Vue.reactive({ currentProjectId: 'project' });
  const approval = deferred();
  let allowed = true;
  let writes = 0;
  const api = {
    getPlanReviewNotices: async () => ({
      items: [{ id: 'fault', fault_round: 987654, detected_at: 1, deliveries: [] }],
      canRecover: true,
    }),
    previewPlanReviewNoticeRetry: async () => approval.promise,
    confirmPlanReviewNoticeRetry: async () => {
      writes++;
    },
  };
  const Child = compile('components/business/plan-review/noticePanel.vue', api, app);
  const h = mount(Child, {
    props: { projectId: 'project', beforeAction: async () => allowed },
    attachTo: document.body,
    global: { plugins: [Arco.default] },
  });
  mounted.push(h);
  h.vm.open('plan', 'plan');
  await settle();
  click(button('预览重试未送达通知'));
  await settle();
  allowed = false;
  // Direct component call verifies its guard also owns cleanup without parent/list destruction.
  h.vm.invalidate();
  await settle();
  approval.resolve({ previewId: 'late-approved', eligibleCount: 654321, expiresAt: Date.now() + 600000 });
  await settle();
  assert.doesNotMatch(document.body.textContent, /987654|654321/);
  assert.equal(writes, 0);
});
function realHook(rows) {
  const source = readFileSync(new URL('../src/components/pure/ms-table/useTable.ts', import.meta.url), 'utf8');
  const code = ts.transpileModule(source.replace(/^import .*;\s*$/gm, '').replaceAll('import.meta.env.DEV', 'false'), {
    compilerOptions: { module: ts.ModuleKind.CommonJS },
  }).outputText;
  const exports = {};
  vm.runInNewContext(code, {
    exports,
    ref: Vue.ref,
    reactive: Vue.reactive,
    watch: Vue.watch,
    watchEffect: Vue.watchEffect,
    cloneDeep,
    useAppStore: () => ({ innerHeight: 900, pageSize: 10 }),
    useTableStore: () => ({}),
    useListRefresh: () => undefined,
    ...enums,
  });
  return exports.default(async () => structuredClone(rows), {
    data: rows,
    showPagination: false,
    columns: [{ title: '名称', dataIndex: 'name', slotName: 'name' }],
    rowSelectionDisabledConfig: { disabledChildren: true, showChildCheckboxes: true, parentKey: 'parent' },
    msPagination: { current: 1, pageSize: 10, total: rows.length },
  });
}
test('CR-D3-01 real table and useTable: expanded child selection -> second target -> cancel/confirm -> same child focus', async () => {
  const rows = [
    {
      id: 'group',
      name: 'Group',
      type: 'GROUP',
      status: 'PREPARED',
      children: [{ id: 'child', parent: 'group', name: 'Child', num: 100008, type: 'TEST_PLAN', status: 'UNDERWAY' }],
    },
  ];
  let paired = false;
  const calls = [];
  const app = Vue.reactive({ currentProjectId: 'project' });
  const api = {
    getPlanReview: async () => (paired ? linked() : { relationStatus: 'NONE', restricted: false, canPair: true }),
    getPlanReviewCandidates: async () => ({
      items: [
        { id: 'r1', name: 'First target', num: 100003, status: 'PREPARED' },
        { id: 'r2', name: 'Second target', num: 100004, status: 'UNDERWAY' },
      ],
      total: 2,
    }),
    previewPlanReview: async (request) => {
      calls.push(request);
      return {
        previewId: 'preview',
        generation: 'own-g',
        expiresAt: Date.now() + 600000,
        difference: { added: [], removed: [], retained: [], reentered: [] },
      };
    },
    getPlanReviewPreviewItems: async () => ({ items: [] }),
    confirmPlanReview: async () => {
      paired = true;
      return {};
    },
  };
  const scope = Vue.effectScope();
  const state = scope.run(() => realHook(rows));
  const cache = new Map();
  const Table = compile('components/pure/ms-table/base-table.vue', api, app, cache);
  const Toolbar = compile('components/business/plan-review/listToolbar.vue', api, app, cache);
  const expanded = Vue.ref(['group']);
  const Root = {
    setup() {
      return () =>
        Vue.h(
          Table,
          {
            ...state.propsRes.value,
            columns: [{ title: '名称', dataIndex: 'name', slotName: 'name' }],
            selectable: true,
            selectorType: 'checkbox',
            rowKey: 'id',
            expandedKeys: expanded.value,
            actionConfig: { baseAction: [] },
            batchActionPlacement: 'top',
            alwaysShowSelectedCount: true,
            onRowSelectChange: state.propsEvent.value.rowSelectChange,
            onSelectAllChange: state.propsEvent.value.selectAllChange,
          },
          {
            'batch-leading': () =>
              Vue.h(Toolbar, {
                side: 'plan',
                rows: state.propsRes.value.data,
                selectedIds: [...state.propsRes.value.selectedKeys],
                expandedIds: expanded.value,
                project: 'project',
                context: 'page1',
                reload: async () => state.loadList(),
                onClear: () => state.resetSelector(),
              }),
            'name': ({ record }) =>
              Vue.h('span', { 'data-relation-result': `plan-${record.id}`, 'tabindex': -1 }, record.name),
          }
        );
    },
  };
  const h = mount(Root, { attachTo: document.body, global: { plugins: [Arco.default] } });
  mounted.push(h);
  await settle();
  const childRow = [...document.querySelectorAll('tr')].find((x) => x.textContent.includes('Child'));
  assert.ok(childRow);
  click(childRow.querySelector('input[type=checkbox]'));
  await settle();
  assert.deepEqual([...state.propsRes.value.selectedKeys], ['child']);
  click(button('关联评审计划'));
  await settle();
  click([...document.querySelectorAll('.arco-modal td')].find((x) => x.textContent.includes('Second target')));
  await settle();
  click(button('下一步：预览差异'));
  await settle();
  assert.equal(calls[0].planId, 'child');
  assert.equal(calls[0].reviewId, 'r2');
  click([...document.querySelectorAll('.arco-modal button')].filter((x) => x.textContent.trim() === '取消').at(-1));
  await settle();
  assert.equal(paired, false);
  assert.deepEqual([...state.propsRes.value.selectedKeys], ['child']);
  click(button('关联评审计划'));
  await settle();
  click([...document.querySelectorAll('.arco-modal td')].find((x) => x.textContent.includes('Second target')));
  await settle();
  click(button('下一步：预览差异'));
  await settle();
  click([...document.querySelectorAll('.arco-checkbox')].find((x) => x.textContent.includes('已知晓')));
  await settle();
  click(button('确认关联并同步'));
  await settle();
  assert.equal(paired, true);
  assert.deepEqual([...state.propsRes.value.selectedKeys], ['child']);
  assert.equal(document.activeElement?.getAttribute('data-relation-result'), 'plan-child');
  expanded.value = [];
  await settle();
  assert.equal(state.propsRes.value.selectedKeys.size, 0);
  state.propsEvent.value.rowSelectChange(rows[0]);
  await settle();
  assert.deepEqual([...state.propsRes.value.selectedKeys], ['group']);
  assert.match(document.body.textContent, /计划组不能建立关联/);
  state.resetSelector();
  state.propsEvent.value.selectAllChange(enums.SelectAllEnum.CURRENT, true);
  assert.deepEqual([...state.propsRes.value.selectedKeys], ['group']);
  scope.stop();
});
