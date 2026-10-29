import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { createRequire } from 'node:module';
import { test } from 'node:test';
import vm from 'node:vm';
import ts from 'typescript';

const require = createRequire(import.meta.url);
const source = readFileSync(new URL('../src/hooks/useModal.ts', import.meta.url), 'utf8');
const code = ts.transpileModule(source, {
  compilerOptions: { module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2020 },
}).outputText;

function modalHarness() {
  const dialogs = [];
  const refs = [];
  const exported = {};
  const capture = (options) => dialogs.push(options);
  const modal = Object.fromEntries(['info', 'success', 'warning', 'error'].map((kind) => [kind, capture]));
  vm.runInNewContext(code, {
    exports: exported,
    require(name) {
      if (name === 'vue')
        return {
          ref(value) {
            const result = require('vue').ref(value);
            refs.push(result);
            return result;
          },
        };
      if (name === '@arco-design/web-vue') return { Modal: modal };
      if (name === '@/hooks/useI18n') return { useI18n: () => ({ t: (key) => key }) };
      throw new Error(`Unexpected hook dependency: ${name}`);
    },
  });
  return { api: exported.default(), dialogs, refs };
}

async function confirm(dialog) {
  const decisions = [];
  dialog.onBeforeOk((closed) => decisions.push(closed));
  await new Promise((resolve) => setImmediate(resolve));
  return decisions;
}

test('successful confirmation closes once and releases loading', async () => {
  const { api, dialogs, refs } = modalHarness();
  let calls = 0;
  api.openModal({
    type: 'warning',
    onBeforeOk: async () => {
      calls += 1;
    },
  });
  assert.deepEqual(await confirm(dialogs[0]), [true]);
  assert.equal(calls, 1);
  assert.equal(refs[0].value, false);
});

test('failed request keeps the dialog open, releases loading and allows retry', async () => {
  const { api, dialogs, refs } = modalHarness();
  let attempts = 0;
  api.openModal({
    type: 'error',
    onBeforeOk: async () => {
      if (++attempts === 1) throw new Error('request rejected');
    },
  });
  assert.deepEqual(await confirm(dialogs[0]), [false]);
  assert.equal(refs[0].value, false);
  assert.deepEqual(await confirm(dialogs[0]), [true]);
  assert.equal(attempts, 2);
});

test('synchronous callback errors are contained without closing', async () => {
  const { api, dialogs, refs } = modalHarness();
  api.openModal({
    type: 'error',
    onBeforeOk: () => {
      throw new Error('validation failed');
    },
  });
  assert.deepEqual(await confirm(dialogs[0]), [false]);
  assert.equal(refs[0].value, false);
});

test('delete modal uses the same rejection contract', async () => {
  const { api, dialogs, refs } = modalHarness();
  api.openDeleteModal({
    title: 'Delete',
    content: 'Confirm',
    onBeforeOk: async () => {
      throw new Error('denied');
    },
  });
  assert.deepEqual(await confirm(dialogs[0]), [false]);
  assert.equal(refs[0].value, false);
});

test('pending request prevents cancellation and only closes after settlement', async () => {
  const { api, dialogs, refs } = modalHarness();
  let release;
  const pending = new Promise((resolve) => {
    release = resolve;
  });
  api.openModal({ type: 'warning', onBeforeOk: () => pending });
  const decisions = [];
  dialogs[0].onBeforeOk((closed) => decisions.push(closed));
  assert.equal(refs[0].value, true);
  assert.equal(dialogs[0].onBeforeCancel(), false);
  assert.deepEqual(decisions, []);
  release();
  await new Promise((resolve) => setImmediate(resolve));
  assert.deepEqual(decisions, [true]);
  assert.equal(refs[0].value, false);
});
