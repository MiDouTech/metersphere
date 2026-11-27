import { z } from "zod";
import type { MeterSphereClient } from "../client.js";
const id = z.string().min(1).max(64);
export const dataAssetTools = [{
  name: "metersphere.data_asset.manage",
  description: "Manage organization-global test data through the native service. sourceProjectId selects authorized organization context and Token whitelist, never content scope. Use expectedRevision for writes and stable requestId for uploads. Downloads return bounded Base64 chunks. No new Token scopes.",
  inputSchema: {
    sourceProjectId: id,
    action: z.enum(["list", "detail", "content", "versions", "create", "update", "delete", "batch", "job", "retry_job", "upload", "replace_upload", "download", "legacy_inventory", "legacy_import"]),
    id: id.optional(), versionId: id.optional(), requestId: z.string().min(1).max(128).optional(),
    name: z.string().min(1).max(255).optional(), fileName: z.string().min(1).max(255).optional(),
    base64: z.string().max(2666672).optional(), offset: z.number().int().nonnegative().optional(), chunkSize: z.number().int().min(1).max(65536).optional(),
    keyword: z.string().max(255).optional(), categoryId: z.string().max(64).optional(),
    page: z.number().int().min(1).optional(), pageSize: z.number().int().min(1).max(100).optional(),
    data: z.record(z.unknown()).optional(), items: z.array(z.record(z.unknown())).max(100).optional(), expectedRevision: z.number().int().min(1).optional(),
  },
  handler: (client: MeterSphereClient, args: Record<string, unknown>) => client.callNativeTool("metersphere.data_asset.manage", args),
}];
