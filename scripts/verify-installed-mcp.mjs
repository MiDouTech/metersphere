// Exercise the installed npm artifact, not the repository TypeScript sources.
// Credentials are read from the environment and never printed.
import { createRequire } from 'node:module';
import { pathToFileURL } from 'node:url';
import { resolve } from 'node:path';
import assert from 'node:assert/strict';

const installRoot = resolve(process.env.MSP_MCP_INSTALL_ROOT || '.codex-tmp/mcp-remediation-installed');
const requireInstalled = createRequire(resolve(installRoot, 'package.json'));
const { Client } = await import(pathToFileURL(requireInstalled.resolve('@modelcontextprotocol/sdk/client/index.js')).href);
const { StdioClientTransport } = await import(pathToFileURL(requireInstalled.resolve('@modelcontextprotocol/sdk/client/stdio.js')).href);
const entry = requireInstalled.resolve('@midoo/metersphere-mcp');
const client = new Client({ name: 'msp-installed-acceptance', version: '1.0.0' });
const transport = new StdioClientTransport({
  command: process.execPath, args: [entry], stderr: 'pipe',
  env: Object.fromEntries(Object.entries(process.env).filter(([, value]) => value !== undefined)),
});
let stage = 'initialize';
try {
  await client.connect(transport);
  stage = 'version';
  assert.equal(client.getServerVersion()?.version, '0.7.0');
  stage = 'tools/list';
  const discovery = await client.listTools();
  assert(discovery.tools.some(tool => tool.name === 'metersphere.functional.execution.evidence.upload'));
  stage = 'case search';
  const result = await client.callTool({
    name: 'search_functional_cases',
    arguments: { projectId: process.env.MS_PROJECT_ID, pageSize: 1, includeSteps: true },
  });
  if (result.isError) {
    const failure = JSON.parse(result.content.find(item => item.type === 'text')?.text || '{}');
    const code = String(failure.code || failure.status || 'unknown').replace(/[^a-zA-Z0-9_.:-]/g, '').slice(0, 80);
    console.error(`MCP case search error code: ${code}; HTTP status: ${Number(failure.status) || 0}`);
  }
  assert.notEqual(result.isError, true, 'Installed client read failed; inspect authorized service logs.');
  stage = 'case response shape';
  const text = result.content.find(item => item.type === 'text')?.text;
  const page = JSON.parse(text || '{}');
  assert(Array.isArray(page.cases), 'Expected actual case search response.');
  stage = 'case fixture';
  assert(page.cases.length > 0, 'Isolated fixture requires at least one readable case.');
  stage = 'native tool forwarding';
  const attachments = await client.callTool({
    name: 'metersphere.functional.attachments.list',
    arguments: { projectId: process.env.MS_PROJECT_ID, caseId: page.cases[0].caseId },
  });
  assert.notEqual(attachments.isError, true, 'Native read tool failed.');
  assert(Array.isArray(attachments.content));
  console.log('PASS: installed MCP 0.7.0 initialize, tools/list, authorized case search and native tool forwarding.');
} catch {
  // Never echo transport exceptions that might contain sensitive request context.
  console.error(`FAIL: installed MCP ${stage} failed; check server version, credentials and service logs.`);
  process.exitCode = 1;
} finally {
  await client.close();
}
