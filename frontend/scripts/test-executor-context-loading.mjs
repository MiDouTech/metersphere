import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import test from 'node:test';
import { runInNewContext } from 'node:vm';
import ts from 'typescript';

const sfc = await readFile(new URL('../src/views/execution/external-executors.vue', import.meta.url), 'utf8');
const source = sfc.match(/<script setup lang="ts">([\s\S]*?)<\/script>/)[1];
const ast = ts.createSourceFile('executor.ts', source, ts.ScriptTarget.Latest, true);
const body = ast.statements
  .filter((n) => !ts.isImportDeclaration(n))
  .map((n) => n.getText(ast))
  .join('\n');
function deferred() {
  let resolve, reject;
  const promise = new Promise((yes, no) => {
    resolve = yes;
    reject = no;
  });
  return { promise, resolve, reject };
}
async function until(predicate) {
  for (let i = 0; i < 30 && !predicate(); i++) await Promise.resolve();
  assert.ok(predicate(), 'request reached the expected asynchronous boundary');
}
function harness(get, assets = async () => [], post = async () => {}) {
  const app = { currentProjectId: 'project' };
  const context = {
    ref: (value) => ({ value }),
    computed: (getter) => ({
      get value() {
        return getter();
      },
    }),
    watch() {},
    onBeforeUnmount() {},
    window: {
      setInterval() {
        return 1;
      },
      clearInterval() {},
    },
    useAppStore: () => app,
    hasAnyPermission: () => true,
    MSR: { get, post },
    listBusinessAssets: assets,
    businessAssetError: (failure, fallback) => fallback,
  };
  const state = runInNewContext(
    ts.transpileModule(body, { compilerOptions: { target: ts.ScriptTarget.ES2022 } }).outputText +
      ';({open,refreshDetail,loadCommandHistory,loadEventHistory,correct,updateContext,selected,visible,error,availableAssets,contextAssetId,contextReason});',
    context
  );
  state.selected.value = { id: 'old' };
  state.visible.value = true;
  return { state, app };
}
for (const action of ['refreshDetail', 'loadCommandHistory', 'loadEventHistory']) {
  test(`${action}: current session failures remain visible`, async () => {
    const { state } = harness(async () => {
      throw new Error('current request');
    });
    await state[action](2);
    assert.notEqual(state.error.value, '');
  });
}
for (const action of ['refreshDetail', 'loadCommandHistory', 'loadEventHistory']) {
  test(`${action}: failure from a previous project/session cannot overwrite current error`, async () => {
    const wait = deferred();
    const { state, app } = harness(() => wait.promise);
    const pending = state[action](2);
    app.currentProjectId = 'new-project';
    state.selected.value = { id: 'new' };
    state.error.value = 'current-error';
    wait.reject(new Error('old request'));
    await pending;
    assert.equal(state.error.value, 'current-error');
  });
}
test('an old context failure cannot overwrite a newly selected session', async () => {
  const wait = deferred();
  let started = false;
  const { state } = harness(
    async () => ({ commands: [], events: [] }),
    () => {
      started = true;
      return wait.promise;
    }
  );
  const pending = state.open({ id: 'old' });
  await until(() => started);
  state.selected.value = { id: 'new' };
  state.error.value = 'current-error';
  wait.reject(new Error('old context'));
  await pending;
  assert.equal(state.error.value, 'current-error');
});
test('reopening the same session invalidates its previous context request', async () => {
  const old = deferred();
  let calls = 0;
  const { state } = harness(
    async () => ({ commands: [], events: [] }),
    () => (++calls === 1 ? old.promise : Promise.resolve([{ id: 'current-asset' }]))
  );
  const pending = state.open({ id: 'same' });
  await until(() => calls === 1);
  await state.open({ id: 'same' });
  state.error.value = 'current-error';
  old.reject(new Error('old context'));
  await pending;
  assert.equal(state.error.value, 'current-error');
  assert.equal(state.availableAssets.value[0].id, 'current-asset');
});
test('project change while detail is loading does not launch context reads in the new project', async () => {
  const wait = deferred();
  let assetCalls = 0;
  const { state, app } = harness(
    () => wait.promise,
    async () => {
      assetCalls++;
      return [];
    }
  );
  const pending = state.open({ id: 'old' });
  app.currentProjectId = 'new-project';
  state.selected.value = { id: 'new' };
  wait.resolve({ commands: [], events: [] });
  await pending;
  assert.equal(assetCalls, 0);
});
test('late correction failure cannot overwrite another session error', async () => {
  const wait = deferred();
  const { state } = harness(
    async () => ({}),
    async () => [],
    () => wait.promise
  );
  const pending = state.correct();
  state.selected.value = { id: 'new' };
  state.error.value = 'current-error';
  wait.reject(new Error('old write'));
  await pending;
  assert.equal(state.error.value, 'current-error');
});
test('closing a session invalidates its pending detail failure', async () => {
  const wait = deferred();
  const { state } = harness(() => wait.promise);
  const pending = state.refreshDetail();
  state.visible.value = false;
  state.error.value = 'current-error';
  wait.reject(new Error('closed context'));
  await pending;
  assert.equal(state.error.value, 'current-error');
});
test('late context update failure cannot overwrite another session error', async () => {
  const wait = deferred();
  const { state } = harness(
    async () => ({}),
    async () => [],
    () => wait.promise
  );
  state.availableAssets.value = [{ id: 'asset', currentVersionId: 'version' }];
  state.contextAssetId.value = 'asset';
  const pending = state.updateContext();
  state.selected.value = { id: 'new' };
  state.error.value = 'current-error';
  wait.reject(new Error('old update'));
  await pending;
  assert.equal(state.error.value, 'current-error');
});
