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
    if (name.includes('ai-execution')) return { listTestAssetCategories: async () => [] };
    if (name.includes('permission')) return { hasAnyPermission: () => true };
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
  h.state.importVisible = true;
  h.state.legacyVisible = true;
  h.state.app.currentOrgId = 'org-b';
  await flushPromises();
  resolve({ content: { private: 'previous organization' }, complete: true });
  await pending;
  assert.equal(h.state.editorVisible, false);
  assert.equal(h.state.categoryVisible, false);
  assert.equal(h.state.batchVisible, false);
  assert.equal(h.state.importVisible, false);
  assert.equal(h.state.legacyVisible, false);
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
test('detail entity switch resets history and late replacement cannot mutate newer target', async () => {
  const route = vue.reactive({ params: { dataId: 'a' }, query: { versionId: 'a-old' } });
  const reads = [];
  let finish;
  const h = setup(
    {
      getDataContent: async (id, version) => {
        reads.push([id, version]);
        return { content: id, complete: true };
      },
      replaceDataFile: () =>
        new Promise((r) => {
          finish = r;
        }),
    },
    { detail: true, route }
  );
  await flushPromises();
  assert.deepEqual(reads[0], ['a', 'a-old']);
  const pending = h.state.replaceFile({ target: { files: [{ name: 'x.json', size: 1, lastModified: 1 }], value: '' } });
  await flushPromises();
  route.params.dataId = 'b';
  route.query = {};
  await flushPromises();
  assert.deepEqual(reads.at(-1), ['b', undefined]);
  const count = reads.length;
  finish({ id: 'a' });
  await pending;
  assert.equal(reads.length, count);
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
