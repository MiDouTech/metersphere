import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { resolve, dirname } from 'node:path';
import { test } from 'node:test';
import vm from 'node:vm';
import ts from 'typescript';

const src = resolve(import.meta.dirname, '../src');
const grants = new Set();
const hidden = new Set();
function load(file) {
  const filename = resolve(src, file);
  const output = ts.transpileModule(readFileSync(filename, 'utf8'), {
    compilerOptions: { module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2022 },
  }).outputText;
  const module = { exports: {} };
  const require = (name) => {
    if (name === '@/utils/permission') return {
      hasAnyPermission: (roles) => roles.some((role) => grants.has(role)),
      hasPageVisible: (code) => !hidden.has(code),
      hasRouteVisible: (name) => !hidden.has(name),
    };
    if (name.endsWith('/base')) return { DEFAULT_LAYOUT: {} };
    const target = name.startsWith('@/') ? resolve(src, name.slice(2)) : resolve(dirname(filename), name);
    return load(`${target}.ts`);
  };
  vm.runInNewContext(output, { module, exports: module.exports, require });
  return module.exports;
}
const plain = (value) => JSON.parse(JSON.stringify(value));
const navigation = load('utils/execution-navigation.ts');
const legacy = load('router/routes/modules/agent.ts').default;
const execution = load('router/routes/modules/execution.ts').default;
const settings = load('router/routes/execution-settings.ts').default;

test('task links retain scope and supported filters, never credentials or arbitrary return URLs', () => {
  const target = plain(navigation.executionLocation({ taskId: 'task-1', projectId: 'project-1',
    keyword: 'failed', status: 'FAILED', token: 'secret', returnUrl: 'https://outside.invalid' }));
  assert.equal(target.name, 'AgentExecutionDetail');
  assert.deepEqual(target.params, { id: 'task-1' });
  assert.deepEqual(target.query, { projectId: 'project-1', pId: 'project-1', keyword: 'failed', status: 'FAILED' });
});
test('invalid, repeated or missing task IDs resolve to the list, never interpolated paths', () => {
  for (const id of [undefined, ['a', 'b'], '../../setting', '//outside', 'a?token=x']) {
    const target = navigation.executionLocation({ executionTaskId: id });
    assert.equal(target.path, '/execution/tasks');
    assert.equal(target.query.executionTaskId, undefined);
  }
});
test('case and asset creation links keep explicit case and immutable version scope', () => {
  const query = { creating: '1', caseIds: ['a', 'b'], assetType: 'DOCUMENT', assetId: 'd', assetVersionId: 'v1' };
  assert.deepEqual(plain(navigation.executionLocation(query).query), query);
});
test('legacy queue routes distinguish business tasks from leases and triggers', () => {
  const redirect = legacy.children.find((route) => route.path === 'queue').redirect;
  assert.equal(redirect({ query: {} }).path, '/execution/tasks');
  for (const tab of ['leases', 'triggers']) {
    const target = redirect({ query: { tab, pId: 'p' } });
    assert.equal(target.path, '/setting/runtime/queue');
    assert.equal(target.query.tab, tab);
    assert.equal(target.query.pId, 'p');
  }
});
test('legacy evaluation routes reach real statistics with supported filters', () => {
  const target = legacy.children.find((route) => route.path === 'evaluation').redirect({ query: { executorType: 'AGENT' } });
  assert.equal(target.path, '/execution/tasks');
  assert.equal(target.query.view, 'evaluation');
  assert.equal(target.query.executorType, 'AGENT');
});
test('there is one business task primary menu and no approval placeholder', () => {
  assert.equal(execution.children.filter((route) => route.meta?.isTopMenu).length, 1);
  assert.equal(execution.children.some((route) => /approv/.test(route.path)), false);
  assert.equal(legacy.meta.hideInMenu, true);
});
test('moving configuration retains admin boundaries and specific permissions', () => {
  for (const group of settings) {
    for (const route of group.children) {
      if (['agentAccess', 'ExecutionQualityPolicy'].includes(route.name)) continue;
      assert.equal(route.meta.adminOnly, true, String(route.name));
      assert.ok(route.meta.roles.length, String(route.name));
    }
  }
  const personal = settings.find((route) => route.path === 'personal-access').children[0];
  assert.deepEqual(plain(personal.meta.roles), ['SYSTEM_PERSONAL_AI_AGENT:READ']);
  assert.equal(personal.meta.resourceCode, 'AGENT_INTEGRATION_PAGE');
});
test('asset default chooses an authorized primary page and respects custom UI denial', () => {
  const asset = load('router/routes/modules/testAsset.ts').default;
  grants.clear(); hidden.clear();
  assert.equal(asset.redirect(), '/no-resource');
  grants.add('CASE_ASSET:READ');
  assert.equal(asset.redirect(), '/test-assets/cases/project');
  hidden.add('TEST_ASSET_CASE_PROJECT_TAB');
  assert.equal(asset.redirect(), '/no-resource');
  grants.add('PROJECT_FILE_MANAGEMENT:READ');
  assert.equal(asset.redirect(), '/test-assets/datasets');
  assert.equal(asset.children.filter((route) => route.meta?.isTopMenu).length, 3);
});
test('historical asset URLs retain their page implementation, never redirect to latest source data', () => {
  const asset = load('router/routes/modules/testAsset.ts').default;
  for (const path of ['bugs', 'environments', 'versions', 'relations', 'evidence', 'apis', 'common-steps']) {
    const route = asset.children.find((item) => item.path === path);
    assert.equal(route.meta.hideInMenu, true);
    assert.equal(route.redirect, undefined);
    assert.equal(typeof route.component, 'function');
  }
});
