import axios from 'axios';

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
const { createApp, h, nextTick, ref, readonly } = await import('vue');
const { createRouter, createMemoryHistory, onBeforeRouteLeave, RouterView, isNavigationFailure } = await import(
  'vue-router'
);
const source = readFileSync(new URL('../src/views/setting/utils.ts', import.meta.url), 'utf8');
const compiled = ts.transpileModule(source, { compilerOptions: { module: ts.ModuleKind.CommonJS } }).outputText;
const compileSource = (path) =>
  ts.transpileModule(readFileSync(new URL(`../src/${path}`, import.meta.url), 'utf8'), {
    compilerOptions: { module: ts.ModuleKind.CommonJS },
  }).outputText;
const navigationCode = compileSource('utils/projectSwitchNavigation.ts');
const pageGuardCode = compileSource('router/guard/index.ts');
const cancelerCode = compileSource('api/http/axiosCancel.ts');
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
  const pageEffects = [];
  const cancelledRequests = [];
  const httpStarted = [];
  const navigationContext = { exports: {}, require: () => ({ ref, readonly }) };
  vm.runInNewContext(navigationCode, navigationContext);
  const navigation = navigationContext.exports;
  const cancelerContext = {
    exports: {},
    require: (name) => {
      if (name === 'axios') return { default: axios };
      if (name === '@/utils/is') return { isFunction: (value) => typeof value === 'function' };
      throw new Error(name);
    },
  };
  vm.runInNewContext(cancelerCode, cancelerContext);
  const canceler = new cancelerContext.exports.AxiosCanceler();
  const client = axios.create();
  client.interceptors.request.use((config) => {
    canceler.addPending(config);
    return config;
  });
  client.interceptors.response.use((response) => {
    canceler.removePending(response.config);
    return response;
  });
  async function controlledRequest(url, barrier, data) {
    if (!options.realAxios) {
      if (barrier) await barrier;
      return data;
    }
    const response = await client.request({
      url,
      method: 'get',
      adapter: (config) =>
        new Promise((resolve, reject) => {
          httpStarted.push(url);
          let settled = false;
          config.cancelToken.promise.then((reason) => {
            if (!settled) {
              settled = true;
              cancelledRequests.push(url);
              reject(reason);
            }
          });
          Promise.resolve(barrier).then(() => {
            if (!settled) {
              settled = true;
              resolve({ data, status: 200, statusText: 'OK', config, headers: {} });
            }
          });
        }),
    });
    return response.data;
  }
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
  const guardContext = {
    exports: {},
    require(name) {
      if (name === '@/api/http/axiosCancel') return cancelerContext.exports;
      if (name === '@/utils/projectSwitchNavigation') return navigation;
      if (name === '@/store/modules/app') return { default: () => appStore };
      if (name === '@/utils/route-listener') return { setRouteEmitter: (to) => pageEffects.push(to.fullPath) };
      if (name === '@/config/pathMap') return { MENU_LEVEL: ['SYSTEM', 'ORGANIZATION', 'PROJECT'] };
      if (name === '@/hooks/usePathMap')
        return {
          default: () => ({
            getRouteLevelByKey: (routeName) => (routeName === 'projectSwitchPending' ? null : 'PROJECT'),
          }),
        };
      if (name === './permission' || name === './userLoginInfo') return { default: () => undefined };
      throw new Error(name);
    },
  };
  vm.runInNewContext(pageGuardCode, guardContext);
  guardContext.exports.default(router); // Actual setupPageGuard, including actual shared AxiosCanceler.
  let initialized = false;
  router.beforeEach((to) => {
    if (!initialized) return true;
    if (to.name === 'projectSwitchPending' && options.throwBeforeLeaving) throw new Error('leave navigation failed');
    if (to.name === 'caseManagementCase' && options.abortTarget) return false;
    if (to.name === 'caseManagementCase' && options.throwTarget) throw new Error('target navigation failed');
    if (to.name === 'editor' && options.failRouteRestore) return false;
    return true;
  });
  router.beforeResolve(async (to) => {
    if (initialized && to.name === 'caseManagementCase' && options.targetNavigationBarrier) {
      events.push('target-navigation-wait');
      await options.targetNavigationBarrier;
    }
  });
  if (options.seedHistory) {
    await router.push('/home?orgId=org-A&pId=project-A');
    await router.push('/editor?orgId=org-A&pId=project-A');
    await router.push('/cases?orgId=org-A&pId=project-A');
    const returned = new Promise((resolve) => {
      const remove = router.afterEach(() => {
        remove();
        resolve();
      });
    });
    router.back();
    await returned;
  }
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
      return controlledRequest(
        `/project/switch/${projectId}`,
        projectId === 'project-A' ? options.restoreBarrier : options.switchBarrier,
        session()
      );
    },
    async getProjectInfo() {
      events.push('configuration');
      targetAttempts += 1;
      if (options.failConfiguration || (options.failConfigurationOnce && targetAttempts === 1))
        throw new Error('config timeout');
      return controlledRequest(
        '/project/get/project-B',
        options.configurationBarrier,
        options.malformedConfiguration ? {} : { moduleIds: options.modules || ['caseManagement'] }
      );
    },
  };
  const context = {
    exports: {},
    console: { log: () => undefined },
    window: { location: { origin: 'http://localhost', pathname: '/', replace: (url) => redirects.push(url) } },
    require(name) {
      if (name === '@/utils/projectSwitchNavigation') return navigation;
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
    navigation,
    pageEffects,
    cancelledRequests,
    httpStarted,
    cancelRequests: () => canceler.removeAllPending(),
    controlledRequest,
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

function deferred() {
  let resolve;
  const promise = new Promise((done) => {
    resolve = done;
  });
  return { promise, resolve };
}

function until(condition, deadline = Date.now() + 3000) {
  if (condition()) return Promise.resolve();
  assert.ok(Date.now() < deadline, 'controlled transaction did not reach its expected phase');
  return new Promise((resolve) => {
    setTimeout(resolve, 1);
  }).then(() => until(condition, deadline));
}

async function assertBlockedNavigation(f, action) {
  const contextBefore = JSON.stringify({
    project: f.appStore.currentProjectId,
    org: f.appStore.currentOrgId,
    modules: f.appStore.currentMenuConfig,
    permissions: f.user.permissions,
    server: f.server,
  });
  const effectsBefore = f.pageEffects.length;
  const cancellationsBefore = f.cancelledRequests.length;
  const finished = new Promise((resolve) => {
    const remove = f.router.afterEach((to, from, failure) => {
      remove();
      resolve(failure);
    });
  });
  action();
  assert.equal(isNavigationFailure(await finished), true);
  await nextTick();
  assert.equal(f.router.currentRoute.value.name, 'projectSwitchPending');
  assert.equal(f.router.options.history.location, '/project-switch');
  assert.equal(document.body.textContent, 'pending', 'no business DOM may remount during isolation');
  assert.equal(f.pageEffects.length, effectsBefore, 'blocked navigation must not emit route effects');
  assert.equal(f.cancelledRequests.length, cancellationsBefore, 'blocked navigation must not cancel pending HTTP');
  assert.equal(
    JSON.stringify({
      project: f.appStore.currentProjectId,
      org: f.appStore.currentOrgId,
      modules: f.appStore.currentMenuConfig,
      permissions: f.user.permissions,
      server: f.server,
    }),
    contextBefore,
    'setupPageGuard must not apply the attempted URL context'
  );
}

async function attackNavigation(f) {
  await assertBlockedNavigation(f, () => f.router.back());
  // Unlike browser replaceState, Vue Router's memory-history replace truncates forward
  // entries. Seed a real forward entry via its public history API before router.forward().
  f.router.options.history.push('/cases?orgId=org-A&pId=project-A');
  f.router.options.history.go(-1, false);
  await assertBlockedNavigation(f, () => f.router.forward());
  await assertBlockedNavigation(f, () => f.router.push('/editor?orgId=org-C&pId=project-C'));
  await assertBlockedNavigation(f, () => f.router.replace('/cases?orgId=org-A&pId=project-A'));
}

['switch', 'configuration', 'recovery', 'target-navigation'].forEach((phase) => {
  test(`real setupPageGuard and Axios isolate back/forward/push/replace during ${phase} waiting`, async () => {
    const barrier = deferred();
    const options = { seedHistory: true, realAxios: true };
    if (phase === 'switch') options.switchBarrier = barrier.promise;
    if (phase === 'configuration') options.configurationBarrier = barrier.promise;
    if (phase === 'recovery') {
      options.failConfiguration = true;
      options.restoreBarrier = barrier.promise;
    }
    if (phase === 'target-navigation') options.targetNavigationBarrier = barrier.promise;
    const f = await fixture(options);
    const background = deferred();
    let unrelated;
    try {
      const switching = f.module.enterProject('project-B', 'org-B');
      await until(() => {
        if (phase === 'switch') return f.httpStarted.includes('/project/switch/project-B');
        if (phase === 'configuration') return f.httpStarted.includes('/project/get/project-B');
        if (phase === 'recovery') return f.httpStarted.includes('/project/switch/project-A');
        return f.events.includes('target-navigation-wait');
      });
      unrelated = f.controlledRequest('/background', background.promise, {}).catch((error) => {
        assert.equal(axios.isCancel(error), true);
        return 'cancelled';
      });
      await until(() => f.httpStarted.includes('/background'));
      await attackNavigation(f);
      if (phase === 'target-navigation') {
        // Even a second navigation to the exact authorized target cannot reuse its consumed permit.
        await assertBlockedNavigation(f, () => f.router.push('/cases?orgId=org-B&pId=project-B'));
      }
      barrier.resolve();
      const result = await switching;
      if (phase === 'recovery' || phase === 'target-navigation') {
        assert.equal(result, false);
        f.assertOriginal();
      } else {
        assert.equal(result, true);
        assert.equal(f.router.currentRoute.value.name, 'caseManagementCase');
        assert.equal(f.router.currentRoute.value.query.pId, 'project-B');
        assert.equal(f.appStore.currentProjectId, f.server.projectId);
        assert.deepEqual(Array.from(f.user.permissions), ['project-B:READ']);
      }
      assert.equal(
        await unrelated,
        'cancelled',
        'authorized final/restore navigation still performs normal Axios cancellation'
      );
      assert.equal(f.module.projectSwitchPending.value, false);
    } finally {
      barrier.resolve();
      background.resolve();
      if (unrelated) await unrelated;
      f.dispose();
    }
  });
});

test('real Axios cancellation enters recovery; history navigation cannot cancel or outrun the restoring session request', async () => {
  const configuration = deferred();
  const recovery = deferred();
  const f = await fixture({
    seedHistory: true,
    realAxios: true,
    configurationBarrier: configuration.promise,
    restoreBarrier: recovery.promise,
  });
  try {
    const switching = f.module.enterProject('project-B', 'org-B');
    await until(() => f.httpStarted.includes('/project/get/project-B'));
    f.cancelRequests();
    await until(() => f.httpStarted.includes('/project/switch/project-A'));
    assert.equal(f.cancelledRequests.includes('/project/get/project-B'), true);
    await attackNavigation(f);
    recovery.resolve();
    assert.equal(await switching, false);
    f.assertOriginal();
    assert.equal(f.module.projectSwitchPending.value, false);
  } finally {
    configuration.resolve();
    recovery.resolve();
    f.dispose();
  }
});

test('unverified recovery keeps navigation isolated after clearing local authentication and authorizes a full login reload', async () => {
  const f = await fixture({ seedHistory: true, realAxios: true, failConfiguration: true, failSessionRestore: true });
  try {
    assert.equal(await f.module.enterProject('project-B', 'org-B'), false);
    assert.equal(f.module.projectSwitchPending.value, true);
    assert.equal(f.user.loggedOut, true);
    assert.deepEqual(f.redirects, ['http://localhost/#/login']);
    await attackNavigation(f);
  } finally {
    f.dispose();
  }
});
