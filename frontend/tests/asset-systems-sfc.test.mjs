import * as vue from 'vue';

import ts from 'typescript';

import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import test from 'node:test';
import vm from 'node:vm';
import { compileScript, parse } from 'vue/compiler-sfc';

const source = readFileSync(
  new URL('../src/views/test-asset/components/SystemCaseAssets.vue', import.meta.url),
  'utf8'
);
const script = ts.transpileModule(compileScript(parse(source).descriptor, { id: 'systems' }).content, {
  compilerOptions: { module: ts.ModuleKind.CommonJS },
}).outputText;
function fixture(apiOverrides = {}) {
  const mounted = [];
  const queries = [];
  const writes = [];
  let rejectSave = false;
  const systems = [
    {
      id: 'system',
      name: 'System',
      caseCount: 1,
      modules: [{ id: 'module', name: 'Module', parentId: 'NONE', caseCount: 1 }],
    },
  ];
  const api = {
    getAssetSystems: async () => ({ systems, unclassifiedCount: 1 }),
    saveAssetSystem: async (name, id) => {
      if (rejectSave) throw Error('network');
      writes.push({ name, id });
    },
    classifyAssetCases: async (ids, systemId, moduleId) => {
      writes.push({ ids, systemId, moduleId });
      return { count: ids.length };
    },
  };
  Object.assign(api, apiOverrides);
  const exports = {};
  vm.runInNewContext(script, {
    exports,
    require: (name) => {
      if (name === 'vue') return { ...vue, onMounted: (fn) => mounted.push(fn), onBeforeUnmount: () => undefined };
      if (name === 'vue-router')
        return { useRoute: () => ({ query: {} }), useRouter: () => ({ push: () => undefined }) };
      if (name.includes('assetSystems')) return api;
      if (name.includes('api/http'))
        return {
          default: {
            post: async (request) => {
              queries.push(request.data);
              return { list: [{ id: 'asset', name: 'Case', assetCatalogId: 'catalog' }], total: 1 };
            },
          },
        };
      if (name.includes('permission')) return { hasAnyPermission: () => true };
      if (name === '@arco-design/web-vue')
        return { Message: { warning: () => undefined, success: () => undefined }, Modal: { warning: () => undefined } };
      return { TestAssetRouteEnum: {} };
    },
  });
  const state = exports.default.setup({}, { expose: () => undefined });
  return {
    state,
    queries,
    writes,
    init: () => Promise.all(mounted.map((fn) => fn())),
    failSave: () => {
      rejectSave = true;
    },
  };
}
test('system and module selection sends real scope and preserves asset identity', async () => {
  const h = fixture();
  await h.init();
  assert.equal(h.state.rows.value[0].id, 'asset');
  h.state.selectedKey.value = 'module';
  await h.state.loadRows();
  assert.equal(h.queries.at(-1).systemId, 'system');
  assert.equal(h.queries.at(-1).systemModuleId, 'module');
  h.state.openClassification(['asset']);
  h.state.classification.systemId = undefined;
  h.state.classification.moduleId = undefined;
  assert.equal(await h.state.saveClassification(), true);
  assert.equal(h.writes[0].ids[0], 'asset');
  assert.equal(h.writes[0].systemId, undefined);
  h.state.selectedKey.value = '__unclassified__';
  await h.state.loadRows();
  assert.equal(h.queries.at(-1).unclassifiedSystem, true);
});
test('failed directory save retains edited name and dialog for retry', async () => {
  const h = fixture();
  await h.init();
  h.state.openEditor('system');
  h.state.editor.name = 'draft system';
  h.failSave();
  assert.equal(await h.state.saveDirectory(), false);
  assert.equal(h.state.editorVisible.value, true);
  assert.equal(h.state.editor.name, 'draft system');
});

test('directory refresh ignores stale responses and keeps latest loading state', async () => {
  await Promise.all(
    [true, false].map(async (olderFirst) => {
      const { promise: oldRequest, resolve: resolveOld } = Promise.withResolvers();
      const { promise: newRequest, resolve: resolveNew } = Promise.withResolvers();
      const responses = [oldRequest, newRequest];
      const h = fixture({ getAssetSystems: () => responses.shift() });
      const oldRefresh = h.state.refresh();
      const newRefresh = h.state.refresh();
      const oldData = { systems: [{ id: 'old', name: 'old', modules: [] }], unclassifiedCount: 1 };
      const newData = { systems: [{ id: 'new', name: 'new', modules: [] }], unclassifiedCount: 2 };
      if (olderFirst) {
        resolveOld(oldData);
        await oldRefresh;
        assert.equal(h.state.treeLoading.value, true);
        resolveNew(newData);
        await newRefresh;
      } else {
        resolveNew(newData);
        await newRefresh;
        resolveOld(oldData);
        await oldRefresh;
      }
      assert.equal(h.state.systems.value[0].id, 'new');
      assert.equal(h.state.unclassifiedCount.value, 2);
      assert.equal(h.state.treeLoading.value, false);
    })
  );
  const { promise: staleRequest, reject: rejectStale } = Promise.withResolvers();
  const responses = [staleRequest, Promise.resolve({ systems: [], unclassifiedCount: 0 })];
  const h = fixture({ getAssetSystems: () => responses.shift() });
  const staleRefresh = h.state.refresh();
  await h.state.refresh();
  rejectStale(Error('stale network failure'));
  await staleRefresh;
  assert.equal(h.state.failed.value, false);
});
