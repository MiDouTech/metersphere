import * as vue from 'vue';

import ts from 'typescript';

import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import test from 'node:test';
import vm from 'node:vm';
import { compileScript, parse } from 'vue/compiler-sfc';

const source = readFileSync(new URL('../src/views/test-asset/asset-case-detail.vue', import.meta.url), 'utf8');
const compiled = compileScript(parse(source).descriptor, { id: 'msp38' }).content;
const js = ts.transpileModule(compiled, { compilerOptions: { module: ts.ModuleKind.CommonJS } }).outputText;
function setup() {
  let stored = {
    name: 'saved',
    description: 'saved note',
    updateTime: 1,
    attachments: [],
    caseEditType: 'STEP',
    steps: '[]',
  };
  let uploadFails = false;
  const mounted = [];
  const writes = [];
  const route = vue.reactive({ params: { catalogId: 'catalog', caseId: 'case' } });
  const api = {
    getCaseAssetDetail: async () => JSON.parse(JSON.stringify(stored)),
    uploadCaseAssetAttachment: async (catalogId, caseId, file) => {
      if (uploadFails || file.fail) throw new Error('network');
      stored.attachments.push({ id: 'file' });
    },
    deleteCaseAssetAttachment: async () => {
      stored.attachments = [];
    },
    updateCaseAsset: async (request) => {
      writes.push(request);
      if (request.expectedUpdateTime !== stored.updateTime) throw new Error('conflict');
      stored = { ...stored, ...request, updateTime: stored.updateTime + 1 };
    },
  };
  const exports = {};
  const require = (name) => {
    if (name === 'vue')
      return {
        ...vue,
        onMounted: (f) => mounted.push(f),
        onBeforeUnmount() {
          return undefined;
        },
      };
    if (name === 'vue-router')
      return {
        useRoute: () => route,
        useRouter: () => ({
          push() {
            return undefined;
          },
        }),
      };
    if (name.includes('featureCase')) return api;
    if (name.includes('permission')) return { hasAnyPermission: () => true };
    if (name === '@arco-design/web-vue')
      return {
        Message: {
          warning() {
            return undefined;
          },
          success() {
            return undefined;
          },
        },
      };
    return { TestAssetRouteEnum: {} };
  };
  vm.runInNewContext(js, { require, exports });
  const state = exports.default.setup(
    {},
    {
      expose() {
        return undefined;
      },
    }
  );
  return {
    state,
    writes,
    route,
    api,
    init: async () => {
      await Promise.all(mounted.map((f) => f()));
    },
    failUpload: () => {
      uploadFails = true;
    },
    concurrentEdit: () => {
      stored.updateTime++;
    },
    stored: () => stored,
  };
}
test('complete SFC setup preserves every draft field through upload/delete, save and reload', async () => {
  const h = setup();
  await h.init();
  const s = h.state;
  Object.assign(s.form, {
    name: 'draft',
    description: 'note',
    tags: ['tag'],
    prerequisite: 'pre',
    body: '[]',
    expectedResult: 'expected',
    customFields: [{ fieldId: 'f', value: 'v' }],
  });
  const draft = structuredClone(vue.toRaw(s.form));
  await s.uploadAttachments({ target: { files: [{}], value: 'file' } });
  assert.deepEqual(JSON.parse(JSON.stringify(s.form)), draft);
  assert.equal(s.attachments.value.length, 1);
  await s.removeAttachment({ id: 'file' });
  assert.deepEqual(JSON.parse(JSON.stringify(s.form)), draft);
  await s.save();
  assert.equal(h.stored().name, 'draft');
  assert.equal(s.form.description, 'note');
  await s.load();
  assert.equal(s.form.name, 'draft');
});

test('same component route navigation reloads target and ignores a late previous read', async () => {
  const h = setup();
  await h.init();
  const late = Promise.withResolvers();
  h.api.getCaseAssetDetail = async (_catalog, id) =>
    id === 'old' ? late.promise : { name: id, caseEditType: 'TEXT', textDescription: id, attachments: [] };
  h.route.params.caseId = 'old';
  await vue.nextTick();
  h.route.params.caseId = 'new';
  await vue.nextTick();
  await new Promise((resolve) => {
    setImmediate(resolve);
  });
  assert.equal(h.state.form.name, 'new');
  late.resolve({ name: 'stale', steps: '[]' });
  await new Promise((resolve) => {
    setImmediate(resolve);
  });
  assert.equal(h.state.form.name, 'new');
  assert.equal(h.state.form.body, 'new');
});

test('late attachment refresh cannot replace next route state; failed load cannot save', async () => {
  const h = setup();
  await h.init();
  const gate = Promise.withResolvers();
  h.api.getCaseAssetDetail = async (_catalog, id) =>
    id === 'case' ? gate.promise : { name: id, attachments: [{ id: 'new-file' }] };
  const pending = h.state.refreshAttachments();
  h.route.params.caseId = 'next';
  await vue.nextTick();
  await new Promise((resolve) => {
    setImmediate(resolve);
  });
  gate.resolve({ name: 'old', attachments: [{ id: 'old-file' }] });
  await pending;
  assert.equal(h.state.attachments.value[0].id, 'new-file');
  h.api.getCaseAssetDetail = async () => {
    throw new Error('private failure');
  };
  h.route.params.caseId = 'failed';
  await vue.nextTick();
  await new Promise((resolve) => {
    setImmediate(resolve);
  });
  assert.equal(h.state.form.name, '');
  assert.equal(h.state.ready.value, false);
  await h.state.save();
  assert.equal(h.writes.length, 0);
});
test('save completion from previous catalog cannot reload or clear current editor', async () => {
  const h = setup();
  await h.init();
  const gate = Promise.withResolvers();
  const writes = [];
  h.api.updateCaseAsset = async (request) => {
    writes.push(request);
    await gate.promise;
  };
  const pending = h.state.save();
  h.api.getCaseAssetDetail = async (catalog) => ({ name: catalog, attachments: [], updateTime: 9 });
  h.route.params.catalogId = 'next-catalog';
  await vue.nextTick();
  await new Promise((resolve) => {
    setImmediate(resolve);
  });
  gate.resolve();
  await pending;
  assert.equal(writes[0].catalogId, 'catalog');
  assert.equal(writes[0].id, 'case');
  assert.equal(h.state.form.name, 'next-catalog');
  assert.equal(h.state.saving.value, false);
});

test('failed upload keeps draft and concurrent refresh cannot bypass save conflict', async () => {
  const h = setup();
  await h.init();
  h.state.form.name = 'draft';
  h.failUpload();
  await assert.rejects(h.state.uploadAttachments({ target: { files: [{}], value: 'file' } }));
  assert.equal(h.state.form.name, 'draft');
  h.concurrentEdit();
  await h.state.refreshAttachments();
  await assert.rejects(h.state.save(), /conflict/);
  assert.equal(h.writes[0].expectedUpdateTime, 1);
  assert.equal(h.state.form.name, 'draft');
  assert.equal(h.state.saving.value, false);
});

test('mixed upload result refreshes successful files and resets input without discarding draft', async () => {
  const h = setup();
  await h.init();
  h.state.form.name = 'draft';
  const input = { files: [{}, { fail: true }], value: 'selected' };
  await assert.rejects(h.state.uploadAttachments({ target: input }));
  assert.equal(h.state.form.name, 'draft');
  assert.equal(h.state.attachments.value.length, 1);
  assert.equal(input.value, '');
});
