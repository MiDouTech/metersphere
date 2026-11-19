import assert from "node:assert/strict";
import test from "node:test";
import { executionBatchNextTool } from "../dist/tools/executionBatchNext.js";

const row = (caseId, status = "CREATED") => ({ caseId, testPlanCaseId: caseId + "-relation", status });
function fixture(cases, options = {}) {
  const { verified = [], accountedPlanCaseIds = [], failedSteps = {}, legacy = [], waitingSteps = {}, unavailable = [], status = "RUNNING", executionId = "run" } = options;
  const calls = [];
  const client = { async callNativeTool(name, args) {
    calls.push({ name, args });
    const entry = cases.find(item => item.testPlanCaseId === args.testPlanCaseId);
    if (name === "metersphere.execution.contract.get" && unavailable.includes(entry.caseId)) {
      return { isError: true, content: [{ type: "text", text: JSON.stringify({
        data: { code: "STRUCTURED_CONTRACT_REVIEW_REQUIRED", traceId: "trace-case" },
      }) }] };
    }
    const data = name === "metersphere.execution.get" ? {
      id: "task", currentExecutionId: executionId, status, cases, accountedPlanCaseIds,
      executionContract: JSON.stringify({ cases: cases.filter(entry => !legacy.includes(entry.caseId))
        .map(entry => ({ testPlanCaseId: entry.testPlanCaseId, structuredContractVersion: 2 })) }),
    } : {
      caseId: entry.caseId,
      executionId, contractVersion: legacy.includes(entry.caseId) ? 0 : 2,
      verifiedStepIds: verified, failedStepIds: failedSteps[entry.caseId], waitingSteps: waitingSteps[entry.caseId],
      contract: { steps: [{ id: "step-1" }, { id: "step-2" }] },
    };
    return { content: [{ type: "text", text: JSON.stringify(data) }] };
  } };
  const next = async () => {
    const result = await executionBatchNextTool.handler(client, { taskId: "task", executionId: "run" });
    return JSON.parse(result.content[0].text);
  };
  return { calls, next };
}

test("a case waiting for a person or missing a contract does not hide the next runnable case", async () => {
  const { next, calls } = fixture([row("done", "SUCCESS"), row("human", "WAITING_HUMAN"),
    row("missing"), row("runnable")], { unavailable: ["missing"], verified: ["step-1"] });
  const result = await next();
  assert.equal(result.action, "CONTINUE_CASE");
  assert.equal(result.caseId, "runnable");
  assert.equal(result.decision.step.id, "step-2");
  assert.deepEqual(result.pending.map(entry => entry.reason), ["WAITING_HUMAN", "STRUCTURED_CONTRACT_REVIEW_REQUIRED"]);
  assert.equal(result.pending[1].traceId, "trace-case");
  assert.deepEqual(calls.map(call => call.name), [
    "metersphere.execution.get", "metersphere.execution.contract.get", "metersphere.execution.contract.get",
  ]);
});

test("a persisted assertion failure advances the batch without re-running that case", async () => {
  const failedSteps = { failed: ["step-1"] };
  const next = await fixture([row("failed"), row("next")], { failedSteps }).next();
  assert.equal(next.action, "CONTINUE_CASE");
  assert.equal(next.caseId, "next");
  assert.equal((await fixture([row("failed")], { failedSteps }).next()).action, "REQUEST_FINALIZATION");
});

test("a 120-case plan does not fetch the contracts of its 119 accounted cases again", async () => {
  const cases = Array.from({ length: 120 }, (_, index) => row("case-" + index));
  const accountedPlanCaseIds = cases.slice(0, 119).map(item => item.testPlanCaseId);
  const { next, calls } = fixture(cases, { accountedPlanCaseIds });
  const result = await next();
  assert.equal(result.caseId, "case-119");
  assert.equal(calls.length, 2);
  assert.equal(calls[0].args.includeStepProgress, true);
  assert.equal(calls[1].args.testPlanCaseId, "case-119-relation");
});

test("batch requests finalization only when all cases are accounted for", async () => {
  assert.deepEqual(await fixture([row("done", "SUCCESS"), row("failed", "FAILED"), row("verified")],
    { verified: ["step-1", "step-2"] }).next(), { action: "REQUEST_FINALIZATION", taskId: "task" });
  assert.equal((await fixture([row("done", "SUCCESS"), row("pending", "PAUSED")]).next()).action,
    "WAIT_FOR_CASES");
  assert.equal((await fixture([]).next()).reason, "EXECUTION_SCOPE_EMPTY");
});

test("stale attempts and paused tasks cannot advance or request completion", async () => {
  const stale = fixture([row("case")], { executionId: "newer" });
  assert.equal((await stale.next()).reason, "EXECUTION_NOT_CURRENT");
  assert.equal(stale.calls.length, 1);
  assert.equal((await fixture([row("case")], { status: "WAITING_HUMAN" }).next()).action, "WAIT_FOR_TASK");
});

test("finish rechecks the batch, then passes the exact task, execution and lease to MSP", async () => {
  const { executionBatchFinishTool } = await import("../dist/tools/executionBatchNext.js");
  const input = { taskId: "task", executionId: "run", leaseId: "lease", leaseToken: "proof" };
  const saved = {
    id: "task", currentExecutionId: "run", status: "RUNNING", cases: [row("case", "PAUSED")],
    executionContract: '{"cases":[{"testPlanCaseId":"case-relation","structuredContractVersion":2}]}',
  };
  let completes = 0;
  const client = { async callNativeTool(name, args) {
    if (name === "metersphere.execution.complete") {
      assert.deepEqual(args, input);
      completes++;
      return { content: [{ type: "text", text: '{"ok":true}' }] };
    }
    assert.equal(name, "metersphere.execution.get");
    return { content: [{ type: "text", text: JSON.stringify(saved) }] };
  } };
  const waiting = await executionBatchFinishTool.handler(client, input);
  assert.equal(JSON.parse(waiting.content[0].text).action, "WAIT_FOR_CASES");
  assert.equal(completes, 0);
  saved.cases[0].status = "SUCCESS";
  const completed = await executionBatchFinishTool.handler(client, input);
  assert.equal(JSON.parse(completed.content[0].text).ok, true);
  assert.equal(completes, 1);
});

test("batch skips a waiting step recorded in MSP and picks a legacy snapshot that can run", async () => {
  const { next } = fixture([row("waiting"), row("legacy")], {
    legacy: ["legacy"], waitingSteps: { waiting: { "step-1": "WAITING_HUMAN" } },
  });
  const result = await next();
  assert.equal(result.action, "CONTINUE_CASE");
  assert.equal(result.caseId, "legacy");
  assert.equal(result.decision.contractVersion, 0);
  assert.equal(result.pending[0].reason, "WAITING_HUMAN");
});

test("stdio batch runs a second case while the first waits and only finalizes after both receipts", async t => {
  const http = await import("node:http");
  const { Client } = await import("@modelcontextprotocol/sdk/client/index.js");
  const { StdioClientTransport } = await import("@modelcontextprotocol/sdk/client/stdio.js");
  const { fileURLToPath } = await import("node:url");
  const states = new Map([["first", { status: "OPEN" }], ["second", { status: "OPEN" }]]);
  let completed = false;
  const server = http.createServer(async (req, res) => {
    const chunks = [];
    for await (const chunk of req) chunks.push(chunk);
    const request = JSON.parse(Buffer.concat(chunks).toString());
    const { name, arguments: input } = request.params;
    const caseId = input.testPlanCaseId?.replace(/-relation$/, "") || input.stepId?.replace(/-runtime$/, "");
    const state = states.get(caseId);
    let data;
    switch (name) {
      case "metersphere.execution.get":
        data = { id: "task", currentExecutionId: "run", status: "RUNNING",
          cases: ["first", "second"].map(value => row(value)) };
        break;
      case "metersphere.execution.contract.get":
        data = { caseId, executionId: "run", contractVersion: 0,
          executionStepIds: { inspect: caseId + "-runtime" },
          verifiedStepIds: state.status === "SUCCESS" ? ["inspect"] : [],
          waitingSteps: state.status === "BLOCKED" ? { inspect: state.reason } : {},
          contract: { steps: [{ id: "inspect" }] } };
        break;
      case "metersphere.execution.step.submit":
        assert.equal(input.leaseId, "lease");
        state.status = input.status;
        state.reason = input.errorCode;
        state.receipt = { stepId: input.stepId, executionId: "run", status: input.status,
          requestId: input.requestId, artifactIds: JSON.stringify(input.artifactIds) };
        data = state.receipt;
        break;
      case "metersphere.artifact.list":
        data = ["first", "second"].map(value => ({ id: value + "-proof", taskId: "task", executionId: "run",
          caseId: value, status: "AVAILABLE", redacted: true }));
        break;
      case "metersphere.execution.step.result.get":
        assert.equal(input.requestId, state.receipt.requestId);
        data = state.receipt;
        break;
      case "metersphere.execution.complete":
        assert.equal(input.taskId, "task");
        assert.equal(input.executionId, "run");
        assert.equal(input.leaseId, "lease");
        assert.ok([...states.values()].every(item => item.status === "SUCCESS"));
        completed = true;
        data = { ok: true };
        break;
      default:
        assert.fail(name);
    }
    res.setHeader("Content-Type", "application/json");
    res.end(JSON.stringify({ jsonrpc: "2.0", id: request.id,
      result: { content: [{ type: "text", text: JSON.stringify(data) }] } }));
  });
  await new Promise(resolve => server.listen(0, "127.0.0.1", resolve));
  t.after(() => new Promise(resolve => server.close(resolve)));
  const client = new Client({ name: "batch-self-check", version: "1" });
  const transport = new StdioClientTransport({
    command: process.execPath,
    args: [fileURLToPath(new URL("../dist/index.js", import.meta.url))],
    env: { MS_BASE_URL: "http://127.0.0.1:" + server.address().port,
      MS_AGENT_TOKEN: "fixture", MS_PROJECT_ID: "project" },
  });
  const task = { taskId: "task", executionId: "run" };
  const lease = { ...task, leaseId: "lease", leaseToken: "fixture-lease" };
  const step = caseId => ({ ...lease, testPlanCaseId: caseId + "-relation",
    stepId: "inspect", evidenceRef: caseId + "-proof" });
  async function call(name, args) {
    const result = await client.callTool({ name: "metersphere.execution." + name, arguments: args });
    assert.equal(result.isError, undefined);
    return JSON.parse(result.content[0].text);
  }
  try {
    await client.connect(transport);
    assert.equal((await call("batch.next", task)).caseId, "first");
    await call("step.defer", { ...step("first"), requestId: "wait", reason: "WAITING_HUMAN",
      observedReason: "The page is waiting for human confirmation" });
    assert.equal((await call("batch.next", task)).caseId, "second");
    assert.equal((await call("batch.finish", lease)).action, "CONTINUE_CASE");
    await call("step.submit_and_advance", { ...step("second"), requestId: "second-success",
      assertionResult: '[{"actual":"ready"}]' });
    assert.equal((await call("batch.finish", lease)).action, "WAIT_FOR_CASES");
    assert.equal((await call("step.decide", { ...task, testPlanCaseId: "first-relation",
      observation: { kind: "RESUMING" } })).action, "RECHECK_CHECKPOINT");
    await call("step.submit_and_advance", { ...step("first"), requestId: "first-success",
      assertionResult: '[{"actual":"ready"}]' });
    assert.equal((await call("batch.finish", lease)).ok, true);
    assert.equal(completed, true);
  } finally {
    await client.close();
  }
});
