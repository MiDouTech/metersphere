/* eslint-disable no-script-url -- These rejected protocol fixtures are never executed. */
import ts from 'typescript';

import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import test from 'node:test';
import vm from 'node:vm';

const source = readFileSync(new URL('../src/utils/platformConfiguration.ts', import.meta.url), 'utf8');
const compiled = ts.transpileModule(source, { compilerOptions: { module: ts.ModuleKind.CommonJS } }).outputText;
const context = { exports: {}, URL };
vm.runInNewContext(compiled, context);
const { platformUrl, validateLoginJson } = context.exports;

[
  'http://example.com',
  'https://example.com/login',
  ' https://example.com:8443/login?next=%2Fhome ',
  'http://127.0.0.1:8080/login',
  'https://[2001:db8::1]:8443/login',
].forEach((value) => {
  test(`configuration accepts a complete HTTP URL: ${value}`, () => {
    assert.ok(platformUrl(value).hostname);
  });
});
[
  '',
  'msp.ebcone.net',
  '//example.com/login',
  'ftp://example.com',
  'data:image/png;base64,AA==',
  'javascript:alert(1)',
  'https://user:password@example.com',
  'https://user@example.com',
  'https://',
  'https://example.com:99999',
].forEach((value) => {
  test(`configuration rejects invalid or credential-bearing URL: ${value}`, () => {
    assert.throws(() => platformUrl(value), /完整|账号密码/);
  });
});
['https://example.com', 'https://example.com/', 'http://example.com:8080', 'https://[2001:db8::1]:8443/'].forEach(
  (value) => {
    test(`Origin accepts protocol, host and port: ${value}`, () => {
      assert.ok(platformUrl(value, true).origin);
    });
  }
);
['https://example.com/path', 'https://example.com/?token=secret', 'https://example.com/#section'].forEach((value) => {
  test(`Origin rejects paths, queries and fragments: ${value}`, () => {
    assert.throws(() => platformUrl(value, true), /Origin/);
  });
});
test('browser origin matching canonicalizes host case and the default HTTPS port', () => {
  assert.equal(platformUrl('https://EXAMPLE.com:443/login').origin, platformUrl('https://example.com/', true).origin);
  assert.notEqual(platformUrl('http://example.com/login').origin, platformUrl('https://example.com/', true).origin);
  assert.notEqual(
    platformUrl('https://example.com:8443/login').origin,
    platformUrl('https://example.com/', true).origin
  );
});
[
  '{}',
  '{"strategy":"LABEL","label":"用户名"}',
  '{"contractVersion":"v1","type":"URL","operator":"NOT_EQUALS","expected":"/login"}',
].forEach((value) => {
  test(`JSON object passes the UI syntax boundary: ${value}`, () => {
    assert.doesNotThrow(() => validateLoginJson(value, '定位器'));
  });
});
['', '{', '{"strategy":}', '{strategy:"LABEL"}'].forEach((value) => {
  test(`malformed JSON has a field-specific safe error: ${value}`, () => {
    assert.throws(() => validateLoginJson(value, '定位器'), /定位器必须是合法 JSON/);
  });
});
['null', '[]', '[{}]', 'true', '1', '"text"'].forEach((value) => {
  test(`non-object JSON is rejected: ${value}`, () => {
    assert.throws(() => validateLoginJson(value, '成功断言'), /成功断言必须是 JSON 对象/);
  });
});
