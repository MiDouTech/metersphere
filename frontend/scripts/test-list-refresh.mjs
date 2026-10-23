import ts from 'typescript';

import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import test from 'node:test';
import vm from 'node:vm';

const source = readFileSync(new URL('../src/utils/listRefreshGate.ts', import.meta.url), 'utf8');
const compiled = ts.transpileModule(source, { compilerOptions: { module: ts.ModuleKind.CommonJS } }).outputText;
const context = { exports: {}, Date, Number };
vm.runInNewContext(compiled, context);
const createGate = context.exports.default;

test('focus and visibility events coalesce even when a request finishes immediately', async () => {
  let calls = 0;
  let time = 0;
  const gate = createGate(
    async () => {
      calls += 1;
    },
    () => true,
    () => time
  );
  await gate.run();
  await gate.run();
  assert.equal(calls, 1);
  time = 1000;
  await gate.run();
  assert.equal(calls, 2);
});

test('hidden/offline/editing readiness pauses refresh; returning ready resumes it', async () => {
  let ready = false;
  let calls = 0;
  const gate = createGate(
    async () => {
      calls += 1;
    },
    () => ready
  );
  await gate.run();
  assert.equal(calls, 0);
  ready = true;
  await gate.run();
  assert.equal(calls, 1);
});

test('slow requests do not overlap and failed requests can retry', async () => {
  let reject;
  let calls = 0;
  let time = 0;
  const gate = createGate(
    () => {
      calls += 1;
      return new Promise((resolve, fail) => {
        reject = fail;
      });
    },
    () => true,
    () => time
  );
  const first = gate.run();
  time = 60000;
  await gate.run();
  assert.equal(calls, 1);
  reject(new Error('network failure'));
  await assert.rejects(first, /network failure/);
  const retry = gate.run();
  assert.equal(calls, 2);
  reject(new Error('server failure'));
  await assert.rejects(retry, /server failure/);
});

test('unmounted lists never refresh again', async () => {
  let calls = 0;
  const gate = createGate(
    async () => {
      calls += 1;
    },
    () => true
  );
  gate.dispose();
  await gate.run();
  assert.equal(calls, 0);
});

test('browser lifecycle pauses offline, hidden and editing lists and removes every listener', async () => {
  const lifecycle = {};
  const listeners = new Map();
  let time = 0;
  let calls = 0;
  let dialog = false;
  let paneVisible = true;
  let interval;
  class Element {
    getClientRects() {
      return paneVisible ? [1] : [];
    }
  }
  const surface = {
    addEventListener: (name, handler) => listeners.set(name, handler),
    removeEventListener: (name, handler) => {
      assert.equal(listeners.get(name), handler);
      listeners.delete(name);
    },
  };
  const browser = {
    exports: {},
    require: (name) => {
      if (name === 'vue')
        return {
          getCurrentInstance: () => ({ proxy: { $el: new Element() } }),
          ...Object.fromEntries(
            ['onMounted', 'onActivated', 'onDeactivated', 'onUnmounted'].map((key) => [
              key,
              (callback) => {
                lifecycle[key] = callback;
              },
            ])
          ),
        };
      if (name.endsWith('httpMessage')) return { isSessionExpiredHandling: () => false };
      if (name.endsWith('listRefreshGate'))
        return { default: (refresh, ready) => createGate(refresh, ready, () => time) };
      throw new Error(name);
    },
    window: surface,
    document: {
      ...surface,
      visibilityState: 'visible',
      activeElement: null,
      querySelectorAll: () => (dialog ? [{ getClientRects: () => [1] }] : []),
    },
    navigator: { onLine: false },
    HTMLElement: Element,
    setInterval: (callback) => {
      interval = callback;
      return 1;
    },
    clearInterval: (id) => {
      assert.equal(id, 1);
      interval = undefined;
    },
  };
  const hook = readFileSync(new URL('../src/hooks/useListRefresh.ts', import.meta.url), 'utf8');
  vm.runInNewContext(
    ts.transpileModule(hook, { compilerOptions: { module: ts.ModuleKind.CommonJS } }).outputText,
    browser
  );
  const options = { allowReadOnlyOverlay: false };
  browser.exports.default(
    async () => {
      calls += 1;
    },
    () => true,
    60000,
    options
  );
  lifecycle.onMounted();
  lifecycle.onActivated();
  await listeners.get('focus')();
  assert.equal(calls, 0);
  browser.navigator.onLine = true;
  await listeners.get('online')();
  await listeners.get('focus')();
  assert.equal(calls, 1);
  time += 2000;
  browser.document.visibilityState = 'hidden';
  await interval();
  assert.equal(calls, 1);
  browser.document.visibilityState = 'visible';
  paneVisible = false;
  await interval();
  assert.equal(calls, 1);
  paneVisible = true;
  dialog = true;
  await interval();
  assert.equal(calls, 1);
  options.allowReadOnlyOverlay = true;
  await interval();
  assert.equal(calls, 2);
  options.allowReadOnlyOverlay = false;
  time += 2000;
  await interval();
  assert.equal(calls, 2);
  dialog = false;
  await interval();
  assert.equal(calls, 3);
  time += 2000;
  lifecycle.onDeactivated();
  await interval();
  assert.equal(calls, 3);
  lifecycle.onUnmounted();
  assert.equal(listeners.size, 0);
  assert.equal(interval, undefined);
});
