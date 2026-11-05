import { z } from "zod";
import type { MeterSphereClient } from "../client.js";

export const planReviewSettingsSchema = z.object({
  name: z.string().trim().min(1).max(255),
  moduleId: z.string().trim().min(1).max(50),
  reviewers: z.array(z.string().trim().min(1).max(50)).min(1).refine(values => new Set(values).size === values.length, "Reviewers must be unique"),
  reviewPassRule: z.enum(["SINGLE", "MULTIPLE"]),
  followPlanName: z.boolean().optional(),
}).strict();

export const createTestPlanInputSchema = {
  reviewSettings: planReviewSettingsSchema,
  requestId: z.string().regex(/^[A-Za-z0-9_.:-]{1,128}$/),
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
  description: "Create a test plan with its own linked review. Explicit current-project review settings are required; keep the same requestId after response loss.",
  inputSchema: createTestPlanInputSchema,
  handler: async (client: MeterSphereClient, args: Record<string, unknown>) => {
    const result = await client.createTestPlan(args as Parameters<MeterSphereClient["createTestPlan"]>[0]);
    return { content: [{ type: "text" as const, text: JSON.stringify(result, null, 2) }] };
  },
};

export const associateTestPlanCasesInputSchema = {
  requestId: z.string().regex(/^[A-Za-z0-9_.:-]{1,128}$/),
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
  requestId: z.string().regex(/^[A-Za-z0-9_.:-]{1,128}$/).describe("Stable logical request ID; reuse unchanged after response loss"),
  projectId: z.string().optional(),
  name: z.string(),
  moduleId: z.string().optional(),
  reviewPassRule: z.enum(["SINGLE", "MULTIPLE"]).optional(),
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
  requestId: z.string().regex(/^[A-Za-z0-9_.:-]{1,128}$/).describe("Stable logical request ID; reuse unchanged after response loss"),
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

export const getCaseReviewTool = {
  name: "get_case_review",
  description: "Read an authorized review and its current status.",
  inputSchema: { reviewId: z.string().min(1) },
  handler: async (client: MeterSphereClient, args: Record<string, unknown>) => ({
    content: [{ type: "text" as const, text: JSON.stringify(await client.getCaseReview(String(args.reviewId))) }],
  }),
};

export const previewLinkedCaseReviewTool = {
  name: "preview_linked_case_review",
  description: "Preview a review for an existing unpaired plan. Creates no plan/review/relation. The plan is the only membership source; keep requestId and previewId for explicit confirmation.",
  inputSchema: {
    requestId: createCaseReviewInputSchema.requestId,
    projectId: z.string().optional(),
    planId: z.string().min(1).max(50),
    name: z.string().trim().min(1).max(255),
    moduleId: z.string().min(1).max(50),
    reviewers: z.array(z.string().min(1).max(50)).min(1),
    reviewPassRule: z.enum(["SINGLE", "MULTIPLE"]),
    description: z.string().optional(),
    tags: z.array(z.string()).optional(),
  },
  handler: async (client: MeterSphereClient, args: Record<string, unknown>) => ({
    content: [{type: "text" as const,text: JSON.stringify(await client.previewLinkedCaseReview(args as Parameters<MeterSphereClient["previewLinkedCaseReview"]>[0]))}],
  }),
};
export const confirmLinkedCaseReviewTool = {
  name: "confirm_linked_case_review",
  description: "Explicitly confirm the exact linked-review preview using its original requestId. Never regenerate identity after timeout. Expired/stale previews must be requested again.",
  inputSchema: {
    projectId: z.string().optional(),
    previewId: z.string().min(1).max(50),
    requestId: createCaseReviewInputSchema.requestId,
    acknowledged: z.literal(true),
  },
  handler: async (client: MeterSphereClient, args: Record<string, unknown>) => ({
    content: [{type:"text" as const,text:JSON.stringify(await client.confirmLinkedCaseReview(args as Parameters<MeterSphereClient["confirmLinkedCaseReview"]>[0]))}],
  }),
};
