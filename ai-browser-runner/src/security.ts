import path from "node:path";
import type { RunnerConfig } from "./types.js";

const SECRET_PATTERN = /(authorization|cookie|set-cookie|password|passwd|token|secret)\s*[:=]\s*[^,;\s}]+/gi;
const BEARER_PATTERN = /bearer\s+[a-z0-9._~-]+/gi;

export function sanitize(value: unknown): string {
  const text = typeof value === "string" ? value : JSON.stringify(value ?? {});
  return text.replace(SECRET_PATTERN, "$1=***").replace(BEARER_PATTERN, "Bearer ***").slice(0, 65_535);
}

export function assertAllowedUrl(rawUrl: string, allowedOrigins: ReadonlySet<string>): URL {
  let url: URL;
  try {
    url = new URL(rawUrl);
  } catch {
    throw new RunnerError("NAVIGATION_URL_INVALID", "目标 URL 无效");
  }
  if (!(["http:", "https:"] as string[]).includes(url.protocol) || url.username || url.password) {
    throw new RunnerError("SECURITY_DOMAIN_NOT_ALLOWED", "仅允许无内嵌凭据的 HTTP(S) URL");
  }
  if (!allowedOrigins.has(url.origin)) {
    throw new RunnerError("SECURITY_DOMAIN_NOT_ALLOWED", `目标域名未在白名单中: ${url.origin}`);
  }
  return url;
}

export function resolveValue(value: string | undefined, valueRef: string | undefined,
                             values: Readonly<Record<string, string>>): string {
  if (valueRef) {
    if (!(valueRef in values)) {
      throw new RunnerError("SECURITY_SECRET_REFERENCE_NOT_FOUND", `凭据引用不存在: ${valueRef}`);
    }
    return values[valueRef];
  }
  return value ?? "";
}

export function resolveUploadPath(candidate: string, config: RunnerConfig): string {
  if (!config.uploadRoot) {
    throw new RunnerError("SECURITY_UPLOAD_ROOT_REQUIRED", "UPLOAD 动作未配置受控目录");
  }
  const root = path.resolve(config.uploadRoot);
  const resolved = path.resolve(root, candidate);
  const relative = path.relative(root, resolved);
  if (relative.startsWith("..") || path.isAbsolute(relative)) {
    throw new RunnerError("SECURITY_LOCAL_FILE_NOT_ALLOWED", "上传文件超出受控目录");
  }
  return resolved;
}

export class RunnerError extends Error {
  constructor(public readonly category: string, message: string, public readonly cause?: unknown) {
    super(message);
    this.name = "RunnerError";
  }
}
