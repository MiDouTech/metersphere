import { z } from "zod";
import type { MeterSphereClient } from "../client.js";
import { executionDecideTool } from "./executionDecide.js";

const id = z.string().min(1).max(128);
const taskSchema = z.object({
  id,
  currentExecutionId: id.nullish(),
  status: z.string(),
  accountedPlanCaseIds: z.array(id).nullish(),
  cases: z.array(z.object({
    caseId: id, caseName: z.string().nullish(), testPlanCaseId: id.nullish(), status: z.string(),
  })),
});
const terminal = new Set(["SUCCESS", "FAILED", "BLOCKED", "SKIPPED", "ERROR",
  "PARTIAL_SUCCESS", "CANCELED", "EXPIRED", "SKIPPED_REVIEW_REQUIRED"]);
const waiting = new Set(["WAITING_HUMAN", "WAITING_LOGIN", "PAUSED", "NEEDS_REVIEW"]);

function decode(content: Array<{ type: string; text?: string }>): unknown {
  const text = content.find(item => item.type === "text")?.text;
  if (!text) return undefined;
  try {
    return JSON.parse(text);
  } catch {
    return undefined;
  }
}

export const executionBatchNextTool = {
  name: "metersphere.execution.batch.next",
  description: "Read an active personal plan task and select its next unfinished case and frozen step. "
    + "Returns local wait reasons and never finalizes the task or reports a test verdict.",
  inputSchema: { taskId: id, executionId: id },
  handler: async (client: MeterSphereClient, args: Record<string, unknown>) => {
    const input = z.object(executionBatchNextTool.inputSchema).parse(args);
    const response = await client.callNativeTool("metersphere.execution.get", { executionTaskId: input.taskId, includeStepProgress: true });
    if (response.isError) return response;
    const task = taskSchema.safeParse(decode(response.content));
    if (!task.success) throw new Error("无法读取当前执行任务和用例");
    const reply = (value: Record<string, unknown>) =>
      ({ content: [{ type: "text" as const, text: JSON.stringify(value) }] });
    const current = task.data;
    if (current.id !== input.taskId || current.currentExecutionId !== input.executionId) {
      return reply({ action: "STOP", reason: "EXECUTION_NOT_CURRENT" });
    }
    if (terminal.has(current.status)) return reply({ action: "TASK_ALREADY_TERMINAL", status: current.status });
    if (!["RUNNING", "PREPARING_BROWSER"].includes(current.status)) {
      return reply({ action: "WAIT_FOR_TASK", status: current.status });
    }
    if (!current.cases.length) return reply({ action: "STOP", reason: "EXECUTION_SCOPE_EMPTY" });
    const pending = [];
    const accounted = new Set(current.accountedPlanCaseIds ?? []);
    for (const entry of current.cases) {
      if (terminal.has(entry.status)) continue;
      const relationId = entry.testPlanCaseId;
      if (relationId && accounted.has(relationId)) continue;
      if (waiting.has(entry.status) || !relationId) {
        pending.push({ ...entry, reason: waiting.has(entry.status) ? entry.status : "PLAN_CASE_RELATION_MISSING" });
        continue;
      }
      const decision = await executionDecideTool.handler(client, {
        ...input, testPlanCaseId: relationId, observation: { kind: "READY" },
      });
      if ("isError" in decision && decision.isError) {
        const failure = z.object({ data: z.object({ code: z.string().optional(),
          traceId: z.string().regex(/^[A-Za-z0-9_-]{1,128}$/).optional() }).optional() })
          .safeParse(decode(decision.content));
        const code = failure.success ? failure.data.data?.code : undefined;
        const traceId = failure.success ? failure.data.data?.traceId : undefined;
        const caseIssues = ["PROTOCOL_ENVIRONMENT_MISMATCH", "STRUCTURED_CONTRACT_STEP_MISMATCH",
          "STRUCTURED_CONTRACT_REVIEW_REQUIRED", "QUALITY_ASSERTION_INVALID", "PROTOCOL_CONTRACT_UNAVAILABLE"];
        pending.push({ ...entry, reason: code && caseIssues.includes(code) ? code : "CASE_CONTEXT_UNAVAILABLE",
          ...(traceId ? { traceId } : {}) });
        continue;
      }
      const next = z.object({ action: z.string(), reason: z.string().optional() }).passthrough()
        .safeParse(decode(decision.content));
      if (!next.success || next.data.action === "STOP" || next.data.action === "WAIT_FOR_STEP") {
        pending.push({ ...entry, reason: next.success ? next.data.reason : "CASE_CONTEXT_UNAVAILABLE" });
        continue;
      }
      if (["SUBMIT_OBSERVATIONS", "CASE_FAILED_RECORDED"].includes(next.data.action)) continue;
      return reply({ action: "CONTINUE_CASE", caseId: entry.caseId, testPlanCaseId: relationId,
        decision: next.data, pending });
    }
    if (pending.length) return reply({ action: "WAIT_FOR_CASES", pending });
    return reply({ action: "REQUEST_FINALIZATION", taskId: input.taskId });
  },
};


export const executionBatchFinishTool = {
  name: "metersphere.execution.batch.finish",
  description: "Recheck the current batch, then request MSP finalization with its active lease. "
    + "MSP evaluates the verdict and writes results; this tool does not declare success.",
  inputSchema: { taskId: id, executionId: id, leaseId: id, leaseToken: z.string().min(1).max(512) },
  handler: async (client: MeterSphereClient, args: Record<string, unknown>) => {
    const input = z.object(executionBatchFinishTool.inputSchema).parse(args);
    const decision = await executionBatchNextTool.handler(client, input);
    if (("isError" in decision && decision.isError)
        || (decode(decision.content) as { action?: string } | undefined)?.action !== "REQUEST_FINALIZATION") {
      return decision;
    }
    return client.callNativeTool("metersphere.execution.complete", input);
  },
};
