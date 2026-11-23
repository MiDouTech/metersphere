import ts from 'typescript';

import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import test from 'node:test';
import { runInNewContext } from 'node:vm';

const source = await readFile(new URL('../src/utils/businessAssetTree.ts', import.meta.url), 'utf8');
const javascript = ts.transpileModule(source, {
  compilerOptions: { target: ts.ScriptTarget.ES2022, module: ts.ModuleKind.ES2022 },
}).outputText;
const { businessAssetTree, matchesBusinessCategory } = await import(
  `data:text/javascript;base64,${Buffer.from(javascript).toString('base64')}`
);
const row = (id, moduleKey, pageKeys = ['page'], branchCount = 2) => ({
  id,
  systemKey: 'system',
  moduleKey,
  flowKey: 'flow',
  pageKeys,
  branchCount,
});
test('classification matches exact module rather than a shared prefix', () => {
  assert.equal(matchesBusinessCategory(row('1', 'order-detail'), JSON.stringify(['system', 'order'])), false);
  assert.equal(matchesBusinessCategory(row('1', 'order'), JSON.stringify(['system', 'order'])), true);
});
test('same document counts once at its parent and exposes its related pages as branches', () => {
  const tree = businessAssetTree([row('1', 'order', ['create', 'approve'], 3)]);
  assert.match(tree[0].title, /1 文档 \/ 3 分支/);
  assert.equal(tree[0].children[0].children[0].children.length, 2);
  assert.equal(
    matchesBusinessCategory(
      row('1', 'order', ['create', 'approve']),
      JSON.stringify(['system', 'order', 'flow', 'approve'])
    ),
    true
  );
});
test('unclassified assets remain discoverable and malformed keys cannot broaden scope', () => {
  const asset = { ...row('1', ''), systemKey: '', flowKey: '', pageKeys: [] };
  assert.match(businessAssetTree([asset])[0].title, /待归类/);
  assert.equal(matchesBusinessCategory(asset, JSON.stringify([''])), true);
  assert.equal(matchesBusinessCategory(asset, '{invalid'), false);
});

// Execute the actual SFC edit function against deferred HTTP responses.
const configurationSfc = await readFile(
  new URL('../src/views/test-asset/shared-configuration.vue', import.meta.url),
  'utf8'
);
const script = configurationSfc.match(/<script setup lang="ts">([\s\S]*?)<\/script>/)[1];
const ast = ts.createSourceFile('configuration.ts', script, ts.ScriptTarget.Latest, true);
const editNode = ast.statements.find((node) => ts.isFunctionDeclaration(node) && node.name?.text === 'edit');
assert.ok(editNode, 'Configuration editor must provide its actual async edit implementation');
const editJavascript = ts.transpileModule(editNode.getText(ast), {
  compilerOptions: { target: ts.ScriptTarget.ES2022 },
}).outputText;

function configurationEditor() {
  const state = {
    editorGeneration: 0,
    contentLoading: { value: false },
    originalKind: { value: '' },
    previousKind: { value: 'PLAIN' },
    previousSecretType: { value: 'ACCOUNT_PASSWORD' },
    keepSecret: { value: false },
    secretType: { value: 'OTHER' },
    resetFields: () => undefined,
    app: { currentProjectId: 'project-a' },
    form: { value: {} },
    editor: { value: true },
    saveError: { value: '' },
  };
  const pending = [];
  state.empty = () => ({ value: '' });
  state.create = () => {
    state.editorGeneration += 1;
    state.form.value = {};
    state.editor.value = true;
  };
  state.MSR = {
    get: () =>
      new Promise((resolve, reject) => {
        pending.push({ resolve, reject });
      }),
  };
  state.businessAssetError = () => 'safe error';
  // `with` supplies reactive bindings, so the tested function sees the current generation.
  const edit = runInNewContext(`with (state) { ${editJavascript}; edit; }`, { state });
  return { state, pending, edit };
}

test('late configuration content and errors cannot overwrite a newer editor', async () => {
  const { state, pending, edit } = configurationEditor();
  const first = edit({ id: 'a', kind: 'PLAIN', currentVersion: 1 });
  const second = edit({ id: 'b', kind: 'PLAIN', currentVersion: 1 });
  pending[1].resolve({ value: 'B content' });
  await second;
  pending[0].resolve({ value: 'A content' });
  await first;
  assert.equal(state.form.value.value, 'B content');
  const staleFailure = edit({ id: 'c', kind: 'PLAIN', currentVersion: 1 });
  state.create();
  pending[2].reject(new Error('private technical message'));
  await staleFailure;
  assert.equal(state.saveError.value, '');
});

test('configuration response is discarded after project switch or editor close', async () => {
  const { state, pending, edit } = configurationEditor();
  const first = edit({ id: 'a', kind: 'PLAIN', currentVersion: 1 });
  state.app.currentProjectId = 'project-b';
  pending[0].resolve({ value: 'A content' });
  await first;
  assert.equal(state.form.value.value, '');
  const closed = edit({ id: 'b', kind: 'PLAIN', currentVersion: 1 });
  state.editor.value = false;
  pending[1].resolve({ value: 'closed content' });
  await closed;
  assert.equal(state.form.value.value, '');
});

test('plain content loading blocks editing until the current response completes', async () => {
  const { state, pending, edit } = configurationEditor();
  const reading = edit({ id: 'a', kind: 'PLAIN', currentVersion: 1 });
  assert.equal(state.contentLoading.value, true);
  pending[0].resolve({ value: 'original' });
  await reading;
  assert.equal(state.contentLoading.value, false);
});
