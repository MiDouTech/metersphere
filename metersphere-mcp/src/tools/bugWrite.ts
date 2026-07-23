import { z } from "zod";
import type { MeterSphereClient } from "../client.js";

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
