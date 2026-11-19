import assert from "node:assert/strict";
import test from "node:test";
import { executionDecideTool } from "../dist/tools/executionDecide.js";

const contract = {
  caseId: "case",
  executionId: "run",
  executionStepIds: { find: "run-find", cancel: "run-cancel" },
  contractVersion: 3,
  contract: {
    steps: [{ id: "find" }, { id: "cancel" }],
    dataRequirements: [{ key: "order", strategies: ["SEARCH", "CREATE"],
      preparationMethodRef: "order:v1", conditions: [{ field: "state", value: "pending" }] }],
  },
};

function useContract(saved, executionId = "run", status = "SUCCESS", redacted = true) {
  const artifacts = ["search-result", "order-state", "actual"].map(id => ({
    id, taskId: "task", executionId, caseId: "case", status: "AVAILABLE", redacted,
  }));
  const client = { async callNativeTool(name, args) {
    if (name === "metersphere.execution.data_preparation.get") {
      assert.deepEqual(args, { taskId: "task", testPlanCaseId: "relation", dataKey: "order" });
      return { content: [{ type: "text", text: JSON.stringify({
        knowledgeId: "order", version: 1, dataKey: "order", preparation: { query: "find pending orders" },
      }) }] };
    }
    if (name === "metersphere.artifact.list") {
      assert.deepEqual(args, { taskId: "task" });
      return { content: [{ type: "text", text: JSON.stringify(artifacts) }] };
    }
    if (name === "metersphere.execution.step.result.get") {
      assert.equal(args.taskId, "task");
      assert.equal(args.executionId, "run");
      assert.equal(args.requestId, "receipt");
      return { content: [{ type: "text", text: JSON.stringify({
        stepId: args.stepId, executionId: "run", status, artifactIds: '["actual"]',
      }) }] };
    }
    assert.equal(name, "metersphere.execution.contract.get");
    assert.deepEqual(args, { taskId: "task", testPlanCaseId: "relation" });
    return { content: [{ type: "text", text: JSON.stringify(saved) }] };
  } };
  return async (stepId, observation) => {
    const result = await executionDecideTool.handler(client, { taskId: "task", executionId: "run", testPlanCaseId: "relation", stepId, observation });
    return JSON.parse(result.content[0].text);
  };
}

test("falls back from a missing candidate to the frozen preparation method", async () => {
  const decide = useContract(contract);
  const missing = { kind: "DATA_MISSING", dataKey: "order", searchExhausted: false,
    preparationAttempted: false, evidenceRef: "search-result" };
  assert.equal((await decide("find", missing)).action, "SEARCH_ALTERNATIVE_DATA");
  const prepared = await decide("find", { ...missing, searchExhausted: true });
  assert.equal(prepared.action, "PREPARE_DATA");
  assert.equal(prepared.requirement.preparationMethodRef, "order:v1");
  assert.equal(prepared.method.preparation.query, "find pending orders");
  assert.equal((await decide("find", { ...missing, searchExhausted: true, preparationAttempted: true })).action,
    "WAIT_FOR_REQUIRED_DATA");
});

test("unknown write must be queried before any retry and resume starts with a recheck", async () => {
  const decide = useContract({ ...contract, verifiedStepIds: ["find"] });
  assert.equal((await decide("cancel", { kind: "WRITE_UNKNOWN", businessKey: "order-17" })).action,
    "QUERY_BUSINESS_STATE");
  const noEffect = { kind: "WRITE_NOT_APPLIED", businessKey: "order-17", queryEvidenceRef: "order-state",
    attempt: 1, conditionChanged: false };
  assert.equal((await decide("cancel", noEffect)).action, "STOP");
  assert.equal((await decide("cancel", { ...noEffect, conditionChanged: true })).reason, "WRITE_RECONCILIATION_REQUIRED");
  assert.equal((await decide("cancel", { ...noEffect, attempt: 2, conditionChanged: true })).action, "STOP");
  assert.deepEqual(await decide("cancel", { kind: "RESUMING" }),
    { contractVersion: 3, action: "RECHECK_CHECKPOINT", stepId: "cancel" });
});

test("advances only within the frozen contract and sends final observations for submission", async () => {
  const firstFinished = useContract({ ...contract, verifiedStepIds: ["find"] });
  const allFinished = useContract({ ...contract, verifiedStepIds: ["find", "cancel"] });
  const verified = { kind: "STEP_VERIFIED", evidenceRef: "actual", requestId: "receipt" };
  assert.equal((await firstFinished("find", verified)).step.id, "cancel");
  assert.equal((await allFinished("cancel", verified)).action, "SUBMIT_OBSERVATIONS");
  assert.equal((await allFinished("find", verified)).action, "SUBMIT_OBSERVATIONS");
  assert.equal((await firstFinished("unlisted", { kind: "READY" })).reason, "STEP_NOT_IN_CONTRACT");
});

test("evidence from an earlier attempt cannot advance the current attempt", async () => {
  const decide = useContract({ ...contract, verifiedStepIds: ["find"] }, "old-run");
  const result = await decide("find", { kind: "STEP_VERIFIED", evidenceRef: "actual", requestId: "receipt" });
  assert.equal(result.action, "STOP");
  assert.equal(result.reason, "EVIDENCE_NOT_AVAILABLE");
});

test("a committed screenshot cannot advance a failed or unverified step receipt", async () => {
  const decide = useContract({ ...contract, verifiedStepIds: ["find"] }, "run", "BLOCKED");
  const result = await decide("find", { kind: "STEP_VERIFIED", evidenceRef: "actual", requestId: "receipt" });
  assert.equal(result.action, "STOP");
  assert.equal(result.reason, "STEP_RESULT_NOT_VERIFIED");
});

test("a caller cannot use an execution identifier from a superseded run", async () => {
  const decide = useContract({ ...contract, executionId: "newer-run" });
  const result = await decide("find", { kind: "READY" });
  assert.equal(result.reason, "EXECUTION_NOT_CURRENT");
});

test("resume and ready use the first incomplete persisted step", async () => {
  const fresh = useContract(contract);
  assert.equal((await fresh("cancel", { kind: "READY" })).reason, "STEP_OUT_OF_ORDER");
  const resumed = useContract({ ...contract, verifiedStepIds: ["find"] });
  assert.equal((await resumed("find", { kind: "RESUMING" })).stepId, "cancel");
  assert.equal((await resumed("cancel", { kind: "READY" })).step.id, "cancel");
});

test("previously completed writes are sent for recheck instead of replay after a new lease", async () => {
  const decide = useContract({ ...contract, previouslyVerifiedStepIds: ["find"], resumedFromExecutionId: "old-run" });
  assert.deepEqual(await decide("find", { kind: "READY" }),
    { contractVersion: 3, action: "RECHECK_CHECKPOINT", stepId: "find", previousExecutionId: "old-run" });
});

test("an unredacted artifact cannot be used to advance the step", async () => {
  const decide = useContract({ ...contract, verifiedStepIds: ["find"] }, "run", "SUCCESS", false);
  const result = await decide("find", { kind: "STEP_VERIFIED", evidenceRef: "actual", requestId: "receipt" });
  assert.equal(result.reason, "EVIDENCE_NOT_AVAILABLE");
});

test("submit and advance saves facts once and uses the read-back receipt on replay", async () => {
  const { submitAndAdvanceTool } = await import("../dist/tools/executionDecide.js");
  let written = false;
  const calls = [];
  const client = { async callNativeTool(name, args) {
    calls.push(name);
    let data;
    switch (name) {
      case "metersphere.execution.contract.get":
        data = { ...contract, verifiedStepIds: written ? ["find"] : [] };
        break;
      case "metersphere.execution.step.submit":
        assert.equal(args.stepId, "run-find");
        assert.equal(args.requestId, "stable");
        assert.equal(args.assertionResult, '[{"actual":"pending"}]');
        assert.deepEqual(args.artifactIds, ["actual"]);
        written = true;
        data = {};
        break;
      case "metersphere.artifact.list":
        data = [{ id: "actual", taskId: "task", executionId: "run", caseId: "case",
          status: "AVAILABLE", redacted: true }];
        break;
      case "metersphere.execution.step.result.get":
        data = { stepId: "run-find", executionId: "run", status: "SUCCESS", artifactIds: '["actual"]' };
        break;
      default:
        assert.fail(name);
    }
    return { content: [{ type: "text", text: JSON.stringify(data) }] };
  } };
  const input = { taskId: "task", executionId: "run", testPlanCaseId: "relation", stepId: "find",
    leaseId: "lease", leaseToken: "fixture", requestId: "stable", evidenceRef: "actual",
    assertionResult: '[{"actual":"pending"}]' };
  const premature = await submitAndAdvanceTool.handler(client, { ...input, stepId: "cancel" });
  assert.equal(JSON.parse(premature.content[0].text).reason, "EXECUTION_STEP_NOT_CURRENT");
  const first = await submitAndAdvanceTool.handler(client, input);
  assert.equal(JSON.parse(first.content[0].text).step.id, "cancel");
  const replay = await submitAndAdvanceTool.handler(client, input);
  assert.deepEqual(replay, first);
  assert.equal(calls.filter(name => name === "metersphere.execution.step.submit").length, 1);
});

test("the agent can start or resume a case without guessing its step identifier", async () => {
  const fresh = useContract(contract);
  assert.equal((await fresh(undefined, { kind: "READY" })).step.id, "find");
  const resumed = useContract({ ...contract, verifiedStepIds: ["find"] });
  assert.equal((await resumed(undefined, { kind: "RESUMING" })).stepId, "cancel");
  assert.equal((await resumed(undefined, { kind: "WRITE_UNKNOWN", businessKey: "order" })).reason,
    "STEP_ID_REQUIRED");
});

test("advancing after a recovered step does not replay the next previously verified write", async () => {
  const resumed = useContract({ ...contract, verifiedStepIds: ["find"],
    previouslyVerifiedStepIds: ["find", "cancel"], resumedFromExecutionId: "old-run" });
  const result = await resumed("find", { kind: "STEP_VERIFIED", evidenceRef: "actual", requestId: "receipt" });
  assert.deepEqual(result, { contractVersion: 3, action: "RECHECK_CHECKPOINT",
    stepId: "cancel", previousExecutionId: "old-run" });
});

test("the stdio execution tool starts from the first unfinished frozen step", async t => {
  const http = await import("node:http");
  const { Client } = await import("@modelcontextprotocol/sdk/client/index.js");
  const { StdioClientTransport } = await import("@modelcontextprotocol/sdk/client/stdio.js");
  const { fileURLToPath } = await import("node:url");
  const calls = [];
  const server = http.createServer(async (req, res) => {
    const chunks = [];
    for await (const chunk of req) chunks.push(chunk);
    const body = JSON.parse(Buffer.concat(chunks).toString());
    calls.push(body.params);
    res.setHeader("Content-Type", "application/json");
    res.end(JSON.stringify({
      jsonrpc: "2.0", id: body.id,
      result: { content: [{ type: "text", text: JSON.stringify({ ...contract, verifiedStepIds: ["find"] }) }] },
    }));
  });
  await new Promise(resolve => server.listen(0, "127.0.0.1", resolve));
  t.after(() => new Promise(resolve => server.close(resolve)));
  const client = new Client({ name: "execution-loop-check", version: "1" });
  const transport = new StdioClientTransport({
    command: process.execPath,
    args: [fileURLToPath(new URL("../dist/index.js", import.meta.url))],
    env: { MS_BASE_URL: "http://127.0.0.1:" + server.address().port,
      MS_AGENT_TOKEN: "fixture", MS_PROJECT_ID: "project" },
  });
  try {
    await client.connect(transport);
    const result = await client.callTool({ name: executionDecideTool.name, arguments: {
      taskId: "task", executionId: "run", testPlanCaseId: "relation", observation: { kind: "READY" },
    } });
    assert.equal(result.isError, undefined);
    assert.deepEqual(JSON.parse(result.content[0].text), {
      contractVersion: 3, action: "EXECUTE_STEP", step: { id: "cancel" },
    });
    assert.deepEqual(calls.map(call => call.name), ["metersphere.execution.contract.get"]);
  } finally {
    await client.close();
  }
});

test("deferring one case records an observed wait without releasing the batch lease", async () => {
  const { deferExecutionStepTool } = await import("../dist/tools/executionDecide.js");
  const calls = [];
  const client = { async callNativeTool(name, args) {
    calls.push({ name, args });
    const data = name === "metersphere.execution.contract.get" ? contract : { status: "BLOCKED" };
    return { content: [{ type: "text", text: JSON.stringify(data) }] };
  } };
  const response = await deferExecutionStepTool.handler(client, {
    taskId: "task", executionId: "run", testPlanCaseId: "relation", stepId: "find",
    leaseId: "lease", leaseToken: "fixture", requestId: "wait-1", reason: "WAITING_HUMAN",
    observedReason: "The test page needs a human confirmation", evidenceRef: "actual",
  });
  assert.equal(JSON.parse(response.content[0].text).status, "BLOCKED");
  assert.deepEqual(calls.map(call => call.name), ["metersphere.execution.contract.get", "metersphere.execution.step.submit"]);
  assert.equal(calls[1].args.errorCode, "WAITING_HUMAN");
  assert.equal(calls[1].args.stepId, "run-find");
});

test("an unknown write stays deferred across start and resume decisions", async () => {
  const decide = useContract({ ...contract, verifiedStepIds: ["find"],
    waitingSteps: { cancel: "WAITING_WRITE_RECONCILIATION" } });
  assert.deepEqual(await decide(undefined, { kind: "READY" }),
    { contractVersion: 3, action: "WAIT_FOR_STEP", reason: "WAITING_WRITE_RECONCILIATION", stepId: "cancel" });
  assert.deepEqual(await decide(undefined, { kind: "RESUMING" }),
    { contractVersion: 3, action: "QUERY_BUSINESS_STATE", reason: "WAITING_WRITE_RECONCILIATION", stepId: "cancel" });
});

test("browser instructions carry the same frozen assertions the server will evaluate", async () => {
  const rules = [{ assertionId: "A1", contractVersion: "v2", operator: "EQUALS",
    expected: "ready", valueType: "STRING" }];
  const decide = useContract({ ...contract, executionAssertions: { find: rules } });
  assert.deepEqual((await decide(undefined, { kind: "READY" })).step.executionAssertions, rules);
});

test("a write with an unknown result is recorded before requesting business lookup", async () => {
  const { deferExecutionStepTool } = await import("../dist/tools/executionDecide.js");
  const calls = [];
  const client = { async callNativeTool(name, args) {
    calls.push({ name, args });
    const data = name === "metersphere.execution.contract.get" ? contract : { status: "BLOCKED" };
    return { content: [{ type: "text", text: JSON.stringify(data) }] };
  } };
  const args = { taskId: "task", executionId: "run", testPlanCaseId: "relation", stepId: "find",
    leaseId: "lease", leaseToken: "fixture", requestId: "unknown-1",
    reason: "WAITING_WRITE_RECONCILIATION", observedReason: "Submit timed out", evidenceRef: "actual" };
  const invalid = await deferExecutionStepTool.handler(client, args);
  assert.equal(JSON.parse(invalid.content[0].text).reason, "BUSINESS_KEY_REQUIRED");
  assert.equal(calls.length, 0);
  const result = await deferExecutionStepTool.handler(client, { ...args, businessKey: "order-17" });
  assert.deepEqual(JSON.parse(result.content[0].text), {
    action: "QUERY_BUSINESS_STATE", stepId: "find", businessKey: "order-17", requestId: "unknown-1",
    executionId: "run", executionStepId: "run-find",
  });
  assert.deepEqual(calls.map(call => call.name), ["metersphere.execution.contract.get", "metersphere.execution.step.submit"]);
  assert.equal(calls[1].args.errorCode, "WAITING_WRITE_RECONCILIATION");
  assert.equal(calls[1].args.businessKey, undefined);
});

test("a retry is issued only once after MSP consumes a signed terminal receipt", async () => {
  const run = { ...contract, verifiedStepIds: ["find"],
    waitingSteps: { cancel: "WAITING_WRITE_RECONCILIATION" },
    contract: { ...contract.contract, steps: [
      { id: "find" }, { id: "cancel", writeRecovery: "SIGNED_TERMINAL_NO_EFFECT_ONCE" },
    ] } };
  let claims = 0;
  const client = { async callNativeTool(name, args) {
    let data;
    if (name === "metersphere.execution.contract.get") {
      data = run;
    } else if (name === "metersphere.artifact.list") {
      data = [{ id: "order-state", taskId: "task", executionId: "run", caseId: "case",
        status: "AVAILABLE", redacted: true }];
    } else {
      assert.equal(name, "metersphere.execution.step.write_retry.claim");
      assert.equal(args.stepId, "cancel");
      assert.equal(args.waitingRequestId, "waiting");
      assert.match(args.businessKeyHash, /^[a-f0-9]{64}$/);
      assert.equal(args.businessKey, undefined);
      data = { granted: ++claims === 1 };
    }
    return { content: [{ type: "text", text: JSON.stringify(data) }] };
  } };
  const call = async observation => JSON.parse((await executionDecideTool.handler(client, {
    taskId: "task", executionId: "run", testPlanCaseId: "relation", stepId: "cancel", observation,
  })).content[0].text);
  const observed = { kind: "WRITE_NOT_APPLIED", businessKey: "order-17", queryEvidenceRef: "order-state",
    attempt: 1, conditionChanged: true };
  assert.equal((await call(observed)).reason, "WRITE_RECONCILIATION_REQUIRED");
  const recovery = { leaseId: "lease", leaseToken: "token", waitingExecutionId: "prior",
    waitingRequestId: "waiting", proof: "signed-receipt" };
  assert.equal((await call({ ...observed, conditionChanged: false, recovery })).reason, "RETRY_CONDITIONS_NOT_MET");
  assert.equal((await call({ ...observed, recovery })).action, "RETRY_WRITE_ONCE");
  assert.equal((await call({ ...observed, recovery })).reason, "WRITE_RETRY_UNAVAILABLE");
  assert.equal((await call({ kind: "READY" })).action, "WAIT_FOR_STEP");
  assert.equal(claims, 2);
});

test("a failed observation advances only after a matching failed receipt is stored", async () => {
  const run = { ...contract, failedStepIds: ["find"] };
  const observation = { kind: "STEP_FAILED", evidenceRef: "actual", requestId: "receipt" };
  assert.equal((await useContract(run, "run", "FAILED")("find", observation)).action, "CASE_FAILED_RECORDED");
  assert.equal((await useContract(run, "run", "SUCCESS")("find", observation)).reason, "STEP_RESULT_NOT_VERIFIED");
  assert.equal((await useContract(contract, "run", "FAILED")("find", observation)).reason, "STEP_RESULT_NOT_VERIFIED");
});

test("an evidence upload failure resumes the upload without replaying the browser action", async () => {
  const { deferExecutionStepTool } = await import("../dist/tools/executionDecide.js");
  const calls = [];
  const client = { async callNativeTool(name, args) {
    calls.push({ name, args });
    const data = name === "metersphere.execution.contract.get" ? contract : { status: "BLOCKED" };
    return { content: [{ type: "text", text: JSON.stringify(data) }] };
  } };
  const result = await deferExecutionStepTool.handler(client, {
    taskId: "task", executionId: "run", testPlanCaseId: "relation", stepId: "find",
    leaseId: "lease", leaseToken: "fixture", requestId: "evidence-later",
    reason: "WAITING_EVIDENCE", observedReason: "Upload timed out",
  });
  assert.equal(JSON.parse(result.content[0].text).status, "BLOCKED");
  assert.deepEqual(calls[1].args.artifactIds, []);
  const decide = useContract({ ...contract, waitingSteps: { find: "WAITING_EVIDENCE" } });
  assert.equal((await decide("find", { kind: "READY" })).action, "WAIT_FOR_STEP");
  assert.equal((await decide("find", { kind: "RESUMING" })).action, "RETRY_EVIDENCE_UPLOAD");
});
