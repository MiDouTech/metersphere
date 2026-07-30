import { z } from "zod";
import type { MeterSphereClient } from "../client.js";

export const searchBugsInputSchema = {
  projectId: z.string().optional(),
  query: z.string().optional().describe("Keyword: title / num / tags"),
  status: z.array(z.string()).optional().describe("Bug status values"),
  handleUserIds: z.array(z.string()).optional(),
  current: z.number().int().min(1).optional(),
  pageSize: z.number().int().min(1).max(500).optional(),
};

export const searchBugsTool = {
  name: "search_bugs",
  description: "Search defects in a project by keyword, status, or handlers.",
  inputSchema: searchBugsInputSchema,
  handler: async (client: MeterSphereClient, args: Record<string, unknown>) => {
    const result = await client.searchBugs(args as Parameters<MeterSphereClient["searchBugs"]>[0]);
    return { content: [{ type: "text" as const, text: JSON.stringify(result, null, 2) }] };
  },
};

export const getBugInputSchema = {
  bugId: z.string().describe("Bug ID"),
};

export const getBugTool = {
  name: "get_bug",
  description: "Get defect detail including description, tags, and custom fields.",
  inputSchema: getBugInputSchema,
  handler: async (client: MeterSphereClient, args: Record<string, unknown>) => {
    const result = await client.getBug(String(args.bugId));
    return { content: [{ type: "text" as const, text: JSON.stringify(result, null, 2) }] };
  },
};

export const createBugInputSchema = {
  projectId: z.string().optional(),
  title: z.string(),
  description: z.string().optional(),
  templateId: z.string().optional(),
  tags: z.array(z.string()).optional(),
  caseId: z.string().optional().describe("Relate functional case on create"),
  caseType: z.string().optional().describe("Default FUNCTIONAL"),
  testPlanId: z.string().optional(),
  testPlanCaseId: z.string().optional(),
  customFields: z.record(z.string()).optional().describe("fieldId -> value for required template fields"),
};

export const createBugTool = {
  name: "create_bug",
  description:
    "Create a defect; optionally relate a failed functional case (caseId). Use after ERROR execution submit.",
  inputSchema: createBugInputSchema,
  handler: async (client: MeterSphereClient, args: Record<string, unknown>) => {
    const result = await client.createBug(args as Parameters<MeterSphereClient["createBug"]>[0]);
    return { content: [{ type: "text" as const, text: JSON.stringify(result, null, 2) }] };
  },
};

export const updateBugInputSchema = {
  projectId: z.string().optional(),
  bugId: z.string(),
  title: z.string().optional(),
  description: z.string().optional(),
  tags: z.array(z.string()).optional(),
  templateId: z.string().optional(),
  customFields: z
    .record(z.string())
    .optional()
    .describe("fieldId -> value; merges with existing custom fields (status/handler etc.)"),
};

export const updateBugTool = {
  name: "update_bug",
  description: "Update defect title/description/tags/custom fields (including status and handlers).",
  inputSchema: updateBugInputSchema,
  handler: async (client: MeterSphereClient, args: Record<string, unknown>) => {
    const result = await client.updateBug(args as Parameters<MeterSphereClient["updateBug"]>[0]);
    return { content: [{ type: "text" as const, text: JSON.stringify(result, null, 2) }] };
  },
};

export const relateBugCaseInputSchema = {
  projectId: z.string().optional(),
  bugId: z.string(),
  caseIds: z.array(z.string()).min(1),
  caseType: z.string().optional(),
};

export const relateBugCaseTool = {
  name: "relate_bug_case",
  description: "Relate functional cases to an existing bug.",
  inputSchema: relateBugCaseInputSchema,
  handler: async (client: MeterSphereClient, args: Record<string, unknown>) => {
    const result = await client.relateBugCase(args as Parameters<MeterSphereClient["relateBugCase"]>[0]);
    return { content: [{ type: "text" as const, text: JSON.stringify({ success: true, result }, null, 2) }] };
  },
};

export const getExecLogInputSchema = {
  id: z.string().describe("Exec log ID"),
};

export const getExecLogTool = {
  name: "get_exec_log",
  description: "Get agent execution audit log detail by id.",
  inputSchema: getExecLogInputSchema,
  handler: async (client: MeterSphereClient, args: Record<string, unknown>) => {
    const result = await client.getExecLog(String(args.id));
    return { content: [{ type: "text" as const, text: JSON.stringify(result, null, 2) }] };
  },
};
