#!/usr/bin/env node
import { caseExportTools } from "./tools/caseExport.js";
import { readFileSync } from "node:fs";
import { bugTransferTools } from "./tools/bugTransfer.js";
import { caseLibraryTools } from "./tools/caseLibrary.js";
import { executionBatchFinishTool, executionBatchNextTool } from "./tools/executionBatchNext.js";
import { deferExecutionStepTool, executionDecideTool, submitAndAdvanceTool } from "./tools/executionDecide.js";
import { managedExecutionTools } from "./tools/managedExecution.js";
import { governedExecutionTools } from "./tools/governedExecution.js";
import { z } from "zod";
import { McpServer } from "@modelcontextprotocol/sdk/server/mcp.js";
import { StdioServerTransport } from "@modelcontextprotocol/sdk/server/stdio.js";
import { nativeBatchSubmitTool, executionHistoryTool, executionPreflightTool, checkpointResumeTool, bugTransitionsTool,
  bugTransitionTool, testPlanUpdateTool, testPlanDisassociateTool, testPlanGetTool } from "./tools/maintenance.js";
import { loadConfig, MeterSphereClient, MsApiError } from "./client.js";
import { getFunctionalCaseTool } from "./tools/getFunctionalCase.js";
import { listModulesTool } from "./tools/listModules.js";
import { searchFunctionalCasesTool } from "./tools/searchFunctionalCases.js";
import { submitFunctionalResultTool } from "./tools/submitFunctionalResult.js";
import { submitFunctionalResultsBatchTool } from "./tools/submitFunctionalResultsBatch.js";
import { createProjectTool } from "./tools/createProject.js";
import { addProjectMembersTool } from "./tools/addProjectMembers.js";
import { searchProjectsTool } from "./tools/searchProjects.js";
import {
  batchCreateFunctionalCasesTool,
  createFunctionalCaseTool,
  createFunctionalModuleTool,
} from "./tools/caseWrite.js";
import {
  associateCaseReviewCasesTool,
  getCaseReviewTool,
  associateTestPlanCasesTool,
  createCaseReviewTool,
  createTestPlanTool,
  previewLinkedCaseReviewTool,
  confirmLinkedCaseReviewTool,
} from "./tools/planReview.js";
import {
  cancelExecutionTaskTool,
  createExecutionTaskTool,
  getExecutionEventsTool,
  getExecutionTaskTool,
  listTestPlanCasesTool,
  readPlanExecutionContractTool,
  resolveExecutionScopeTool,
  resumeExecutionTaskTool,
  searchTestPlansTool,
} from "./tools/testPlanExecution.js";
import { createBugTool, getBugTool, getExecLogTool, relateBugCaseTool, searchBugsTool, updateBugTool } from "./tools/bugWrite.js";
import { readBugImageTool } from "./tools/bugImages.js";

type ToolDef = {
  name: string;
  description: string;
  inputSchema: Record<string, unknown>;
  handler: (client: MeterSphereClient, args: Record<string, unknown>) => Promise<{
    content: Array<{ type: "text"; text: string } | { type: "image"; data: string; mimeType: string }>;
    isError?: boolean;
  }>;
};

const tools: ToolDef[] = [
  ...governedExecutionTools,
  ...managedExecutionTools,
  executionDecideTool,
  deferExecutionStepTool,
  executionBatchNextTool,
  executionBatchFinishTool,
  submitAndAdvanceTool,
  ...caseLibraryTools,
  ...caseExportTools,
  ...bugTransferTools,
  executionHistoryTool, executionPreflightTool, checkpointResumeTool, bugTransitionsTool,
  bugTransitionTool, testPlanUpdateTool, testPlanDisassociateTool, testPlanGetTool,
  searchFunctionalCasesTool,
  getFunctionalCaseTool,
  listModulesTool,
  searchProjectsTool,
  createProjectTool,
  addProjectMembersTool,
  createFunctionalModuleTool,
  createFunctionalCaseTool,
  batchCreateFunctionalCasesTool,
  createTestPlanTool,
  associateTestPlanCasesTool,
  searchTestPlansTool,
  listTestPlanCasesTool,
  readPlanExecutionContractTool,
  resolveExecutionScopeTool,
  createExecutionTaskTool,
  getExecutionTaskTool,
  getExecutionEventsTool,
  cancelExecutionTaskTool,
  resumeExecutionTaskTool,
  createCaseReviewTool,
  associateCaseReviewCasesTool,
  previewLinkedCaseReviewTool,
  confirmLinkedCaseReviewTool,
  getCaseReviewTool,
  searchBugsTool,
  getBugTool,
  readBugImageTool,
  createBugTool,
  updateBugTool,
  relateBugCaseTool,
  getExecLogTool,
];

async function main() {
  const config = loadConfig();
  const client = new MeterSphereClient(config);

  const server = new McpServer({
    name: "metersphere-mcp",
    version: JSON.parse(readFileSync(new URL("../package.json", import.meta.url), "utf8")).version,
  }, {
    instructions: [
      "For a personal browser plan, use metersphere.execution.preflight, metersphere.execution.create, and metersphere.task.claim with the ordinary project MCP identity.",
      "Read metersphere.execution.batch.next before choosing a case. Follow the frozen step and submit browser observations and committed evidence with metersphere.execution.step.submit_and_advance.",
      "For missing data, call metersphere.execution.step.decide with DATA_MISSING and use the returned preparation method.",
      "If a browser write's effect is unknown, persist the wait and query business state before any further write.",
      "After an evidence upload failure, resume the upload without repeating the business action.",
      "After a per-case wait or persisted assertion failure, use metersphere.execution.batch.next for the remaining cases. Finish only when it requests finalization.",
    ].join("\n"),
  });

  for (const tool of tools) {
    const handle = async (args: Record<string, unknown>) => {
      try {
        return await tool.handler(client, args as Record<string, unknown>);
      } catch (error) {
        if (error instanceof MsApiError) {
          return {
            isError: true,
            content: [
              {
                type: "text" as const,
                text: JSON.stringify(
                  {
                    error: error.message,
                    status: error.status,
                    code: error.code,
                    traceId: error.traceId,
                  },
                  null,
                  2
                ),
              },
            ],
          };
        }
        const message = "请求失败，请检查连接或参数后重试；不要直接重复执行已提交的业务操作";
        return {
          isError: true,
          content: [{ type: "text" as const, text: JSON.stringify({ error: message }, null, 2) }],
        };
      }
    };
    if (tool.name.startsWith('metersphere.functional.execution.session.') || tool.name.startsWith('metersphere.functional.knowledge.')) {
      server.registerTool(tool.name, { description: tool.description, inputSchema: z.object(tool.inputSchema as z.ZodRawShape).strict() }, handle);
    } else server.tool(tool.name, tool.description, tool.inputSchema, handle);
  }

  const transport = new StdioServerTransport();
  await server.connect(transport);
}

main().catch((error) => {
  console.error(error);
  process.exit(1);
});
