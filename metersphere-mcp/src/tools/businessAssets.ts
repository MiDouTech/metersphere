import { z } from "zod";
import type { MeterSphereClient } from "../client.js";
const id = z.string().min(1).max(64);
const text = (max: number) => z.string().min(1).max(max);
const tool = (name: string, description: string, inputSchema: z.ZodRawShape) => ({
  name, description, inputSchema,
  handler: (client: MeterSphereClient, args: Record<string, unknown>) => client.callNativeTool(name, args),
});
export const businessAssetTools = [
  tool("metersphere.executor.context.get", "Read pinned understanding and versionNo for this execution session.", { projectId: id, sessionId: id, assetId: id }),
  tool("metersphere.executor.context.refresh", "Explicitly update pinned understanding with a reason and correction notification. Preserve original context until this deliberate action.", { projectId: id, sessionId: id, assetId: id, versionId: id, expectedVersionId: z.string().max(64), reason: text(500) }),
  tool("metersphere.executor.evidence", "Upload redacted PNG/JPEG Base64 evidence (2 MB maximum). Use evidenceId in understanding branches.", { projectId: id, sessionId: id, requestKey: text(128), content: text(2800000), redacted: z.literal(true) }),
  tool("metersphere.business_asset.search", "Read current business understanding before exploration. Group same-module and related-flow operations in one document.", { projectId: id.optional(), sourceProjectId: id.optional(), keyword: z.string().max(255).optional() }),
  tool("metersphere.business_asset.get", "Read business understanding and its immutable versions.", { projectId: id.optional(), sourceProjectId: id.optional(), assetId: id }),
  tool("metersphere.business_asset.upload", "Upload understanding after exploration/execution. Update original asset with baseVersion, retain evidence, distinguish facts from inference. No secrets. Conflict content is retained for merge.", {
    projectId: id.optional(), sourceProjectId: id.optional(), schemaVersion: z.literal("1.0"), operation: z.enum(["CREATE", "UPDATE"]), assetId: id.optional(),
    baseVersion: z.number().int().nonnegative(), idempotencyKey: text(128),
    scope: z.object({ systemKey: z.string().max(128), moduleKey: z.string().max(128), pageKeys: z.array(text(128)).max(100), flowKey: z.string().max(128) }),
    title: text(255), document: z.object({ format: z.literal("markdown"), content: text(500000) }),
    branches: z.array(z.object({ branchKey: text(128), title: text(255), understandingStatus: z.enum(["OBSERVED", "INFERRED", "UNCONFIRMED"]), evidenceRefs: z.array(id).max(100) })).max(200),
    changeSummary: text(1000), source: z.object({ taskId: id, executorId: id }),
  }),
  tool("metersphere.executor.connect", "Connect executor and pin business context. No frozen actions or legacy lease. Use same requestKey after a lost response.", { projectId: id, executorId: id, requestKey: text(128) }),
  tool("metersphere.executor.event", "Report heartbeat, observation, deviation or terminal outcome. Use stable uppercase stage and category; no secrets.", {
    projectId: id, sessionId: id, requestKey: text(128), summary: z.string().max(2000).optional(), evidenceId: id.optional(), type: z.enum(["HEARTBEAT", "OBSERVATION", "DEVIATION", "COMPLETE", "FAILED"]), stage: z.string().regex(/^[A-Z][A-Z0-9_]{0,63}$/), errorCategory: z.string().regex(/^[A-Z][A-Z0-9_]{0,63}$/), outcome: z.enum(["RUNNING", "PASSED", "FAILED", "BLOCKED"]),
  }),
  tool("metersphere.executor.commands", "Poll and apply pending correction instructions while executing.", { projectId: id, sessionId: id }),
  tool("metersphere.executor.respond", "Report whether the correction was APPLIED or FAILED. A recorded response cannot be overwritten.", { projectId: id, sessionId: id, commandId: id, outcome: z.enum(["APPLIED", "FAILED"]), category: z.string().regex(/^[A-Z][A-Z0-9_]{0,63}$/) }),
];
