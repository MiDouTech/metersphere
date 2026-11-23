import { z } from "zod";
import type { MeterSphereClient } from "../client.js";

const id = z.string().min(1).max(64);
const version = z.number().int().min(1).max(2147483647);
const save = {
  projectId: id,
  id: id.optional(),
  systemKey: z.string().min(1).max(128),
  environmentKey: z.string().min(1).max(128),
  name: z.string().min(1).max(128),
  kind: z.enum(["PLAIN", "SECRET", "DOCUMENT"]),
  value: z.string().max(2800000).optional(),
  fileName: z.string().max(255).optional(),
  expectedVersion: z.number().int().min(0).max(2147483647),
  secretType: z.enum(["ACCOUNT_PASSWORD", "OTHER"]).optional(),
  keepSecret: z.boolean().optional(),
};
const tool = (action: string, description: string, inputSchema: z.ZodRawShape) => ({
  name: `metersphere.shared_configuration.${action}`,
  description: `${description} Uses linked user roles and project access; no separate Token feature grant. Never log actual configuration values.`,
  inputSchema,
  handler: (client: MeterSphereClient, args: Record<string, unknown>) => client.callNativeTool(`metersphere.shared_configuration.${action}`, args),
});

export const sharedConfigurationTools = [
  tool("list", "List configuration metadata without sensitive values.", { projectId: id }),
  tool("versions", "List immutable version metadata without sensitive values.", { projectId: id, id }),
  tool("read", "Read a fixed ordinary value or original document. Sensitive content remains masked; use resolve for an authorized executor.",
    { projectId: id, id, version, document: z.boolean().optional() }),
  tool("resolve", "Read the version pinned to this Token's RUNNING executor session, including sensitive values for the authorized task. Keep the response only in memory.",
    { projectId: id, id, sessionId: id }),
  tool("save", "Create/update a configuration with expectedVersion. After an uncertain write query metadata before retrying. Documents use Base64; structured secrets use JSON fields.", save),
  tool("batch", "Save 1..100 configurations atomically in one project. After an uncertain write inspect metadata before retrying.",
    { entries: z.array(z.object(save)).min(1).max(100) }),
  tool("select_version", "Select a historical version for future sessions, with a reason and optimistic version check. Existing sessions stay pinned.",
    { projectId: id, id, version, expectedVersion: version, reason: z.string().min(1).max(500) }),
  tool("retry_parse", "Retry parsing an existing document version without replacing its original bytes.", { projectId: id, id, version }),
];
