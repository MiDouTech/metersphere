import ts from 'typescript';

import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { test } from 'node:test';
import vm from 'node:vm';

const code = ts.transpileModule(readFileSync(new URL('../src/store/modules/user/index.ts', import.meta.url), 'utf8'), {
  compilerOptions: { module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2020 },
}).outputText;

function harness(response, rejected = false) {
  let options;
  let redirected = false;
  let cleared = 0;
  let calls = 0;
  const stored = new Map([['miduo_reauth_redirecting', '1']]);
  const credentials = [];
  const app = {
    setCurrentOrgId(value) {
      this.org = value;
    },
    setCurrentProjectId(value) {
      this.project = value;
    },
  };
  const exported = {};
  vm.runInNewContext(code, {
    exports: exported,
    console: { log: () => undefined },
    window: {
      location: {
        set href(value) {
          redirected = true;
        },
      },
    },
    sessionStorage: { removeItem: (key) => stored.delete(key), getItem: (key) => stored.get(key) },
    require(name) {
      if (name === 'pinia')
        return {
          defineStore: (id, value) => {
            options = value;
            return () => value;
          },
        };
      if (name === '@/api/modules/user')
        return {
          isLogin: async () => {
            calls++;
            if (rejected) throw new Error('expired');
            return response;
          },
        };
      if (name === '@/utils/auth')
        return {
          clearToken: () => {
            cleared++;
          },
          setToken: (...values) => credentials.push(values),
        };
      if (name === '@/utils') return { getHashParameters: () => ({}), getQueryVariable: () => '' };
      if (name === '../app') return { default: () => app };
      if (name === '@/api/modules/sso/miduo') throw new Error('Established MSP session must not call the SSO bridge');
      return { default: () => ({}) };
    },
  });
  const context = {
    setInfo(value) {
      this.user = value;
    },
  };
  return {
    run: () => options.actions.isLogin.call(context),
    inspect: () => ({ redirected, cleared, calls, credentials, stored, context }),
  };
}

test('legacy upstream reauthentication marker cannot redirect or clear an authenticated MSP session', async () => {
  const h = harness({ sessionId: 'browser-a', csrfToken: 'synthetic-csrf', needMiduoReauth: true });
  assert.equal(await h.run(), true);
  const actual = h.inspect();
  assert.equal(actual.redirected, false);
  assert.equal(actual.cleared, 0);
  assert.equal(actual.calls, 1);
  assert.equal(actual.credentials[0][0], 'browser-a');
  assert.equal(actual.stored.has('miduo_reauth_redirecting'), false);
});

test('normal independent session remains logged in', async () => {
  const h = harness({ sessionId: 'browser-b', csrfToken: 'synthetic-csrf', needMiduoReauth: false });
  assert.equal(await h.run(), true);
  assert.equal(h.inspect().redirected, false);
});

test('server rejection is not silently recovered through SSO or treated as successful login', async () => {
  const h = harness(null, true);
  assert.equal(await h.run(), false);
  assert.equal(h.inspect().redirected, false);
  assert.equal(h.inspect().credentials.length, 0);
});
