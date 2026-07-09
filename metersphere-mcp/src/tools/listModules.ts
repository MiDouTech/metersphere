import { z } from "zod";
import type { MeterSphereClient } from "../client.js";

export const listModulesInputSchema = {
  projectId: z.string().optional().describe("Project ID; uses MS_PROJECT_ID when omitted"),
};

export const listModulesTool = {
  name: "list_modules",
  description: "List functional case modules for disambiguation before searching cases.",
  inputSchema: listModulesInputSchema,
  handler: async (client: MeterSphereClient, args: Record<string, unknown>) => {
    const projectId = args.projectId as string | undefined;
    const result = await client.listModules(projectId);
    return {
      content: [{ type: "text" as const, text: JSON.stringify(result, null, 2) }],
    };
  },
};
