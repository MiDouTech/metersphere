import { z } from "zod";
import type { MeterSphereClient } from "../client.js";

export const readBugImageTool = {
  name: "read_bug_image",
  description: "Read one saved defect image using imageRef from get_bug. Returns original PNG/JPEG image content; refresh detail for an expired reference. No URL fallback or automatic retry.",
  inputSchema: {
    projectId: z.string().min(1).max(128),
    bugId: z.string().min(1).max(128),
    imageRef: z.string().regex(/^bi1_[a-f0-9]{64}_[a-f0-9]{64}$/),
  },
  handler: (client: MeterSphereClient, args: Record<string, unknown>) => client.readBugImage(args),
};
