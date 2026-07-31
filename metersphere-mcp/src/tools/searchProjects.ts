import { z } from "zod";
import type { MeterSphereClient } from "../client.js";

export const searchProjectsInputSchema = {
  keyword: z.string().optional().describe("Project keyword. Matches internal project ID, project name, or the numeric ID shown in the MeterSphere project list."),
  limit: z.number().int().min(1).max(200).optional().describe("Maximum returned projects. Default 50, max 200."),
};

export const searchProjectsTool = {
  name: "search_projects",
  description: "Search MeterSphere projects by internal ID, project name, or project number shown as ID in the UI. Returns all matched projects, including projects with the same number.",
  inputSchema: searchProjectsInputSchema,
  handler: async (client: MeterSphereClient, args: Record<string, unknown>) => {
    const result = await client.searchProjects(args as Parameters<MeterSphereClient["searchProjects"]>[0]);
    return { content: [{ type: "text" as const, text: JSON.stringify(result, null, 2) }] };
  },
};
