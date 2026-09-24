import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { test } from 'node:test';
import vm from 'node:vm';
import ts from 'typescript';

const user = {
  isAdmin: false,
  currentRole: { projectPermissions: [], orgPermissions: [], systemPermissions: [] },
  userRolePermissions: [{ userRole: { id: 'project_admin', type: 'PROJECT', enabled: true } }],
  userRoleRelations: [{ roleId: 'project_admin', sourceId: 'p' }],
};
const compiled = ts.transpileModule(readFileSync(new URL('../src/utils/permission.ts', import.meta.url), 'utf8'), {
  compilerOptions: { module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2022 },
}).outputText;
const module = { exports: {} };
vm.runInNewContext(compiled, {
  module, exports: module.exports,
  require: (name) => {
    if (name === '@/store') return { useUserStore: () => user, useAppStore: () => ({ currentProjectId: 'p', currentOrgId: 'o' }) };
    return { default: [], INDEX_ROUTE: { name: 'index' } };
  },
});
const { hasAnyPermission } = module.exports;
test('project administrator bypass cannot grant any platform policy operation', () => {
  for (const operation of ['READ', 'MANAGE', 'PUBLISH']) {
    assert.equal(hasAnyPermission([`SYSTEM_QUALITY:${operation}`]), false);
  }
  assert.equal(hasAnyPermission(['QUALITY:READ']), true); // unrelated project behavior is preserved
});
test('system explicit grants remain independent', () => {
  user.currentRole.systemPermissions = ['SYSTEM_QUALITY:READ', 'SYSTEM_QUALITY:MANAGE'];
  assert.equal(hasAnyPermission(['SYSTEM_QUALITY:READ']), true);
  assert.equal(hasAnyPermission(['SYSTEM_QUALITY:MANAGE']), true);
  assert.equal(hasAnyPermission(['SYSTEM_QUALITY:PUBLISH']), false);
  user.currentRole.systemPermissions = [];
});
test('pure system administrator needs no project membership', () => {
  user.isAdmin = true; user.userRoleRelations = [];
  assert.equal(hasAnyPermission(['SYSTEM_QUALITY:PUBLISH']), true);
});
