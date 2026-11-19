import test from 'node:test';
import assert from 'node:assert/strict';
import { z } from 'zod';
import { businessAssetTools } from '../dist/tools/businessAssets.js';
import { MsApiError, MeterSphereClient } from '../dist/client.js';
import { createExecutionTaskTool } from '../dist/tools/testPlanExecution.js';

test('understanding upload requires authenticated source and preserves immutable version intent', () => {
  const tool = businessAssetTools.find((item) => item.name === 'metersphere.business_asset.upload');
  const schema = z.object(tool.inputSchema);
  const request = { projectId: 'p', schemaVersion: '1.0', operation: 'UPDATE', assetId: 'asset', baseVersion: 2, idempotencyKey: 'stable', scope: { systemKey: 'system', moduleKey: 'module', pageKeys: ['page'], flowKey: '' }, title: '业务理解', document: { format: 'markdown', content: '实际观察' }, branches: [], changeSummary: '补充分支', source: { taskId: 'session', executorId: 'executor' } };
  assert.deepEqual(schema.parse(request), request);
  assert.equal(schema.safeParse({ ...request, source: undefined }).success, false);
  assert.equal(schema.safeParse({ ...request, baseVersion: -1 }).success, false);
});
test('executor adapter forwards events without inventing actions or requiring a legacy lease', async () => {
  const tool = businessAssetTools.find((item) => item.name === 'metersphere.executor.event');
  const args = { projectId: 'p', sessionId: 'session', requestKey: 'one', type: 'DEVIATION', stage: 'PAGE', errorCategory: 'BUSINESS_CHANGED', outcome: 'RUNNING', summary: '审批路径发生变化' };
  const parsed = z.object(tool.inputSchema).parse(args);
  let observed;
  await tool.handler({ callNativeTool: async (name, value) => { observed = { name, value }; return {}; } }, parsed);
  assert.equal(observed.name, tool.name); assert.deepEqual(observed.value, args);
  assert.equal('leaseId' in tool.inputSchema, false); assert.equal('actionJson' in tool.inputSchema, false);
});
test('credential errors expose safe guidance instead of raw technical details', () => {
  const error = new MsApiError(503, JSON.stringify({ code: 'CONFIGURATION_KEY_UNAVAILABLE', message: 'password=must-not-be-shown', traceId: 'trace-id' }));
  assert.match(error.message, /加密密钥/); assert.equal(error.traceId, 'trace-id'); assert.equal(error.message.includes('must-not-be-shown'), false);
});

test('existing execution tools retain the authenticated native MCP transport and creation contract', async () => {
  const client = new MeterSphereClient({ baseUrl: 'https://example.invalid', projectId: 'p', agentToken: 'synthetic' });
  const calls = [];
  client.callNativeTool = async (name, args) => { calls.push({ name, args }); return {}; };
  client.request = async () => { throw new Error('Browser-only endpoints cannot authenticate a stdio Agent Token'); };
  const schema = z.object(createExecutionTaskTool.inputSchema);
  assert.equal(schema.safeParse({ projectId: 'p' }).success, false);
  const payload = schema.parse({ projectId: 'p', preflightId: 'preflight', environmentProfileId: 'environment' });
  await createExecutionTaskTool.handler(client, payload);
  await client.getExecutionTask('task');
  await client.getExecutionEvents({ executionTaskId: 'task', cursor: 1 });
  await client.cancelExecutionTask({ executionTaskId: 'task' });
  await client.resumeExecutionTask({ executionTaskId: 'task' });
  assert.deepEqual(calls.map((call) => call.name), ['metersphere.execution.create', 'metersphere.execution.get', 'metersphere.execution.events', 'metersphere.execution.cancel', 'metersphere.execution.resume']);
  assert.equal(calls[0].args.preflightId, 'preflight');
  assert.equal(calls[0].args.environmentProfileId, 'environment');
});
