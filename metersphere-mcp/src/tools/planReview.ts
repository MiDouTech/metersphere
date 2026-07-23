import { z } from "zod";
import type { MeterSphereClient } from "../client.js";

export const createTestPlanInputSchema = {
  projectId: z.string().optional(),
  name: z.string(),
  description: z.string().optional(),
  moduleId: z.string().optional(),
  caseIds: z.array(z.string()).optional().describe("Associate these case IDs on create"),
  automaticStatusUpdate: z.boolean().optional(),
  repeatCase: z.boolean().optional(),
  passThreshold: z.number().optional(),
};

export const createTestPlanTool = {
  name: "create_test_plan",
  description: "Create a test plan and optionally associate functional cases.",
  inputSchema: createTestPlanInputSchema,
  handler: async (client: MeterSphereClient, args: Record<string, unknown>) => {
    const result = await client.createTestPlan(args as Parameters<MeterSphereClient["createTestPlan"]>[0]);
    return { content: [{ type: "text" as const, text: JSON.stringify(result, null, 2) }] };
  },
};

export const associateTestPlanCasesInputSchema = {
  projectId: z.string().optional(),
  testPlanId: z.string(),
  caseIds: z.array(z.string()).min(1),
  collectionId: z.string().optional(),
};

export const associateTestPlanCasesTool = {
  name: "associate_test_plan_cases",
  description: "Associate functional cases to an existing test plan.",
  inputSchema: associateTestPlanCasesInputSchema,
  handler: async (client: MeterSphereClient, args: Record<string, unknown>) => {
    const result = await client.associateTestPlanCases(
      args as Parameters<MeterSphereClient["associateTestPlanCases"]>[0]
    );
    return { content: [{ type: "text" as const, text: JSON.stringify({ success: true, result }, null, 2) }] };
  },
};

export const createCaseReviewInputSchema = {
  projectId: z.string().optional(),
  name: z.string(),
  moduleId: z.string().optional(),
  reviewPassRule: z.string().optional().describe("SINGLE or MULTIPLE"),
  reviewers: z.array(z.string()).optional(),
  description: z.string().optional(),
  caseIds: z.array(z.string()).optional(),
  tags: z.array(z.string()).optional(),
};

export const createCaseReviewTool = {
  name: "create_case_review",
  description: "Create a case review plan and optionally associate cases.",
  inputSchema: createCaseReviewInputSchema,
  handler: async (client: MeterSphereClient, args: Record<string, unknown>) => {
    const result = await client.createCaseReview(args as Parameters<MeterSphereClient["createCaseReview"]>[0]);
    return { content: [{ type: "text" as const, text: JSON.stringify(result, null, 2) }] };
  },
};

export const associateCaseReviewCasesInputSchema = {
  projectId: z.string().optional(),
  reviewId: z.string(),
  caseIds: z.array(z.string()).min(1),
  reviewers: z.array(z.string()).optional(),
};

export const associateCaseReviewCasesTool = {
  name: "associate_case_review_cases",
  description: "Associate functional cases to an existing case review.",
  inputSchema: associateCaseReviewCasesInputSchema,
  handler: async (client: MeterSphereClient, args: Record<string, unknown>) => {
    const result = await client.associateCaseReviewCases(
      args as Parameters<MeterSphereClient["associateCaseReviewCases"]>[0]
    );
    return { content: [{ type: "text" as const, text: JSON.stringify({ success: true, result }, null, 2) }] };
  },
};
