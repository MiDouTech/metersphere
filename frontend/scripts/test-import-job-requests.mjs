import test from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import ts from 'typescript';
import { parse } from 'vue/compiler-sfc';
import { ref, watch, computed, effectScope, reactive, nextTick } from 'vue';

const source = readFileSync(
  new URL('../src/views/case-management/caseManagementFeature/components/import/importJob.vue', import.meta.url),
  'utf8'
);
const script = parse(source).descriptor.scriptSetup.content.replace(/^\s*import .*?;\s*$/gm, '');
const js = ts.transpileModule(script, {
  compilerOptions: { target: ts.ScriptTarget.ES2022, module: ts.ModuleKind.ESNext },
}).outputText;
function mount(get) {
  const props = reactive({ jobId: 'job-1', visible: true });
  const scope = effectScope();
  const deps = {
    ref,
    watch,
    defineProps: () => props,
    defineEmits: () => () => {},
    useRouter: () => ({ push() {} }),
    MSR: { get },
    getReviewUsers: async () => [],
    onBeforeUnmount() {},
    TestPlanRouteEnum: {},
    ensureAppError: (e) => e,
    formatAppErrorMessage: (e) => String(e),
    useClientTablePaging: (_key, total) => {
      const current = ref(1);
      const pageSize = ref(20);
      return computed(() => ({
        pagination: { current: current.value, pageSize: pageSize.value, total: total() },
        onPageChange: (v) => {
          current.value = v;
        },
      }));
    },
  };
  const state = scope.run(() =>
    new Function(...Object.keys(deps), js + '\nreturn {job, load};')(...Object.values(deps))
  );
  return { props, state, stop: () => scope.stop() };
}
const flush = async () => {
  for (let i = 0; i < 20; i++) {
    await Promise.resolve();
    await nextTick();
  }
};
test('PRECHECKED total updates do not refetch the job or its followup options', async () => {
  let jobs = 0,
    options = 0;
  const app = mount(async ({ url }) => {
    if (url.endsWith('followup-options')) {
      options++;
      return { projectId: 'p', canCreate: false, groups: [] };
    }
    assert.ok(++jobs < 10, 'unbounded request loop');
    return { jobId: 'job-1', state: 'PRECHECKED', total: 576, items: [], counts: [] };
  });
  try {
    await flush();
    assert.equal(jobs, 1);
    assert.equal(options, 1);
  } finally {
    app.stop();
  }
});
test('a stale response from the previous job cannot replace current dialog state', async () => {
  let resolveOld;
  const old = new Promise((resolve) => {
    resolveOld = resolve;
  });
  const app = mount(async ({ url }) =>
    url.includes('job-1') ? old : { jobId: 'job-2', state: 'COMPLETED', total: 1, items: [], counts: [] }
  );
  try {
    app.props.jobId = 'job-2';
    await flush();
    resolveOld({ jobId: 'job-1', state: 'COMPLETED', total: 999, items: [], counts: [] });
    await flush();
    assert.equal(app.state.job.value.jobId, 'job-2');
  } finally {
    app.stop();
  }
});
