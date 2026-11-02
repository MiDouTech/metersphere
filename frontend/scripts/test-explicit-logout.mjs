import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import test from 'node:test';
import ts from 'typescript';

const source = readFileSync(new URL('../src/hooks/useUser.ts', import.meta.url), 'utf8');
const body = source.slice(source.indexOf('export default function useUser()'));
const js = ts.transpileModule(body.replace('export default ', ''), {
  compilerOptions: { target: ts.ScriptTarget.ES2020, module: ts.ModuleKind.CommonJS },
}).outputText;

function harness(fail = false) {
  const events = [];
  const storage = new Map();
  const router = {
    currentRoute: { value: { name: 'featureCase', fullPath: '/case', query: {} } },
    replace: async (route) => events.push(['route', route]),
  };
  const create = new Function(
    'Message',
    'useI18n',
    'router',
    'useUserStore',
    'useAppStore',
    'endSessionExpiredHandling',
    'HTTP_MESSAGE_DURATION',
    'sessionStorage',
    `${js}; return useUser();`
  );
  const hook = create(
    { success: () => events.push('success'), warning: () => events.push('warning') },
    () => ({ t: (key) => key }),
    router,
    () => ({
      logout: async () => {
        events.push('server');
        if (fail) throw new Error('offline');
      },
    }),
    () => ({ setTopMenus: () => events.push('clearMenus') }),
    () => events.push('finished'),
    3000,
    { setItem: (key, value) => storage.set(key, value) }
  );
  return { hook, events, storage };
}

test('explicit logout suppresses automatic SSO and replaces the protected history entry', async () => {
  const { hook, events, storage } = harness();
  await hook.logout(undefined, true);
  assert.equal(storage.get('msp_explicit_logout'), '1');
  assert.deepEqual(events, ['server', 'clearMenus', 'success', ['route', { name: 'login', query: {} }], 'finished']);
});

test('server logout failure warns without claiming server success, and still leaves the protected page', async () => {
  const { hook, events, storage } = harness(true);
  await hook.logout(undefined, true);
  assert.equal(storage.get('msp_explicit_logout'), '1');
  assert.ok(events.includes('warning'));
  assert.ok(!events.includes('success'));
  assert.equal(events.find(Array.isArray)[1].name, 'login');
});

test('passive expiry keeps the existing reauthentication flow and stays silent', async () => {
  const { hook, events, storage } = harness();
  await hook.logout(undefined, false, true);
  assert.equal(storage.has('msp_explicit_logout'), false);
  assert.ok(!events.includes('success'));
  assert.equal(events.find(Array.isArray)[1].query.returnTo, '/case');
});
