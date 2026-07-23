import { z } from "zod";
import type { MeterSphereClient } from "../client.js";

export const createProjectInputSchema = {
  organizationId: z.string().describe("Organization ID"),
  name: z.string().describe("Project name"),
  description: z.string().optional(),
  userIds: z.array(z.string()).min(1).describe("Member user IDs, at least creator"),
  moduleIds: z.array(z.string()).optional(),
  resourcePoolIds: z.array(z.string()).optional(),
  allResourcePool: z.boolean().optional(),
};

export const createProjectTool = {
  name: "create_project",
  description: "Create a MeterSphere project and add initial members. Returns projectId for subsequent tools.",
  inputSchema: createProjectInputSchema,
  handler: async (client: MeterSphereClient, args: Record<string, unknown>) => {
    const result = await client.createProject(args as Parameters<MeterSphereClient["createProject"]>[0]);
    return { content: [{ type: "text" as const, text: JSON.stringify(result, null, 2) }] };
  },
};
