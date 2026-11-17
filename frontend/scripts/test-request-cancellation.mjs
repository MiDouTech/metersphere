import ts from 'typescript';

import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import test from 'node:test';
import vm from 'node:vm';

function createCanceler() {
  class CancelToken {
    constructor(executor) {
      this.cancelled = false;
      executor(() => {
        this.cancelled = true;
      });
    }
  }
  const source = readFileSync(new URL('../src/api/http/axiosCancel.ts', import.meta.url), 'utf8');
  const context = {
    exports: {},
    require: (name) => {
      if (name === 'axios') return { default: { CancelToken } };
      if (name === '@/utils/is') return { isFunction: (value) => typeof value === 'function' };
      throw new Error(name);
    },
  };
  vm.runInNewContext(
    ts.transpileModule(source, {
      compilerOptions: { module: ts.ModuleKind.CommonJS },
    }).outputText,
    context
  );
  return new context.exports.AxiosCanceler();
}
const request = () => ({ method: 'post', url: '/test-plan/page' });

test('late completion of an older list request does not cancel the current list', () => {
  const canceler = createCanceler();
  const old = request();
  const current = request();
  canceler.addPending(old);
  canceler.addPending(current);
  assert.equal(old.cancelToken.cancelled, true);
  canceler.completePending(old);
  assert.equal(current.cancelToken.cancelled, false);
  canceler.removeAllPending();
  assert.equal(current.cancelToken.cancelled, true);
});

test('successful completion unregisters without cancelling its own token', () => {
  const canceler = createCanceler();
  const current = request();
  canceler.addPending(current);
  canceler.completePending(current);
  canceler.removeAllPending();
  assert.equal(current.cancelToken.cancelled, false);
});

test('route change cancels pending requests, including requests owned by another instance', () => {
  const canceler = createCanceler();
  const current = request();
  canceler.addPending(current);
  new canceler.constructor().removeAllPending();
  assert.equal(current.cancelToken.cancelled, true);
});
