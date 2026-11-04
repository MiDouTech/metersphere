import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { webcrypto } from 'node:crypto';
import test from 'node:test';
import ts from 'typescript';
import { computed, reactive, ref } from 'vue';

const source = readFileSync(new URL('../src/components/business/plan-review/legacyMigrationPanel.vue', import.meta.url), 'utf8');
const script = source.match(/<script setup lang="ts">([\s\S]*?)<\/script>/)[1].replace(/^\s*import[\s\S]*?;/gm, '');
const code = ts.transpileModule(script, { compilerOptions: { target: ts.ScriptTarget.ES2022 } }).outputText;
const inventory = { projectId: 'project-a', manifestHash: 'a'.repeat(64), groups: [{ legacyGroupId: 'old', classification: 'LEGAL_UNIQUE', planId: 'plan', reviewId: 'review', jobs: [{ jobId: 'job', policy: 'CREATE_NEW_PAIR', state: 'FAILED' }] }], orphanJobs: [] };
function harness(overrides = {}) {
  const app = { currentProjectId: 'project-a' }, calls = [], watchers = [], unmounts = [];
  const details = { id: 'change', project_id: 'project-a', state: 'PREVIEW', snapshot_hash: 'b'.repeat(64), canExecute: true, canApprove: true };
  const request = (kind, fallback) => async (...args) => { calls.push({ kind, args: structuredClone(args) }); return (overrides[kind] || fallback)(...args); };
  const create = new Function('computed', 'onBeforeUnmount', 'reactive', 'ref', 'watch', 'useAppStore', 'hasAnyPermission', 'getLegacyChange', 'getLegacyChanges', 'legacyMigrationStep', 'previewLegacyMigration', 'scanLegacyRelations', 'crypto', 'defineExpose', `${code};return {open,scan,preview,step,loadExisting,visible,busy,inventory,change,groupChoices,jobChoices,acknowledged,lookupId,rollbackHash,rollbackMode,canClose};`);
  const api = create(computed, (callback) => unmounts.push(callback), reactive, ref, (getter, callback) => watchers.push(callback), () => app, () => true,
    request('details', async () => structuredClone(details)), request('recent', async () => ({ projectId: 'project-a', items: [] })), request('step', async () => ({ projectId: 'project-a' })), request('preview', async () => structuredClone(details)), request('scan', async () => structuredClone(inventory)), webcrypto, () => {});
  return { api, calls, switchProject() { app.currentProjectId = 'project-b'; watchers[0](); }, unmount() { unmounts.forEach((callback) => callback()); } };
}
test('opening only inventories; it never chooses a target, transfers a job or executes migration', async () => {
  const h = harness(); await h.api.open();
  assert.deepEqual(h.calls.map((call) => call.kind), ['scan', 'recent']); assert.deepEqual(Object.keys(h.api.groupChoices), []); assert.equal(h.api.jobChoices.job.action, '');
});
test('unknown preview response retries the original key and sends only explicitly chosen rows', async () => {
  let attempt = 0; const h = harness({ preview: async () => { if (++attempt === 1) throw new Error('lost response'); return { id: 'change', project_id: 'project-a' }; } });
  await h.api.open(); h.api.groupChoices.old = 'KEEP'; h.api.jobChoices.job.action = 'ADOPT'; h.api.jobChoices.job.actor = 'actor';
  await assert.rejects(h.api.preview(), /lost response/); await h.api.preview();
  const attempts = h.calls.filter((call) => call.kind === 'preview'); assert.equal(attempts.length, 2); assert.equal(attempts[0].args[0].requestKey, attempts[1].args[0].requestKey);
  assert.deepEqual(attempts[0].args[0].groups, { old: 'KEEP' }); assert.equal(attempts[0].args[0].jobs.job.executionMode, 'ORIGINAL'); assert.equal(attempts[0].args[0].jobs.job.reviewSettings, undefined);
  assert.equal(h.api.acknowledged.value, false); assert.equal(h.calls.some((call) => call.kind === 'step'), false);
});
test('execution needs fresh explicit acknowledgement and never follows preview automatically', async () => {
  const h = harness(); await h.api.open(); h.api.groupChoices.old = 'KEEP'; await h.api.preview();
  await h.api.step('execute'); assert.equal(h.calls.some((call) => call.kind === 'step'), false);
  h.api.acknowledged.value = true; await h.api.step('backup');
  const command = h.calls.find((call) => call.kind === 'step'); assert.equal(command.args[0], 'backup'); assert.equal(command.args[1].snapshotHash, 'b'.repeat(64)); assert.equal(h.api.acknowledged.value, false);
});
test('project switch invalidates an in-flight inventory and clears all approval context', async () => {
  let finish; const waiting = new Promise((resolve) => { finish = resolve; }); const h = harness({ scan: async () => waiting });
  const open = h.api.open(); assert.equal(h.api.busy.value, true); assert.equal(h.api.canClose(), false); h.switchProject(); finish(structuredClone(inventory)); await open;
  assert.equal(h.api.visible.value, false); assert.equal(h.api.inventory.value, undefined); assert.equal(h.api.change.value, undefined); assert.equal(h.api.acknowledged.value, false);
});
test('a response from another project cannot populate a migration preview', async () => {
  const h = harness({ preview: async () => ({ id: 'foreign', project_id: 'project-b' }) }); await h.api.open(); h.api.groupChoices.old = 'KEEP'; await h.api.preview();
  assert.equal(h.api.change.value, undefined); assert.equal(h.calls.some((call) => call.kind === 'details'), false);
});
test('rollback is a separate preview and explicit action; unmount discards stale approval', async () => {
  const h = harness({ step: async () => ({ projectId: 'project-a', previewHash: 'rollback', mode: 'COMPENSATE_KEEP_FACTS' }) });
  await h.api.open(); h.api.lookupId.value = 'change'; await h.api.loadExisting(); await h.api.step('rollback-preview');
  assert.equal(h.api.rollbackHash.value, 'rollback'); assert.equal(h.api.rollbackMode.value, 'COMPENSATE_KEEP_FACTS'); assert.equal(h.calls.filter((call) => call.kind === 'step').length, 1);
  await h.api.step('rollback'); assert.equal(h.calls.filter((call) => call.kind === 'step').length, 1);
  h.api.acknowledged.value = true; h.unmount(); assert.equal(h.api.rollbackHash.value, ''); assert.equal(h.api.acknowledged.value, false);
});
