// Verify the installed artifact against the isolated backend. Never print credentials.
import { createRequire } from 'node:module';
import { pathToFileURL } from 'node:url';
import { resolve } from 'node:path';
import { createHash } from 'node:crypto';
import assert from 'node:assert/strict';

const root = resolve(process.env.MSP_MCP_INSTALL_ROOT || '.codex-tmp/case-export-installed');
const installed = createRequire(resolve(root, 'package.json'));
const { Client } = await import(pathToFileURL(installed.resolve('@modelcontextprotocol/sdk/client/index.js')).href);
const { StdioClientTransport } = await import(pathToFileURL(installed.resolve('@modelcontextprotocol/sdk/client/stdio.js')).href);
const client = new Client({ name: 'case-export-acceptance', version: '1.0.0' });
const transport = new StdioClientTransport({ command: process.execPath, args: [installed.resolve('@midoo/metersphere-mcp')],
  stderr: 'pipe', env: Object.fromEntries(Object.entries(process.env).filter(([, value]) => value !== undefined)) });
let stage = 'initialize';
try {
  assert.equal(process.env.MS_BASE_URL, 'http://127.0.0.1:15175');
  await client.connect(transport);
  const call = async (action, args) => {
    stage = action;
    const result = await client.callTool({ name: `metersphere.functional.export.${action}`,
      arguments: { projectId: process.env.MS_PROJECT_ID, ...args } });
    assert.notEqual(result.isError, true);
    return JSON.parse(result.content.find(item => item.type === 'text').text);
  };
  const task = await call('start', JSON.parse(process.env.CASE_EXPORT_REQUEST_JSON));
  assert.equal(task.taskId, process.env.CASE_EXPORT_TASK_ID);
  assert.equal((await call('status', { taskId: task.taskId })).state, 'SUCCEEDED');
  const chunks = [];
  let offset = 0;
  let complete = false;
  for (let index = 0; index < 1024; index += 1) {
    const chunk = await call('download', { taskId: task.taskId, offset, length: 65536 });
    const bytes = Buffer.from(chunk.base64, 'base64');
    chunks.push(bytes);
    assert.equal(chunk.nextOffset, offset + bytes.length);
    offset = chunk.nextOffset;
    if (chunk.eof) { complete = true; break; }
  }
  assert(complete);
  assert.equal(createHash('sha256').update(Buffer.concat(chunks)).digest('hex'), process.env.CASE_EXPORT_SHA256);
  assert.equal((await call('stop', { taskId: task.taskId, requestId: `stdio-stop-${task.taskId}` })).state, 'SUCCEEDED');
  console.log('PASS: installed MCP export replay, status, authorized byte-identical download and stop.');
} catch {
  console.error(`FAIL: installed MCP export stage ${stage}; inspect isolated service logs.`);
  process.exitCode = 1;
} finally {
  await client.close();
}
