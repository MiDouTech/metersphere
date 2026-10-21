import { z } from "zod";
import { open } from "node:fs/promises";
import type { MeterSphereClient } from "../client.js";

const id = z.string().min(1).max(128);
const requestId = z.string().regex(/^[A-Za-z0-9_.:-]{1,128}$/);
const resource = { projectId: id, caseId: id };
const moduleResource = { projectId: id, moduleId: id };
const version = z.number().int().nonnegative();
const tool = (name: string, description: string, inputSchema: z.ZodRawShape) => ({
  name, description, inputSchema,
  handler: (client: MeterSphereClient, args: Record<string, unknown>) => client.callNativeTool(name, args),
});

export const caseLibraryTools = [
  tool("metersphere.functional.execution.contract", "Read case steps, contentHash and global policyHash. Structure-only direct execution: Agent reports SUCCESS/ERROR; no expected-text comparison. Original positions must all be covered; extra and repeated positions are retained.", resource),
  tool("metersphere.functional.execution.get", "Read step outcomes, evidence mapping and the frozen global policy of a submitted execution.", { ...resource, submissionId: id }),
  {
    name: "metersphere.functional.execution.evidence.upload",
    description: "Stage step evidence from a local PNG/JPEG filePath or base64 (max 5 MiB). Requires FUNCTIONAL_SUBMIT and CASE_ATTACHMENT; published only after submit passes the gate.",
    inputSchema: { ...resource, contentHash: z.string().length(64), stepNum: z.number().int().min(1).max(1000), purpose: z.enum(["BEFORE_STEP", "AFTER_STEP"]), fileName: z.string().min(1).max(200), contentType: z.enum(["image/png", "image/jpeg"]), filePath: z.string().min(1).optional(), base64: z.string().min(1).max(6990508).optional(), requestId },
    handler: async (client: MeterSphereClient, args: Record<string, unknown>) => {
      if (Boolean(args.filePath) === Boolean(args.base64)) throw new Error("Provide exactly one of filePath or base64");
      const { filePath, ...payload } = args;
      if (filePath) {
        const file = await open(String(filePath), "r");
        try {
          const stat = await file.stat();
          if (!stat.isFile() || stat.size < 1 || stat.size > 5242880) throw new Error("Evidence must be a regular file of 1 byte to 5 MiB");
          const bytes = Buffer.alloc(stat.size + 1);
          let offset = 0;
          while (offset < bytes.length) {
            const { bytesRead } = await file.read(bytes, offset, bytes.length - offset, offset);
            if (bytesRead === 0) break;
            offset += bytesRead;
          }
          if (offset !== stat.size) throw new Error("Evidence changed while reading; retry with a stable file");
          payload.base64 = bytes.subarray(0, offset).toString("base64");
        } finally { await file.close(); }
      }
      return client.callNativeTool("metersphere.functional.execution.evidence.upload", payload);
    },
  },
  tool("metersphere.functional.execution.submit", "Validate original-step coverage and evidence structure; retain extra/repeated positions. Agent reports SUCCESS/ERROR without expected-text comparison or content review. Any ERROR makes the case ERROR. ACCEPTED/PUBLISHED means structure accepted, not execution verified. Same requestId retries safely.", {
    ...resource, contentHash: z.string().length(64), policyHash: z.string().length(64), requestId,
    steps: z.array(z.object({ stepNum: z.number().int().min(1).max(1000), actual: z.string().min(1).max(20000), result: z.enum(["SUCCESS", "ERROR"]), evidenceIds: z.array(id).min(1).max(10) }).strict()).min(1).max(1000),
  }),
  tool("metersphere.functional.comment.update", "Edit your own comment text with version checking. Explicit CASE_COMMENT_EDIT_V2 required; preserves replies and attachments.", {
    ...resource, commentId: id, content: z.string().min(1).max(20000), expectedUpdateTime: version, requestId,
  }),
  tool("metersphere.functional.overview.get", "Read case plan/review relationships and personal progress.", resource),
  tool("metersphere.functional.versions.list", "Read authorized non-deleted case versions.", { ...resource, current: z.number().int().positive().optional(), pageSize: z.number().int().min(1).max(100).optional() }),
  tool("metersphere.functional.template.get", "Read the project case template and custom field constraints.", { projectId: id, templateId: id.optional() }),
  tool("metersphere.functional.history.list", "Read authorized case change history.", { ...resource, current: z.number().int().positive().optional(), pageSize: z.number().int().min(1).max(100).optional() }),
  tool("metersphere.functional.comments.list", "Read case comments and replies.", { caseId: id }),
  tool("metersphere.functional.attachments.list", "Read case attachment metadata. Execution artifacts are a separate resource.", resource),
  tool("metersphere.functional.case.update", "Update selected case fields using the current updateTime. Does not set execution results.", {
    ...resource, expectedUpdateTime: version, requestId, reason: z.string().max(2000).optional(),
    patch: z.object({ name: z.string().min(1).max(255).optional(), moduleId: id.optional(), prerequisite: z.string().optional(), description: z.string().optional(), tags: z.array(z.string()).max(100).optional(), steps: z.array(z.record(z.unknown())).optional(), customFields: z.record(z.unknown()).optional(), priority: z.string().optional() }).strict().refine(value => Object.keys(value).length > 0, "patch must not be empty"),
  }),
  tool("metersphere.functional.comment.create", "Create a comment/reply. uploadFileIds are temporary attachment IDs, not storage file IDs.", {
    ...resource, content: z.string().min(1).max(20000), requestId,
    event: z.enum(["COMMENT", "AT", "REPLY"]).optional(), parentId: id.optional(), replyUser: id.optional(), notifier: z.string().max(2000).optional(), uploadFileIds: z.array(id).max(10).optional(),
  }),
  tool("metersphere.functional.module.get", "Read module metadata including updateTime.", moduleResource),
  tool("metersphere.functional.module.update", "Rename a case module. Requires an explicit CASE_MODULE_WRITE_V2 grant.", { ...moduleResource, name: z.string().min(1).max(255), expectedUpdateTime: version, requestId }),
  tool("metersphere.functional.module.move", "Move a module within its project. parentId=NONE is the project root. Requires CASE_MODULE_WRITE_V2.", { ...moduleResource, parentId: id, expectedUpdateTime: version, requestId }),
];
