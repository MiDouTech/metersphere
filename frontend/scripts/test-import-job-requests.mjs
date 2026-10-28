/* eslint-disable no-new-func, no-await-in-loop -- Execute the real setup script and flush Vue microtasks sequentially. */
import { computed, effectScope, nextTick, reactive, ref, watch } from 'vue';

import ts from 'typescript';

import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import test from 'node:test';
import { fileURLToPath } from 'node:url';
import { parse } from 'vue/compiler-sfc';

const source = readFileSync(
  new URL('../src/views/case-management/caseManagementFeature/components/import/importJob.vue', import.meta.url),
  'utf8'
);
const script = parse(source).descriptor.scriptSetup.content.replace(/^\s*import .*?;\s*$/gm, '');
const js = ts.transpileModule(script, {
  compilerOptions: { target: ts.ScriptTarget.ES2022, module: ts.ModuleKind.ESNext },
}).outputText;
function mount(get, getReviewUsers = async () => []) {
  const props = reactive({ jobId: 'job-1', visible: true });
  const scope = effectScope();
  const deps = {
    ref,
    watch,
    defineProps: () => props,
    defineEmits: () => () => {},
    useRouter: () => ({ push() {} }),
    MSR: { get },
    getReviewUsers,
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
    new Function(...Object.keys(deps), `${js}\nreturn {job, load};`)(...Object.values(deps))
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
  let jobs = 0;
  let options = 0;
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

test('reviewer loading reuses the in-flight request when creation is selected automatically', async () => {
  let userRequests = 0;
  let resolveUsers;
  const app = mount(
    async ({ url }) =>
      url.endsWith('followup-options')
        ? { projectId: 'p', canCreate: true, groups: [] }
        : { jobId: 'job-1', state: 'PRECHECKED', total: 5, items: [], counts: [] },
    () => {
      userRequests++;
      return new Promise((resolve) => {
        resolveUsers = resolve;
      });
    }
  );
  try {
    await flush();
    assert.equal(userRequests, 1);
    resolveUsers([{ id: 'reviewer', name: 'Reviewer' }]);
    await flush();
    assert.equal(userRequests, 1);
  } finally {
    app.stop();
  }
});

test('the real reviewer loader passes strict semantic type checking across the async closure', () => {
  const ast = ts.createSourceFile('setup.ts', script, ts.ScriptTarget.Latest, true);
  const loader = ast.statements.find((node) => ts.isFunctionDeclaration(node) && node.name?.text === 'loadReviewers');
  assert.ok(loader);
  const filename = fileURLToPath(new URL('./__reviewer_typecheck__.ts', import.meta.url)).replaceAll('\\', '/');
  const body = `import { ref } from 'vue';
    interface FollowupOptions { projectId: string; }
    const followupOptions = ref<FollowupOptions>();
    const policy = ref(''); const error = ref('');
    const reviewerOptions = ref<{label:string;value:string}[]>([]);
    let reviewersLoaded=false; let reviewersPromise:Promise<void>|undefined;
    let generation=0;
    declare function getReviewUsers(projectId:string, keyword:string):Promise<{id:string;name:string}[]>;
    ${loader.getText(ast)}`;
  const options = {
    strict: true,
    noEmit: true,
    skipLibCheck: true,
    types: [],
    target: ts.ScriptTarget.ES2022,
    module: ts.ModuleKind.ESNext,
    moduleResolution: ts.ModuleResolutionKind.Bundler,
  };
  const host = ts.createCompilerHost(options);
  const original = host.getSourceFile.bind(host);
  host.getSourceFile = (name, languageVersion, ...rest) =>
    name === filename
      ? ts.createSourceFile(name, body, languageVersion, true)
      : original(name, languageVersion, ...rest);
  const program = ts.createProgram([filename], options, host);
  const diagnostics = ts.getPreEmitDiagnostics(program);
  assert.equal(
    diagnostics.length,
    0,
    diagnostics.map((d) => ts.flattenDiagnosticMessageText(d.messageText, '\n')).join('\n')
  );
});
