import { z } from "zod";
import type { MeterSphereClient } from "../client.js";

const stepSchema = z.object({
  id: z.string().optional(),
  num: z.number().optional(),
  desc: z.string().optional(),
  expected: z.string().optional(),
  actualResult: z.string().optional(),
  executeResult: z.string().optional(),
});

export const submitFunctionalResultInputSchema = {
  projectId: z.string().optional().describe("Project ID; uses MS_PROJECT_ID when omitted"),
  caseId: z.string().describe("Functional case ID"),
  testPlanCaseId: z.string().optional().describe("Plan relation ID; omit for out-of-plan submit"),
  testPlanId: z.string().optional().describe("Test plan ID; uses MS_TEST_PLAN_ID when omitted"),
  lastExecResult: z
    .string()
    .describe("Final result: SUCCESS, ERROR, BLOCKED, or FAKE_ERROR"),
  executedBy: z.string().optional().describe("Agent identifier, e.g. cursor-agent"),
  steps: z
    .array(stepSchema)
    .optional()
    .describe("Step execution results; keep step.id from search response"),
  content: z.string().optional().describe("Execution comment"),
  attachmentIds: z.array(z.string()).optional().describe("Uploaded attachment IDs"),
};

export const submitFunctionalResultTool = {
  name: "submit_functional_result",
  description:
    "Submit functional test execution result to MeterSphere test plan. testPlanCaseId is the plan relation ID, not caseId.",
  inputSchema: submitFunctionalResultInputSchema,
  handler: async (client: MeterSphereClient, args: Record<string, unknown>) => {
    const result = await client.submitFunctionalResult(
      args as Parameters<MeterSphereClient["submitFunctionalResult"]>[0]
    );
    return {
      content: [{ type: "text" as const, text: JSON.stringify({ success: true, result }, null, 2) }],
    };
  },
};
