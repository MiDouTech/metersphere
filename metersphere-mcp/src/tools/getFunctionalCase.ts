import { z } from "zod";
import type { MeterSphereClient } from "../client.js";

export const getFunctionalCaseInputSchema = {
  caseId: z.string().describe("Functional case ID"),
  includeSteps: z.boolean().optional().describe("Include steps, default true"),
  testPlanId: z.string().optional().describe("Test plan ID for testPlanCaseId lookup"),
};

export const getFunctionalCaseTool = {
  name: "get_functional_case",
  description: "Get a single functional test case by caseId, optionally with steps and testPlanCaseId.",
  inputSchema: getFunctionalCaseInputSchema,
  handler: async (client: MeterSphereClient, args: Record<string, unknown>) => {
    const caseId = String(args.caseId);
    const includeSteps = args.includeSteps !== false;
    const testPlanId = args.testPlanId as string | undefined;
    const result = await client.getFunctionalCase(caseId, includeSteps, testPlanId);
    return {
      content: [{ type: "text" as const, text: JSON.stringify(result, null, 2) }],
    };
  },
};
