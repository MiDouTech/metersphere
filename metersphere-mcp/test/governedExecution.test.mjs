import test from 'node:test';
import assert from 'node:assert/strict';
import { fileURLToPath } from 'node:url';
import { createServer } from 'node:http';
import { Client } from '@modelcontextprotocol/sdk/client/index.js';
import { StdioClientTransport } from '@modelcontextprotocol/sdk/client/stdio.js';

test('actual stdio handshake discovers governed operations and preserves original logical request over HTTP', async () => {
  const requests = [];
  const server = createServer(async (request, response) => {
    let body = ''; for await (const bytes of request) body += bytes;
    const rpc = JSON.parse(body); requests.push(rpc);
    response.setHeader('content-type', 'application/json');
    response.end(JSON.stringify({ jsonrpc: '2.0', id: rpc.id, result: { content: [{ type: 'text', text: JSON.stringify({ actionId: 'original-action', state: 'UNKNOWN', replayed: true, permissionToAct: false }) }] } }));
  });
  await new Promise(resolve => server.listen(0, '127.0.0.1', resolve));
  const client = new Client({ name: 'governed-contract-fixture', version: '1' });
  const transport = new StdioClientTransport({ command: process.execPath, args: ['--max-old-space-size=128', fileURLToPath(new URL('../dist/index.js', import.meta.url))], env: { MS_BASE_URL: `http://127.0.0.1:${server.address().port}`, MS_AGENT_TOKEN: 'synthetic-fixture', MS_PROJECT_ID: 'project' } });
  try {
    await client.connect(transport); const { tools } = await client.listTools();
    const prefix = 'metersphere.functional.execution.session.';
    for (const operation of ['contract', 'start', 'resume', 'lease.takeover', 'checkpoint.get', 'action.prepare', 'action.resolve', 'observation.append', 'case.finish', 'result.evidence.append', 'result.close-unaccepted', 'result.correction.request', 'knowledge.substitution.confirm']) assert.ok(tools.some(tool => tool.name === prefix + operation), operation);
    assert.ok(!tools.some(tool => tool.name === prefix + 'result.verify'), 'Agent bridge must not expose human acceptance');
    const prepare = tools.find(tool => tool.name === prefix + 'action.prepare'); assert.ok(prepare.inputSchema.required.includes('idempotencyKey')); assert.ok(prepare.inputSchema.required.includes('leaseGeneration')); assert.ok(!prepare.inputSchema.properties.verified);
    assert.ok(tools.some(tool => tool.name === 'metersphere.execution.step.submit'), 'legacy schema remains separate');
    const args = { protocolVersion: '1.0', idempotencyKey: 'original-key', sessionId: 'session', caseExecutionId: 'instance', expectedContextVersion: 7, leaseGeneration: 2, actionType: 'CREATE', targetEntity: 'fixture', target: 'owned-object', parameters: { a: 1 }, expectedEffect: 'one object', businessKey: 'tracked', queryMethod: 'authoritative query' };
    for (let index = 0; index < 2; index++) { const result = await client.callTool({ name: prepare.name, arguments: args }); assert.equal(JSON.parse(result.content[0].text).permissionToAct, false); }
    assert.equal(requests.length, 2); assert.deepEqual(requests[0].params.arguments, args); assert.deepEqual(requests[1].params.arguments, args);
    const rejected = await client.callTool({ name: prepare.name, arguments: { ...args, verified: true } }); assert.equal(rejected.isError, true); assert.equal(requests.length, 2);
  } finally { await client.close(); await new Promise(resolve => server.close(resolve)); }
});
