import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import test from 'node:test';
import { parse } from 'vue/compiler-sfc';

const source = await readFile(
  new URL('../src/views/setting/system/agentIntegration/index.vue', import.meta.url),
  'utf8'
);
const template = parse(source).descriptor.template.content;

test('Token creation and settings keep identity/project fields without a second feature-permission form', () => {
  assert.doesNotMatch(template, /field="scopes"|v-for="scope in scopeOptions"/);
  assert.equal((template.match(/field="projectIds"/g) || []).length, 2);
  assert.equal((template.match(/field="name"/g) || []).length, 2);
  assert.match(template, /system\.agentIntegration\.authorizationHelp/);
});
