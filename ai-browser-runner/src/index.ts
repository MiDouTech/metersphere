#!/usr/bin/env node
import { RunnerClient } from "./client.js";
import { executeAssignment } from "./executor.js";
import { RunnerError, sanitize } from "./security.js";
import type { RunnerConfig } from "./types.js";

const config = loadConfig(process.env);
const client = new RunnerClient(config.apiUrl, config.runnerId, config.runnerToken);
let stopping = false;
let activeCount = 0;

process.on("SIGINT", () => { stopping = true; });
process.on("SIGTERM", () => { stopping = true; });

await client.heartbeat(0);
const heartbeat = setInterval(() => void client.heartbeat(activeCount).catch(error => logError(error)), 30_000);
try {
  while (!stopping) {
    const assignment = await client.poll();
    if (!assignment) {
      await delay(config.pollIntervalMs);
      continue;
    }
    activeCount = 1;
    await client.heartbeat(activeCount);
    await executeAssignment(config, client, assignment);
    activeCount = 0;
    await client.heartbeat(activeCount);
  }
} finally {
  clearInterval(heartbeat);
  await client.heartbeat(0).catch(() => undefined);
}

export function loadConfig(env: NodeJS.ProcessEnv): RunnerConfig {
  const apiUrl = required(env.MS_RUNNER_API_URL, "MS_RUNNER_API_URL").replace(/\/$/, "");
  const runnerId = required(env.MS_RUNNER_ID, "MS_RUNNER_ID");
  const runnerToken = required(env.MS_RUNNER_TOKEN, "MS_RUNNER_TOKEN");
  if (!runnerToken.startsWith("msrt_")) throw new RunnerError("RUNNER_CONFIG_INVALID", "MS_RUNNER_TOKEN 前缀错误");
  const origins = required(env.MS_RUNNER_ALLOWED_ORIGINS, "MS_RUNNER_ALLOWED_ORIGINS")
    .split(",").map(item => new URL(item.trim()).origin);
  let values: Record<string, string> = {};
  if (env.MS_RUNNER_VALUES_JSON) {
    const parsed = JSON.parse(env.MS_RUNNER_VALUES_JSON) as unknown;
    if (!parsed || typeof parsed !== "object" || Array.isArray(parsed)) {
      throw new RunnerError("RUNNER_CONFIG_INVALID", "MS_RUNNER_VALUES_JSON 必须为对象");
    }
    values = Object.fromEntries(Object.entries(parsed).filter((entry): entry is [string, string] => typeof entry[1] === "string"));
  }
  return {
    apiUrl,
    runnerId,
    runnerToken,
    allowedOrigins: new Set(origins),
    headless: env.MS_RUNNER_HEADLESS !== "false",
    pollIntervalMs: positiveInt(env.MS_RUNNER_POLL_INTERVAL_MS, 2_000),
    values,
    uploadRoot: env.MS_RUNNER_UPLOAD_ROOT,
    sensitiveSelectors: (env.MS_RUNNER_SENSITIVE_SELECTORS ?? "").split(",").map(item => item.trim()).filter(Boolean),
  };
}

function required(value: string | undefined, name: string): string {
  if (!value?.trim()) throw new RunnerError("RUNNER_CONFIG_INVALID", `缺少 ${name}`);
  return value.trim();
}

function positiveInt(value: string | undefined, fallback: number): number {
  const parsed = Number(value ?? fallback);
  if (!Number.isInteger(parsed) || parsed < 100 || parsed > 60_000) {
    throw new RunnerError("RUNNER_CONFIG_INVALID", "轮询间隔必须为 100..60000ms");
  }
  return parsed;
}

function logError(error: unknown): void {
  const message = error instanceof Error ? error.message : String(error);
  process.stderr.write(`[runner] ${sanitize(message)}\n`);
}

function delay(ms: number): Promise<void> {
  return new Promise(resolve => setTimeout(resolve, ms));
}
