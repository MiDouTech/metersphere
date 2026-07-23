import { z } from "zod";
import type { MeterSphereClient } from "../client.js";

export const addProjectMembersInputSchema = {
  projectId: z.string().describe("Project ID"),
  userIds: z.array(z.string()).min(1).describe("User IDs to add"),
  userRoleIds: z.array(z.string()).optional(),
};

export const addProjectMembersTool = {
  name: "add_project_members",
  description: "Add members to an existing MeterSphere project.",
  inputSchema: addProjectMembersInputSchema,
  handler: async (client: MeterSphereClient, args: Record<string, unknown>) => {
    const result = await client.addProjectMembers(args as Parameters<MeterSphereClient["addProjectMembers"]>[0]);
    return { content: [{ type: "text" as const, text: JSON.stringify({ success: true, result }, null, 2) }] };
  },
};
