import ts from 'typescript';

import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import test from 'node:test';

const source = readFileSync(new URL('../src/api/modules/test-data-assets.ts', import.meta.url), 'utf8');
const calls = [];
const client = Object.fromEntries(
  ['get', 'post', 'put', 'delete', 'uploadFile'].map((method) => [
    method,
    (...args) => {
      calls.push({ method, args });
      return Promise.resolve({});
    },
  ])
);
const js = ts.transpileModule(source, {
  compilerOptions: { module: ts.ModuleKind.CommonJS, esModuleInterop: true },
}).outputText;
const api = {};
// eslint-disable-next-line no-new-func
new Function('exports', 'require', js)(api, () => ({ __esModule: true, default: client }));
test('global data APIs need no current project and preserve version locks', async () => {
  await api.listDataAssets({ page: 1, pageSize: 20 });
  assert.deepEqual(calls.pop().args[0].params, { page: 1, pageSize: 20 });
  await api.saveDataAsset({ id: 'a/b', expectedRevision: 3, content: { rows: [['a,b']] } });
  const save = calls.pop();
  assert.equal(save.method, 'put');
  assert.equal(save.args[0].url, '/test-data-assets/a%2Fb');
  assert.equal(save.args[0].data.expectedRevision, 3);
  await api.deleteDataAsset({ id: 'a', revision: 4 });
  assert.equal(calls.pop().args[0].url, '/test-data-assets/a?expectedRevision=4');
});
test('content requests preserve selected immutable version', async () => {
  await api.getDataContent('a', 'v1');
  assert.equal(calls.pop().args[0].params.versionId, 'v1');
});
test('uploads use multipart transport rather than JSON request transformation', async () => {
  const file = { name: 'sample.csv' };
  await api.uploadDataAsset(file, 'category');
  const upload = calls.pop();
  assert.equal(upload.method, 'uploadFile');
  assert.equal(upload.args[1].fileList[0], file);
  assert.equal(upload.args[0].params.categoryId, 'category');
});
test('batch retries send only explicit selected items with revision', async () => {
  const items = [{ id: 'failed-item', expectedRevision: 8, delete: true }];
  await api.batchDataAssets(items);
  assert.deepEqual(calls.pop().args[0].data, { items });
});
test('replacement upload uses PUT and explicit revision plus stable caller request identity', async () => {
  const file = { name: 'replacement.csv' };
  await api.replaceDataFile('data/id', file, 4, 'stable-retry');
  const call = calls.pop();
  assert.equal(call.method, 'uploadFile');
  assert.equal(call.args[0].method, 'PUT');
  assert.equal(call.args[0].url, '/test-data-assets/data%2Fid/uploads');
  assert.deepEqual(call.args[0].params, { expectedRevision: 4, requestId: 'stable-retry' });
  assert.equal(call.args[1].fileList[0], file);
});
