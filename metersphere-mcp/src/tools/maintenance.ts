import { z } from "zod";
import type { MeterSphereClient } from "../client.js";
import { submitFunctionalResultsBatchInputSchema } from "./submitFunctionalResultsBatch.js";

const id = z.string().min(1).max(128);
const forward = (name: string) => (client: MeterSphereClient, args: Record<string, unknown>) =>
  client.callNativeTool(name, args);

export const nativeBatchSubmitTool = {
  name: "metersphere.functional.submit.batch",
  description: "Batch result writeback through native MCP with per-item results and requestId idempotency.",
  inputSchema: { ...submitFunctionalResultsBatchInputSchema, projectId: id, requestId: id, executionTaskId: id.optional(),
    results: z.array(submitFunctionalResultsBatchInputSchema.results.element.extend({
      projectId: id.optional(), testPlanId: id.optional(), executedBy: z.string().optional(),
      executionTaskId: id.optional(), idempotencyKey: id.optional(),
    }).strict()).min(1).max(100) },
  handler: forward("metersphere.functional.submit.batch"),
};
export const executionHistoryTool = {
  name: "metersphere.execution.search",
  description: "Search personal execution history including completed and failed tasks. Time bounds are inclusive epoch milliseconds.",
  inputSchema: { projectId: id, keyword: z.string().max(255).optional(), status: z.string().max(40).optional(),
    createdAfter: z.number().int().nonnegative().optional(), createdBefore: z.number().int().nonnegative().optional(),
    current: z.number().int().min(1).max(1000000).optional(), pageSize: z.number().int().min(1).max(100).optional() },
  handler: forward("metersphere.execution.search"),
};
export const executionPreflightTool = {
  name: "metersphere.execution.preflight",
  description: "Run the environment and capability preflight required before checkpoint resume.",
  inputSchema: { projectId: id, environmentProfileId: id, runnerType: id, requestId: id,
    caseIds: z.array(id).max(100).optional(), testPlanId: id.optional(), credentialReferenceId: id.optional(),
    requiredCapabilities: z.array(z.string()).max(32).optional() },
  handler: forward("metersphere.execution.preflight"),
};
export const checkpointResumeTool = {
  name: "metersphere.execution.checkpoint.resume",
  description: "Resume a personal checkpoint with its one-time token and a fresh passed preflight.",
  inputSchema: { taskId: id, checkpointId: id, resumeToken: z.string().min(40).max(128), preflightId: id, requestId: id },
  handler: forward("metersphere.execution.checkpoint.resume"),
};
export const bugTransitionsTool = {
  name: "metersphere.bug.transitions",
  description: "Read permitted bug workflow transitions and the concurrency version.",
  inputSchema: { projectId: id, bugId: id },
  handler: forward("metersphere.bug.transitions"),
};
export const bugTransitionTool = {
  name: "metersphere.bug.transition",
  description: "Apply an allowed bug workflow transition using its current version.",
  inputSchema: { projectId: id, bugId: id, requestId: id, transition: z.object({ transitionId: id, targetStatusId: id,
    expectedUpdateTime: z.number().int(), comment: z.string().optional(), override: z.boolean().optional(), overrideReason: z.string().optional() }).strict() },
  handler: forward("metersphere.bug.transition"),
};
export const testPlanUpdateTool = {
  name: "metersphere.test_plan.update",
  description: "Patch plan name, description, tags, schedule and execution configuration; omitted fields are preserved. Use updateTime from test_plan.get.",
  inputSchema: { projectId: id, testPlanId: id, requestId: id, expectedUpdateTime: z.number().int(), patch: z.object({
    name: z.string().min(1).max(255).optional(), description: z.string().optional(), tags: z.array(z.string()).optional(),
    plannedStartTime: z.number().int().nonnegative().optional(), plannedEndTime: z.number().int().nonnegative().optional(),
    automaticStatusUpdate: z.boolean().optional(), repeatCase: z.boolean().optional(), passThreshold: z.number().min(0).max(100).optional(),
  }).strict().refine(value => Object.keys(value).length > 0) },
  handler: forward("metersphere.test_plan.update"),
};
export const testPlanDisassociateTool = {
  name: "metersphere.test_plan.disassociate_cases",
  description: "Remove plan associations using testPlanCaseId values (not repository caseIds), at most 100.",
  inputSchema: { projectId: id, testPlanId: id, requestId: id, associationIds: z.array(id).min(1).max(100) },
  handler: forward("metersphere.test_plan.disassociate_cases"),
};
export const testPlanGetTool = {
  name: "metersphere.test_plan.get",
  description: "Get a test plan including updateTime for optimistic edits.",
  inputSchema: { testPlanId: id },
  handler: forward("metersphere.test_plan.get"),
};
