import test from 'node:test';
import assert from 'node:assert/strict';
import { z } from 'zod';
import { caseExportTools } from '../dist/tools/caseExport.js';

test('export download rejects unbounded chunks and preserves native authorization arguments', async () => {
  const download = caseExportTools.find(tool => tool.name.endsWith('.download'));
  const schema = z.object(download.inputSchema).strict();
  assert.equal(schema.safeParse({ projectId: 'p', taskId: 't', length: 65537 }).success, false);
  assert.equal(schema.safeParse({ projectId: 'p', taskId: 't', offset: -1 }).success, false);
  const args = schema.parse({ projectId: 'p', taskId: 't', offset: 64, length: 1024 });
  let sent;
  const expected = { eof: true, base64: 'aGVsbG8=' };
  const response = await download.handler({ callNativeTool: async (name, payload) => { sent = { name, payload }; return expected; } }, args);
  assert.deepEqual(sent, { name: 'metersphere.functional.export.download', payload: args });
  assert.equal(response, expected);
});

test('export creation requires a stable bounded idempotency key', () => {
  const start = caseExportTools.find(tool => tool.name.endsWith('.start'));
  const schema = z.object(start.inputSchema).strict();
  const base = { projectId: 'p', format: 'xlsx', request: { selectIds: ['c'] } };
  assert.equal(schema.safeParse(base).success, false);
  assert.equal(schema.safeParse({ ...base, requestId: 'x'.repeat(65) }).success, false);
  assert.equal(schema.safeParse({ ...base, requestId: 'stable-1' }).success, true);
});

test('export stop declares the idempotency key required by the native write gate', () => {
  const stop = caseExportTools.find(tool => tool.name.endsWith('.stop'));
  const schema = z.object(stop.inputSchema).strict();
  assert.equal(schema.safeParse({ projectId: 'p', taskId: 't' }).success, false);
  assert.equal(schema.safeParse({ projectId: 'p', taskId: 't', requestId: 'stop-1' }).success, true);
});
