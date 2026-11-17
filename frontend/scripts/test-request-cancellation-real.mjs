import { cloneDeep } from 'lodash-es';
import axios from 'axios';

import ts from 'typescript';

import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import test from 'node:test';
import vm from 'node:vm';

// Only TypeScript/alias loading is adapted. Axios dispatch, config merging,
// CancelToken, cancellation checks and every interceptor execute for real.
const modules = new Map();
let boundary;
function load(path) {
  if (modules.has(path)) return modules.get(path);
  const exports = {};
  modules.set(path, exports);
  vm.runInNewContext(
    ts.transpileModule(readFileSync(new URL(`../src/${path}.ts`, import.meta.url), 'utf8'), {
      compilerOptions: { module: ts.ModuleKind.CommonJS },
    }).outputText,
    {
      exports,
      // Avoid printing request config from the existing wrapper's error logger.
      console: {
        log() {
          return undefined;
        },
      },
      require(name) {
        if (name === 'axios')
          return {
            default: new Proxy(axios, {
              get(target, key) {
                if (key !== 'create') return Reflect.get(target, key);
                return (options) => {
                  const client = axios.create(options);
                  // A documented interceptor places a controllable barrier AFTER Axios
                  // dispatch cancellation checks and BEFORE application cleanup.
                  client.interceptors.response.use(
                    (response) => (boundary ? boundary(response, false) : response),
                    (error) => (boundary ? boundary(error, true) : Promise.reject(error))
                  );
                  return client;
                };
              },
            }),
          };
        if (name === 'lodash-es') return { cloneDeep };
        if (name === '@/utils/is') return load('utils/is');
        if (name === '@/enums/httpEnum') return load('enums/httpEnum');
        if (name === './axiosCancel') return load('api/http/axiosCancel');
        if (name === './axiosTransform') return load('api/http/axiosTransform');
        throw new Error(`Unexpected dependency: ${name}`);
      },
    }
  );
  return exports;
}
const { MSAxios } = load('api/http/Axios');
const { AxiosCanceler } = load('api/http/axiosCancel');
const deferred = () => {
  let resolve;
  let reject;
  const promise = new Promise((ok, fail) => {
    resolve = ok;
    reject = fail;
  });
  return { promise, resolve, reject };
};
const outcome = (promise) =>
  promise.then(
    (value) => ({ value }),
    (error) => ({ error })
  );
const tick = () =>
  new Promise((resolve) => {
    setImmediate(resolve);
  });

function harness(downstreamErrorHook) {
  AxiosCanceler.reset();
  boundary = undefined;
  const requests = [];
  const errors = [];
  const adapter = (config) => {
    const wait = deferred();
    const onCancel = (error) => wait.reject(error);
    config.cancelToken?.subscribe(onCancel);
    requests.push({
      config,
      wait,
      success: () => wait.resolve({ config, data: { ok: true }, status: 200, statusText: 'OK', headers: {} }),
    });
    return wait.promise.finally(() => config.cancelToken?.unsubscribe(onCancel));
  };
  const client = (requestOptions = {}) =>
    new MSAxios({
      adapter,
      requestOptions,
      transform: {
        requestInterceptors: (config) => config,
        responseInterceptors: (response) => response,
        responseInterceptorsCatch: (error) => {
          errors.push(error);
          return downstreamErrorHook ? downstreamErrorHook(error) : Promise.reject(error);
        },
        transformRequestHook: (response) => response.data,
      },
    });
  const send = async (api, config, options) => {
    const result = outcome(api.post({ url: '/test-plan/page', ...config }, options));
    await tick();
    return { result, request: requests.at(-1) };
  };
  return { requests, errors, client, send, cancel: () => new AxiosCanceler().removeAllPending() };
}

process.stdout.write(
  `Real Axios ${axios.VERSION}; TypeScript ${ts.version}; controlled adapter, no business network\n`
);

[false, true].forEach((failed) => {
  test(`real Axios: late ${
    failed ? 'failure' : 'success'
  } cannot cancel/unregister newer cross-client request`, async () => {
    const h = harness();
    const entered = deferred();
    const release = deferred();
    let oldConfig;
    boundary = async (value, rejected) => {
      if (value.config === oldConfig) {
        entered.resolve();
        await release.promise;
      }
      if (rejected) throw value;
      return value;
    };
    const old = await h.send(h.client());
    oldConfig = old.request.config;
    const original = new axios.AxiosError('controlled network failure', 'ERR_NETWORK', oldConfig);
    if (failed) old.request.wait.reject(original);
    else old.request.success();
    await entered.promise;
    const current = await h.send(h.client());
    assert.ok(oldConfig.cancelToken.reason, 'new same-URL request retains existing cancellation policy');
    release.resolve();
    const settled = await old.result;
    if (failed) {
      assert.equal(settled.error, original);
      assert.equal(h.errors.at(-1), original);
    } else assert.equal(settled.value.ok, true);
    assert.equal(current.request.config.cancelToken.reason, undefined);
    h.cancel();
    assert.ok(current.request.config.cancelToken.reason, 'new request is still registered for route cancellation');
    current.request.success();
    assert.equal((await current.result).error.code, 'ERR_CANCELED');
  });
});

test('real Axios: successful completion is unregistered and subsequent request completes', async () => {
  const h = harness();
  const api = h.client();
  const first = await h.send(api);
  first.request.success();
  assert.equal((await first.result).value.ok, true);
  h.cancel();
  assert.equal(first.request.config.cancelToken.reason, undefined);
  const next = await h.send(api);
  next.request.success();
  assert.equal((await next.result).value.ok, true);
});

test('real Axios: same URL cancels old request; different URLs and two clients remain independent', async () => {
  const h = harness();
  const a = h.client();
  const b = h.client();
  const old = await h.send(a);
  const other = await h.send(b, { url: '/test-plan/module/count' });
  const current = await h.send(b);
  assert.ok(old.request.config.cancelToken.reason);
  assert.equal(other.request.config.cancelToken.reason, undefined);
  old.request.success();
  assert.equal((await old.result).error.code, 'ERR_CANCELED');
  current.request.success();
  other.request.success();
  assert.equal((await current.result).value.ok, true);
  assert.equal((await other.result).value.ok, true);
});

test('real Axios: external token cancels old registration but is not owned by route cancellation', async () => {
  const h = harness();
  const api = h.client();
  const external = axios.CancelToken.source();
  const old = await h.send(api);
  const ownedExternally = await h.send(api, { cancelToken: external.token });
  assert.ok(old.request.config.cancelToken.reason);
  const current = await h.send(api);
  h.cancel();
  assert.equal(external.token.reason, undefined);
  assert.ok(current.request.config.cancelToken.reason);
  external.cancel('controlled external cancellation');
  ownedExternally.request.success();
  old.request.success();
  current.request.success();
  // Existing MSAxios.request cloneDeep clones an externally supplied token.
  // Its source no longer owns that clone; retain and explicitly expose this
  // baseline behavior rather than claiming this patch repairs external cancel.
  assert.notEqual(ownedExternally.request.config.cancelToken, external.token);
  assert.equal((await ownedExternally.result).value.ok, true);
  assert.equal((await old.result).error.code, 'ERR_CANCELED');
  assert.equal((await current.result).error.code, 'ERR_CANCELED');
});

test('real Axios instance: external token keeps source cancellation and is excluded from route management', async () => {
  const h = harness();
  const api = h.client();
  const external = axios.CancelToken.source();
  const result = outcome(
    api.axiosInstance.request({ method: 'post', url: '/test-plan/page', cancelToken: external.token })
  );
  await tick();
  h.cancel();
  assert.equal(external.token.reason, undefined);
  external.cancel('controlled external cancellation');
  assert.equal((await result).error.code, 'ERR_CANCELED');
});

test('real Axios: ignoreCancelToken completion cannot unregister managed same-URL request', async () => {
  const h = harness();
  const api = h.client();
  const managed = await h.send(api);
  const ignored = await h.send(api, {}, { ignoreCancelToken: true });
  assert.equal(ignored.request.config.cancelToken, undefined);
  assert.equal(managed.request.config.cancelToken.reason, undefined);
  ignored.request.success();
  assert.equal((await ignored.result).value.ok, true);
  h.cancel();
  assert.ok(managed.request.config.cancelToken.reason);
  managed.request.success();
  assert.equal((await managed.result).error.code, 'ERR_CANCELED');
});

test('real Axios: per-request override of default ignoreCancelToken restores route ownership', async () => {
  const h = harness();
  const api = h.client({ ignoreCancelToken: true });
  const ignored = await h.send(api);
  const managed = await h.send(api, {}, { ignoreCancelToken: false });
  h.cancel();
  assert.equal(ignored.request.config.cancelToken, undefined);
  ignored.request.success();
  managed.request.success();
  assert.equal((await ignored.result).value.ok, true);
  assert.equal((await managed.result).error.code, 'ERR_CANCELED');
});

['ERR_NETWORK', 'ECONNABORTED', 'ERR_BAD_RESPONSE', 'ERR_CANCELED'].forEach((code) => {
  test(`real Axios: ${code} reaches downstream error hook unchanged and unregisters`, async () => {
    const h = harness();
    const sent = await h.send(h.client());
    const error =
      code === 'ERR_CANCELED'
        ? new axios.CanceledError('controlled cancel', sent.request.config)
        : new axios.AxiosError(
            'controlled failure',
            code,
            sent.request.config,
            undefined,
            code === 'ERR_BAD_RESPONSE'
              ? { status: 503, data: {}, config: sent.request.config, headers: {} }
              : undefined
          );
    sent.request.wait.reject(error);
    assert.equal((await sent.result).error, error);
    assert.equal(h.errors.at(-1), error);
    h.cancel();
    assert.equal(sent.request.config.cancelToken.reason, undefined);
  });
});

[null, undefined].forEach((reason) => {
  test(`real Axios: custom interceptor rejection ${reason} preserves identity`, async () => {
    const h = harness();
    boundary = () => Promise.reject(reason);
    const sent = await h.send(h.client());
    sent.request.success();
    assert.equal((await sent.result).error, reason);
    assert.equal(h.errors.at(-1), reason);
    h.cancel();
  });
});

function projectErrorHook(receipts) {
  const source = readFileSync(new URL('../src/api/http/index.ts', import.meta.url), 'utf8');
  const ast = ts.createSourceFile('index.ts', source, ts.ScriptTarget.Latest, true);
  const declaration = ast.statements
    .flatMap((statement) => statement.declarationList?.declarations || [])
    .find((item) => item.name.getText(ast) === 'transform');
  const handler = declaration.initializer.properties.find(
    (item) => item.name.getText(ast) === 'responseInterceptorsCatch'
  );
  const context = {
    exports: {},
    Blob,
    ...load('utils/appError'),
    isRequestCanceled: load('utils/latestDetail').isRequestCanceled,
    useI18n: () => ({ t: (key) => key }),
    // The real project's hook executes; only its UI/status presentation sinks
    // are recorded so tests do not require a browser or affect a user session.
    showHttpErrorMessage: (...args) => receipts.push({ kind: 'message', args }),
    checkStatus: (...args) => receipts.push({ kind: 'status', args }),
  };
  vm.runInNewContext(
    ts.transpileModule(`exports.handler = ${handler.initializer.getText(ast)}`, {
      compilerOptions: { module: ts.ModuleKind.CommonJS },
    }).outputText,
    context
  );
  return context.exports.handler;
}

[
  ['ERR_NETWORK', 'Network Error', 'network'],
  ['ECONNABORTED', 'timeout of 100ms exceeded', 'timeout'],
  ['ERR_BAD_RESPONSE', 'controlled HTTP failure', 'server'],
  ['ERR_CANCELED', 'controlled cancel', undefined],
].forEach(([code, message, category]) => {
  test(`real Axios plus actual project error hook: ${code}`, async () => {
    const receipts = [];
    const h = harness(projectErrorHook(receipts));
    const sent = await h.send(h.client(), {}, { errorMessageMode: 'message' });
    const error =
      code === 'ERR_CANCELED'
        ? new axios.CanceledError(message, sent.request.config)
        : new axios.AxiosError(
            message,
            code,
            sent.request.config,
            undefined,
            code === 'ERR_BAD_RESPONSE'
              ? {
                  status: 503,
                  data: { message: 'controlled service unavailable' },
                  config: sent.request.config,
                  headers: {},
                }
              : undefined
          );
    sent.request.wait.reject(error);
    const result = await sent.result;
    assert.equal(h.errors[0], error, 'cleanup preserves the original value entering the actual downstream hook');
    if (category) {
      assert.equal(result.error.category, category);
      assert.equal(receipts[0].kind, code === 'ERR_BAD_RESPONSE' ? 'status' : 'message');
      if (code === 'ERR_BAD_RESPONSE') assert.equal(receipts[0].args[0], 503);
    } else {
      assert.equal(result.error, error);
      assert.equal(receipts.length, 0, 'cancelled requests remain silent');
    }
    h.cancel();
    assert.equal(sent.request.config.cancelToken.reason, undefined);
  });
});

test('real Axios: external completion cannot unregister a newer managed request', async () => {
  const h = harness();
  const api = h.client();
  const source = axios.CancelToken.source();
  const external = await h.send(api, { cancelToken: source.token });
  const managed = await h.send(api);
  external.request.success();
  assert.equal((await external.result).value.ok, true);
  assert.equal(managed.request.config.cancelToken.reason, undefined);
  h.cancel();
  assert.ok(managed.request.config.cancelToken.reason);
  assert.equal((await managed.result).error.code, 'ERR_CANCELED');
});
