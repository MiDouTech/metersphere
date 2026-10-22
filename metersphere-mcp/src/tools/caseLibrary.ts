import { z } from "zod";
import { open } from "node:fs/promises";
import type { MeterSphereClient } from "../client.js";
import { searchFunctionalCasesInputSchema } from "./searchFunctionalCases.js";

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
  tool("metersphere.functional.attachment.import_url", "Download a public PNG/JPEG URL (max 5 MiB) and persist it in the case attachment area. Same requestId replays safely, even if the source expires.", { ...resource, url:z.string().url().max(8192), requestId, fileName:z.string().min(1).max(200).optional() }),
  tool("metersphere.bug.attachment.import_url", "Download a public PNG/JPEG URL (max 5 MiB) and persist it in the bug attachment area; requires BUG_ATTACHMENT and current bug update permission.", { projectId:id, bugId:id, url:z.string().url().max(8192), requestId, fileName:z.string().min(1).max(200).optional() }),
  tool("metersphere.functional.execution.evidence.import_url", "Stage PNG/JPEG evidence from a public URL (max 5 MiB). Requires FUNCTIONAL_SUBMIT and CASE_ATTACHMENT. Visible only after execution.submit passes the existing gate.", { ...resource, url:z.string().url().max(8192), requestId, contentHash:z.string().length(64), stepNum:z.number().int().min(1).max(1000), purpose:z.enum(["BEFORE_STEP","AFTER_STEP"]), fileName:z.string().min(1).max(200).optional() }),
  tool("metersphere.functional.archive.prepare","Archive an ended TASK or accepted personal SUBMISSION into project documents. Separate status from test success. Requires ARTIFACT_WRITE; no new task/lease for personal submissions.",{projectId:id,sourceType:z.enum(["TASK","SUBMISSION"]),sourceId:id}),
  tool("metersphere.functional.archive.get","Read archive status and document/dataset references. PENDING/FAILED is not a completed archive.",{projectId:id,archiveId:id}),
  tool("metersphere.functional.archive.retry","Retry failed archival without rerunning the test or changing its result.",{projectId:id,archiveId:id}),
  tool("metersphere.functional.archive.data","Archive actual reusable business data after obtaining archiveId. Do not fabricate data. Each record requires source object, reuse conditions and cleanup status; values are field-redacted. Additional project file-add permission is required. Same records replay; different records cannot overwrite the original.",{projectId:id,archiveId:id,records:z.array(z.object({sourceObjectId:z.string().min(1).max(2000),reuseConditions:z.string().min(1).max(2000),cleanupStatus:z.string().min(1).max(2000),values:z.record(z.string(),z.unknown())}).strict()).min(1).max(500)}),
  {
    name: "metersphere.functional.import.precheck",
    description: "Freeze an Excel/XMind import before committing. Maximum 10000 logical cases/50 MiB, expires in 24h. Explicit CASE_IMPORT_V2 required; cover defaults false. Inspect per-item report, then use import.commit.",
    inputSchema: { projectId:id, versionId:id.optional(), moduleId:id.optional(), cover:z.boolean().optional(), requestId:z.string().regex(/^[A-Za-z0-9_-]{1,64}$/), fileName:z.string().min(1).max(255), filePath:z.string().min(1).optional(), base64:z.string().min(1).max(69905068).optional() },
    handler: async (client:MeterSphereClient,args:Record<string,unknown>) => {
      if(Boolean(args.filePath)===Boolean(args.base64))throw new Error("Provide exactly one of filePath or base64");
      const {filePath,...payload}=args;
      if(filePath) {
        const file=await open(String(filePath),"r");
        try {
          const stat=await file.stat();
          if(!stat.isFile()||stat.size<1||stat.size>52428800)throw new Error("Expected a regular file of at most 50 MiB");
          const bytes=Buffer.alloc(stat.size+1);let offset=0;
          while(offset<bytes.length){const result=await file.read(bytes,offset,bytes.length-offset,null);if(!result.bytesRead)break;offset+=result.bytesRead;}
          if(offset!==stat.size)throw new Error("File changed while reading; retry with a stable file");
          payload.base64=bytes.subarray(0,offset).toString("base64");
        }finally{await file.close();}
      }
      return client.callNativeTool("metersphere.functional.import.precheck",payload);
    },
  },
  tool("metersphere.functional.import.get","Read durable import state and paginated item results; PENDING is not success.",{jobId:id,current:z.number().int().positive().optional(),pageSize:z.number().int().min(1).max(100).optional()}),
  ...["commit","stop","retry"].map(action=>tool(`metersphere.functional.import.${action}`,`Import ${action}: same jobId safely replays; successful items are never reimported. Stop preserves completed writes. Retry uses the frozen file and versions.`,{jobId:id})),
  tool("metersphere.functional.case.delete_preview", "Preview explicit case versions and active tasks. Return confirmationPath for the user to choose cancel/wait/stop/delete. Never replace the user's choice with confirm=true. Requires explicit CASE_DELETE_V2.", {
    projectId: id, caseIds: z.array(id).min(1).max(100), versionScope: z.enum(["CURRENT","ALL"]).optional(),
  }),
  tool("metersphere.functional.case.delete", "Continue a user's approved deletion preview. Pending/waiting results are not deletion success; retry the same previewId. Does not permanently erase cases.", { previewId: id }),
  tool("metersphere.functional.case.batch_delete", "Continue the exact target set in a user-approved deletion preview; no filter expansion.", { previewId: id }),
  tool("metersphere.functional.trash.list", "Page authorized deleted case versions including updateTime for restore.", {projectId: id, current: z.number().int().positive().optional(), pageSize: z.number().int().min(1).max(100).optional()}),
  tool("metersphere.functional.case.restore", "Restore explicit case versions; CURRENT default, ALL explicit. Supply a valid moduleId if the former directory is gone. Never restarts execution. Requires explicit CASE_RESTORE_V2.", {
    ...resource, expectedUpdateTime: version, moduleId: id.optional(), versionScope: z.enum(["CURRENT","ALL"]).optional(), requestId,
  }),
  tool("metersphere.functional.attachment.upload.prepare", "Prepare an ordinary case attachment, bound to project/purpose/SHA-256 for 15 minutes. Max 5 MiB. Does not associate it with a case.", {
    projectId: id, purpose: z.enum(["CASE_DETAIL", "CASE_COMMENT"]), fileName: z.string().min(1).max(200),
    contentType: z.string().min(1).max(128), size: z.number().int().min(1).max(5242880), sha256: z.string().regex(/^[a-fA-F0-9]{64}$/), requestId,
  }),
  {
    name: "metersphere.functional.attachment.upload.complete",
    description: "Complete a prepared ordinary attachment upload with local filePath or base64. Returns a temporary attachmentId for attachment.attach/comment.create; max 5 MiB.",
    inputSchema: { uploadId: id, fileName: z.string().min(1).max(200), contentType: z.string().min(1).max(128), filePath: z.string().min(1).optional(), base64: z.string().min(1).max(6990508).optional() },
    handler: async (client: MeterSphereClient, args: Record<string, unknown>) => {
      if (Boolean(args.filePath) === Boolean(args.base64)) throw new Error("Provide exactly one of filePath or base64");
      const { filePath, ...payload } = args;
      if (filePath) {
        const file = await open(String(filePath), "r");
        try {
          const stat = await file.stat();
          if (!stat.isFile() || stat.size < 1 || stat.size > 5242880) throw new Error("Expected a regular file of at most 5 MiB");
          const buffer = Buffer.alloc(5242881); let offset = 0;
          while (offset < buffer.length) { const part = await file.read(buffer, offset, buffer.length-offset, null); if (!part.bytesRead) break; offset += part.bytesRead; }
          if (offset !== stat.size || offset > 5242880) throw new Error("File changed while reading; prepare the upload again");
          payload.base64 = buffer.subarray(0,offset).toString("base64");
        } finally { await file.close(); }
      }
      return client.callNativeTool("metersphere.functional.attachment.upload.complete", payload);
    },
  },
  tool("metersphere.functional.snapshot.create", "Freeze a complete case-library query for 15 minutes (10000 cases/50 MiB). Returns first page and snapshotId. Ordinary search remains live.", {
    query: z.object(searchFunctionalCasesInputSchema).strict(),
  }),
  tool("metersphere.functional.snapshot.page", "Page frozen contents with current authorization. Deleted or inaccessible scope requires rebuilding the snapshot.", {
    snapshotId: id, current: z.number().int().positive().optional(), pageSize: z.number().int().min(1).max(100).optional(),
  }),
  tool("metersphere.functional.snapshot.release", "Release your temporary extraction snapshot without deleting cases.", { snapshotId: id }),
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
  tool("metersphere.functional.comment.delete", "Delete a comment into a tombstone, preserving replies. Requires explicit CASE_COMMENT_DELETE_V2, author/moderator permission and the current version. AGENT_ALL does not include deletion.", {
    ...resource, commentId: id, expectedUpdateTime: version, requestId,
  }),
  tool("metersphere.functional.overview.get", "Read case plan/review relationships and personal progress.", resource),
  tool("metersphere.functional.versions.list", "Read authorized non-deleted case versions.", { ...resource, current: z.number().int().positive().optional(), pageSize: z.number().int().min(1).max(100).optional() }),
  tool("metersphere.functional.template.get", "Read the project case template and custom field constraints.", { projectId: id, templateId: id.optional() }),
  tool("metersphere.functional.history.list", "Read authorized case change history.", { ...resource, current: z.number().int().positive().optional(), pageSize: z.number().int().min(1).max(100).optional() }),
  tool("metersphere.functional.comments.list", "Page comments as {list,total,current,pageSize}. Default 10, maximum 100; roots include 10 replies and childTotal. Supply parentId to page replies. Deleted comments retain tombstones.", {
    caseId: id, projectId: id.optional(), parentId: id.optional(),
    current: z.number().int().min(1).max(2147483647).optional(), pageSize: z.number().int().min(1).max(100).optional(),
  }),
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
