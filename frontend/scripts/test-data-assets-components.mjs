/* eslint-disable no-await-in-loop, no-nested-ternary -- Sequential UI scenarios and SFC fixture selection. */
/* global globalThis */
/* eslint-disable no-restricted-syntax */
import ts from 'typescript';

import { JSDOM } from 'jsdom';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { createRequire } from 'node:module';
import test from 'node:test';
import { compileScript, parse } from 'vue/compiler-sfc';

const dom = new JSDOM('<html><body></body></html>', { url: 'http://localhost' });
for (const key of ['window', 'document', 'navigator', 'Element', 'HTMLElement', 'SVGElement', 'Node', 'sessionStorage'])
  Object.defineProperty(globalThis, key, { value: dom.window[key], configurable: true });
const require = createRequire(import.meta.url);
const vue = require('vue');
const { mount, flushPromises } = require('@vue/test-utils');

const source = readFileSync(new URL('../src/views/test-asset/datasets.vue', import.meta.url), 'utf8');
const compiled = compileScript(parse(source).descriptor, { id: 'data-tests' }).content;
const js = ts.transpileModule(compiled, {
  compilerOptions: { module: ts.ModuleKind.CommonJS, esModuleInterop: true },
}).outputText;
const appErrors = {};
const errorFormatter = {};
function evaluateErrorModule(path, target) {
  const moduleJs = ts.transpileModule(readFileSync(new URL(path, import.meta.url), 'utf8'), {
    compilerOptions: { module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2022 },
  }).outputText;
  // eslint-disable-next-line no-new-func
  new Function('exports', 'require', moduleJs)(target, () => appErrors);
}
evaluateErrorModule('../src/utils/appError.ts', appErrors);
evaluateErrorModule('../src/utils/businessAssetError.ts', errorFormatter);
function setup(overrides = {}, options = {}) {
  const calls = [];
  const api = {
    getLegacyMigrationStatus: async () => ({
      counts: { pending: 0, running: 0, failed: 0, done: 0 },
      canRun: false,
      hasMore: false,
      items: [],
    }),
    dataVersions: async () => [],
    listDataCategories: async () => [],
    getDataLimits: async () => ({ maxBytes: 2000000, maxRows: 10000, formats: ['CSV', 'JSON'] }),
    listDataAssets: async () => ({
      items: [
        { id: 'a', name: 'A', format: 'JSON', revision: 1 },
        { id: 'b', name: 'B', format: 'JSON', revision: 2 },
      ],
      total: 2,
    }),
    getDataAsset: async (id) => ({ id, name: id, format: 'JSON', revision: 3, currentVersionId: `${id}-v3` }),
    getDataContent: async () => ({ content: { value: 'real' }, complete: true }),
    saveDataAsset: async (data) => {
      calls.push(data);
      return data;
    },
    batchDataAssets: async (items) => ({
      items: items.map((item) => ({
        id: item.id,
        status: item.id === 'a' ? 'SUCCESS' : 'FAILED',
        error: item.id === 'b' ? 'conflict' : undefined,
      })),
    }),
    ...overrides,
  };
  const exports = {};
  const paths = [];
  // eslint-disable-next-line no-new-func
  new Function(
    'exports',
    'require',
    options.detail
      ? ts.transpileModule(
          compileScript(
            parse(readFileSync(new URL('../src/views/test-asset/data-detail.vue', import.meta.url), 'utf8')).descriptor,
            { id: 'detail-test' }
          ).content,
          { compilerOptions: { module: ts.ModuleKind.CommonJS, esModuleInterop: true } }
        ).outputText
      : options.template
      ? ts.transpileModule(
          compileScript(parse(source).descriptor, { id: 'render-editor', inlineTemplate: true }).content,
          { compilerOptions: { module: ts.ModuleKind.CommonJS, esModuleInterop: true } }
        ).outputText
      : js
  )(exports, (name) => {
    if (name === 'vue') return vue;
    if (name.includes('businessAssetError')) return errorFormatter;
    if (name.includes('/appError')) return appErrors;
    if (name.includes('store/modules/app'))
      return { __esModule: true, default: () => vue.reactive({ currentOrgId: 'org-a' }) };
    if (name === 'vue-router')
      return {
        useRoute: () => options.route || { query: {}, params: {} },
        useRouter: () => ({ push: async (path) => paths.push(path), replace: async () => undefined }),
      };
    if (name.includes('test-data-assets')) return api;
    if (name.includes('ai-execution')) return { listTestAssetCategories: async () => [], ...api };
    if (name.includes('permission'))
      return {
        hasAnyPermission: (permissions) =>
          permissions.includes('TEST_ASSET_CATEGORY:MANAGE')
            ? options.canManageCategories !== false
            : options.canWrite !== false,
      };
    return { __esModule: true, default: { render: () => vue.h('div') } };
  });
  const component = exports.default;
  if (!options.template)
    component.render = options.errorView
      ? function renderError() {
          return vue.h('p', this.$.setupState.error);
        }
      : () => vue.h('div');
  const wrapper = mount(
    component,
    options.template
      ? {
          global: {
            stubs: {
              AButton: { template: '<button><slot /></button>' },
              AAlert: { template: '<div role="alert"><slot /></div>' },
              AModal: { props: ['visible'], template: '<section v-if="visible" data-modal><slot /></section>' },
              ASpin: { template: '<div><slot /></div>' },
              ASpace: { template: '<div><slot /></div>' },
              AForm: { template: '<form><slot /></form>' },
              AFormItem: { template: '<label><slot /></label>' },
              AInput: {
                props: ['modelValue'],
                emits: ['update:modelValue'],
                template: '<input :value="modelValue" @input="$emit(\'update:modelValue\',$event.target.value)" />',
              },
              ATextarea: {
                props: ['modelValue'],
                emits: ['update:modelValue'],
                template: '<textarea :value="modelValue" @input="$emit(\'update:modelValue\',$event.target.value)" />',
              },
              ATable: true,
              ATableColumn: true,
              ASelect: true,
              ATree: true,
              ATreeSelect: true,
              AInputSearch: true,
              ATag: true,
              APopconfirm: true,
              ADropdown: true,
              ADoption: true,
              AList: true,
              AListItem: true,
            },
          },
        }
      : {}
  );
  return { wrapper, state: wrapper.vm.$.setupState, calls, paths };
}
test('manual draft resets identity and saves edited JSON', async () => {
  const h = setup();
  await flushPromises();
  h.state.create();
  h.state.draft.name = 'New';
  h.state.contentText = '{"edited":true}';
  await h.state.save();
  assert.equal(h.calls.length, 1);
  assert.equal(h.calls[0].id, undefined);
  assert.deepEqual(h.calls[0].content, { edited: true });
  h.wrapper.unmount();
});
test('detail action navigates dedicated entity page', async () => {
  const h = setup();
  await flushPromises();
  await h.state.open({ id: 'a' }, false);
  assert.deepEqual(h.paths, [{ name: 'testAssetDataDetail', params: { dataId: 'a' } }]);
  h.wrapper.unmount();
});
test('incomplete content disables editing and batch reports partial failures', async () => {
  const h = setup({ getDataContent: async () => ({ content: [], complete: false }) });
  await flushPromises();
  await h.state.open({ id: 'a' }, true);
  assert.equal(h.state.complete, false);
  h.state.selected = ['a', 'b'];
  await h.state.batchDelete();
  assert.deepEqual(
    h.state.results.map((x) => x.status),
    ['SUCCESS', 'FAILED']
  );
  assert.deepEqual(h.state.selected, []);
  h.wrapper.unmount();
});

test('cancel invalidates pending entity request and incomplete data cannot save', async () => {
  let resolve;
  const h = setup({
    getDataContent: () =>
      new Promise((r) => {
        resolve = r;
      }),
  });
  await flushPromises();
  const pending = h.state.open({ id: 'late' }, true);
  await flushPromises();
  h.state.cancelEditor();
  resolve({ content: { late: true }, complete: true });
  await pending;
  assert.equal(h.state.complete, false);
  await h.state.save();
  assert.equal(h.calls.length, 0);
  h.wrapper.unmount();
});
test('organization change clears drafts and rejects late content from previous organization', async () => {
  let resolve;
  const h = setup({
    getDataContent: () =>
      new Promise((r) => {
        resolve = r;
      }),
  });
  await flushPromises();
  const pending = h.state.open({ id: 'previous-org' }, true);
  await flushPromises();
  h.state.categoryVisible = true;
  h.state.batchVisible = true;
  h.state.app.currentOrgId = 'org-b';
  await flushPromises();
  resolve({ content: { private: 'previous organization' }, complete: true });
  await pending;
  assert.equal(h.state.editorVisible, false);
  assert.equal(h.state.categoryVisible, false);
  assert.equal(h.state.batchVisible, false);
  assert.equal(h.state.complete, false);
  assert.equal(h.state.contentText, '');
  h.wrapper.unmount();
});
test('asynchronous upload exposes parse failure and retries durable job', async () => {
  let status = 'FAILED';
  let retries = 0;
  const h = setup({
    uploadDataAsset: async () => ({ id: 'uploaded', jobId: 'parse-job', parseStatus: 'PENDING' }),
    getDataJob: async () => ({ id: 'parse-job', status }),
    retryDataJob: async () => {
      retries += 1;
      status = 'SUCCESS';
      return { id: 'parse-job', status };
    },
  });
  await flushPromises();
  await h.state.upload({ target: { files: [{ name: 'data.csv' }], value: 'selected' } });
  assert.equal(h.state.parseStatus, 'FAILED');
  await h.state.retryParse();
  assert.equal(retries, 1);
  assert.equal(h.state.parseStatus, 'SUCCESS');
  h.wrapper.unmount();
  sessionStorage.clear();
});
test('table preview paginates all real rows and escapes cell markup', async () => {
  const contentSource = readFileSync(
    new URL('../src/views/test-asset/components/DataContent.vue', import.meta.url),
    'utf8'
  );
  const script = compileScript(parse(contentSource).descriptor, { id: 'content-render', inlineTemplate: true }).content;
  const contentCompiled = ts.transpileModule(script, {
    compilerOptions: { module: ts.ModuleKind.CommonJS, esModuleInterop: true },
  }).outputText;
  const exports = {};
  // eslint-disable-next-line no-new-func
  new Function('exports', 'require', contentCompiled)(exports, () => vue);
  const rows = Array.from({ length: 105 }, (_, index) => [index === 0 ? '<script>alert(1)</script>' : `row-${index}`]);
  rows[1][0] = { type: 'DATE', value: '2026-11-27T00:00', serial: 46353, date1904: false, format: 'yyyy-mm-dd' };
  const wrapper = mount(exports.default, {
    props: { value: { rows }, format: 'CSV' },
    global: {
      stubs: {
        APagination: {
          props: ['current', 'total'],
          emits: ['change'],
          template: '<button @click="$emit(\'change\',2)">next</button>',
        },
      },
    },
  });
  assert.equal(wrapper.findAll('tbody tr').length, 100);
  assert.equal(wrapper.find('script').exists(), false);
  assert.match(wrapper.text(), /2026-11-27T00:00/);
  assert.doesNotMatch(wrapper.text(), /46353/);
  assert.match(wrapper.text(), /<script>alert\(1\)<\/script>/);
  await wrapper.find('button').trigger('click');
  assert.equal(wrapper.findAll('tbody tr').length, 5);
  assert.match(wrapper.text(), /row-104/);
  await wrapper.setProps({
    format: 'JSON',
    value: { rows: [[1]], totals: 100, sheets: [{ name: 'business', other: 'retained' }] },
  });
  assert.equal(wrapper.find('table').exists(), false);
  assert.match(wrapper.text(), /"totals": 100/);
  assert.match(wrapper.text(), /"sheets"/);
  assert.match(wrapper.text(), /"other": "retained"/);
  await wrapper.setProps({ format: undefined, value: { type: 'DATE', value: '2026-11-27', other: 'all-fields' } });
  assert.match(wrapper.text(), /"type": "DATE"/);
  assert.match(wrapper.text(), /"value": "2026-11-27"/);
  assert.match(wrapper.text(), /"other": "all-fields"/);
  await wrapper.setProps({ format: 'TXT', value: '<plain> text' });
  assert.equal(wrapper.find('pre').text(), '<plain> text');

  wrapper.unmount();
});
test('spreadsheet editor edits actual cells and preserves other sheets', async () => {
  const tableSource = readFileSync(
    new URL('../src/views/test-asset/components/DataTableEditor.vue', import.meta.url),
    'utf8'
  );
  const script = compileScript(parse(tableSource).descriptor, { id: 'table-editor', inlineTemplate: true }).content;
  const tableCompiled = ts.transpileModule(script, {
    compilerOptions: { module: ts.ModuleKind.CommonJS, esModuleInterop: true },
  }).outputText;
  const exports = {};
  // eslint-disable-next-line no-new-func
  new Function('exports', 'require', tableCompiled)(exports, () => vue);
  const value = {
    sheets: [
      {
        name: 'first',
        rows: [
          ['old', 2, { type: 'DATE', value: '2026-11-27T00:00', serial: 46353, date1904: false, format: 'yyyy-mm-dd' }],
        ],
      },
      { name: 'second', rows: [['preserved']] },
    ],
  };
  const wrapper = mount(exports.default, {
    props: { modelValue: JSON.stringify(value), format: 'XLSX' },
    global: {
      stubs: {
        ASelect: true,
        ASpace: { template: '<div><slot /></div>' },
        AInput: {
          props: ['modelValue'],
          emits: ['update:modelValue'],
          template: '<input :value="modelValue" @input="$emit(\'update:modelValue\', $event.target.value)" />',
        },
        AButton: { template: '<button><slot /></button>' },
      },
    },
  });
  await wrapper.find('input').setValue('edited');
  const edited = JSON.parse(wrapper.emitted('update:modelValue')[0][0]);
  assert.equal(edited.sheets[0].rows[0][0], 'edited');
  assert.equal(edited.sheets[0].rows[0][1], 2);
  assert.deepEqual(edited.sheets[1], value.sheets[1]);
  assert.deepEqual(edited.sheets[0].rows[0][2], value.sheets[0].rows[0][2]);
  assert.equal(wrapper.findAll('input')[2].element.value, '2026-11-27T00:00');
  await wrapper.findAll('input')[2].setValue('2026-11-28T00:00');
  const changedDate = JSON.parse(wrapper.emitted('update:modelValue')[1][0]).sheets[0].rows[0][2];
  assert.deepEqual(changedDate, { ...value.sheets[0].rows[0][2], value: '2026-11-28T00:00' });
  await wrapper.find('button').trigger('click');
  const expanded = JSON.parse(wrapper.emitted('update:modelValue')[2][0]);
  assert.equal(expanded.sheets[0].rows.length, 2);
  wrapper.unmount();
});
test('network retry preserves manual create request identity', async () => {
  const identities = [];
  let attempts = 0;
  const h = setup({
    saveDataAsset: async (data) => {
      identities.push(data.requestId);
      attempts += 1;
      if (attempts === 1) throw Error('network');
      return data;
    },
  });
  await flushPromises();
  h.state.create();
  h.state.draft.name = 'Retry';
  h.state.contentText = '{}';
  await h.state.save();
  await h.state.save();
  assert.ok(identities[0]);
  assert.equal(identities[0], identities[1]);
  h.wrapper.unmount();
});

test('task usage explicitly publishes immutable data version into selected project', async () => {
  const published = [];
  const h = setup({
    publishDataAsset: async (...args) => {
      published.push(args);
      return { id: 'published-bridge', sourceVersion: 'raw-version' };
    },
  });
  await flushPromises();
  h.state.moreAction('task', { id: 'global-data', name: 'Dataset', currentVersionId: 'raw-version' });
  assert.equal(h.state.executionProject, '');
  h.state.executionProject = 'chosen-project';
  assert.equal(await h.state.publishSelected(), true);
  assert.deepEqual(published, [['global-data', 'chosen-project', 'raw-version']]);
  assert.equal(h.paths[0].name, 'executionTasks');
  assert.equal(h.paths[0].query.assetVersionId, 'published-bridge');
  assert.equal(h.paths[0].query.projectId, 'chosen-project');
  h.wrapper.unmount();
});
test('actual Arco textarea removes native readonly when reopening detail as create or edit', async () => {
  const { Textarea } = require('@arco-design/web-vue');
  const component = vue.defineComponent({
    props: { editing: Boolean },
    setup(props) {
      return () => vue.h(Textarea, { modelValue: '{}', textareaAttrs: { readonly: props.editing ? undefined : true } });
    },
  });
  const wrapper = mount(component, { props: { editing: false } });
  assert.equal(wrapper.find('textarea').element.readOnly, true);
  await wrapper.setProps({ editing: true });
  assert.equal(wrapper.find('textarea').element.readOnly, false);
  await wrapper.find('textarea').setValue('{"created":true}');
  await wrapper.setProps({ editing: false });
  assert.equal(wrapper.find('textarea').element.readOnly, true);
  await wrapper.setProps({ editing: true });
  assert.equal(wrapper.find('textarea').element.readOnly, false);
  wrapper.unmount();
});
test('batch description-only preserves categories and no selected fields sends nothing', async () => {
  const sent = [];
  const h = setup({
    batchDataAssets: async (items) => {
      sent.push(items);
      return { items: [] };
    },
  });
  await flushPromises();
  h.state.selected = ['a'];
  assert.equal(h.state.applyCategory(), false);
  assert.equal(sent.length, 0);
  h.state.descriptionMode = 'set';
  h.state.batchDescription = 'Changed';
  await h.state.applyCategory();
  assert.equal(sent[0][0].description, 'Changed');
  assert.equal('categoryId' in sent[0][0], false);
  h.state.selected = ['a'];
  h.state.categoryMode = 'clear';
  h.state.descriptionMode = 'clear';
  await h.state.applyCategory();
  assert.equal(sent[1][0].categoryId, '');
  assert.equal(sent[1][0].description, '');
  h.wrapper.unmount();
});
test('historical navigation retains immutable version query', async () => {
  const h = setup();
  await flushPromises();
  await h.state.open({ id: 'a' }, false, 'historical-v');
  assert.equal(h.paths[0].query.versionId, 'historical-v');
  h.wrapper.unmount();
});
test('detail entity switch resets fixed version and uses historical metadata without full content fetch', async () => {
  const route = vue.reactive({ params: { dataId: 'a' }, query: { versionId: 'a-old' } });
  const h = setup(
    {
      dataVersions: async (id) => [{ id: `${id}-old`, metadata: { name: 'Historical' }, metadataAvailable: true }],
      getDataContent: () => {
        throw new Error('must not load full content');
      },
    },
    { detail: true, route }
  );
  await flushPromises();
  assert.equal(h.state.fixedVersion, 'a-old');
  assert.equal(h.state.snapshot.metadata.name, 'Historical');
  route.params.dataId = 'b';
  route.query = {};
  await flushPromises();
  assert.equal(h.state.fixedVersion, 'b-v3');
  assert.equal(h.state.asset.id, 'b');
  h.wrapper.unmount();
});

test('rendered editor reaches row101, retains page during edit, and clamps shorter documents', async () => {
  const tableSource = readFileSync(
    new URL('../src/views/test-asset/components/DataTableEditor.vue', import.meta.url),
    'utf8'
  );
  const tableScript = compileScript(parse(tableSource).descriptor, { id: 'pages', inlineTemplate: true }).content;
  const tableJs = ts.transpileModule(tableScript, {
    compilerOptions: { module: ts.ModuleKind.CommonJS, esModuleInterop: true },
  }).outputText;
  const exports = {};
  // eslint-disable-next-line no-new-func
  new Function('exports', 'require', tableJs)(exports, () => vue);
  const rows = Array.from({ length: 205 }, (_, i) => [`row-${i}`]);
  const wrapper = mount(exports.default, {
    props: {
      'identity': 'a',
      'modelValue': JSON.stringify({ rows }),
      'onUpdate:modelValue': (value) => wrapper.setProps({ modelValue: value }),
    },
    global: {
      stubs: {
        AAlert: true,
        ASelect: true,
        ASpace: { template: '<div><slot /></div>' },
        AButton: { template: '<button><slot /></button>' },
        AInput: {
          props: ['modelValue'],
          emits: ['update:modelValue'],
          template: '<input :value="modelValue" @input="$emit(\'update:modelValue\',$event.target.value)" />',
        },
        APagination: {
          props: ['current'],
          emits: ['update:current'],
          template: '<button data-next @click="$emit(\'update:current\',current+1)">next</button>',
        },
      },
    },
  });
  await wrapper.find('[data-next]').trigger('click');
  assert.equal(wrapper.find('input').attributes('aria-label'), '第 101 行第 1 列');
  await wrapper.find('input').setValue('edited101');
  await flushPromises();
  assert.equal(wrapper.find('input').element.value, 'edited101');
  assert.equal(JSON.parse(wrapper.emitted('update:modelValue')[0][0]).rows[100][0], 'edited101');
  await wrapper.setProps({ modelValue: JSON.stringify({ rows: [['short']] }) });
  await flushPromises();
  assert.equal(wrapper.find('input').element.value, 'short');
  await wrapper.setProps({ modelValue: JSON.stringify({ rows: rows.slice(0, 100) }) });
  await wrapper.find('button').trigger('click');
  await flushPromises();
  assert.equal(wrapper.find('input').attributes('aria-label'), '第 101 行第 1 列');
  await wrapper.setProps({ identity: 'b' });
  await flushPromises();
  assert.equal(wrapper.find('input').attributes('aria-label'), '第 1 行第 1 列');
  wrapper.unmount();
});
test('version metadata renders historical values separately and never infers missing legacy category', async () => {
  const metaSource = readFileSync(
    new URL('../src/views/test-asset/components/DataVersionMetadata.vue', import.meta.url),
    'utf8'
  );
  const metaScript = compileScript(parse(metaSource).descriptor, { id: 'version-meta', inlineTemplate: true }).content;
  const metaJs = ts.transpileModule(metaScript, {
    compilerOptions: { module: ts.ModuleKind.CommonJS, esModuleInterop: true },
  }).outputText;
  const exports = {};
  // eslint-disable-next-line no-new-func
  new Function('exports', 'require', metaJs)(exports, () => vue);
  const wrapper = mount(exports.default, {
    props: {
      snapshot: {
        metadataAvailable: true,
        metadataSource: 'ASSET_VERSION',
        metadata: { name: '历史名称', description: '历史说明', categoryId: 'old-category', format: 'CSV' },
      },
    },
  });
  assert.match(wrapper.text(), /历史名称/);
  assert.match(wrapper.text(), /历史说明/);
  assert.match(wrapper.text(), /old-category/);
  assert.match(wrapper.text(), /CSV/);
  await wrapper.setProps({
    snapshot: { metadataAvailable: true, metadataSource: 'ASSET_VERSION', metadata: { categoryId: null } },
  });
  assert.match(wrapper.text(), /默认分类/);
  await wrapper.setProps({
    snapshot: {
      metadataAvailable: true,
      metadataSource: 'LEGACY_SOURCE_VERSION',
      metadata: { name: '旧文件', categoryId: null },
    },
  });
  assert.match(wrapper.text(), /历史分类未知/);
  assert.doesNotMatch(wrapper.text(), /未分类/);
  await wrapper.setProps({ snapshot: { metadataAvailable: false } });
  assert.match(wrapper.text(), /名称、说明、分类及格式未知/);
  assert.doesNotMatch(wrapper.text(), /历史名称/);
  wrapper.unmount();
});
test('rendered global data versions pages assets and rejects stale asset or organization responses', async () => {
  const viewSource = readFileSync(
    new URL('../src/views/test-asset/components/DataVersions.vue', import.meta.url),
    'utf8'
  );
  const viewScript = compileScript(parse(viewSource).descriptor, { id: 'data-versions', inlineTemplate: true }).content;
  const viewJs = ts.transpileModule(viewScript, {
    compilerOptions: { module: ts.ModuleKind.CommonJS, esModuleInterop: true },
  }).outputText;
  const exports = {};
  const store = vue.reactive({ currentOrgId: 'org1' });
  const paths = [];
  const pages = [];
  let finishA;
  const api = {
    listDataAssets: async (params) => {
      pages.push(params.page);
      return {
        items:
          store.currentOrgId === 'org1'
            ? [
                { id: 'a', name: 'DataA' },
                { id: 'b', name: 'DataB' },
              ]
            : [{ id: 'c', name: 'DataC' }],
        total: 41,
      };
    },
    dataVersions: (id) =>
      id === 'a'
        ? new Promise((resolve) => {
            finishA = resolve;
          })
        : Promise.resolve([{ id: `${id}-version`, versionNo: 2, metadataAvailable: false }]),
  };
  // eslint-disable-next-line no-new-func
  new Function('exports', 'require', viewJs)(exports, (name) => {
    if (name === 'vue') return vue;
    if (name.includes('businessAssetError')) return errorFormatter;
    if (name.includes('/appError')) return appErrors;
    if (name === 'vue-router') return { useRouter: () => ({ push: (path) => paths.push(path) }) };
    if (name.includes('store/modules/app')) return { __esModule: true, default: () => store };
    if (name.includes('test-data-assets')) return api;
    return { __esModule: true, default: { template: '<div>snapshot</div>' } };
  });
  const wrapper = mount(exports.default, {
    global: {
      stubs: {
        AAlert: true,
        AEmpty: true,
        AInputSearch: true,
        ASpin: { template: '<div><slot /></div>' },
        AButton: { template: '<button><slot /></button>' },
        APagination: {
          props: ['current'],
          emits: ['update:current', 'change'],
          template:
            "<button data-next @click=\"$emit('update:current',current+1);$emit('change',current+1)\">next page</button>",
        },
      },
    },
  });
  await flushPromises();
  await wrapper.find('[data-next]').trigger('click');
  await flushPromises();
  assert.deepEqual(pages, [1, 2]);
  await wrapper.find('[data-asset="a"]').trigger('click');
  await flushPromises();
  await wrapper.find('[data-asset="b"]').trigger('click');
  await flushPromises();
  finishA([{ id: 'stale-A', versionNo: 99 }]);
  await flushPromises();
  assert.equal(wrapper.find('[data-version="stale-A"]').exists(), false);
  await wrapper.find('[data-version="b-version"]').trigger('click');
  assert.deepEqual(paths[0], {
    name: 'testAssetDataDetail',
    params: { dataId: 'b' },
    query: { versionId: 'b-version' },
  });
  await wrapper.find('[data-next]').trigger('click');
  await flushPromises();
  assert.equal(wrapper.find('[data-version="b-version"]').exists(), false);
  await wrapper.find('[data-asset="a"]').trigger('click');
  await flushPromises();
  store.currentOrgId = 'org2';
  await flushPromises();
  finishA([{ id: 'late-old-org', versionNo: 99 }]);
  await flushPromises();
  assert.equal(wrapper.find('[data-version="late-old-org"]').exists(), false);
  assert.equal(wrapper.find('[data-asset="c"]').exists(), true);
  assert.equal(wrapper.find('[data-version="b-version"]').exists(), false);
  assert.equal(pages.at(-1), 1);
  wrapper.unmount();
});

test('safe AppError classification/CAS message and trace remain visible while local secrets stay hidden', async () => {
  const conflict = new appErrors.AppError({
    code: 'DATA_ASSET_REVISION_CONFLICT',
    category: 'conflict',
    message: '数据已更新，请刷新后重试',
    requestId: 'trace-safe-cas',
    retryable: true,
  });
  const h = setup(
    {
      listDataAssets: async () => {
        throw conflict;
      },
    },
    { errorView: true }
  );
  await flushPromises();
  assert.match(h.wrapper.text(), /数据已更新，请刷新后重试/);
  assert.match(h.wrapper.text(), /trace-safe-cas/);
  h.wrapper.unmount();
  const detail = setup(
    {
      getDataAsset: async () => {
        throw conflict;
      },
    },
    { detail: true, errorView: true, route: vue.reactive({ params: { dataId: 'a' }, query: {} }) }
  );
  await flushPromises();
  assert.match(detail.wrapper.text(), /trace-safe-cas/);
  detail.wrapper.unmount();
  const secret = setup(
    {
      listDataAssets: async () => {
        throw new Error('secretToken=DO_NOT_EXPOSE jdbc password=PRIVATE');
      },
    },
    { errorView: true }
  );
  await flushPromises();
  assert.match(secret.wrapper.text(), /操作失败/);
  assert.doesNotMatch(secret.wrapper.text(), /DO_NOT_EXPOSE|jdbc|PRIVATE/);
  secret.wrapper.unmount();
});

test('rendered editor modal contains visible CAS trace banner and hides local secrets', async () => {
  const conflict = new appErrors.AppError({
    code: 'DATA_ASSET_REVISION_CONFLICT',
    category: 'conflict',
    message: '版本已更新，请刷新',
    requestId: 'visible-modal-trace',
    retryable: true,
  });
  for (const reason of [conflict, new Error('secret-password-DO_NOT_SHOW')]) {
    const h = setup(
      {
        saveDataAsset: async () => {
          throw reason;
        },
      },
      { template: true }
    );
    await flushPromises();
    await h.wrapper
      .findAll('button')
      .find((button) => button.text() === '人工新建')
      .trigger('click');
    await flushPromises();
    await h.wrapper.find('[data-modal] input').setValue('New');
    await h.wrapper
      .findAll('[data-modal] button')
      .find((button) => button.text() === '保存')
      .trigger('click');
    await flushPromises();
    const banner = h.wrapper.find('[data-modal] [data-testid="data-editor-error"]');
    assert.equal(banner.exists(), true);
    if (reason === conflict) assert.match(banner.text(), /版本已更新，请刷新.*visible-modal-trace/);
    else {
      assert.match(banner.text(), /操作失败/);
      assert.doesNotMatch(banner.text(), /secret-password|DO_NOT_SHOW/);
    }
    h.wrapper.unmount();
  }
});

test('parse recovery clears completed or deleted jobs, retains failures and exposes non-404 errors', async () => {
  for (const status of ['READY', 'SUCCESS', 'CANCELLED', 'FAILED']) {
    sessionStorage.setItem('dataAssetParseJobId', 'saved-job');
    const h = setup({ getDataJob: async () => ({ id: 'saved-job', status }) });
    await flushPromises();
    assert.equal(sessionStorage.getItem('dataAssetParseJobId'), status === 'FAILED' ? 'saved-job' : null);
    h.wrapper.unmount();
    sessionStorage.clear();
  }
  const missing = new appErrors.AppError({
    status: 404,
    category: 'not-found',
    message: '任务不存在',
    retryable: false,
  });
  sessionStorage.setItem('dataAssetParseJobId', 'deleted-job');
  const h = setup({
    getDataJob: async () => {
      throw missing;
    },
  });
  await flushPromises();
  assert.equal(h.state.parseJob, '');
  assert.equal(h.state.parseStatus, '');
  assert.equal(h.state.error, '');
  assert.equal(sessionStorage.getItem('dataAssetParseJobId'), null);
  h.wrapper.unmount();
  const forbidden = new appErrors.AppError({
    status: 403,
    category: 'permission',
    message: '无权访问解析任务',
    requestId: 'trace-permission',
    retryable: false,
  });
  sessionStorage.setItem('dataAssetParseJobId', 'forbidden-job');
  const denied = setup({
    getDataJob: async () => {
      throw forbidden;
    },
  });
  await flushPromises();
  assert.equal(sessionStorage.getItem('dataAssetParseJobId'), 'forbidden-job');
  assert.match(denied.state.error, /无权访问解析任务.*trace-permission/);
  denied.wrapper.unmount();
  sessionStorage.clear();
});

test('editor waits for metadata then reads its immutable version and preserves CAS revision', async () => {
  let finishMetadata;
  let current = 'old-v1';
  const reads = [];
  const saved = [];
  const h = setup({
    getDataAsset: () =>
      new Promise((resolve) => {
        finishMetadata = resolve;
      }),
    getDataContent: async (id, version) => {
      reads.push(version);
      return { content: { from: version || current }, complete: true };
    },
    saveDataAsset: async (data) => {
      saved.push(data);
      return data;
    },
  });
  await flushPromises();
  const opening = h.state.open({ id: 'a' }, true);
  await flushPromises();
  assert.equal(reads.length, 0);
  current = 'new-v2';
  finishMetadata({ id: 'a', name: 'A', format: 'JSON', revision: 2, currentVersionId: 'new-v2' });
  await opening;
  assert.deepEqual(reads, ['new-v2']);
  await h.state.save();
  assert.deepEqual(saved[0].content, { from: 'new-v2' });
  assert.equal(saved[0].expectedRevision, 2);
  h.wrapper.unmount();
});
for (const phase of ['metadata', 'content'])
  test(`closing deferred ${phase} then creating saves without stale loading`, async () => {
    let finish;
    const saved = [];
    const h = setup({
      getDataAsset:
        phase === 'metadata'
          ? () =>
              new Promise((resolve) => {
                finish = () => resolve({ id: 'a', name: 'A', format: 'JSON', revision: 1, currentVersionId: 'a-v1' });
              })
          : async () => ({ id: 'a', name: 'A', format: 'JSON', revision: 1, currentVersionId: 'a-v1' }),
      getDataContent:
        phase === 'content'
          ? () =>
              new Promise((resolve) => {
                finish = () => resolve({ content: { old: true }, complete: true });
              })
          : async () => ({ content: { old: true }, complete: true }),
      saveDataAsset: async (data) => {
        saved.push(data);
        return data;
      },
    });
    await flushPromises();
    const opening = h.state.open({ id: 'a' }, true);
    await flushPromises();
    h.state.cancelEditor();
    h.state.create();
    h.state.draft.name = 'Fresh';
    h.state.contentText = '{"fresh":true}';
    await h.state.save();
    assert.equal(saved.length, 1);
    assert.deepEqual(saved[0].content, { fresh: true });
    finish();
    await opening;
    assert.equal(h.state.detailLoading, false);
    assert.equal(h.state.draft.name, 'Fresh');
    h.wrapper.unmount();
  });
test('fixed-version edit retains draft on intervening CAS conflict and metadata-only queue binds each snapshot', async () => {
  const reads = [];
  const saved = [];
  let serverRevision = 3;
  const h = setup({
    getDataContent: async (id, version) => {
      reads.push([id, version]);
      return { content: { snapshot: version }, complete: true };
    },
    saveDataAsset: async (data) => {
      saved.push(data);
      if (data.expectedRevision !== serverRevision)
        throw new appErrors.AppError({
          status: 409,
          category: 'conflict',
          message: '版本冲突',
          requestId: 'trace-cas',
          retryable: true,
        });
      return data;
    },
  });
  await flushPromises();
  await h.state.open({ id: 'a' }, true);
  h.state.draft.name = 'My metadata change';
  serverRevision = 4;
  await h.state.save();
  assert.equal(h.state.editorVisible, true);
  assert.equal(h.state.draft.name, 'My metadata change');
  assert.equal(saved[0].expectedRevision, 3);
  assert.deepEqual(saved[0].content, { snapshot: 'a-v3' });
  assert.match(h.state.error, /trace-cas/);
  serverRevision = 3;
  h.state.cancelEditor();
  h.state.selected = ['a', 'b'];
  h.state.startQueue();
  await flushPromises();
  await h.state.save();
  assert.equal(h.state.draft.id, 'b');
  assert.equal(h.state.draft.expectedRevision, 3);
  await h.state.save();
  assert.deepEqual(saved.at(-1).content, { snapshot: 'b-v3' });
  assert.deepEqual(reads.slice(-2), [
    ['a', 'a-v3'],
    ['b', 'b-v3'],
  ]);
  h.wrapper.unmount();
});
for (const outcome of ['success', 'failure'])
  test(`late old ${outcome} cannot release another entity loading or replace its draft`, async () => {
    let finishA;
    let finishB;
    const saved = [];
    const h = setup({
      getDataAsset: (id) =>
        new Promise((resolve, reject) => {
          if (id === 'a')
            finishA = () =>
              outcome === 'success'
                ? resolve({ id: 'a', name: 'A', format: 'JSON', revision: 1, currentVersionId: 'a-v1' })
                : reject(Error('old-secret'));
          else finishB = () => resolve({ id: 'b', name: 'B', format: 'JSON', revision: 2, currentVersionId: 'b-v2' });
        }),
      saveDataAsset: async (data) => {
        saved.push(data);
        return data;
      },
    });
    await flushPromises();
    const old = h.state.open({ id: 'a' }, true);
    await flushPromises();
    h.state.cancelEditor();
    const fresh = h.state.open({ id: 'b' }, true);
    await flushPromises();
    finishA();
    await old;
    assert.equal(h.state.detailLoading, true);
    assert.equal(h.state.complete, false);
    assert.equal(h.state.error, '');
    await h.state.save();
    assert.equal(saved.length, 0);
    finishB();
    await fresh;
    assert.equal(h.state.draft.id, 'b');
    await h.state.save();
    assert.equal(saved.length, 1);
    assert.equal(saved[0].id, 'b');
    assert.equal(saved[0].expectedRevision, 2);
    h.wrapper.unmount();
  });
test('historical snapshot cannot enter current edit or overwrite it', async () => {
  let reads = 0;
  const saved = [];
  const h = setup({
    getDataContent: async () => {
      reads += 1;
      return { content: 'old', complete: true };
    },
    saveDataAsset: async (data) => saved.push(data),
  });
  await flushPromises();
  await h.state.open({ id: 'a' }, true, 'old-version');
  assert.equal(reads, 0);
  assert.equal(h.state.complete, false);
  await h.state.save();
  assert.equal(saved.length, 0);
  h.wrapper.unmount();
});

test('quick category creation validates, persists, refreshes and preserves failure dialog', async () => {
  let created;
  const h = setup({
    createTestAssetCategory: async (data) => {
      created = data;
    },
  });
  await flushPromises();
  h.state.quickCategoryVisible = true;
  h.state.quickCategoryName = '   ';
  assert.equal(await h.state.createCategory(), false);
  h.state.quickCategoryName = ' New category ';
  assert.equal(await h.state.createCategory(), true);
  assert.deepEqual(created, { name: 'New category' });
  assert.equal(h.state.categoryLabel(''), '默认分类');
  h.wrapper.unmount();
});

test('dataset actions use modal, named controls and capacity units without legacy entry', () => {
  assert.match(source, /v-model:visible="deleteVisible"/);
  assert.doesNotMatch(source, /a-popconfirm content="确认删除所选/);
  assert.match(source, /aria-label="新增分类"/);
  assert.match(source, /aria-label="管理分类"/);
  assert.match(source, /aria-label="更多操作">更多操作/);
  assert.match(source, /1024 \/ 1024/);
  assert.doesNotMatch(source, /历史项目数据|导入历史项目文件|全局资产/);
  const route = readFileSync(new URL('../src/router/routes/modules/testAsset.ts', import.meta.url), 'utf8');
  const versions = route.slice(route.indexOf("path: 'versions'"), route.indexOf("path: 'relations'"));
  assert.doesNotMatch(versions, /resourceCode|roles: readRoles/);
});

test('quick category server failure remains actionable', async () => {
  const h = setup({
    createTestAssetCategory: async () => {
      throw new Error('private server');
    },
  });
  await flushPromises();
  h.state.quickCategoryVisible = true;
  h.state.quickCategoryName = 'Name';
  assert.equal(await h.state.createCategory(), false);
  assert.equal(h.state.quickCategoryVisible, true);
  assert.ok(h.state.error);
  assert.doesNotMatch(h.state.error, /private server/);
  h.wrapper.unmount();
});

function recordsSetup(api, clipboard) {
  const recordSource = readFileSync(
    new URL('../src/views/test-asset/components/DataRecordContent.vue', import.meta.url),
    'utf8'
  );
  const code = ts.transpileModule(compileScript(parse(recordSource).descriptor, { id: 'records' }).content, {
    compilerOptions: { module: ts.ModuleKind.CommonJS },
  }).outputText;
  const out = {};
  Object.defineProperty(navigator, 'clipboard', { value: { writeText: clipboard }, configurable: true });
  // eslint-disable-next-line no-new-func
  new Function('exports', 'require', code)(out, (name) =>
    name === 'vue'
      ? vue
      : name.includes('test-data-assets')
      ? api
      : name.includes('businessAssetError')
      ? errorFormatter
      : { default: () => vue.reactive({ currentOrgId: 'org', id: 'actor' }) }
  );
  out.default.render = () => vue.h('div');
  const wrapper = mount(out.default, { props: { assetId: 'a', versionId: 'v1' } });
  return { wrapper, state: wrapper.vm.$.setupState };
}
test('records copy marks only after clipboard success and retry never copies twice', async () => {
  const calls = [];
  let fail = true;
  const h = recordsSetup(
    {
      getDataRecords: async () => ({ items: [{ recordKey: 'r', content: 'x', used: false }], total: 100000 }),
      copyDataRecords: async () => ({ content: 'x' }),
      markDataRecordsUsed: async (...args) => {
        calls.push(args);
        if (fail) throw new Error('private');
      },
    },
    async () => {
      calls.push('clipboard');
    }
  );
  await flushPromises();
  await h.state.copy(['r']);
  assert.equal(calls[0], 'clipboard');
  assert.ok(h.state.pending);
  assert.equal(h.state.rows[0].used, false);
  fail = false;
  await h.state.retryMark();
  assert.equal(calls.filter((x) => x === 'clipboard').length, 1);
  assert.equal(h.state.rows[0].used, true);
  assert.equal(h.state.pending, undefined);
  assert.equal(calls[1][3], calls[2][3]);
  h.wrapper.unmount();
});
test('clipboard rejection never marks records and late copy cannot cross version', async () => {
  let marks = 0;
  let finish;
  const api = {
    getDataRecords: async () => ({ items: [], total: 0 }),
    copyDataRecords: async () => ({ content: 'x' }),
    markDataRecordsUsed: async () => {
      marks++;
    },
  };
  const h = recordsSetup(api, async () => {
    throw new Error('denied');
  });
  await flushPromises();
  await h.state.copy(['r']);
  assert.equal(marks, 0);
  assert.ok(h.state.error);
  h.wrapper.unmount();
  const late = recordsSetup(
    {
      ...api,
      copyDataRecords: () =>
        new Promise((r) => {
          finish = r;
        }),
    },
    async () => {
      throw new Error('must not copy');
    }
  );
  await flushPromises();
  const pending = late.state.copy(['r']);
  await late.wrapper.setProps({ versionId: 'v2' });
  finish({ content: 'old' });
  await pending;
  assert.equal(marks, 0);
  assert.equal(late.state.error, '');
  late.wrapper.unmount();
});

test('detail and record full SFC templates compile with bounded paging and named state', () => {
  for (const file of ['data-detail.vue', 'components/DataRecordContent.vue']) {
    const text = readFileSync(new URL(`../src/views/test-asset/${file}`, import.meta.url), 'utf8');
    const compiledSfc = compileScript(parse(text).descriptor, { id: file, inlineTemplate: true });
    assert.ok(compiledSfc.content.includes('return'));
  }
  const detail = readFileSync(new URL('../src/views/test-asset/data-detail.vue', import.meta.url), 'utf8');
  assert.match(detail, /default-active-key="content"/);
  assert.match(detail, /title="基本信息"/);
  assert.doesNotMatch(detail, /replaceDataFile|替换文件|全局资产|getDataContent/);
  const records = readFileSync(
    new URL('../src/views/test-asset/components/DataRecordContent.vue', import.meta.url),
    'utf8'
  );
  assert.match(records, /pageSize: 100/);
  assert.match(records, /仅重试已使用标记/);
  assert.match(records, /role="status"/);
});

test('migration displays failure and retries only explicitly', async () => {
  let runs = 0;
  const failed = { counts: { pending: 0, running: 0, failed: 1, done: 0 }, canRun: true, hasMore: false, items: [] };
  const h = setup({
    getLegacyMigrationStatus: async () => failed,
    runLegacyMigration: async (_org, retry) => {
      assert.equal(retry, true);
      runs++;
      return { ...failed, counts: { ...failed.counts, failed: 0, done: 1 } };
    },
  });
  await flushPromises();
  assert.equal(runs, 0);
  assert.equal(h.state.migration.counts.failed, 1);
  await h.state.migrate(true);
  assert.equal(runs, 1);
  assert.equal(h.state.migration.counts.done, 1);
  h.wrapper.unmount();
});

function caseDirectorySetup(route, api, app) {
  function component(file) {
    const text = readFileSync(new URL(`../src/views/test-asset/${file}`, import.meta.url), 'utf8');
    const code = ts.transpileModule(compileScript(parse(text).descriptor, { id: file }).content, {
      compilerOptions: { module: ts.ModuleKind.CommonJS },
    }).outputText;
    const out = {};
    // eslint-disable-next-line no-new-func
    new Function('exports', 'require', code)(out, (name) => {
      if (name === 'vue') return vue;
      if (name === 'vue-router') return { useRoute: () => route, useRouter: () => ({ push: () => undefined }) };
      if (name.includes('routeEnum')) return { TestAssetRouteEnum: { TEST_ASSET_CASES_SYSTEM: 'system' } };
      if (name.includes('permission'))
        return { hasAnyPermission: () => true, hasPageVisible: () => true, hasRouteVisible: () => true };
      if (name.includes('store/modules/app')) return { default: () => app };
      if (name.includes('/http/index')) return { default: { post: api.page } };
      return { __esModule: true, default: () => undefined, ...api };
    });
    return out.default;
  }
  const child = component('components/SystemCaseAssets.vue');
  child.name = 'DirectoryChild';
  child.render = () => vue.h('div');
  const parent = component('cases.vue');
  parent.render = function renderDirectory() {
    return this.$.setupState.systemView ? vue.h(child) : vue.h('div');
  };
  const wrapper = mount(parent);
  return { wrapper, state: wrapper.vm.$.setupState };
}
test('mounted system directory emits only SYSTEM request and first load succeeds', async () => {
  const route = vue.reactive({ name: 'system', query: {} });
  const app = vue.reactive({ currentOrgId: '' });
  const requests = [];
  const api = {
    getAssetSystems: async () => ({ systems: [], unclassifiedCount: 0 }),
    page: async (req) => {
      requests.push(req.data.dimension);
      return { list: [{ id: 's' }], total: 1342 };
    },
    listTestAssetCategories: async () => {
      requests.push('PROJECT categories');
      return [];
    },
    pageCaseAssetCatalogs: async () => {
      requests.push('PROJECT catalogs');
      return { list: [], total: 0 };
    },
  };
  const h = caseDirectorySetup(route, api, app);
  await flushPromises();
  assert.deepEqual(requests, []);
  app.currentOrgId = 'org';
  await flushPromises();
  assert.deepEqual(requests, ['SYSTEM']);
  const child = h.wrapper.findComponent({ name: 'DirectoryChild' });
  assert.equal(child.vm.$.setupState.failed, false);
  assert.equal(child.vm.$.setupState.total, 1342);
  h.wrapper.unmount();
});
test('project catalog late response cannot emit PROJECT page after system switch', async () => {
  const route = vue.reactive({ name: 'project', query: {} });
  const app = vue.reactive({ currentOrgId: 'org' });
  let finish;
  const requests = [];
  const api = {
    listTestAssetCategories: async () => [],
    pageCaseAssetCatalogs: () =>
      new Promise((r) => {
        finish = r;
      }),
    getAssetSystems: async () => ({ systems: [], unclassifiedCount: 0 }),
    page: async () => {
      requests.push('SYSTEM');
      return { list: [], total: 1342 };
    },
    getCaseAssetList: async () => {
      requests.push('PROJECT');
      return { list: [], total: 0 };
    },
  };
  const h = caseDirectorySetup(route, api, app);
  await flushPromises();
  route.name = 'system';
  await flushPromises();
  finish({ list: [{ id: 'old' }], total: 1 });
  await flushPromises();
  assert.deepEqual(requests, ['SYSTEM']);
  h.wrapper.unmount();
});

test('system organization switch rejects old tree and old row results', async () => {
  const route = vue.reactive({ name: 'system', query: {} });
  const app = vue.reactive({ currentOrgId: 'old' });
  let oldTree;
  let oldPage;
  let calls = 0;
  const api = {
    getAssetSystems: () => {
      calls++;
      return calls === 1
        ? new Promise((r) => {
            oldTree = r;
          })
        : Promise.resolve({ systems: [], unclassifiedCount: 0 });
    },
    page: () =>
      new Promise((r) => {
        oldPage = r;
      }),
  };
  const h = caseDirectorySetup(route, api, app);
  await flushPromises();
  app.currentOrgId = 'new';
  await flushPromises();
  oldTree({ systems: [{ id: 'private' }], unclassifiedCount: 9 });
  await flushPromises();
  const child = h.wrapper.findComponent({ name: 'DirectoryChild' });
  assert.deepEqual(child.vm.$.setupState.systems, []);
  app.currentOrgId = '';
  await flushPromises();
  oldPage({ list: [{ id: 'private' }], total: 99 });
  await flushPromises();
  assert.deepEqual(child.vm.$.setupState.rows, []);
  assert.equal(child.vm.$.setupState.total, 0);
  h.wrapper.unmount();
});
test('directory and asset layout full SFC templates compile', () => {
  for (const file of ['cases.vue', 'catalog.vue', 'business-assets.vue', 'components/SystemCaseAssets.vue']) {
    const text = readFileSync(new URL(`../src/views/test-asset/${file}`, import.meta.url), 'utf8');
    assert.ok(compileScript(parse(text).descriptor, { id: file, inlineTemplate: true }).content);
  }
});

test('read-only migration status never runs and late organization status is ignored', async () => {
  let runs = 0;
  const status = { counts: { pending: 1, running: 0, failed: 0, done: 0 }, canRun: true, hasMore: false, items: [] };
  const readonly = setup(
    {
      getLegacyMigrationStatus: async () => status,
      runLegacyMigration: async () => {
        runs++;
        return status;
      },
    },
    { canWrite: false }
  );
  await flushPromises();
  assert.equal(runs, 0);
  assert.equal(readonly.state.migration.counts.pending, 1);
  readonly.wrapper.unmount();
  let finish;
  let count = 0;
  const h = setup({
    getLegacyMigrationStatus: () => {
      count++;
      return count === 1
        ? new Promise((r) => {
            finish = r;
          })
        : Promise.resolve({ ...status, counts: { ...status.counts, pending: 0 }, canRun: false });
    },
    runLegacyMigration: async () => {
      runs++;
      return status;
    },
  });
  await flushPromises();
  h.state.app.currentOrgId = 'org-b';
  await flushPromises();
  finish(status);
  await flushPromises();
  assert.equal(runs, 0);
  assert.equal(h.state.migration.counts.pending, 0);
  h.wrapper.unmount();
});

test('record refresh failure removes stale selectable rows', async () => {
  let fail = false;
  const h = recordsSetup(
    {
      getDataRecords: async () => {
        if (fail) throw new Error('offline');
        return { items: [{ recordKey: 'r' }], total: 1 };
      },
    },
    async () => undefined
  );
  await flushPromises();
  h.state.selected = ['r'];
  fail = true;
  await h.state.load();
  assert.deepEqual(h.state.rows, []);
  assert.deepEqual(h.state.selected, []);
  h.wrapper.unmount();
});
test('pending mark restores after remount and metadata version change', async () => {
  sessionStorage.clear();
  let fail = true;
  const versions = [];
  const api = {
    getDataRecords: async () => ({ items: [], total: 0 }),
    copyDataRecords: async () => ({ content: 'secret content' }),
    markDataRecordsUsed: async (_id, version) => {
      versions.push(version);
      if (fail) throw new Error('offline');
    },
  };
  const h = recordsSetup(api, async () => undefined);
  await flushPromises();
  await h.state.copy(['r']);
  assert.ok(h.state.pending);
  assert.doesNotMatch(JSON.stringify(sessionStorage), /secret content/);
  h.wrapper.unmount();
  const restored = recordsSetup(api, async () => {
    throw new Error('no recopy');
  });
  await flushPromises();
  await restored.wrapper.setProps({ versionId: 'metadata-v2' });
  assert.equal(restored.state.pending.versionId, 'v1');
  fail = false;
  await restored.state.retryMark();
  assert.equal(versions.at(-1), 'v1');
  assert.equal(sessionStorage.length, 0);
  restored.wrapper.unmount();
});

test('outbox is absent on clipboard failure and never sends across organization or actor', async () => {
  sessionStorage.clear();
  let marks = 0;
  const api = {
    getDataRecords: async () => ({ items: [], total: 0 }),
    copyDataRecords: async () => ({ content: 'x' }),
    markDataRecordsUsed: async () => {
      marks++;
      throw new Error('offline');
    },
  };
  const denied = recordsSetup(api, async () => {
    throw new Error('denied');
  });
  await flushPromises();
  await denied.state.copy(['r']);
  assert.equal(sessionStorage.length, 0);
  denied.wrapper.unmount();
  const h = recordsSetup(api, async () => undefined);
  await flushPromises();
  await h.state.copy(['r']);
  assert.equal(marks, 1);
  h.state.app.currentOrgId = 'other';
  await flushPromises();
  assert.equal(h.state.pending, undefined);
  await h.state.retryMark();
  assert.equal(marks, 1);
  h.wrapper.unmount();
  sessionStorage.clear();
});

test('version page renders data-only access without authorization empty and no-access exactly one empty', () => {
  const text = readFileSync(new URL('../src/views/test-asset/versions.vue', import.meta.url), 'utf8');
  const template = parse(text).descriptor.template.content;
  function renderAccess(canReadData) {
    return mount(
      {
        data: () => ({
          canReadData,
          canReadBusiness: false,
          canReadConfiguration: false,
          canReadLegacy: false,
          versionTab: 'data',
        }),
        render: vue.compile(template),
      },
      {
        global: {
          stubs: {
            TestAssetPage: { template: '<div><slot /></div>' },
            ATabs: { template: '<div><slot /></div>' },
            ATabPane: { template: '<article><slot /></article>' },
            AEmpty: { props: ['description'], template: '<p data-empty>{{description}}</p>' },
            DataVersions: { template: '<div data-data-versions>Data versions</div>' },
          },
        },
      }
    );
  }
  const dataOnly = renderAccess(true);
  assert.equal(dataOnly.find('[data-data-versions]').exists(), true);
  assert.equal(dataOnly.findAll('[data-empty]').length, 0);
  dataOnly.unmount();
  const denied = renderAccess(false);
  assert.equal(denied.findAll('[data-empty]').length, 1);
  assert.equal(denied.find('[data-empty]').text(), '暂无可查看的资产版本');
  assert.equal(denied.find('[data-data-versions]').exists(), false);
  denied.unmount();
});

test('category management permission is independent of data write permission', async () => {
  for (const [canWrite, canManageCategories] of [
    [true, false],
    [false, true],
    [true, true],
    [false, false],
  ]) {
    let creates = 0;
    const h = setup(
      {
        createTestAssetCategory: async () => {
          creates++;
        },
      },
      { canWrite, canManageCategories }
    );
    await flushPromises();
    assert.equal(h.state.canManageCategories, canManageCategories);
    h.state.openCreateCategory();
    assert.equal(h.state.quickCategoryVisible, canManageCategories);
    h.state.quickCategoryName = 'Category';
    assert.equal(await h.state.createCategory(), canManageCategories);
    assert.equal(creates, canManageCategories ? 1 : 0);
    h.wrapper.unmount();
  }
});

test('failed legacy family does not block independent pending family or trigger automatic failure retry', async () => {
  const flags = [];
  const initial = { counts: { pending: 1, running: 0, failed: 1, done: 0 }, canRun: true, hasMore: true, items: [] };
  const h = setup({
    getLegacyMigrationStatus: async () => initial,
    runLegacyMigration: async (_org, retry) => {
      flags.push(retry);
      return { ...initial, counts: { ...initial.counts, pending: 0, done: 1 }, hasMore: false };
    },
  });
  await flushPromises();
  assert.deepEqual(flags, [false]);
  assert.equal(h.state.migration.counts.failed, 1);
  assert.equal(h.state.migration.counts.done, 1);
  h.wrapper.unmount();
});

test('dataset full template keeps readable column widths and wraps list toolbar', () => {
  const { descriptor } = parse(source);
  assert.ok(compileScript(descriptor, { id: 'readable-table', inlineTemplate: true }).content);
  assert.match(source, /:scroll="\{ x: 1400 \}"/);
  assert.match(source, /<a-space wrap class="list-toolbar"/);
  assert.match(source, /min-width: 1400px/);
  assert.match(source, /white-space: nowrap/);
  for (const title of ['名称', '分类', '负责人', '更新时间', '格式', '解析状态', '修订'])
    assert.match(source, new RegExp(`title="${title}"\\s+:width="[0-9]+"`));
});

for (const endpoint of ['listDataAssets', 'listDataCategories']) {
  test(`superseded ${endpoint} rejection cannot overwrite newer success; current failure is visible`, async () => {
    let rejectOld;
    let count = 0;
    const success =
      endpoint === 'listDataAssets'
        ? { items: [{ id: 'new' }], total: 1 }
        : [{ id: 'new', name: 'New', assetCount: 1 }];
    const h = setup({
      getLegacyMigrationStatus: async () => ({
        counts: { pending: 0, running: 0, failed: 1, done: 0 },
        canRun: false,
        hasMore: false,
        items: [],
      }),
      [endpoint]: () => {
        count++;
        return count === 1
          ? new Promise((_resolve, reject) => {
              rejectOld = reject;
            })
          : Promise.resolve(success);
      },
    });
    await flushPromises();
    await h.state[endpoint === 'listDataAssets' ? 'load' : 'loadCategories']();
    rejectOld(new Error('canceled old'));
    await flushPromises();
    assert.equal(h.state.error, '');
    assert.equal((endpoint === 'listDataAssets' ? h.state.rows : h.state.categories)[0].id, 'new');
    h.wrapper.unmount();
    const failed = setup({
      [endpoint]: async () => {
        throw new Error('current network failure');
      },
    });
    await flushPromises();
    assert.ok(failed.state.error);
    failed.wrapper.unmount();
  });
}

test('nested case and catalog cards use local auto width with full templates intact', () => {
  for (const file of ['cases.vue', 'components/SystemCaseAssets.vue', 'catalog.vue']) {
    const text = readFileSync(new URL(`../src/views/test-asset/${file}`, import.meta.url), 'utf8');
    assert.ok(compileScript(parse(text).descriptor, { id: file, inlineTemplate: true }).content);
    for (const match of text.matchAll(/<MsCard\b[^>]*>/g)) assert.match(match[0], /auto-width/);
  }
});

test('late limits after unmount cannot launch list or category requests', async () => {
  let resolve;
  let reads = 0;
  const h = setup({
    getDataLimits: () =>
      new Promise((r) => {
        resolve = r;
      }),
    getLegacyMigrationStatus: async () => ({ counts: { failed: 1 }, canRun: false, hasMore: true }),
    listDataAssets: async () => {
      reads++;
      return { items: [], total: 0 };
    },
    listDataCategories: async () => {
      reads++;
      return [];
    },
  });
  await flushPromises();
  h.wrapper.unmount();
  resolve({ formats: [], maxBytes: 1, maxRows: 1 });
  await flushPromises();
  assert.equal(reads, 0);
  await h.state.load();
  await h.state.loadCategories();
  assert.equal(reads, 0);
});

for (const transition of ['version', 'asset', 'unmount', 'organization', 'actor'])
  for (const succeeds of [true, false]) {
    test(`clipboard completion ${succeeds} after ${transition} preserves original ownership only on success`, async () => {
      sessionStorage.clear();
      let finish;
      let reject;
      let marks = 0;
      const api = {
        getDataRecords: async () => ({ items: [], total: 0 }),
        copyDataRecords: async () => ({ content: 'private payload' }),
        markDataRecordsUsed: async () => {
          marks++;
        },
      };
      const h = recordsSetup(
        api,
        () =>
          new Promise((resolve, rejectPromise) => {
            finish = resolve;
            reject = rejectPromise;
          })
      );
      await flushPromises();
      const operation = h.state.copy(['r']);
      await flushPromises();
      if (transition === 'version') await h.wrapper.setProps({ versionId: 'v2' });
      if (transition === 'asset') await h.wrapper.setProps({ assetId: 'b' });
      if (transition === 'organization') h.state.app.currentOrgId = 'other';
      if (transition === 'actor') h.state.user.id = 'other';
      if (transition === 'unmount') h.wrapper.unmount();
      await flushPromises();
      if (succeeds) finish();
      else reject(new Error('denied'));
      await operation;
      await flushPromises();
      assert.equal(marks, 0);
      assert.equal(sessionStorage.length, succeeds ? 1 : 0);
      if (succeeds) {
        const job = JSON.parse(sessionStorage.getItem(sessionStorage.key(0)));
        assert.equal(job.actor, 'actor');
        assert.equal(job.organization, 'org');
        assert.equal(job.assetId, 'a');
        assert.equal(job.versionId, 'v1');
        assert.deepEqual(job.keys, ['r']);
        assert.doesNotMatch(JSON.stringify(job), /private payload/);
      }
      if (transition !== 'unmount') h.wrapper.unmount();
      sessionStorage.clear();
    });
  }

test('two in-flight clipboard successes survive remount without outbox overwrite and retry never recopies', async () => {
  sessionStorage.clear();
  let finishFirst;
  let finishSecond;
  let fail = true;
  let copies = 0;
  const marks = [];
  const api = {
    getDataRecords: async () => ({ items: [], total: 0 }),
    copyDataRecords: async () => {
      copies++;
      return { content: 'x' };
    },
    markDataRecordsUsed: async (...args) => {
      marks.push(args);
      if (fail) throw new Error('offline');
    },
  };
  const first = recordsSetup(
    api,
    () =>
      new Promise((r) => {
        finishFirst = r;
      })
  );
  await flushPromises();
  const firstCopy = first.state.copy(['first']);
  await flushPromises();
  first.wrapper.unmount();
  const second = recordsSetup(
    api,
    () =>
      new Promise((r) => {
        finishSecond = r;
      })
  );
  await flushPromises();
  const secondCopy = second.state.copy(['second']);
  await flushPromises();
  finishFirst();
  await firstCopy;
  finishSecond();
  await secondCopy;
  assert.equal(sessionStorage.length, 2);
  second.wrapper.unmount();
  const restored = recordsSetup(api, async () => {
    throw new Error('must not recopy');
  });
  await flushPromises();
  fail = false;
  await restored.state.retryMark();
  assert.equal(sessionStorage.length, 1);
  assert.ok(restored.state.pending);
  await restored.state.retryMark();
  assert.equal(sessionStorage.length, 0);
  assert.equal(restored.state.pending, undefined);
  assert.equal(copies, 2);
  assert.notEqual(marks.at(-2)[3], marks.at(-1)[3]);
  restored.wrapper.unmount();
});
test('legacy base-key outbox coexists with request entries and marking removes only its own task', async () => {
  sessionStorage.clear();
  const base = 'data-record-outbox:actor:org:a';
  const job = {
    actor: 'actor',
    organization: 'org',
    assetId: 'a',
    versionId: 'historical',
    requestId: 'legacy',
    keys: ['one'],
  };
  sessionStorage.setItem(base, JSON.stringify(job));
  sessionStorage.setItem(`${base}:new`, JSON.stringify({ ...job, requestId: 'new', keys: ['two'] }));
  let copies = 0;
  const marks = [];
  const h = recordsSetup(
    {
      getDataRecords: async () => ({ items: [], total: 0 }),
      copyDataRecords: async () => {
        copies++;
        return { content: 'x' };
      },
      markDataRecordsUsed: async (...args) => {
        marks.push(args);
      },
    },
    async () => undefined
  );
  await flushPromises();
  await h.state.retryMark();
  assert.equal(sessionStorage.getItem(base), null);
  assert.ok(sessionStorage.getItem(`${base}:new`));
  assert.equal(h.state.pending.requestId, 'new');
  await h.state.retryMark();
  assert.equal(sessionStorage.length, 0);
  assert.equal(copies, 0);
  assert.deepEqual(
    marks.map((m) => m[1]),
    ['historical', 'historical']
  );
  h.wrapper.unmount();
});
