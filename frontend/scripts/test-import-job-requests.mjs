/* eslint-disable no-new-func, no-await-in-loop -- Execute the real setup script and flush Vue microtasks sequentially. */
import { computed, effectScope, nextTick, reactive, ref, watch } from 'vue';

import ts from 'typescript';

import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import test from 'node:test';
import { fileURLToPath } from 'node:url';
import { compileScript, compileTemplate, parse } from 'vue/compiler-sfc';

const source = readFileSync(
  new URL('../src/views/case-management/caseManagementFeature/components/import/importJob.vue', import.meta.url),
  'utf8'
);
const script = parse(source).descriptor.scriptSetup.content.replace(/^\s*import .*?;\s*$/gm, '');
const js = ts.transpileModule(script, {
  compilerOptions: { target: ts.ScriptTarget.ES2022, module: ts.ModuleKind.ESNext },
}).outputText;
function mount(get, getReviewUsers = async () => [], post = async () => {}) {
  const props = reactive({ jobId: 'job-1', visible: true });
  const scope = effectScope();
  const deps = {
    ref,
    computed,
    watch,
    defineProps: () => props,
    defineEmits: () => () => {},
    useRouter: () => ({ push() {} }),
    MSR: { get, post },
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
    new Function(
      ...Object.keys(deps),
      `${js}\nreturn {job, load, followupLabel, retryFollowup, action, saving, error, queryFailed, clearTimer};`
    )(...Object.values(deps))
  );
  return {
    props,
    state,
    stop: () => {
      state.clearTimer();
      scope.stop();
    },
  };
}
const flush = async () => {
  for (let i = 0; i < 20; i++) {
    await Promise.resolve();
    await nextTick();
  }
};

test('the import drawer compiles its real script and template', () => {
  const { descriptor, errors } = parse(source);
  assert.deepEqual(errors, []);
  assert.ok(compileScript(descriptor, { id: 'import-job' }).content);
  assert.deepEqual(
    compileTemplate({ source: descriptor.template.content, filename: 'importJob.vue', id: 'import-job' }).errors,
    []
  );
});

test('followup feedback distinguishes zero success, creation, synchronization and import only', async () => {
  const app = mount(async () => ({ jobId: 'job-1', state: 'COMPLETED', total: 0, items: [], counts: [] }));
  try {
    await flush();
    const check = (followup, expected) => {
      app.state.job.value.followup = followup;
      assert.equal(app.state.followupLabel.value, expected);
    };
    check({ state: 'SUCCEEDED', stage: 'SKIPPED_ZERO_SUCCESS' }, '无成功导入用例，未创建计划');
    check({ state: 'SKIPPED' }, '仅导入，未创建或关联计划');
    check({ state: 'PENDING', policy: 'CREATE_NEW_PAIR', stage: 'WAIT_IMPORT' }, '正在创建并关联');
    check({ state: 'PENDING', stage: 'TARGET_PENDING' }, '关系已建立，正在同步用例');
    check({ state: 'FAILED', stage: 'TARGET_PENDING' }, '用例已导入，计划关系已建立，评审用例同步未完成');
    check({ state: 'FAILED', planId: 'plan' }, '用例已导入，测试计划已创建，后续步骤未完成');
    check({ state: 'SUCCEEDED', policy: 'CREATE_NEW_PAIR' }, '已创建并关联');
    check({ state: 'TERMINATED' }, '原操作已终止或关联已变更，不会自动续建');
  } finally {
    app.stop();
  }
});

test('query failure retains the original job and blocks writes until a successful refresh', async () => {
  let fail = false;
  let writes = 0;
  const app = mount(
    async () => {
      if (fail) throw new Error('offline');
      return { jobId: 'job-1', state: 'COMPLETED', total: 1, items: [], counts: [], followup: { state: 'FAILED' } };
    },
    undefined,
    async () => {
      writes++;
    }
  );
  try {
    await flush();
    fail = true;
    await app.state.load();
    assert.equal(app.state.job.value.jobId, 'job-1');
    assert.equal(app.state.queryFailed.value, true);
    await app.state.retryFollowup();
    await app.state.action('retry');
    assert.equal(writes, 0);
    fail = false;
    await app.state.load();
    await app.state.retryFollowup();
    assert.equal(writes, 1);
  } finally {
    app.stop();
  }
});

test('a lost retry response queries the original job and never reimports or repeats the write', async () => {
  let writes = 0;
  let reads = 0;
  const app = mount(
    async () => {
      reads++;
      return {
        jobId: 'job-1',
        state: 'COMPLETED',
        total: 1,
        items: [],
        counts: [],
        followup: { state: writes ? 'SUCCEEDED' : 'FAILED', policy: 'CREATE_NEW_PAIR' },
      };
    },
    undefined,
    async ({ url }) => {
      assert.equal(url, '/ai/case-imports/job-1/followup/retry');
      writes++;
      throw new Error('response lost');
    }
  );
  try {
    await flush();
    await Promise.all([app.state.retryFollowup(), app.state.retryFollowup()]);
    assert.equal(writes, 1);
    assert.equal(reads, 2);
    assert.equal(app.state.job.value.followup.state, 'SUCCEEDED');
    assert.equal(app.state.error.value, '');
    await app.state.retryFollowup();
    assert.equal(writes, 1);
  } finally {
    app.stop();
  }
});

test('an old retry failure cannot alter a newly opened job', async () => {
  let rejectOld;
  const app = mount(
    async ({ url }) => ({
      jobId: url.includes('job-2') ? 'job-2' : 'job-1',
      state: 'COMPLETED',
      total: 1,
      items: [],
      counts: [],
      followup: { state: 'FAILED' },
    }),
    undefined,
    () =>
      new Promise((_resolve, reject) => {
        rejectOld = reject;
      })
  );
  try {
    await flush();
    const oldRetry = app.state.retryFollowup();
    app.props.jobId = 'job-2';
    await flush();
    rejectOld(new Error('old job failed'));
    await oldRetry;
    assert.equal(app.state.job.value.jobId, 'job-2');
    assert.equal(app.state.error.value, '');
    assert.equal(app.state.saving.value, false);
  } finally {
    app.stop();
  }
});
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

test('CR26-01 legacy and current feedback contracts report only confirmed outcomes', async () => {
  const app = mount(async () => ({ jobId: 'job-1', state: 'COMPLETED', total: 1, items: [], counts: [] }));
  try {
    await flush();
    const cases = [
      [{ state: 'PENDING', legacy: true }, '仅导入，等待处理完成'],
      [{ state: 'SUCCEEDED', legacy: true }, '仅导入，未创建或关联计划'],
      [{ state: 'PENDING', policy: 'IMPORT_ONLY', legacy: true }, '仅导入，等待处理完成'],
      [{ state: 'SUCCEEDED', policy: 'IMPORT_ONLY', legacy: true }, '仅导入，未创建或关联计划'],
      [{ state: 'SKIPPED', policy: 'IMPORT_ONLY' }, '仅导入，未创建或关联计划'],
      [{ state: 'SUCCEEDED', policy: 'CREATE_NEW_PAIR' }, '已创建并关联'],
      [{ state: 'SUCCEEDED', policy: 'APPEND_EXISTING' }, '已完成关联'],
      [{ state: 'LEGACY_ADOPTION_REQUIRED', legacy: true }, '原任务需核对，成功结果保留'],
      [{ state: 'SUCCEEDED', policy: 'CREATE_NEW_PAIR', stage: 'SKIPPED_ZERO_SUCCESS' }, '无成功导入用例，未创建计划'],
      [{ state: 'SUCCEEDED' }, '结果暂未确认，请刷新'],
      [{ state: 'SUCCEEDED', policy: 'UNKNOWN' }, '结果暂未确认，请刷新'],
    ];
    for (const [followup, expected] of cases) {
      app.state.job.value.followup = followup;
      assert.equal(app.state.followupLabel.value, expected, JSON.stringify(followup));
    }
  } finally {
    app.stop();
  }
});
