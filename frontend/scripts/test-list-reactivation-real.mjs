import { cloneDeep } from 'lodash-es';
import axios from 'axios';
import dayjs from 'dayjs';

import ts from 'typescript';

import { JSDOM } from 'jsdom';
import assert from 'node:assert/strict';
import { execFileSync } from 'node:child_process';
import { readFileSync } from 'node:fs';
import test from 'node:test';
import vm from 'node:vm';

/* global globalThis */
const dom = new JSDOM('<!doctype html><html><body></body></html>', { pretendToBeVisual: true });
['window', 'document', 'Element', 'HTMLElement', 'SVGElement'].forEach((key) => {
  globalThis[key] = dom.window[key];
});
dom.window.HTMLElement.prototype.getClientRects = () => [1];
const vue = await import('vue');
const tick = async () => {
  await vue.nextTick();
  await new Promise((resolve) => {
    setImmediate(resolve);
  });
};
const source = (file) => {
  if (process.env.MSP_REFRESH_BASELINE_REF && ['utils/listRefreshGate', 'hooks/useListRefresh'].includes(file)) {
    return execFileSync('git', ['show', `${process.env.MSP_REFRESH_BASELINE_REF}:frontend/src/${file}.ts`], {
      encoding: 'utf8',
    });
  }
  return readFileSync(new URL(`../src/${file}.ts`, import.meta.url), 'utf8');
};

test('real KeepAlive, useTable and Axios recover a route-cancelled list on immediate return', async () => {
  const modules = new Map();
  let time = 0;
  const appStore = { pageSize: 10, currentProjectId: 'A', innerHeight: 1000 };
  function load(file) {
    if (modules.has(file)) return modules.get(file);
    const exports = {};
    modules.set(file, exports);
    vm.runInNewContext(
      ts.transpileModule(source(file).replaceAll('import.meta.env.DEV', 'false'), {
        compilerOptions: { module: ts.ModuleKind.CommonJS },
      }).outputText,
      {
        ...vue,
        exports,
        window: dom.window,
        document: dom.window.document,
        navigator: dom.window.navigator,
        HTMLElement: dom.window.HTMLElement,
        setInterval: () => 1,
        clearInterval: () => undefined,
        console: { log: () => undefined },
        require(name) {
          if (name === 'vue') return vue;
          if (name === 'axios') return { default: axios };
          if (name === 'lodash-es') return { cloneDeep };
          if (name === 'dayjs') return { default: dayjs };
          if (name === '@/store') return { useAppStore: () => appStore, useTableStore: () => ({}) };
          if (name === '@/utils/httpMessage') return { isSessionExpiredHandling: () => false };
          if (name === '@/utils/is') return { isFunction: (value) => typeof value === 'function' };
          if (name === '@/utils/listRefreshGate') {
            const create = load('utils/listRefreshGate').default;
            return { default: (refresh, ready) => create(refresh, ready, () => time) };
          }
          if (name === '@/hooks/useListRefresh') return load('hooks/useListRefresh');
          if (name === '@/enums/tableEnum') return load('enums/tableEnum');
          throw new Error(name);
        },
      }
    );
    return exports;
  }
  const canceler = new (load('api/http/axiosCancel').AxiosCanceler)();
  const client = axios.create();
  client.interceptors.request.use((config) => {
    canceler.addPending(config);
    return config;
  });
  client.interceptors.response.use((response) => {
    canceler.completePending(response.config);
    return response;
  });
  let slow = false;
  let requests = 0;
  const active = vue.ref(true);
  let table;
  const Page = {
    name: 'Plan',
    setup() {
      table = load('components/pure/ms-table/useTable').default(async () => {
        const response = await client.post(
          '/test-plan/page',
          {},
          {
            adapter: (config) => {
              requests += 1;
              if (slow)
                return new Promise((resolve, reject) => {
                  config.cancelToken.subscribe(reject);
                });
              return Promise.resolve({ data: { list: [{ id: 'plan' }], total: 1, current: 1 }, status: 200, config });
            },
          }
        );
        return response.data;
      });
      const refresh = table.loadList;
      // The production plan owns this hook in addition to useTable's hook.
      load('hooks/useListRefresh').default(refresh, () => true);
      vue.onBeforeMount(refresh);
      vue.onDeactivated(() => canceler.removeAllPending());
      return () => vue.h('div', table.propsRes.value.data.map((row) => row.id).join(','));
    },
  };
  const root = document.createElement('div');
  document.body.append(root);
  const app = vue.createApp({
    setup: () => () =>
      vue.h(vue.KeepAlive, null, {
        default: () => (active.value ? vue.h(Page) : vue.h('div', 'review')),
      }),
  });
  try {
    app.mount(root);
    await tick();
    assert.equal(root.textContent, 'plan');
    active.value = false;
    await tick();
    slow = true;
    active.value = true;
    await tick();
    assert.equal(table.propsRes.value.loading, true);
    active.value = false;
    await tick();
    assert.equal(table.propsRes.value.data.length, 0, 'actual useTable cancellation clears its data');
    slow = false;
    time = 100;
    active.value = true;
    await tick();
    assert.equal(root.textContent, 'plan');
    assert.ok(requests >= 3, 'returning dispatched a replacement list request');
  } finally {
    app.unmount();
    root.remove();
  }
});
