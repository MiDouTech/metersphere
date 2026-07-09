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

const resultSchema = z.object({
  caseId: z.string(),
  testPlanCaseId: z.string().optional(),
  lastExecResult: z.string(),
  steps: z.array(stepSchema).optional(),
  content: z.string().optional(),
  attachmentIds: z.array(z.string()).optional(),
});

export const submitFunctionalResultsBatchInputSchema = {
  projectId: z.string().optional(),
  testPlanId: z.string().optional(),
  executedBy: z.string().optional(),
  failFast: z.boolean().optional(),
  results: z.array(resultSchema).min(1),
};

export const submitFunctionalResultsBatchTool = {
  name: "submit_functional_results_batch",
  description: "Batch submit functional test execution results to MeterSphere.",
  inputSchema: submitFunctionalResultsBatchInputSchema,
  handler: async (client: MeterSphereClient, args: Record<string, unknown>) => {
    const result = await client.submitFunctionalResultsBatch(
      args as Parameters<MeterSphereClient["submitFunctionalResultsBatch"]>[0]
    );
    return {
      content: [{ type: "text" as const, text: JSON.stringify(result, null, 2) }],
    };
  },
};
