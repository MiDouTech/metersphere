import { z } from "zod";
import type { MeterSphereClient } from "../client.js";

const id = z.string().min(1).max(128);
const task = { projectId: id, taskId: z.string().min(1).max(64) };
const tool = (action: string, description: string, inputSchema: z.ZodRawShape) => ({
  name: `metersphere.functional.export.${action}`, description, inputSchema,
  handler: (client: MeterSphereClient, args: Record<string, unknown>) =>
    client.callNativeTool(`metersphere.functional.export.${action}`, args),
});

export const caseExportTools = [
  tool("start", "Create a durable Excel/XMind export. Poll status, then read download chunks. request uses the web export field/range schema.", {
    projectId: id, format: z.enum(["xlsx", "xmind"]),
    requestId: z.string().regex(/^[A-Za-z0-9_-]{1,64}$/), request: z.record(z.unknown()),
  }),
  tool("status", "Read your export task state and safe error details.", task),
  tool("retry", "Retry a failed, cancelled or expired export with original filters and CURRENT content. Creates a linked new task; reuse requestId if a response is lost.", {
    ...task, requestId: z.string().regex(/^[A-Za-z0-9_-]{1,64}$/),
  }),
  tool("stop", "Stop your export with a stable requestId; a completed task stays successful.", {
    ...task, requestId: z.string().regex(/^[A-Za-z0-9_-]{1,64}$/),
  }),
  tool("download", "Read a freshly authorized base64 chunk. Append bytes in offset order until eof; no public download URL.", {
    ...task, offset: z.number().int().min(0).max(67108864).optional(),
    length: z.number().int().min(1).max(65536).optional(),
  }),
];
