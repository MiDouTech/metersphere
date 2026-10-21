import { z } from "zod";
import type { MeterSphereClient } from "../client.js";
const id = z.string().min(1).max(128);
const fileOrRows = {
  projectId: id, templateId: id.optional(), fileName: z.string().max(200).optional(),
  base64: z.string().min(1).max(6990508).optional(),
  rows: z.array(z.object({ title: z.string().min(1).max(255), description: z.string().max(100000).optional(),
    handleUser: z.string().min(1).max(2000).optional(), customFields: z.record(z.string()).optional() }).strict()).min(1).max(100).optional(),
};
const forward = (name: string) => (client: MeterSphereClient, args: Record<string, unknown>) => client.callNativeTool(name, args);
export const bugTransferTools = [
  { name: "metersphere.bug.export.columns", description: "Read bug export columns; requires user export permission and BUG_READ.",
    inputSchema: { projectId: id }, handler: forward("metersphere.bug.export.columns") },
  { name: "metersphere.bug.export", description: "Export 1..100 explicit bugIds to a ZIP/XLSX authenticated temporary download (max 5 MiB).",
    inputSchema: { projectId: id, requestId: id, bugIds: z.array(id).min(1).max(100), exportColumns: z.array(z.object({ key: id, columnType: z.enum(["system", "custom", "other"]) }).strict()).min(1).max(50).optional() }, handler: forward("metersphere.bug.export") },
  { name: "metersphere.bug.import.preview", description: "Preview rows or base64 XLSX (exclusive), max 100 rows/5 MiB. Headers: title, description, handleUser, custom:<fieldId>.",
    inputSchema: fileOrRows, handler: forward("metersphere.bug.import.preview") },
  { name: "metersphere.bug.import.commit", description: "Import with previewHash and requestId; independent row transactions, persistent idempotency, explicit partial failures.",
    inputSchema: { ...fileOrRows, requestId: id, previewHash: z.string().length(64) }, handler: forward("metersphere.bug.import.commit") },
];
