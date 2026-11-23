import ts from 'typescript';

import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import test from 'node:test';
import { runInNewContext } from 'node:vm';

const sfc = await readFile(new URL('../src/views/test-asset/components/BusinessVersions.vue', import.meta.url), 'utf8');
const source = sfc.match(/<script setup lang="ts">([\s\S]*?)<\/script>/)[1];
const ast = ts.createSourceFile('versions.ts', source, ts.ScriptTarget.Latest, true);
const body = ast.statements
  .filter((node) => !ts.isImportDeclaration(node))
  .map((node) => node.getText(ast))
  .join('\n');
function harness(get) {
  const app = { currentProjectId: 'fixture-project' };
  const context = {
    ref: (value) => ({ value }),
    computed: (getter) => ({
      get value() {
        return getter();
      },
    }),
    watch: () => undefined,
    useAppStore: () => app,
    useRoute: () => ({ query: {} }),
    useRouter: () => ({ push: () => undefined }),
    hasAnyPermission: () => true,
    MSR: { get },
    listBusinessAssets: async () => [{ id: 'asset', title: 'business' }],
    getBusinessAsset: async () => ({ versions: [] }),
    businessAssetError: () => 'safe error',
  };
  const state = runInNewContext(
    `${
      ts.transpileModule(body, { compilerOptions: { target: ts.ScriptTarget.ES2022 } }).outputText
    }\n;({ reload, loadLegacy, assets, error, legacyError, loading, legacy });`,
    context
  );
  return { state, app };
}
test('legacy directory failure cannot hide the normal business version directory', async () => {
  const { state } = harness(async () => {
    throw new Error('legacy failure');
  });
  await Promise.all([state.reload(), state.loadLegacy()]);
  assert.equal(state.assets.value.length, 2);
  assert.equal(state.error.value, '');
  assert.equal(state.legacyError.value, 'safe error');
  assert.equal(state.loading.value, false);
});
test('a previous legacy response cannot overwrite a newer query', async () => {
  const pending = [];
  const { state } = harness(
    () =>
      new Promise((resolve) => {
        pending.push(resolve);
      })
  );
  const first = state.loadLegacy();
  const second = state.loadLegacy();
  pending[1]([{ id: 'new' }]);
  await second;
  pending[0]([{ id: 'old' }]);
  await first;
  assert.equal(state.legacy.value[0].id, 'new');
});

const versionPage = await readFile(new URL('../src/views/test-asset/versions.vue', import.meta.url), 'utf8');
const versionScript = versionPage.match(/<script setup lang="ts">([\s\S]*?)<\/script>/)[1];
const versionAst = ts.createSourceFile('page.ts', versionScript, ts.ScriptTarget.Latest, true);
const initialTab = versionAst.statements.find(
  (node) => ts.isFunctionDeclaration(node) && node.name?.text === 'initialVersionTab'
);
test('legacy asset deep links open their own version directory', () => {
  const pick = runInNewContext(
    `${
      ts.transpileModule(initialTab.getText(versionAst), { compilerOptions: { target: ts.ScriptTarget.ES2022 } })
        .outputText
    };initialVersionTab;`,
    {
      route: { query: { assetType: 'CASE', assetId: 'fixture-case' } },
      canReadBusiness: { value: true },
      canReadConfiguration: { value: true },
      canReadLegacy: { value: true },
    }
  );
  assert.equal(pick(), 'other');
});
const legacyLoadNode = versionAst.statements.find(
  (node) => ts.isFunctionDeclaration(node) && node.name?.text === 'load'
);
const legacyLoadJs = ts.transpileModule(legacyLoadNode.getText(versionAst), {
  compilerOptions: { target: ts.ScriptTarget.ES2022 },
}).outputText;
function legacyVersionHarness() {
  const state = {
    legacyGeneration: 0,
    appStore: { currentProjectId: 'project-a' },
    canReadLegacy: { value: true },
    query: { assetId: 'asset-a', keyword: '', current: 1 },
    versions: { value: [] },
    total: { value: 0 },
    error: { value: '' },
    loading: { value: false },
    businessAssetError: () => 'safe error',
  };
  const pending = [];
  state.pageTestAssetVersions = () =>
    new Promise((resolve, reject) => {
      pending.push({ resolve, reject });
    });
  const load = runInNewContext(`with (state) { ${legacyLoadJs};load; }`, { state });
  return { state, pending, load };
}
test('legacy version rows follow latest asset query even when responses are reversed', async () => {
  const { state, pending, load } = legacyVersionHarness();
  const first = load();
  state.query.assetId = 'asset-b';
  const second = load();
  pending[1].resolve({ list: [{ assetId: 'asset-b' }], total: 1 });
  await second;
  pending[0].resolve({ list: [{ assetId: 'asset-a' }], total: 3 });
  await first;
  assert.equal(state.versions.value[0].assetId, 'asset-b');
  assert.equal(state.total.value, 1);
});
test('project and permission changes discard legacy rows and stale failures', async () => {
  const { state, pending, load } = legacyVersionHarness();
  const first = load();
  state.appStore.currentProjectId = 'project-b';
  const second = load();
  pending[0].reject(new Error('old project failure'));
  await first;
  assert.equal(state.error.value, '');
  assert.equal(state.loading.value, true);
  pending[1].resolve({ list: [{ assetId: 'b' }], total: 1 });
  await second;
  state.canReadLegacy.value = false;
  await load();
  assert.equal(state.versions.value.length, 0);
  assert.equal(state.total.value, 0);
});
