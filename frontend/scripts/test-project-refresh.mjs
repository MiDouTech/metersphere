import ts from 'typescript';

import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import test from 'node:test';
import vm from 'node:vm';
import { parse } from 'vue/compiler-sfc';

const read = (path) => readFileSync(new URL(`../src/${path}`, import.meta.url), 'utf8');
function execute(source, globals) {
  const context = { exports: {}, ...globals };
  vm.runInNewContext(
    ts.transpileModule(source, { compilerOptions: { module: ts.ModuleKind.CommonJS } }).outputText,
    context
  );
  return context.exports;
}

const routeNames = { CASE_MANAGEMENT: 'caseManagement', CASE_MANAGEMENT_CASE: 'caseManagementCase' };
const routes = [
  { name: 'workstation', meta: { order: 0 }, children: [] },
  { name: routeNames.CASE_MANAGEMENT, meta: { order: 3 }, children: [] },
  {
    name: routeNames.CASE_MANAGEMENT_CASE,
    meta: { roles: ['FUNCTIONAL_CASE:READ'], resourceCode: 'FUNCTIONAL_CASE_CASE_TAB' },
    children: [],
  },
];
function permissions({
  enabled = true,
  readPermission = true,
  visible = true,
  parentVisible = true,
  admin = false,
  emptyModules = false,
} = {}) {
  const modules = enabled ? ['caseManagement'] : ['bugManagement'];
  const app = {
    currentMenuConfig: emptyModules ? [] : modules,
    currentProjectId: 'B',
  };
  const user = {
    isAdmin: admin,
    currentRole: {
      projectPermissions: readPermission ? ['FUNCTIONAL_CASE:READ'] : [],
      orgPermissions: [],
      systemPermissions: [],
    },
    uiPermissions: {
      project: {
        visible: visible ? ['FUNCTIONAL_CASE_CASE_TAB'] : [],
        managedRoutes: ['caseManagement'],
        visibleRoutes: parentVisible ? ['caseManagement'] : [],
      },
    },
  };
  return execute(read('utils/permission.ts'), {
    require: (name) => {
      if (name === '@/store') return { useAppStore: () => app, useUserStore: () => user };
      if (name === '@/router/routes/base') return { INDEX_ROUTE: { name: 'index' } };
      if (name === '@/router/routes/index') return { default: [] };
      if (name === '@/enums/routeEnum') return { CaseManagementRouteEnum: routeNames };
      throw new Error(name);
    },
  });
}

test('project entry prefers cases over the first menu when readable and enabled', () => {
  assert.equal(permissions().getProjectEntryRouteName(routes), routeNames.CASE_MANAGEMENT_CASE);
});

test('navbar keeps the previous project until the shared switch handler succeeds', () => {
  const { descriptor } = parse(read('components/pure/navbar/index.vue'));
  const template = descriptor.template.ast;
  const selects = [];
  function collect(node) {
    if (node.tag === 'a-select') selects.push(node);
    (node.children || []).forEach(collect);
  }
  collect(template);
  const projectSelect = selects.find((node) => node.props.some((prop) => prop.exp?.content === 'selectProject'));
  assert.ok(projectSelect);
  const value = projectSelect.props.find((prop) => prop.exp?.content === 'appStore.currentProjectId');
  assert.equal(value?.name, 'bind', 'two-way binding would change the global project before authorization succeeds');
});
Object.entries({
  'missing case read permission': { readPermission: false },
  'disabled case module': { enabled: false },
  'hidden case page': { visible: false },
  'hidden parent route': { parentVisible: false },
  'disabled module for administrator': { enabled: false, admin: true },
  'empty or unknown module configuration': { emptyModules: true },
}).forEach(([name, settings]) => {
  test(`project entry preserves the existing safe fallback for ${name}`, () => {
    const permission = permissions(settings);
    assert.equal(permission.getProjectEntryRouteName(routes), permission.getFirstRouteNameByPermission(routes));
  });
});

// Execute the actual lifecycle callback, not a copied implementation. The layout registers
// cacheViews before this async child mounts; the old callback then made zero list requests.
const planSource =
  read('views/test-plan/testPlan/components/planTable.vue')
    .split('<script setup lang="ts">')[1]
    ?.split('</script>')[0] ||
  read('views/test-plan/testPlan/components/planTable.vue').match(/<script[^>]*>([\s\S]*?)<\/script>/)[1];
const planAst = ts.createSourceFile('plan.ts', planSource, ts.ScriptTarget.Latest, true, ts.ScriptKind.TS);
const mount = planAst.statements.find(
  (node) =>
    ts.isExpressionStatement(node) &&
    ts.isCallExpression(node.expression) &&
    node.expression.expression.getText(planAst) === 'onBeforeMount'
);
assert.ok(mount, 'plan component must register its initial load');

[false, true].forEach((cached) => {
  test(`plan cold instance loads even when route cache registration is ${cached}`, () => {
    let beforeMount;
    let requests = 0;
    const globals = {
      onBeforeMount: (callback) => {
        beforeMount = callback;
      },
      isActivated: { value: cached },
      route: { query: {} },
      appStore: { currentProjectId: 'project-A' },
      fetchData: () => {
        requests += 1;
      },
    };
    execute(mount.getText(planAst), globals);
    beforeMount();
    assert.equal(requests, 1);
  });
});

test('plan initial load preserves a direct-link group filter', () => {
  let beforeMount;
  let requested;
  const showType = { value: 'ALL' };
  const keyword = { value: '' };
  execute(mount.getText(planAst), {
    onBeforeMount: (callback) => {
      beforeMount = callback;
    },
    isActivated: { value: true },
    route: { query: { groupId: 'group-2' } },
    appStore: { currentProjectId: 'project-A' },
    testPlanTypeEnum: { GROUP: 'GROUP' },
    showType,
    keyword,
    fetchData: () => {
      requested = { type: showType.value, keyword: keyword.value };
    },
  });
  beforeMount();
  assert.deepEqual(requested, { type: 'GROUP', keyword: 'group-2' });
});
