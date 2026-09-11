import ts from 'typescript';

import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import test from 'node:test';
import vm from 'node:vm';

const source = readFileSync(new URL('../src/utils/imageClipboard.ts', import.meta.url), 'utf8');
const compiled = ts.transpileModule(source, { compilerOptions: { module: ts.ModuleKind.CommonJS } }).outputText;

function fixture({ secure = true, broken = false, denied = false, width = 12, height = 8 } = {}) {
  const state = { writes: 0, sourceLoads: 0, dimensions: [], blob: undefined };
  const context = {
    exports: {},
    Promise,
    Error,
    Blob,
    window: { isSecureContext: secure, setTimeout, clearTimeout },
    navigator: {
      clipboard: {
        write: async ([item]) => {
          state.writes += 1;
          if (denied) throw Object.assign(new Error('denied'), { name: 'NotAllowedError' });
          state.blob = await item.data['image/png'];
        },
      },
    },
    ClipboardItem: function ClipboardItem(data) {
      this.data = data;
    },
    Image: class {
      naturalWidth = width;

      naturalHeight = height;

      set src(value) {
        state.sourceLoads += 1;
        queueMicrotask(() => {
          if (broken || !value) this.onerror();
          else this.onload();
        });
      }
    },
    document: {
      createElement: () => ({
        width: 0,
        height: 0,
        getContext() {
          return { drawImage: () => state.dimensions.push([this.width, this.height]) };
        },
        toBlob(callback, type) {
          callback(new Blob(['png-fixture'], { type }));
        },
      }),
    },
  };
  vm.runInNewContext(compiled, context);
  return { ...context.exports, state };
}

test('writes PNG using original image dimensions', async () => {
  const { copyImage, state } = fixture();
  await copyImage(() => 'blob:authorized-image');
  assert.equal(state.writes, 1);
  assert.equal(state.blob.type, 'image/png');
  assert.deepEqual(state.dimensions, [[12, 8]]);
});

test('starts clipboard write before asynchronous source resolution', async () => {
  const { copyImage, state } = fixture();
  let resolveSource;
  const sourcePromise = new Promise((resolve) => {
    resolveSource = resolve;
  });
  const copying = copyImage(() => sourcePromise);
  assert.equal(state.writes, 1);
  assert.equal(state.sourceLoads, 0);
  resolveSource('blob:authorized-image');
  await copying;
});

test('unsupported context does not load an image or write clipboard', async () => {
  const { copyImage, state } = fixture({ secure: false });
  await assert.rejects(
    copyImage(() => 'blob:image'),
    /IMAGE_COPY_UNSUPPORTED/
  );
  assert.equal(state.writes, 0);
  assert.equal(state.sourceLoads, 0);
});

test('image decode failure rejects instead of reporting success', async () => {
  const { copyImage, state } = fixture({ broken: true });
  await assert.rejects(
    copyImage(() => 'blob:bad'),
    /IMAGE_LOAD_FAILED/
  );
  assert.equal(state.blob, undefined);
});

test('permission denial propagates to the safe UI message', async () => {
  const { copyImage } = fixture({ denied: true });
  await assert.rejects(
    copyImage(() => 'blob:image'),
    { name: 'NotAllowedError' }
  );
});

test('oversized images do not allocate a canvas', async () => {
  const { copyImage, state } = fixture({ width: 10000, height: 10000 });
  await assert.rejects(
    copyImage(() => 'blob:large'),
    /IMAGE_LOAD_FAILED/
  );
  assert.deepEqual(state.dimensions, []);
});

test('source authorization failure is not converted to clipboard success', async () => {
  const { copyImage, state } = fixture();
  await assert.rejects(
    copyImage(() => Promise.reject(new Error('forbidden'))),
    /forbidden/
  );
  assert.equal(state.sourceLoads, 0);
  assert.equal(state.blob, undefined);
});
