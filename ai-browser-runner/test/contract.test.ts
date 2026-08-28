import assert from "node:assert/strict";
import test from "node:test";
import { parseAction, parseAssertions } from "../src/contract.js";
import { assertAllowedUrl, resolveUploadPath } from "../src/security.js";
import type { RunnerConfig } from "../src/types.js";

test("accepts a v1 low-risk click action", () => {
  const action = parseAction(JSON.stringify({ contractVersion: "v1", id: "action-1", idempotencyKey: "click-save-1", type: "CLICK",
    target: { strategy: "ROLE", role: "button", name: "保存" },
    timeoutMs: 10_000, retryable: true, riskLevel: "LOW" }));
  assert.equal(action.type, "CLICK");
});

test("rejects executable javascript and unknown actions", () => {
  assert.throws(() => parseAction(JSON.stringify({ contractVersion: "v1", type: "JAVASCRIPT",
    timeoutMs: 10_000, retryable: false, riskLevel: "HIGH" })), /不受支持/);
});

test("blocks high-risk actions in phase one", () => {
  assert.throws(() => parseAction(JSON.stringify({ contractVersion: "v1", id: "action-2", idempotencyKey: "click-delete-1", type: "CLICK",
    target: { strategy: "ROLE", role: "button", name: "删除" },
    timeoutMs: 10_000, retryable: false, riskLevel: "HIGH" })), /高风险动作/);
});

test("requires deterministic assertion expected values", () => {
  assert.throws(() => parseAssertions(JSON.stringify({ contractVersion: "v1", type: "TEXT",
    target: { strategy: "TEXT", text: "完成" }, timeoutMs: 1000 })), /expected/);
});

test("enforces exact origin allowlist", () => {
  assert.equal(assertAllowedUrl("https://example.com/a", new Set(["https://example.com"])).pathname, "/a");
  assert.throws(() => assertAllowedUrl("https://evil.example/a", new Set(["https://example.com"])), /白名单/);
});

test("prevents upload path traversal", () => {
  const config = { uploadRoot: "C:/safe" } as RunnerConfig;
  assert.throws(() => resolveUploadPath("../secret.txt", config), /受控目录/);
});
