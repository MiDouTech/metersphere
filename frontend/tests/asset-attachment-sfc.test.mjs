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
    if (name === 'vue') return { ...vue, onMounted: (f) => mounted.push(f) };
    if (name === 'vue-router')
      return {
        useRoute: () => ({ params: { catalogId: 'catalog', caseId: 'case' } }),
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
