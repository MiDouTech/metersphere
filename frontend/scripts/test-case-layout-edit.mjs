import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import test from 'node:test';
import ts from 'typescript';
const source = readFileSync(
  new URL('../src/views/case-management/caseManagementFeature/components/batchEditModal.vue', import.meta.url),
  'utf8'
);
const start = source.indexOf('  async function confirmHandler()');
const end = source.indexOf('\n  watch(', start);
assert.ok(start >= 0 && end > start);
const js = ts.transpileModule(source.slice(start, end), {
  compilerOptions: { target: ts.ScriptTarget.ES2020 },
}).outputText;
function harness(field, fail = false) {
  const calls = [],
    events = [];
  const form = {
    value: {
      selectedAttrsId: field,
      executionValue: field === 'executionUser' ? 'real-user-id' : 'BLOCKED',
      tags: ['stale-tag'],
    },
  };
  const props = {
    batchParams: { selectedIds: ['one'], selectAll: true, excludeIds: ['excluded'] },
    activeFolder: 'folder',
    offspringIds: ['child'],
    condition: { keyword: 'filtered' },
  };
  const save = (kind) => async (payload) => {
    calls.push({ kind, payload });
    if (fail) throw new Error('request failed');
  };
  const factory = new Function(
    'formRef',
    'formItem',
    'form',
    'props',
    'currentProjectId',
    'selectedTagType',
    'TagUpdateTypeEnum',
    'batchEditAttrs',
    'batchUpdateExecutor',
    'Message',
    't',
    'closeHandler',
    'emits',
    'console',
    `${js};return confirmHandler;`
  );
  const run = factory(
    { value: { validate: async () => undefined } },
    { value: [{ field: 'stale-custom', value: 'old' }] },
    form,
    props,
    { value: 'project' },
    { value: 'UPDATE' },
    { UPDATE: 'UPDATE', APPEND: 'APPEND', CLEAR: 'CLEAR' },
    save('attrs'),
    save('executor'),
    { success: () => events.push('success') },
    (x) => x,
    () => events.push('close'),
    (e) => events.push(e),
    { log() {} }
  );
  return { run, calls, events };
}
for (const field of ['executionResult', 'executionUser']) {
  test(`${field} preserves cross-page scope and excludes stale unrelated edits`, async () => {
    const h = harness(field);
    await h.run();
    assert.equal(h.calls.length, 1);
    const { kind, payload } = h.calls[0];
    assert.equal(kind, field === 'executionUser' ? 'executor' : 'attrs');
    assert.deepEqual(payload.selectIds, ['one']);
    assert.equal(payload.selectAll, true);
    assert.deepEqual(payload.excludeIds, ['excluded']);
    assert.deepEqual(payload.moduleIds, ['folder', 'child']);
    assert.deepEqual(payload.condition, { keyword: 'filtered' });
    assert.equal(payload.projectId, 'project');
    assert.equal(payload.tags, undefined);
    assert.equal(payload.customField, undefined);
    assert.equal(
      payload[field === 'executionUser' ? 'userId' : 'lastExecuteResult'],
      field === 'executionUser' ? 'real-user-id' : 'BLOCKED'
    );
    assert.deepEqual(h.events, ['success', 'close', 'success']);
  });
  test(`${field} failed update does not close or emit success`, async () => {
    const h = harness(field, true);
    await h.run();
    assert.deepEqual(h.events, []);
  });
}
test('existing tag edits keep the original attributes endpoint', async () => {
  const h = harness('systemTags');
  await h.run();
  assert.equal(h.calls[0].kind, 'attrs');
  assert.deepEqual(h.calls[0].payload.tags, ['stale-tag']);
  assert.deepEqual(h.calls[0].payload.customField, {});
});
