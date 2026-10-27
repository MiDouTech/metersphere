import test from "node:test";
import assert from "node:assert/strict";
import http from "node:http";
import { MeterSphereClient } from "../dist/client.js";

test("native image content survives the Node bridge without text conversion", async t => {
  const content = [{ type: "image", data: "AQID", mimeType: "image/png" }, { type: "text", text: '{"fileId":"e"}' }];
  const server = http.createServer((req, res) => {
    req.resume(); res.setHeader("Content-Type", "application/json");
    res.end(JSON.stringify({ jsonrpc: "2.0", id: 1, result: { content } }));
  });
  await new Promise(resolve => server.listen(0, "127.0.0.1", resolve));
  t.after(() => new Promise(resolve => server.close(resolve)));
  const client = new MeterSphereClient({ baseUrl: `http://127.0.0.1:${server.address().port}`, agentToken: "fixture", projectId: "p" });
  assert.deepEqual((await client.callNativeTool("metersphere.functional.attachment.read", { projectId: "p", caseId: "c", fileId: "e" })).content, content);
});

test("HTTP business errors retain safe code and correlation with a useful bounded message", async t => {
  const server = http.createServer((req, res) => {
    req.resume(); res.statusCode = 409; res.setHeader("Content-Type", "application/json");
    res.end(JSON.stringify({ code: "IDEMPOTENCY_CONFLICT", message: "SQL password=secret", traceId: "trace-safe" }));
  });
  await new Promise(resolve => server.listen(0, "127.0.0.1", resolve));
  t.after(() => new Promise(resolve => server.close(resolve)));
  const client = new MeterSphereClient({ baseUrl: `http://127.0.0.1:${server.address().port}`, agentToken: "fixture", projectId: "p" });
  const result = await client.callNativeTool("metersphere.functional.execution.submit", {});
  const error = JSON.parse(result.content[0].text);
  assert.equal(error.code, "IDEMPOTENCY_CONFLICT"); assert.equal(error.traceId, "trace-safe");
  assert.match(error.message, /请求标识/); assert.doesNotMatch(JSON.stringify(result), /SQL|password|secret/);
});
