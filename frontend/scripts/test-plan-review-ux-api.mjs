import ts from 'typescript';

import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { test } from 'node:test';

test('relation API requests handle forbidden responses locally and retain precise scope and request key', async () => {
  const source = readFileSync(new URL('../src/api/modules/plan-review/index.ts', import.meta.url), 'utf8');
  const code = ts.transpileModule(source, { compilerOptions: { module: ts.ModuleKind.CommonJS } }).outputText;
  const calls = [];
  const request = (config, options) => {
    calls.push({ config, options });
    return Promise.resolve({ items: [] });
  };
  const exports = {};
  // Execute the real API adapter with its transport boundary replaced.
  // eslint-disable-next-line no-new-func
  new Function('require', 'exports', code)(() => ({ default: { get: request, post: request } }), exports);
  await exports.getPlanReview('plan', 'p2');
  await exports.getPlanReviewCandidates({ side: 'plan', id: 'p2', keyword: '100004', page: 2, size: 20 });
  await exports.previewPlanReview({ planId: 'p2', reviewId: 'r2', purpose: 'PAIR_EXISTING' });
  await exports.getPlanReviewPreviewItems('preview', 'added', 1);
  await exports.confirmPlanReview({ previewId: 'preview', requestKey: 'same-key', acknowledged: true });
  await exports.findPlanReviewConfirmation('project', 'same-key');
  await exports.getPlanReviewOperations('project', false, { relationId: 'own-relation', generation: 'own-g' });
  await exports.getPlanReviewReadOnlyDifference('review', 'r2', 'own-g', 'removed', 1);
  calls.forEach((call) => assert.equal(call.options.handleForbiddenLocally, true));
  assert.equal(calls[4].config.data.requestKey, calls[5].config.params.requestKey);
  assert.deepEqual(calls[6].config.params, {
    projectId: 'project',
    page: 1,
    size: 20,
    allProject: false,
    relationId: 'own-relation',
    generation: 'own-g',
  });
  assert.equal(calls[7].config.params.generation, 'own-g');
});
