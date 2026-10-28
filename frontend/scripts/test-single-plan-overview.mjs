import { computed, effectScope, nextTick, ref, watch } from 'vue';

import ts from 'typescript';

import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import test from 'node:test';
import { fileURLToPath } from 'node:url';

const url = new URL('../src/hooks/useSinglePlanOverview.ts', import.meta.url);
const source = readFileSync(url, 'utf8');
const js = ts.transpileModule(source.replace(/^import .*?;\s*/m, ''), {
  compilerOptions: { target: ts.ScriptTarget.ES2022, module: ts.ModuleKind.CommonJS },
}).outputText;
const compiledModule = {};
// Execute the production composable with the real Vue scheduler.
// eslint-disable-next-line no-new-func
new Function('exports', 'computed', 'ref', 'watch', js)(compiledModule, computed, ref, watch);
const useOverview = compiledModule.default;
function fixture(initial) {
  const plans = ref(initial);
  const project = ref('project-1');
  const scope = effectScope();
  const state = scope.run(() =>
    useOverview(
      () => plans.value,
      () => project.value
    )
  );
  return { plans, project, ...state, stop: () => scope.stop() };
}
test('no plans has no visible summary; arrival selects the first', async () => {
  const f = fixture([]);
  try {
    assert.equal(f.selectedPlan.value, undefined);
    f.plans.value = [{ id: 'a', rate: 10 }];
    await nextTick();
    assert.equal(f.selectedPlan.value.id, 'a');
  } finally {
    f.stop();
  }
});
test('switching shows only the selected plan and preserves selection after refreshed metrics', async () => {
  const f = fixture([
    { id: 'a', rate: 10 },
    { id: 'b', rate: 20 },
  ]);
  try {
    f.selectedPlanId.value = 'b';
    assert.equal(f.selectedPlan.value.rate, 20);
    f.plans.value = [
      { id: 'a', rate: 40 },
      { id: 'b', rate: 60 },
    ];
    await nextTick();
    assert.equal(f.selectedPlan.value.id, 'b');
    assert.equal(f.selectedPlan.value.rate, 60);
  } finally {
    f.stop();
  }
});
test('filter or page removing the selected plan falls back; an empty result clears it', async () => {
  const f = fixture([{ id: 'a' }, { id: 'b' }]);
  try {
    f.selectedPlanId.value = 'b';
    f.plans.value = [{ id: 'c' }];
    await nextTick();
    assert.equal(f.selectedPlan.value.id, 'c');
    f.plans.value = [];
    await nextTick();
    assert.equal(f.selectedPlan.value, undefined);
  } finally {
    f.stop();
  }
});
test('project changes reset the previous selection even when IDs overlap', async () => {
  const f = fixture([{ id: 'a' }, { id: 'b' }]);
  try {
    f.selectedPlanId.value = 'b';
    f.project.value = 'project-2';
    await nextTick();
    assert.equal(f.selectedPlan.value.id, 'a');
  } finally {
    f.stop();
  }
});
test('the entire production composable passes strict semantic type checking', () => {
  const program = ts.createProgram([fileURLToPath(url)], {
    strict: true,
    noEmit: true,
    skipLibCheck: true,
    types: [],
    target: ts.ScriptTarget.ES2022,
    module: ts.ModuleKind.ESNext,
    moduleResolution: ts.ModuleResolutionKind.Bundler,
  });
  const errors = ts.getPreEmitDiagnostics(program);
  assert.equal(errors.length, 0, errors.map((e) => ts.flattenDiagnosticMessageText(e.messageText, '\n')).join('\n'));
});
