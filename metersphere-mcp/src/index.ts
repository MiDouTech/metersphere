#!/usr/bin/env node
import { McpServer } from "@modelcontextprotocol/sdk/server/mcp.js";
import { StdioServerTransport } from "@modelcontextprotocol/sdk/server/stdio.js";
import { loadConfig, MeterSphereClient, MsApiError } from "./client.js";
import { getFunctionalCaseTool } from "./tools/getFunctionalCase.js";
import { listModulesTool } from "./tools/listModules.js";
import { searchFunctionalCasesTool } from "./tools/searchFunctionalCases.js";
import { submitFunctionalResultTool } from "./tools/submitFunctionalResult.js";
import { submitFunctionalResultsBatchTool } from "./tools/submitFunctionalResultsBatch.js";
import { uploadExecutionAttachmentTool } from "./tools/uploadExecutionAttachment.js";
import { createProjectTool } from "./tools/createProject.js";
import { addProjectMembersTool } from "./tools/addProjectMembers.js";
import {
  batchCreateFunctionalCasesTool,
  createFunctionalCaseTool,
  createFunctionalModuleTool,
} from "./tools/caseWrite.js";
import {
  associateCaseReviewCasesTool,
  associateTestPlanCasesTool,
  createCaseReviewTool,
  createTestPlanTool,
} from "./tools/planReview.js";
import { createBugTool, getExecLogTool, relateBugCaseTool } from "./tools/bugWrite.js";

type ToolDef = {
  name: string;
  description: string;
  inputSchema: Record<string, unknown>;
  handler: (client: MeterSphereClient, args: Record<string, unknown>) => Promise<{
    content: Array<{ type: "text"; text: string }>;
    isError?: boolean;
  }>;
};

const tools: ToolDef[] = [
  searchFunctionalCasesTool,
  getFunctionalCaseTool,
  submitFunctionalResultTool,
  submitFunctionalResultsBatchTool,
  uploadExecutionAttachmentTool,
  listModulesTool,
  createProjectTool,
  addProjectMembersTool,
  createFunctionalModuleTool,
  createFunctionalCaseTool,
  batchCreateFunctionalCasesTool,
  createTestPlanTool,
  associateTestPlanCasesTool,
  createCaseReviewTool,
  associateCaseReviewCasesTool,
  createBugTool,
  relateBugCaseTool,
  getExecLogTool,
];

async function main() {
  const config = loadConfig();
  const client = new MeterSphereClient(config);

  const server = new McpServer({
    name: "metersphere-mcp",
    version: "0.2.0",
  });

  for (const tool of tools) {
    server.tool(tool.name, tool.description, tool.inputSchema, async (args) => {
      try {
        return await tool.handler(client, args as Record<string, unknown>);
      } catch (error) {
        if (error instanceof MsApiError) {
          return {
            isError: true,
            content: [
              {
                type: "text",
                text: JSON.stringify(
                  {
                    error: error.message,
                    status: error.status,
                    body: error.body,
                  },
                  null,
                  2
                ),
              },
            ],
          };
        }
        const message = error instanceof Error ? error.message : String(error);
        return {
          isError: true,
          content: [{ type: "text", text: JSON.stringify({ error: message }, null, 2) }],
        };
      }
    });
  }

  const transport = new StdioServerTransport();
  await server.connect(transport);
}

main().catch((error) => {
  console.error(error);
  process.exit(1);
});
