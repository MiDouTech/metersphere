import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import test from 'node:test';
import vm from 'node:vm';

const source = readFileSync(new URL('../src/views/test-asset/asset-case-detail.vue', import.meta.url), 'utf8');
test('attachment upload and deletion preserve unsaved fields', async () => {
  const functions = source
    .slice(source.indexOf('  async function refreshAttachments'), source.indexOf('  async function downloadAttachment'))
    .replace('event: Event', 'event')
    .replace(' as HTMLInputElement', '')
    .replace('item: any', 'item');
  const form = { name: 'draft title', description: 'draft note' };
  const attachments = { value: [] };
  const context = vm.createContext({
    form,
    attachments,
    catalogId: 'catalog',
    caseId: 'case',
    uploadCaseAssetAttachment: async () => undefined,
    deleteCaseAssetAttachment: async () => undefined,
    getCaseAssetDetail: async () => ({ attachments: [{ id: 'new' }] }),
    load: async () => {
      form.name = 'saved title';
      form.description = 'saved note';
    },
  });
  vm.runInContext(
    `${functions}\nglobalThis.run = async () => { await uploadAttachments({target:{files:[{}],value:"file"}}); await removeAttachment({id:"new"}); };`,
    context
  );
  await context.run();
  assert.deepEqual(form, { name: 'draft title', description: 'draft note' });
  assert.equal(attachments.value[0].id, 'new');
});
