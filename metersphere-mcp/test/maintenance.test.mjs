import test from "node:test";
import assert from "node:assert/strict";
import http from "node:http";
import { fileURLToPath } from "node:url";
import { Client } from "@modelcontextprotocol/sdk/client/index.js";
import { StdioClientTransport } from "@modelcontextprotocol/sdk/client/stdio.js";
import { MeterSphereClient } from "../dist/client.js";
import { nativeBatchSubmitTool } from "../dist/tools/maintenance.js";
import { z } from "zod";

test("native adapter forwards requestId and preserves structured MCP errors", async t => {
  let received;
  const server = http.createServer(async (req, res) => {
    const chunks = []; for await (const chunk of req) chunks.push(chunk);
    received = { path: req.url, body: JSON.parse(Buffer.concat(chunks).toString()) };
    res.setHeader("Content-Type", "application/json");
    res.end(JSON.stringify({ jsonrpc: "2.0", id: received.body.id, error: { code: -32001, message: "没有执行此操作的权限", data: { code: "PERMISSION_DENIED", traceId: "test-trace" } } }));
  });
  await new Promise(resolve => server.listen(0, "127.0.0.1", resolve));
  t.after(() => new Promise(resolve => server.close(resolve)));
  const client = new MeterSphereClient({ baseUrl: `http://127.0.0.1:${server.address().port}`, agentToken: "test-token", projectId: "project" });
  const result = await client.callNativeTool("metersphere.test_plan.update", { requestId: "stable-key" });
  assert.equal(received.path, "/api/mcp"); assert.equal(received.body.method, "tools/call");
  assert.equal(received.body.params.arguments.requestId, "stable-key");
  assert.equal(result.isError, true); assert.equal(JSON.parse(result.content[0].text).data.traceId, "test-trace");
});

test("batch schema keeps execution context and rejects oversized batches", () => {
  const schema = z.object(nativeBatchSubmitTool.inputSchema);
  const row = { caseId: "case", lastExecResult: "SUCCESS", executionTaskId: "task", idempotencyKey: "item-key", testPlanId: "plan", testPlanCaseId: "relation" };
  const args = { projectId: "project", requestId: "request", executionTaskId: "task", results: [row] };
  assert.deepEqual(schema.parse(args).results[0], row);
  assert.equal(schema.safeParse({ ...args, results: Array.from({ length: 101 }, () => row) }).success, false);
});

test("native adapter does not expose a raw HTTP failure body", async t => {
  const server = http.createServer((req, res) => { req.resume(); res.statusCode = 500; res.end("SQL stacktrace secret-password"); });
  await new Promise(resolve => server.listen(0, "127.0.0.1", resolve));
  t.after(() => new Promise(resolve => server.close(resolve)));
  const client = new MeterSphereClient({ baseUrl: `http://127.0.0.1:${server.address().port}`, agentToken: "test-token", projectId: "project" });
  const result = await client.callNativeTool("metersphere.execution.search", { projectId: "project" });
  assert.equal(result.isError, true);
  assert.equal(JSON.parse(result.content[0].text).status, 500);
  assert.doesNotMatch(JSON.stringify(result), /SQL|stacktrace|secret-password/);
});

test("real stdio process discovers maintenance tools without a backend call", async () => {
  const client = new Client({ name: "maintenance-contract-test", version: "1" });
  const transport = new StdioClientTransport({ command: process.execPath,
    args: [fileURLToPath(new URL("../dist/index.js", import.meta.url))],
    env: { ...process.env, MS_BASE_URL: "http://127.0.0.1:1", MS_AGENT_TOKEN: "test-token", MS_PROJECT_ID: "project" },
    stderr: "pipe" });
  try {
    await client.connect(transport);
    const { tools } = await client.listTools();
    const names = new Set(tools.map(tool => tool.name));
    for (const name of ["metersphere.functional.submit.batch", "metersphere.execution.search", "metersphere.execution.preflight", "metersphere.execution.checkpoint.resume",
      "metersphere.test_plan.update", "metersphere.test_plan.disassociate_cases", "metersphere.test_plan.get", "metersphere.bug.transitions", "metersphere.bug.transition"]) assert.ok(names.has(name), name);
  } finally { await client.close(); }
});
