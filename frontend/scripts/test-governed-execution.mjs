import ts from 'typescript';

import assert from 'node:assert/strict';
import { webcrypto } from 'node:crypto';
import { readFileSync } from 'node:fs';
import test from 'node:test';
import vm from 'node:vm';

const compile = (path) =>
  ts.transpileModule(readFileSync(new URL(`../src/${path}`, import.meta.url), 'utf8'), {
    compilerOptions: { target: ts.ScriptTarget.ES2022, module: ts.ModuleKind.CommonJS },
  }).outputText;
function fixture() {
  const target = new EventTarget();
  const memory = { exports: {} };
  const hooks = [];
  const context = {
    module: memory,
    exports: memory.exports,
    require: (name) => {
      assert.equal(name, 'vue');
      return { onMounted: (fn) => hooks.push(fn), onBeforeUnmount: () => {} };
    },
    window: target,
    Event,
    Map,
  };
  vm.runInNewContext(compile('utils/protocol-memory.ts'), context);
  let resolve;
  let reject;
  const request = () =>
    new Promise((done, fail) => {
      resolve = done;
      reject = fail;
    });
  const http = { get: request, post: request };
  const api = { exports: {} };
  vm.runInNewContext(compile('api/modules/governed-execution.ts'), {
    module: api,
    exports: api.exports,
    require: (name) => (name.includes('protocol-memory') ? memory.exports : { default: http }),
    crypto: webcrypto,
    Proxy,
    Reflect,
    Error,
  });
  return {
    memory: memory.exports,
    api: api.exports,
    target,
    hooks,
    finish: (value) => resolve(value),
    reject: (value) => reject(value),
  };
}
test('retry retains an independent original payload and key until explicit memory clearing', () => {
  const { api, memory } = fixture();
  const value = { sessionId: 'session', conditions: { count: 2 } };
  const request = api.stableRequest('scope', value);
  value.conditions.count = 7;
  assert.equal(request.conditions.count, 2);
  assert.equal(
    api.stableRequest('scope', { sessionId: 'session', conditions: { count: 2 } }).idempotencyKey,
    request.idempotencyKey
  );
  memory.clearProtocolMemory();
  assert.equal(memory.protocolRequests.size, 0);
  assert.notEqual(
    api.stableRequest('scope', { sessionId: 'session', conditions: { count: 2 } }).idempotencyKey,
    request.idempotencyKey
  );
});
test('identity clearing notifies mounted views and fences late requests from the previous identity', async () => {
  const value = fixture();
  let cleared = 0;
  value.memory.useProtocolMemoryClear(() => {
    cleared++;
  });
  value.hooks.forEach((fn) => fn());
  const pending = value.api.readSession('session');
  value.memory.clearProtocolMemory();
  value.finish({ oldPrivateContext: true });
  await assert.rejects(pending, /PROTOCOL_CONTEXT_CHANGED/);
  assert.equal(cleared, 1);
});

const { parse, compileScript } = await import('vue/compiler-sfc');
const vue = await import('vue');
function workForm(options = {}) {
  const { descriptor } = parse(
    readFileSync(new URL('../src/views/execution/components/GovernedWorkForm.vue', import.meta.url), 'utf8')
  );
  const source = compileScript(descriptor, { id: 'governed-work-fixture' }).content;
  const compiled = ts.transpileModule(source, {
    compilerOptions: { target: ts.ScriptTarget.ES2022, module: ts.ModuleKind.CommonJS },
  }).outputText;
  const module = { exports: {} };
  vm.runInNewContext(compiled, {
    module,
    exports: module.exports,
    require: (name) => (name === 'vue' ? vue : { default: {} }),
  });
  const instance = {
    caseExecutionId: 'instance',
    phase: 'EXECUTING',
    channel: 'OPEN',
    verification: 'NONE',
    submissionRevision: 1,
    steps: [],
    actions: [],
    contract: {
      allowedActions: ['CREATE', 'DELETE'],
      steps: [{ id: 'S1', sourcePosition: 1, action: 'inspect' }],
      assertions: [{ id: 'A1', expected: 'visible' }],
      outputs: {},
      variables: {},
      dataRequirements: [],
    },
    ...options.instance,
  };
  const props = vue.reactive({
    instance,
    loading: false,
    canExecute: options.canExecute !== false,
    canSupplement: !!options.canSupplement,
  });
  const events = [];
  const scope = vue.effectScope();
  const model = scope.run(() =>
    module.exports.default.setup(props, { expose: () => {}, emit: (...args) => events.push(args) })
  );
  return { model, events, stop: () => scope.stop() };
}
test('production work form emits explicit target and separates cleanup lease requests', () => {
  const value = workForm();
  try {
    Object.assign(value.model.form, {
      actionType: 'CREATE',
      targetEntity: 'order',
      target: 'owned order',
      expectedEffect: 'one',
      businessKey: 'unique',
      queryMethod: 'authority lookup',
    });
    value.model.submit();
    assert.equal(value.events[0][1], 'action.prepare');
    assert.equal(value.events[0][2].targetEntity, 'order');
    assert.ok(!('objectId' in value.events[0][2]));
  } finally {
    value.stop();
  }
  const cleanup = workForm({
    instance: { phase: 'ENDED', channel: 'CLOSED', verification: 'ACCEPTED', result: 'PASS' },
  });
  try {
    assert.ok(cleanup.model.visibleOperations.value.every((item) => item.value.startsWith('cleanup.')));
    cleanup.model.operation.value = 'cleanup.lease.takeover';
    cleanup.model.submit();
    assert.equal(cleanup.events[0][1], 'cleanup.lease.takeover');
    assert.equal(Object.keys(cleanup.events[0][2]).length, 0);
  } finally {
    cleanup.stop();
  }
});
test('a supplement-only user can correct existing reported facts without an execution operation', () => {
  const value = workForm({
    canExecute: false,
    canSupplement: true,
    instance: { phase: 'VERIFYING', channel: 'CLOSED', verification: 'PENDING', result: 'PASS' },
  });
  try {
    assert.deepEqual(
      Array.from(value.model.visibleOperations.value, (item) => item.value),
      ['result.evidence.append']
    );
    Object.assign(value.model.form, { stepId: 'S1', observation: 'new supported evidence', outcome: 'SATISFIED' });
    value.model.evidenceIds.value = 'TASK:artifact';
    value.model.submit();
    const values = value.events[0][2];
    assert.equal(values.submissionRevision, 1);
    assert.equal(values.correctedSteps[0].stepId, 'S1');
    assert.equal(values.correctedSteps[0].evidence[0].kind, 'TASK');
    assert.equal(values.correctedSteps[0].evidence[0].id, 'artifact');
    assert.ok(!('result' in values));
  } finally {
    value.stop();
  }
});

test('permission rejection clears mounted private views and invalidates in-memory request bodies', async () => {
  const value = fixture();
  let cleared = 0;
  value.memory.useProtocolMemoryClear(() => {
    cleared++;
  });
  value.hooks.forEach((fn) => fn());
  value.api.stableRequest('private', { privateBody: 'synthetic-private-body' });
  const pending = value.api.readSession('session');
  value.reject({ status: 403, code: 'AGENT_RESOURCE_NOT_ACCESSIBLE' });
  await assert.rejects(pending, (error) => error.status === 403);
  assert.equal(value.memory.protocolRequests.size, 0);
  assert.equal(cleared, 1);
});

test('data observations begin empty and changing the requirement never copies expected values', async () => {
  const value = workForm({
    instance: {
      contract: {
        allowedActions: [],
        steps: [],
        assertions: [],
        outputs: {},
        variables: {},
        dataRequirements: [{ key: 'customer', conditions: { active: true, balance: 42 } }],
      },
    },
  });
  try {
    value.model.form.key = 'customer';
    await vue.nextTick();
    assert.equal(Object.keys(value.model.actualConditions.value).length, 0);
    value.model.actualConditions.value = { active: false, balance: 10 };
    value.model.operation.value = 'data.record';
    value.model.form.objectId = 'observed-object';
    value.model.submit();
    assert.equal(value.events[0][2].conditions.active, false);
    assert.equal(value.events[0][2].conditions.balance, 10);
  } finally {
    value.stop();
  }
});
test('text counters use NFC Unicode scalars and UTF-8 bytes and identify unpaired surrogates', () => {
  const module = { exports: {} };
  vm.runInNewContext(compile('utils/protocol-text.ts'), { module, exports: module.exports, TextEncoder });
  const size = module.exports.default('e\u0301😀');
  assert.equal(size.scalars, 2);
  assert.equal(size.bytes, 6);
  assert.equal(size.valid, true);
  assert.equal(module.exports.default('\ud800').valid, false);
});
