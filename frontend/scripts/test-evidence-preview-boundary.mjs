import ts from 'typescript';

import assert from 'node:assert/strict';
import fs from 'node:fs/promises';
import test from 'node:test';
import vm from 'node:vm';

const text = await fs.readFile(new URL('../src/views/test-asset/catalog.vue', import.meta.url), 'utf8');
const ast = ts.createSourceFile(
  'catalog.ts',
  text.match(/<script setup lang="ts">([\s\S]*?)<\/script>/)[1],
  ts.ScriptTarget.Latest,
  true
);
const names = new Set(['clearPreview', 'previewEvidence', 'downloadEvidence']);
const methods = ast.statements
  .filter((n) => ts.isFunctionDeclaration(n) && names.has(n.name?.text))
  .map((n) => n.getText(ast))
  .join('\n');
test('evidence preview from previous project does not reappear after project change', async () => {
  let done;
  const pending = new Promise((resolve) => {
    done = resolve;
  });
  const appStore = { currentProjectId: 'first' };
  const previewVisible = { value: false };
  const previewUrl = { value: '' };
  const previewText = { value: '' };
  const code = `let evidenceRequest=0;${methods};({previewEvidence,clearPreview})`;
  const api = vm.runInNewContext(
    ts.transpileModule(code, { compilerOptions: { target: ts.ScriptTarget.ES2022 } }).outputText,
    {
      appStore,
      previewVisible,
      previewUrl,
      previewText,
      Blob,
      URL: { createObjectURL: () => 'blob:old', revokeObjectURL: () => undefined },
      downloadTestAssetEvidence: () => pending,
      downloadByteFile: () => undefined,
    }
  );
  const run = api.previewEvidence({ id: 'PERSONAL:proof', name: 'proof.png' });
  appStore.currentProjectId = 'second';
  api.clearPreview();
  done(new Uint8Array([1]));
  await run;
  assert.equal(previewVisible.value, false);
  assert.equal(previewUrl.value, '');
});
