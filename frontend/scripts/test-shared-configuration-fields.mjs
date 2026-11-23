import ts from 'typescript';

import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import test from 'node:test';
import { runInNewContext } from 'node:vm';

const s = await readFile(new URL('../src/utils/sharedConfigurationFields.ts', import.meta.url), 'utf8').catch(() => '');
const js = ts.transpileModule(s, {
  compilerOptions: { target: ts.ScriptTarget.ES2022, module: ts.ModuleKind.ES2022 },
}).outputText;
const api = await import(`data:text/javascript;base64,${Buffer.from(js).toString('base64')}`);
test('account password plus extra fields serialize without trimming sensitive values', () => {
  assert.equal(typeof api.encodeSecretFields, 'function');
  const value = api.encodeSecretFields('ACCOUNT_PASSWORD', [
    { name: 'username', value: 'user' },
    { name: 'password', value: ' secret ' },
    { name: ' tenant ', value: 't' },
  ]);
  assert.equal(JSON.parse(value).fields[1].value, ' secret ');
  assert.equal(JSON.parse(value).fields[2].name, 'tenant');
});
test('invalid fields cannot be submitted', () => {
  assert.equal(typeof api.encodeSecretFields, 'function');
  [
    [],
    [{ name: '', value: 'x' }],
    [{ name: 'x', value: ' ' }],
    [
      { name: 'x', value: 'a' },
      { name: ' x ', value: 'b' },
    ],
    Array.from({ length: 21 }, (_, i) => ({ name: String(i), value: 'v' })),
  ].forEach((fields) => {
    assert.throws(() => api.encodeSecretFields('OTHER', fields));
  });
  assert.throws(() => api.encodeSecretFields('ACCOUNT_PASSWORD', [{ name: 'username', value: 'u' }]));
});
const configuration = await readFile(
  new URL('../src/views/test-asset/shared-configuration.vue', import.meta.url),
  'utf8'
);
const script = configuration.match(/<script setup lang="ts">([\s\S]*?)<\/script>/)[1];
const ast = ts.createSourceFile('configuration.ts', script, ts.ScriptTarget.Latest, true);
const body = ast.statements
  .filter((node) => !ts.isImportDeclaration(node))
  .map((node) => node.getText(ast))
  .join('\n');
function editorHarness() {
  const submissions = [];
  const confirmations = [];
  const context = {
    btoa: (value) => Buffer.from(value, 'binary').toString('base64'),
    ref: (value) => ({ value }),
    computed: (getter) => ({
      get value() {
        return getter();
      },
    }),
    watch: () => undefined,
    useAppStore: () => ({ currentProjectId: 'fixture-project' }),
    useRouter: () => ({ push: () => undefined }),
    hasAnyPermission: () => true,
    encodeSecretFields: api.encodeSecretFields,
    businessAssetError: () => 'safe error',
    Message: { success: () => undefined },
    Modal: { confirm: (options) => confirmations.push(options) },
    MSR: {
      post: async (request) => {
        submissions.push(request.data);
      },
      get: async () => [],
    },
  };
  const state = runInNewContext(
    `${
      ts.transpileModule(body, { compilerOptions: { target: ts.ScriptTarget.ES2022 } }).outputText
    }\n;({ create, edit, save, form, username, password, extraFields, secretType, keepSecret, closeEditor, changeKind, changeSecretType, saveError, upload });`,
    context
  );
  return { state, submissions, confirmations };
}
test('actual editor submits structured fields and clears sensitive state after save', async () => {
  const { state, submissions } = editorHarness();
  state.create();
  state.form.value.kind = 'SECRET';
  state.changeKind();
  state.username.value = 'fixture-user';
  state.password.value = 'fixture-password';
  state.extraFields.value = [{ name: 'tenant', value: 'fixture-tenant' }];
  await state.save();
  assert.equal(submissions.length, 1);
  assert.equal(submissions[0].secretType, 'ACCOUNT_PASSWORD');
  assert.equal(JSON.parse(submissions[0].value).fields.length, 3);
  assert.equal(state.password.value, '');
  assert.equal(state.extraFields.value.length, 0);
});
test('actual editor retains old secrets without reading plaintext and rejects duplicate fields', async () => {
  const { state, submissions } = editorHarness();
  await state.edit({ id: 'fixture', kind: 'SECRET', secretType: 'OTHER', currentVersion: 1 });
  await state.save();
  assert.equal(submissions[0].keepSecret, true);
  assert.equal(submissions[0].value, '');
  state.create();
  state.form.value.kind = 'SECRET';
  state.changeKind();
  state.username.value = 'u';
  state.password.value = 'p';
  state.extraFields.value = [{ name: ' username ', value: 'duplicate' }];
  await state.save();
  assert.equal(submissions.length, 1);
  assert.ok(state.saveError.value);
  state.closeEditor();
  assert.equal(state.username.value, '');
});
test('canceling sensitive category switch preserves unsaved contents', () => {
  const { state, confirmations } = editorHarness();
  state.create();
  state.form.value.kind = 'SECRET';
  state.changeKind();
  state.username.value = 'fixture-user';
  state.secretType.value = 'OTHER';
  state.changeSecretType();
  assert.equal(confirmations.length, 1);
  confirmations[0].onCancel();
  assert.equal(state.secretType.value, 'ACCOUNT_PASSWORD');
  assert.equal(state.username.value, 'fixture-user');
});

test('latest file selection wins and an invalid selection cancels earlier bytes', async () => {
  const { state } = editorHarness();
  state.create();
  state.form.value.kind = 'DOCUMENT';
  const pending = [];
  const file = (name) => ({
    target: {
      files: [
        {
          name,
          size: 10,
          arrayBuffer: () =>
            new Promise((resolve) => {
              pending.push(resolve);
            }),
        },
      ],
    },
  });
  const first = state.upload(file('first.txt'));
  const second = state.upload(file('second.txt'));
  pending[1](new TextEncoder().encode('second').buffer);
  await second;
  pending[0](new TextEncoder().encode('first').buffer);
  await first;
  assert.equal(state.form.value.fileName, 'second.txt');
  const third = state.upload(file('third.txt'));
  await state.upload({ target: { files: [{ name: 'oversized.txt', size: 3000000 }] } });
  pending[2](new TextEncoder().encode('third').buffer);
  await third;
  assert.equal(state.form.value.value, '');
  assert.equal(state.form.value.fileName, '');
});
