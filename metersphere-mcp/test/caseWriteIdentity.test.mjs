import test from 'node:test';
import assert from 'node:assert/strict';
import { z } from 'zod';
import { createFunctionalModuleTool, createFunctionalCaseTool, batchCreateFunctionalCasesTool } from '../dist/tools/caseWrite.js';
for (const [tool, fields, method] of [
  [createFunctionalModuleTool, { name: 'module' }, 'createFunctionalModule'],
  [createFunctionalCaseTool, { name: 'case' }, 'createFunctionalCase'],
  [batchCreateFunctionalCasesTool, { cases: [{ name: 'case' }] }, 'batchCreateFunctionalCases'],
]) {
  test(`${tool.name} requires and preserves caller identity`, async () => {
    const schema = z.object(tool.inputSchema).strict();
    for (const requestId of [undefined, '', 'x'.repeat(129), 'bad key', '中文']) {
      assert.equal(schema.safeParse({ ...fields, requestId }).success, false);
    }
    const args = schema.parse({ ...fields, requestId: 'audit:2026.10-27_' });
    let sent;
    await tool.handler({ [method]: async payload => { sent = payload; return { id: 'result' }; } }, args);
    assert.deepEqual(sent, args);
    assert.equal(schema.safeParse({ ...fields, requestId: 'x'.repeat(128) }).success, true);
  });
}
