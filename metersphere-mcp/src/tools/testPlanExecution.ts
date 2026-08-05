import { z } from "zod";
import type { MeterSphereClient } from "../client.js";

export const searchTestPlansTool = {
  name: "metersphere.test_plan.search",
  description: "Search MeterSphere test plans by project, keyword, status, and pagination.",
  inputSchema: {
    projectId: z.string().optional(),
    keyword: z.string().optional(),
    status: z.string().optional(),
    includeArchived: z.boolean().optional(),
    page: z.number().int().min(1).optional(),
    pageSize: z.number().int().min(1).max(100).optional(),
  },
  handler: async (client: MeterSphereClient, args: Record<string, unknown>) => {
    const result = await client.searchTestPlans(args as Parameters<MeterSphereClient["searchTestPlans"]>[0]);
    return { content: [{ type: "text" as const, text: JSON.stringify(result, null, 2) }] };
  },
};

export const listTestPlanCasesTool = {
  name: "metersphere.test_plan.cases",
  description: "List functional cases associated with a MeterSphere test plan. Returns testPlanCaseId for writeback.",
  inputSchema: {
    projectId: z.string().optional(),
    testPlanId: z.string(),
    current: z.number().int().min(1).optional(),
    pageSize: z.number().int().min(1).max(100).optional(),
    includeSteps: z.boolean().optional(),
  },
  handler: async (client: MeterSphereClient, args: Record<string, unknown>) => {
    const result = await client.getTestPlanCases(args as Parameters<MeterSphereClient["getTestPlanCases"]>[0]);
    return { content: [{ type: "text" as const, text: JSON.stringify(result, null, 2) }] };
  },
};

export const resolveExecutionScopeTool = {
  name: "metersphere.execution.resolve",
  description: "Resolve AI execution scope before task creation. Use this when project, plan, or case scope is ambiguous.",
  inputSchema: {
    projectId: z.string().optional(),
    query: z.string().optional(),
    testPlanId: z.string().optional(),
    testPlanName: z.string().optional(),
    caseIds: z.array(z.string()).optional(),
    caseKeyword: z.string().optional(),
    threshold: z.number().int().min(1).max(100).optional(),
  },
  handler: async (client: MeterSphereClient, args: Record<string, unknown>) => {
    const result = await client.resolveExecutionScope(args);
    return { content: [{ type: "text" as const, text: JSON.stringify(result, null, 2) }] };
  },
};

export const createExecutionTaskTool = {
  name: "metersphere.execution.create",
  description:
    "Create a MeterSphere AI execution task. Backend revalidates project, test plan, case IDs, permissions, and confirmation constraints.",
  inputSchema: {
    projectId: z.string().optional(),
    testPlanId: z.string().optional(),
    caseIds: z.array(z.string()).optional(),
    source: z.string().optional(),
    environmentId: z.string().optional(),
    targetUrl: z.string().optional(),
    browserType: z.string().optional(),
    loginMode: z.string().optional(),
    providerId: z.string().optional(),
    runnerId: z.string().optional(),
    executedBy: z.string().optional(),
    idempotencyKey: z.string().optional(),
    confirmed: z.boolean().optional(),
  },
  handler: async (client: MeterSphereClient, args: Record<string, unknown>) => {
    const result = await client.createExecutionTask(args as Parameters<MeterSphereClient["createExecutionTask"]>[0]);
    return { content: [{ type: "text" as const, text: JSON.stringify(result, null, 2) }] };
  },
};

export const getExecutionTaskTool = {
  name: "metersphere.execution.get",
  description: "Get MeterSphere AI execution task status, counts, and fixed case scope.",
  inputSchema: {
    executionTaskId: z.string(),
  },
  handler: async (client: MeterSphereClient, args: Record<string, unknown>) => {
    const result = await client.getExecutionTask(args.executionTaskId as string);
    return { content: [{ type: "text" as const, text: JSON.stringify(result, null, 2) }] };
  },
};

export const getExecutionEventsTool = {
  name: "metersphere.execution.events",
  description: "Read append-only MeterSphere AI execution task events.",
  inputSchema: {
    executionTaskId: z.string(),
    cursor: z.number().int().min(0).optional(),
    limit: z.number().int().min(1).max(500).optional(),
  },
  handler: async (client: MeterSphereClient, args: Record<string, unknown>) => {
    const result = await client.getExecutionEvents(args as Parameters<MeterSphereClient["getExecutionEvents"]>[0]);
    return { content: [{ type: "text" as const, text: JSON.stringify(result, null, 2) }] };
  },
};

export const cancelExecutionTaskTool = {
  name: "metersphere.execution.cancel",
  description: "Cancel a MeterSphere AI execution task without marking unexecuted cases as successful.",
  inputSchema: {
    executionTaskId: z.string(),
    reason: z.string().optional(),
  },
  handler: async (client: MeterSphereClient, args: Record<string, unknown>) => {
    const result = await client.cancelExecutionTask(args as Parameters<MeterSphereClient["cancelExecutionTask"]>[0]);
    return { content: [{ type: "text" as const, text: JSON.stringify(result, null, 2) }] };
  },
};

export const resumeExecutionTaskTool = {
  name: "metersphere.execution.resume",
  description: "Resume a MeterSphere AI execution task after manual login/session recovery.",
  inputSchema: {
    executionTaskId: z.string(),
    reason: z.string().optional(),
  },
  handler: async (client: MeterSphereClient, args: Record<string, unknown>) => {
    const result = await client.resumeExecutionTask(args as Parameters<MeterSphereClient["resumeExecutionTask"]>[0]);
    return { content: [{ type: "text" as const, text: JSON.stringify(result, null, 2) }] };
  },
};
