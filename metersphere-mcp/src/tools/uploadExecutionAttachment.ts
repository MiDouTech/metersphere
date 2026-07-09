import { z } from "zod";
import type { MeterSphereClient } from "../client.js";

export const uploadExecutionAttachmentInputSchema = {
  projectId: z.string().optional().describe("Project ID; uses MS_PROJECT_ID when omitted"),
  filePath: z.string().describe("Local file path to upload as execution evidence"),
  stepNum: z.number().optional().describe("Related step number"),
};

export const uploadExecutionAttachmentTool = {
  name: "upload_execution_attachment",
  description: "Upload execution evidence (screenshot/file) before submitting test results.",
  inputSchema: uploadExecutionAttachmentInputSchema,
  handler: async (client: MeterSphereClient, args: Record<string, unknown>) => {
    const result = await client.uploadExecutionAttachment(
      args as Parameters<MeterSphereClient["uploadExecutionAttachment"]>[0]
    );
    return {
      content: [{ type: "text" as const, text: JSON.stringify(result, null, 2) }],
    };
  },
};
