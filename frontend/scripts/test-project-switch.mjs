import ts from 'typescript';

import { JSDOM } from 'jsdom';
/* global globalThis */
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import test from 'node:test';
import vm from 'node:vm';

// Import the Vue DOM renderer only after a real document exists.
const dom = new JSDOM('<!doctype html><html><body></body></html>', { url: 'http://localhost/' });
['window', 'document', 'Element', 'HTMLElement', 'SVGElement', 'history', 'location'].forEach((key) => {
  globalThis[key] = dom.window[key];
});
const { createApp, h, nextTick, ref } = await import('vue');
const { createRouter, createMemoryHistory, onBeforeRouteLeave, RouterView, isNavigationFailure } = await import(
  'vue-router'
);
const source = readFileSync(new URL('../src/views/setting/utils.ts', import.meta.url), 'utf8');
const compiled = ts.transpileModule(source, { compilerOptions: { module: ts.ModuleKind.CommonJS } }).outputText;
const planDetail = readFileSync(new URL('../src/views/test-plan/testPlan/detail/index.vue', import.meta.url), 'utf8');
const planAst = ts.createSourceFile(
  'plan-detail.ts',
  planDetail.match(/<script[^>]*>([\s\S]*?)<\/script>/)[1],
  ts.ScriptTarget.Latest,
  true
);
const leaveFunction = planAst.statements.find(
  (node) => ts.isFunctionDeclaration(node) && node.name?.text === 'confirmPlanLeave'
);
assert.ok(leaveFunction);
const leaveCode = ts.transpileModule(
  `${leaveFunction.getText(planAst)}\nexports.confirm = confirmPlanLeave;`,
  {}
).outputText;

async function fixture(options = {}) {
  const events = [];
  const errors = [];
  const redirects = [];
  const appStore = {
    currentOrgId: 'org-A',
    currentProjectId: 'project-A',
    currentMenuConfig: ['caseManagement'],
    showLoading() {
      this.loading = true;
    },
    hideLoading() {
      this.loading = false;
    },
    setCurrentProjectId(value) {
      this.currentProjectId = value;
    },
    setCurrentOrgId(value) {
      this.currentOrgId = value;
    },
    setCurrentMenuConfig(value) {
      this.currentMenuConfig = value;
    },
  };
  const server = { projectId: 'project-A', organizationId: 'org-A' };
  const session = () => ({
    id: 'user',
    lastProjectId: server.projectId,
    lastOrganizationId: server.organizationId,
    permissions: [`${server.projectId}:READ`],
  });
  const user = {
    id: 'user',
    ...session(),
    setInfo(value) {
      Object.assign(this, value);
    },
    logoutCallBack() {
      this.loggedOut = true;
      this.permissions = [];
      this.lastProjectId = '';
    },
  };
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      {
        path: '/editor',
        name: 'editor',
        component: {
          setup() {
            // Use the plan editor's actual dirty-state/modal leave function with the real router.
            const leaveContext = {
              exports: {},
              minderStore: { minderUnsaved: true },
              planTabRef: { value: { getAutoSaveStatus: () => 'dirty' } },
              t: (key) => key,
              openModal(modal) {
                events.push('leave-decision');
                if (options.leaveDecision === false) modal.onCancel();
                else modal.onBeforeOk();
              },
            };
            vm.runInNewContext(leaveCode, leaveContext);
            onBeforeRouteLeave(leaveContext.exports.confirm);
            return () => h('div', { 'data-editor': true }, 'unsaved editor');
          },
        },
      },
      { path: '/project-switch', name: 'projectSwitchPending', component: { render: () => h('div', 'pending') } },
      { path: '/cases', name: 'caseManagementCase', component: { render: () => h('div', 'cases') } },
      { path: '/workstation', name: 'workstation', redirect: '/home' },
      { path: '/home', name: 'workstationHome', component: { render: () => h('div', 'home') } },
    ],
  });
  router.onError(() => undefined);
  let initialized = false;
  router.beforeEach((to) => {
    if (!initialized) return true;
    if (to.name === 'projectSwitchPending' && options.throwBeforeLeaving) throw new Error('leave navigation failed');
    if (to.name === 'caseManagementCase' && options.abortTarget) return false;
    if (to.name === 'caseManagementCase' && options.throwTarget) throw new Error('target navigation failed');
    if (to.name === 'editor' && options.failRouteRestore) return false;
    return true;
  });
  await router.push('/editor?orgId=org-A&pId=project-A');
  const host = document.createElement('div');
  document.body.appendChild(host);
  const app = createApp({ render: () => h(RouterView) });
  app.use(router);
  app.mount(host);
  await router.isReady();
  await nextTick();
  initialized = true;
  const originalRoute = router.currentRoute.value.fullPath;
  let targetAttempts = 0;
  const api = {
    async switchProject({ projectId }) {
      events.push(`switch:${projectId}`);
      assert.equal(
        router.currentRoute.value.name,
        'projectSwitchPending',
        'no session writes while the old editor is active'
      );
      if (projectId === 'project-A' && options.failSessionRestore) throw new Error('restore unavailable');
      server.projectId = projectId;
      if (projectId !== 'project-A' && options.failSwitchResponse) throw new Error('response lost after server commit');
      return session();
    },
    async getProjectInfo() {
      events.push('configuration');
      targetAttempts += 1;
      if (options.failConfiguration || (options.failConfigurationOnce && targetAttempts === 1))
        throw new Error('config timeout');
      if (options.configurationBarrier) await options.configurationBarrier;
      return options.malformedConfiguration ? {} : { moduleIds: options.modules || ['caseManagement'] };
    },
  };
  const context = {
    exports: {},
    console: { log: () => undefined },
    window: { location: { origin: 'http://localhost', pathname: '/', replace: (url) => redirects.push(url) } },
    require(name) {
      if (name === 'vue') return { ref };
      if (name === 'vue-router') return { isNavigationFailure };
      if (name === '@arco-design/web-vue') return { Message: { error: (message) => errors.push(message) } };
      if (name.includes('ms-button')) return {};
      if (name === '@/api/modules/project-management/project') return api;
      if (name === '@/api/modules/system')
        return {
          async switchUserOrg(organizationId) {
            events.push(`org:${organizationId}`);
            server.organizationId = organizationId;
          },
        };
      if (name === '@/hooks/useI18n') return { useI18n: () => ({ t: (key) => key }) };
      if (name === '@/store') return { useUserStore: () => user };
      if (name === '@/store/modules/app') return { default: () => appStore };
      if (name === '@/utils/permission')
        return {
          getProjectEntryRouteName: () =>
            appStore.currentMenuConfig.includes('caseManagement') ? 'caseManagementCase' : 'workstation',
        };
      if (name === '@/router') return { default: router };
      throw new Error(name);
    },
  };
  vm.runInNewContext(compiled, context);
  const module = context.exports;
  return {
    module,
    router,
    server,
    user,
    appStore,
    events,
    errors,
    redirects,
    assertOriginal() {
      assert.equal(router.currentRoute.value.fullPath, originalRoute);
      assert.equal(appStore.currentProjectId, 'project-A');
      assert.equal(appStore.currentOrgId, 'org-A');
      assert.deepEqual(Array.from(appStore.currentMenuConfig), ['caseManagement']);
      assert.equal(server.projectId, 'project-A');
      assert.equal(server.organizationId, 'org-A');
      assert.equal(user.lastProjectId, 'project-A');
      assert.equal(user.lastOrganizationId, 'org-A');
      assert.deepEqual(Array.from(user.permissions), ['project-A:READ']);
      assert.equal(redirects.length, 0);
    },
    dispose() {
      app.unmount();
      host.remove();
      router.options.history.destroy();
    },
  };
}

test('real Vue leave guard cancellation preserves the editor, URL, server and all local context without a switch request', async () => {
  const f = await fixture({ leaveDecision: false });
  try {
    const editor = document.querySelector('[data-editor]');
    assert.equal(await f.module.enterProject('project-B', 'org-B'), false);
    f.assertOriginal();
    assert.equal(document.querySelector('[data-editor]'), editor);
    assert.deepEqual(f.events, ['leave-decision']);
  } finally {
    f.dispose();
  }
});

test('confirmed leave happens before server changes and target navigation uses the verified configuration', async () => {
  const f = await fixture();
  try {
    assert.equal(await f.module.enterProject('project-B', 'org-B'), true);
    assert.equal(f.events[0], 'leave-decision');
    assert.equal(f.router.currentRoute.value.name, 'caseManagementCase');
    assert.equal(f.router.currentRoute.value.query.pId, 'project-B');
    assert.equal(f.appStore.currentOrgId, 'org-B');
    assert.equal(f.appStore.currentProjectId, 'project-B');
    assert.equal(f.server.projectId, 'project-B');
    assert.equal(f.server.organizationId, 'org-B');
    assert.deepEqual(Array.from(f.user.permissions), ['project-B:READ']);
  } finally {
    f.dispose();
  }
});

Object.entries({
  'navigation exception before leaving': { throwBeforeLeaving: true },
  'target navigation cancellation': { abortTarget: true },
  'target navigation exception': { throwTarget: true },
  'configuration timeout': { failConfiguration: true },
  'missing configuration': { malformedConfiguration: true },
  'lost response after server change': { failSwitchResponse: true },
}).forEach(([name, options]) => {
  test(`${name} restores the server session, permissions, menu, organization, project and old URL`, async () => {
    const f = await fixture(options);
    try {
      assert.equal(await f.module.enterProject('project-B', 'org-B'), false);
      f.assertOriginal();
      assert.equal(f.errors.length, 1);
    } finally {
      f.dispose();
    }
  });
});

test('configuration failure can be retried; a disabled module uses the safe fallback after configuration succeeds', async () => {
  const f = await fixture({ failConfigurationOnce: true, modules: ['bugManagement'] });
  try {
    assert.equal(await f.module.enterProject('project-B', 'org-B'), false);
    f.assertOriginal();
    assert.equal(await f.module.enterProject('project-B', 'org-B'), true);
    assert.equal(f.router.currentRoute.value.name, 'workstationHome');
    assert.equal(f.router.currentRoute.value.query.pId, 'project-B');
    assert.deepEqual(Array.from(f.appStore.currentMenuConfig), ['bugManagement']);
  } finally {
    f.dispose();
  }
});

[{ failSessionRestore: true }, { failRouteRestore: true }].forEach((recovery) => {
  test(`failed recovery ${JSON.stringify(
    recovery
  )} clears local auth and blocks old business content behind reauthentication`, async () => {
    const f = await fixture({ failConfiguration: true, ...recovery });
    try {
      assert.equal(await f.module.enterProject('project-B', 'org-B'), false);
      assert.equal(f.user.loggedOut, true);
      assert.equal(f.appStore.currentProjectId, '');
      assert.equal(f.appStore.currentOrgId, '');
      assert.equal(f.appStore.currentMenuConfig.length, 0);
      assert.equal(f.router.currentRoute.value.name, 'projectSwitchPending');
      assert.equal(document.querySelector('[data-editor]'), null);
      assert.deepEqual(f.redirects, ['http://localhost/#/login']);
    } finally {
      f.dispose();
    }
  });
});

test('a second project switch cannot overlap the in-flight server transaction', async () => {
  let release;
  const barrier = new Promise((resolve) => {
    release = resolve;
  });
  const f = await fixture({ configurationBarrier: barrier });
  try {
    const first = f.module.enterProject('project-B', 'org-B');
    // Wait for the real route transition, then stop at the controlled configuration response.
    await new Promise((resolve) => {
      const remove = f.router.afterEach((to) => {
        if (to.name === 'projectSwitchPending') {
          remove();
          resolve();
        }
      });
    });
    assert.equal(await f.module.enterProject('project-C'), false);
    f.appStore.hideLoading(); // Unrelated requests may finish while the switch is still pending.
    assert.equal(f.module.projectSwitchPending.value, true);
    release();
    assert.equal(await first, true);
    assert.equal(f.module.projectSwitchPending.value, false);
    assert.equal(f.server.projectId, 'project-B');
    assert.equal(f.events.includes('switch:project-C'), false);
  } finally {
    release();
    f.dispose();
  }
});
