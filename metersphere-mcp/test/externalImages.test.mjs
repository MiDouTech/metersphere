import test from 'node:test';
import assert from 'node:assert/strict';
import { z } from 'zod';
import { caseLibraryTools } from '../dist/tools/caseLibrary.js';

test('URL image tools preserve target context and stable request keys without fetching in the MCP client', async () => {
  const names = ['metersphere.functional.attachment.import_url', 'metersphere.bug.attachment.import_url', 'metersphere.functional.execution.evidence.import_url'];
  for (const name of names) {
    const tool = caseLibraryTools.find(item => item.name === name);
    assert.ok(tool, name);
    const args = { projectId: 'p', url: 'https://example.com/image.png?signature=temporary', requestId: 'stable-request' };
    if (name.includes('.bug.')) args.bugId = 'b';
    else args.caseId = 'c';
    if (name.includes('.execution.')) Object.assign(args, { contentHash: 'a'.repeat(64), stepNum: 1, purpose: 'BEFORE_STEP' });
    const schema = z.object(tool.inputSchema);
    assert.deepEqual(schema.parse(args), args);
    const calls = [];
    const result = await tool.handler({ callNativeTool: async (actualName, payload) => { calls.push([actualName, payload]); return { transportVerified: true }; } }, args);
    assert.deepEqual(calls, [[name, args]]);
    assert.equal(result.transportVerified, true);
    assert.equal(schema.safeParse({ ...args, requestId: '' }).success, false);
    assert.equal(schema.safeParse({ ...args, url: 'not-a-url' }).success, false);
  }
});
