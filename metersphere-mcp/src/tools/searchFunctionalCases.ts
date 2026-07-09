import { z } from "zod";
import type { MeterSphereClient } from "../client.js";

const filtersSchema = z
  .object({
    priority: z.array(z.string()).optional(),
    lastExecuteResult: z.array(z.string()).optional(),
    tags: z.array(z.string()).optional(),
    moduleIds: z.array(z.string()).optional(),
  })
  .optional();

export const searchFunctionalCasesInputSchema = {
  query: z.string().optional().describe("Natural language query: module name, tag, case name or number"),
  testPlanId: z.string().optional().describe("Test plan ID; uses MS_TEST_PLAN_ID env when omitted"),
  includeSteps: z.boolean().optional().describe("Include full steps, default true"),
  current: z.number().int().min(1).optional().describe("Page number, default 1"),
  pageSize: z.number().int().min(1).max(500).optional().describe("Page size, default 50, max 500"),
  filters: filtersSchema.describe("Structured filters: priority, lastExecuteResult, tags, moduleIds"),
};

export const searchFunctionalCasesTool = {
  name: "search_functional_cases",
  description:
    "Search MeterSphere functional test cases. Returns cases with steps and testPlanCaseId when testPlanId is provided.",
  inputSchema: searchFunctionalCasesInputSchema,
  handler: async (client: MeterSphereClient, args: Record<string, unknown>) => {
    const result = await client.searchFunctionalCases(args as Parameters<MeterSphereClient["searchFunctionalCases"]>[0]);
    return {
      content: [{ type: "text" as const, text: JSON.stringify(result, null, 2) }],
    };
  },
};
