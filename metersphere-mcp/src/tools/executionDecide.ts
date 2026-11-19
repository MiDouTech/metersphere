import { createHash } from "node:crypto";
import { z } from "zod";
import type { MeterSphereClient } from "../client.js";

const identifier = z.string().min(1).max(128);
function parseStoredJson(text: string): unknown {
  try {
    return JSON.parse(text);
  } catch {
    return undefined;
  }
}

const observation = z.discriminatedUnion("kind", [
  z.object({ kind: z.literal("READY") }),
  z.object({ kind: z.literal("RESUMING") }),
  z.object({ kind: z.literal("STEP_VERIFIED"), evidenceRef: identifier, requestId: identifier }),
  z.object({ kind: z.literal("STEP_FAILED"), evidenceRef: identifier, requestId: identifier }),
  z.object({ kind: z.literal("DATA_MISSING"), dataKey: identifier, searchExhausted: z.boolean(),
    preparationAttempted: z.boolean(), evidenceRef: identifier }),
  z.object({ kind: z.literal("WRITE_UNKNOWN"), businessKey: identifier }),
  z.object({ kind: z.literal("WRITE_NOT_APPLIED"), businessKey: identifier,
    queryEvidenceRef: identifier, attempt: z.number().int().min(1),
    conditionChanged: z.boolean(), recovery: z.object({
      leaseId: identifier, leaseToken: z.string().min(1).max(512),
      waitingExecutionId: identifier, waitingRequestId: identifier,
      proof: z.string().min(1).max(256),
    }).optional() }),
]);

const frozen = z.object({
  caseId: identifier,
  executionId: identifier.nullish(),
  executionStepIds: z.record(identifier).default({}),
  executionAssertions: z.record(z.array(z.unknown())).default({}),
  verifiedStepIds: z.array(identifier).default([]),
  failedStepIds: z.array(identifier).default([]),
  waitingSteps: z.record(z.enum(["WAITING_HUMAN", "WAITING_DATA", "WAITING_WRITE_RECONCILIATION", "WAITING_EVIDENCE"])).default({}),
  writeRecoveryReceipts: z.record(z.object({
    executionId: identifier, requestId: identifier, executionStepId: identifier, retryConsumed: z.boolean(),
  })).default({}),
  previouslyVerifiedStepIds: z.array(identifier).default([]),
  resumedFromExecutionId: identifier.optional(),
  contractVersion: z.number().int().nonnegative(),
  contract: z.object({
    steps: z.array(z.object({ id: identifier }).passthrough()).min(1),
    dataRequirements: z.array(z.object({
      key: identifier,
      strategies: z.array(z.enum(["SEARCH", "CREATE"])),
      preparationMethodRef: identifier,
    }).passthrough()).default([]),
  }),
});

export function decideExecutionAction(contract: z.infer<typeof frozen>["contract"],
                                      stepId: string, event: z.infer<typeof observation>) {
  const index = contract.steps.findIndex(step => step.id === stepId);
  const step = contract.steps[index];
  if (!step) return { action: "STOP", reason: "STEP_NOT_IN_CONTRACT" };
  switch (event.kind) {
    case "READY":
      return { action: "EXECUTE_STEP", step };
    case "RESUMING":
      return { action: "RECHECK_CHECKPOINT", stepId };
    case "STEP_VERIFIED": {
      const next = contract.steps[index + 1];
      return next ? { action: "EXECUTE_STEP", step: next } : { action: "SUBMIT_OBSERVATIONS" };
    }
    case "STEP_FAILED":
      return { action: "CASE_FAILED_RECORDED", stepId };
    case "DATA_MISSING": {
      const requirement = contract.dataRequirements.find(item => item.key === event.dataKey);
      if (!requirement) return { action: "STOP", reason: "DATA_RULE_MISSING" };
      if (!event.searchExhausted && requirement.strategies.includes("SEARCH")) {
        return { action: "SEARCH_ALTERNATIVE_DATA", requirement };
      }
      if (!event.preparationAttempted && requirement.strategies.includes("CREATE")) {
        return { action: "PREPARE_DATA", requirement };
      }
      return { action: "WAIT_FOR_REQUIRED_DATA", dataKey: requirement.key, evidenceRef: event.evidenceRef };
    }
    case "WRITE_UNKNOWN":
      return { action: "QUERY_BUSINESS_STATE", businessKey: event.businessKey };
    case "WRITE_NOT_APPLIED":
      return { action: "STOP", reason: event.attempt === 1 && event.conditionChanged
        ? "WRITE_RECONCILIATION_REQUIRED" : "RETRY_CONDITIONS_NOT_MET", businessKey: event.businessKey };
  }
}

export const executionDecideTool = {
  name: "metersphere.execution.step.decide",
  description: "Decide the next browser action using the task's frozen JSON and a fresh browser observation. "
    + "Call before a step and after observing its result. Persist actual facts and checkpoints with the execution tools.",
  inputSchema: { taskId: identifier, executionId: identifier, testPlanCaseId: identifier, stepId: identifier.optional(), observation },
  handler: async (client: MeterSphereClient, args: Record<string, unknown>) => {
    const input = z.object(executionDecideTool.inputSchema).parse(args);
    const result = await client.callNativeTool("metersphere.execution.contract.get", {
      taskId: input.taskId, testPlanCaseId: input.testPlanCaseId,
    });
    if (result.isError) return result;
    const content = result.content.find(item => item.type === "text");
    if (!content || content.type !== "text") throw new Error("未取得任务冻结的执行契约");
    const parsed = frozen.safeParse(parseStoredJson(content.text));
    if (!parsed.success) throw new Error("任务冻结的执行契约缺少可执行步骤或数据规则");
    const respond = (decision: Record<string, unknown>) => ({
      content: [{ type: "text" as const, text: JSON.stringify({
        contractVersion: parsed.data.contractVersion, ...decision,
      }) }],
    });
    if (parsed.data.executionId !== input.executionId) {
      return respond({ action: "STOP", reason: "EXECUTION_NOT_CURRENT" });
    }
    const event = input.observation;
    const pending = parsed.data.contract.steps.find(step => !parsed.data.verifiedStepIds.includes(step.id));
    const actionable = (step: NonNullable<typeof pending>) => ({
      ...step,
      ...(parsed.data.executionAssertions[step.id] ? { executionAssertions: parsed.data.executionAssertions[step.id] } : {}),
    });
    if (input.stepId && !parsed.data.contract.steps.some(step => step.id === input.stepId)) {
      return respond({ action: "STOP", reason: "STEP_NOT_IN_CONTRACT" });
    }
    if (event.kind === "READY" || event.kind === "RESUMING") {
      const failedStep = parsed.data.failedStepIds[0];
      if (failedStep) return respond({ action: "CASE_FAILED_RECORDED", stepId: failedStep });
      if (!pending) return respond({ action: "SUBMIT_OBSERVATIONS" });
      const waitReason = parsed.data.waitingSteps[pending.id];
      if (waitReason === "WAITING_WRITE_RECONCILIATION") {
        return event.kind === "RESUMING"
          ? respond({ action: "QUERY_BUSINESS_STATE", reason: waitReason, stepId: pending.id,
            recoveryReceipt: parsed.data.writeRecoveryReceipts[pending.id] })
          : respond({ action: "WAIT_FOR_STEP", reason: waitReason, stepId: pending.id });
      }
      if (waitReason === "WAITING_EVIDENCE") {
        return respond({ action: event.kind === "RESUMING" ? "RETRY_EVIDENCE_UPLOAD" : "WAIT_FOR_STEP",
          reason: waitReason, stepId: pending.id });
      }
      if (event.kind === "RESUMING" || parsed.data.previouslyVerifiedStepIds.includes(pending.id)) {
        return respond({ action: "RECHECK_CHECKPOINT", stepId: pending.id,
          previousExecutionId: parsed.data.resumedFromExecutionId });
      }
      if (waitReason) return respond({ action: "WAIT_FOR_STEP", reason: waitReason, stepId: pending.id });
      if (input.stepId && pending.id !== input.stepId) {
        return respond({ action: "STOP", reason: "STEP_OUT_OF_ORDER", expectedStepId: pending.id });
      }
      return respond({ action: "EXECUTE_STEP", step: actionable(pending) });
    }

    const stepId = input.stepId;
    if (!stepId) return respond({ action: "STOP", reason: "STEP_ID_REQUIRED" });
    const stepIndex = parsed.data.contract.steps.findIndex(step => step.id === stepId);
    if (event.kind === "STEP_FAILED") {
      if (!parsed.data.failedStepIds.includes(stepId) || pending?.id !== stepId) {
        return respond({ action: "STOP", reason: "STEP_RESULT_NOT_VERIFIED" });
      }
    } else if (event.kind === "STEP_VERIFIED") {
      if (parsed.data.contract.steps.slice(0, stepIndex + 1)
          .some(step => !parsed.data.verifiedStepIds.includes(step.id))) {
        return respond({ action: "STOP", reason: "STEP_RESULT_NOT_VERIFIED" });
      }
    } else if (pending?.id !== stepId) {
      return respond({ action: "STOP", reason: "STEP_OUT_OF_ORDER", expectedStepId: pending?.id });
    }
    const evidenceRef = "evidenceRef" in event ? event.evidenceRef
      : "queryEvidenceRef" in event ? event.queryEvidenceRef : undefined;
    if (evidenceRef) {
      const evidence = await client.callNativeTool("metersphere.artifact.list", { taskId: input.taskId });
      if (evidence.isError) return evidence;
      const metadata = evidence.content.find(item => item.type === "text");
      const artifactSchema = z.object({ id: identifier, taskId: identifier, executionId: identifier.nullish(),
        caseId: identifier.nullish(), status: z.string(), redacted: z.boolean().nullish() });
      const artifacts = metadata?.type === "text"
        ? z.array(artifactSchema).safeParse(parseStoredJson(metadata.text)) : undefined;
      const matched = artifacts?.success && artifacts.data.some(item => item.id === evidenceRef
        && item.taskId === input.taskId && item.executionId === input.executionId
        && item.caseId === parsed.data.caseId && item.status === "AVAILABLE" && item.redacted === true);
      if (!matched) {
        return respond({ action: "STOP", reason: "EVIDENCE_NOT_AVAILABLE" });
      }
    }
    if (event.kind === "STEP_VERIFIED" || event.kind === "STEP_FAILED") {
      const executionStepId = parsed.data.executionStepIds[stepId];
      if (!executionStepId) return respond({ action: "STOP", reason: "EXECUTION_STEP_NOT_MAPPED" });
      const persisted = await client.callNativeTool("metersphere.execution.step.result.get", {
        taskId: input.taskId, executionId: input.executionId, stepId: executionStepId, requestId: event.requestId,
      });
      if (persisted.isError) return persisted;
      const receipt = persisted.content.find(item => item.type === "text");
      const receiptSchema = z.object({ stepId: identifier, executionId: identifier, status: z.string(),
        artifactIds: z.string().nullable() });
      const saved = receipt?.type === "text" ? receiptSchema.safeParse(parseStoredJson(receipt.text)) : undefined;
      const linked = saved?.success && saved.data.artifactIds
        ? z.array(identifier).safeParse(parseStoredJson(saved.data.artifactIds)) : undefined;
      const expectedStatus = event.kind === "STEP_FAILED" ? "FAILED" : "SUCCESS";
      if (!saved?.success || saved.data.status !== expectedStatus || saved.data.stepId !== executionStepId
          || saved.data.executionId !== input.executionId || !linked?.success || !linked.data.includes(event.evidenceRef)) {
        return respond({ action: "STOP", reason: "STEP_RESULT_NOT_VERIFIED" });
      }
    }
    if (event.kind === "STEP_VERIFIED") {
      if (!pending) return respond({ action: "SUBMIT_OBSERVATIONS" });
      const waitReason = parsed.data.waitingSteps[pending.id];
      if (waitReason) return respond({ action: "WAIT_FOR_STEP", reason: waitReason, stepId: pending.id });
      if (parsed.data.previouslyVerifiedStepIds.includes(pending.id)) {
        return respond({ action: "RECHECK_CHECKPOINT", stepId: pending.id,
          previousExecutionId: parsed.data.resumedFromExecutionId });
      }
      return respond({ action: "EXECUTE_STEP", step: actionable(pending) });
    }
    const next = decideExecutionAction(parsed.data.contract, stepId, event);
    if (event.kind === "WRITE_NOT_APPLIED" && event.recovery && pending
        && next.reason === "WRITE_RECONCILIATION_REQUIRED"
        && pending.writeRecovery === "SIGNED_TERMINAL_NO_EFFECT_ONCE"
        && parsed.data.waitingSteps[stepId] === "WAITING_WRITE_RECONCILIATION") {
      const claim = await client.callNativeTool("metersphere.execution.step.write_retry.claim", {
        taskId: input.taskId, executionId: input.executionId, testPlanCaseId: input.testPlanCaseId,
        stepId, businessKeyHash: createHash("sha256").update(event.businessKey).digest("hex"),
        ...event.recovery,
      });
      if (claim.isError) return claim;
      const entry = claim.content.find(item => item.type === "text");
      const receipt = z.object({ granted: z.boolean() }).safeParse(
        entry?.type === "text" ? parseStoredJson(entry.text) : undefined,
      );
      return receipt.success && receipt.data.granted
        ? respond({ action: "RETRY_WRITE_ONCE", step: actionable(pending), businessKey: event.businessKey })
        : respond({ action: "STOP", reason: "WRITE_RETRY_UNAVAILABLE" });
    }
    if (event.kind === "DATA_MISSING"
        && (next.action === "SEARCH_ALTERNATIVE_DATA" || next.action === "PREPARE_DATA")) {
      const preparation = await client.callNativeTool("metersphere.execution.data_preparation.get", {
        taskId: input.taskId, testPlanCaseId: input.testPlanCaseId, dataKey: event.dataKey,
      });
      if (preparation.isError) return preparation;
      const details = preparation.content.find(item => item.type === "text");
      const method = details?.type === "text" ? parseStoredJson(details.text) : undefined;
      if (!method || typeof method !== "object") {
        return respond({ action: "STOP", reason: "PREPARATION_METHOD_UNAVAILABLE" });
      }
      return respond({ ...next, method });
    }
    return respond(next);
  },
};

export const submitAndAdvanceTool = {
  name: "metersphere.execution.step.submit_and_advance",
  description: "Save actual observations for the current frozen step, then read the server receipt and decide what to do next. "
    + "Supply observed assertion facts and a committed evidence ID. After a lost response, reuse the same request ID and facts.",
  inputSchema: {
    taskId: identifier, executionId: identifier, testPlanCaseId: identifier, stepId: identifier,
    leaseId: identifier, leaseToken: z.string().min(1).max(512),
    requestId: z.string().regex(/^[A-Za-z0-9_.:-]{1,128}$/),
    assertionResult: z.string().min(1), evidenceRef: identifier,
    status: z.enum(["SUCCESS", "FAILED"]).default("SUCCESS"),
    usedKnowledgeRefs: z.array(z.string().regex(/^[A-Za-z0-9_.:-]{1,128}:v[1-9][0-9]{0,17}$/)).max(32).optional(),
  },
  handler: async (client: MeterSphereClient, args: Record<string, unknown>) => {
    const input = z.object(submitAndAdvanceTool.inputSchema).parse(args);
    const result = await client.callNativeTool("metersphere.execution.contract.get", {
      taskId: input.taskId, testPlanCaseId: input.testPlanCaseId,
    });
    if (result.isError) return result;
    const entry = result.content.find(item => item.type === "text");
    const parsed = frozen.safeParse(entry?.type === "text" ? parseStoredJson(entry.text) : undefined);
    if (!parsed.success) throw new Error("未取得任务冻结的执行契约");
    const run = parsed.data;
    const runtimeStep = run.executionStepIds[input.stepId];
    const pending = run.contract.steps.find(step => !run.verifiedStepIds.includes(step.id));
    const alreadyVerified = (input.status === "SUCCESS" ? run.verifiedStepIds : run.failedStepIds).includes(input.stepId);
    const differentVerdict = (input.status === "SUCCESS" ? run.failedStepIds : run.verifiedStepIds).includes(input.stepId);
    if (run.executionId !== input.executionId || !runtimeStep || differentVerdict || (!alreadyVerified && pending?.id !== input.stepId)) {
      return { content: [{ type: "text" as const, text: JSON.stringify({
        action: "STOP", reason: "EXECUTION_STEP_NOT_CURRENT",
      }) }] };
    }
    if (!alreadyVerified) {
      const saved = await client.callNativeTool("metersphere.execution.step.submit", {
        taskId: input.taskId, executionId: input.executionId, stepId: runtimeStep,
        leaseId: input.leaseId, leaseToken: input.leaseToken, requestId: input.requestId,
        status: input.status, assertionResult: input.assertionResult, artifactIds: [input.evidenceRef],
        ...(input.usedKnowledgeRefs?.length
          ? { testPlanCaseId: input.testPlanCaseId, usedKnowledgeRefs: input.usedKnowledgeRefs } : {}),
      });
      if (saved.isError) return saved;
    }
    return executionDecideTool.handler(client, {
      taskId: input.taskId, executionId: input.executionId, testPlanCaseId: input.testPlanCaseId,
      stepId: input.stepId, observation: { kind: input.status === "FAILED" ? "STEP_FAILED" : "STEP_VERIFIED",
        requestId: input.requestId, evidenceRef: input.evidenceRef },
    });
  },
};

export const deferExecutionStepTool = {
  name: "metersphere.execution.step.defer",
  description: "Persist a single case's waiting reason without releasing the batch lease. "
    + "Use for a human dependency, exhausted data preparation, unknown write, or failed evidence upload. Then ask batch.next for another case.",
  inputSchema: {
    taskId: identifier, executionId: identifier, testPlanCaseId: identifier, stepId: identifier,
    leaseId: identifier, leaseToken: z.string().min(1).max(512),
    requestId: z.string().regex(/^[A-Za-z0-9_.:-]{1,128}$/),
    reason: z.enum(["WAITING_HUMAN", "WAITING_DATA", "WAITING_WRITE_RECONCILIATION", "WAITING_EVIDENCE"]),
    observedReason: z.string().trim().min(1).max(1000), evidenceRef: identifier.optional(),
    businessKey: identifier.optional(),
  },
  handler: async (client: MeterSphereClient, args: Record<string, unknown>) => {
    const input = z.object(deferExecutionStepTool.inputSchema).parse(args);
    const unknownWrite = input.reason === "WAITING_WRITE_RECONCILIATION";
    if (!input.evidenceRef && input.reason !== "WAITING_EVIDENCE" && !unknownWrite) {
      return { content: [{ type: "text" as const, text: JSON.stringify({
        action: "STOP", reason: "EVIDENCE_REQUIRED",
      }) }] };
    }
    if (unknownWrite && !input.businessKey) {
      return { content: [{ type: "text" as const, text: JSON.stringify({
        action: "STOP", reason: "BUSINESS_KEY_REQUIRED",
      }) }] };
    }
    const response = await client.callNativeTool("metersphere.execution.contract.get", {
      taskId: input.taskId, testPlanCaseId: input.testPlanCaseId,
    });
    if (response.isError) return response;
    const entry = response.content.find(item => item.type === "text");
    const parsed = frozen.safeParse(entry?.type === "text" ? parseStoredJson(entry.text) : undefined);
    if (!parsed.success) throw new Error("未取得任务冻结的执行契约");
    const runtimeStep = parsed.data.executionStepIds[input.stepId];
    const pending = parsed.data.contract.steps.find(step => !parsed.data.verifiedStepIds.includes(step.id));
    if (parsed.data.executionId !== input.executionId || !runtimeStep || pending?.id !== input.stepId) {
      return { content: [{ type: "text" as const, text: JSON.stringify({
        action: "STOP", reason: "EXECUTION_STEP_NOT_CURRENT",
      }) }] };
    }
    const saved = await client.callNativeTool("metersphere.execution.step.submit", {
      ...(input.businessKey && unknownWrite ? { inputSnapshot: createHash("sha256").update(input.businessKey).digest("hex") } : {}),
      taskId: input.taskId, executionId: input.executionId, stepId: runtimeStep,
      leaseId: input.leaseId, leaseToken: input.leaseToken, requestId: input.requestId,
      status: "BLOCKED", errorCode: input.reason, errorMessage: input.observedReason,
      artifactIds: input.evidenceRef ? [input.evidenceRef] : [],
    });
    if (saved.isError || !unknownWrite) return saved;
    return { content: [{ type: "text" as const, text: JSON.stringify({
      action: "QUERY_BUSINESS_STATE", stepId: input.stepId, businessKey: input.businessKey,
      requestId: input.requestId, executionId: input.executionId, executionStepId: runtimeStep,
    }) }] };
  },
};
