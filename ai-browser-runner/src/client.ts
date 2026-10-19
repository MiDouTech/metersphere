import { mkdir, readFile, open, link, unlink } from "node:fs/promises";
import { resolve, join } from "node:path";
import { constants, createHash, createCipheriv, randomBytes, createDecipheriv, generateKeyPairSync, privateDecrypt, randomUUID } from "node:crypto";
import type {
  ArtifactResponse, LeaseAssignment, RunnerControl, RunnerEvent, RuntimeCredential, TestDataLease,
} from "./types.js";
import { RunnerError, sanitize } from "./security.js";

export class RunnerClient {
  constructor(private readonly apiUrl: string, private readonly runnerId: string,
              private readonly runnerToken: string) {}

  async heartbeat(activeCount: number): Promise<void> {
    await this.request("/heartbeat", this.runnerToken, {
      method: "POST",
      body: JSON.stringify({ runnerId: this.runnerId, activeCount }),
    });
  }

  async poll(): Promise<LeaseAssignment | undefined> {
    const response = await this.request("/lease/poll", this.runnerToken, {
      method: "POST",
      body: JSON.stringify({ runnerId: this.runnerId }),
    }, true);
    return response.status === 204 ? undefined : await response.json() as LeaseAssignment;
  }

  async renew(assignment: LeaseAssignment): Promise<void> {
    await this.request(`/lease/${assignment.leaseId}/heartbeat`, assignment.leaseToken, { method: "POST" });
  }

  async control(assignment: LeaseAssignment): Promise<RunnerControl> {
    const response = await this.request(`/lease/${assignment.leaseId}/control`, assignment.leaseToken);
    return await response.json() as RunnerControl;
  }

  async state(assignment: LeaseAssignment, status: string, reason?: string): Promise<void> {
    await this.request(`/lease/${assignment.leaseId}/state`, assignment.leaseToken, {
      method: "POST",
      body: JSON.stringify({ status, reason }),
    });
  }

  async events(assignment: LeaseAssignment, events: RunnerEvent[]): Promise<void> {
    if (events.length === 0) return;
    await this.request(`/lease/${assignment.leaseId}/events:batch`, assignment.leaseToken, {
      method: "POST",
      body: JSON.stringify({ leaseId: assignment.leaseId, events }),
    });
  }

  async stepResult(assignment: LeaseAssignment, stepId: string, actuals: unknown[], artifactIds: string[], status: "SUCCESS" | "FAILED" | "BLOCKED" = "SUCCESS", errorCode?: string, assertionIds?: string[]): Promise<void> {
    const facts = { stepId, status, errorCode, assertionResult: JSON.stringify(actuals.map((actual, index) => assertionIds ? ({ assertionId: assertionIds[index], actual }) : ({ actual }))),
      artifactIds: [...new Set(artifactIds)].sort() };
    const digest = createHash("sha256").update(JSON.stringify({ leaseId: assignment.leaseId, facts })).digest("hex");
    const directory = resolve(process.env.MS_RUNNER_STATE_DIR ?? ".runner-state");
    await mkdir(directory, { recursive: true, mode: 0o700 });
    const file = join(directory, `${digest}.json`);
    let body: string;
    try {
      body = revealPending(await readFile(file, "utf8"));
    } catch (error) {
      if ((error as NodeJS.ErrnoException).code !== "ENOENT") throw error;
      body = JSON.stringify({ ...facts, requestId: randomUUID() });
      const temporary = join(directory, `${digest}.${randomUUID()}.tmp`);
      const protectedBody = protectPending(body);
      const handle = await open(temporary, "wx", 0o600);
      try { await handle.writeFile(protectedBody, "utf8"); await handle.sync(); }
      finally { await handle.close(); }
      try { await link(temporary, file); }
      catch (collision) {
        if ((collision as NodeJS.ErrnoException).code !== "EEXIST") throw collision;
        body = revealPending(await readFile(file, "utf8"));
      } finally { await unlink(temporary); }
    }
    for (let attempt = 0; ; attempt++) {
      try {
        await this.request(`/lease/${assignment.leaseId}/step-result`, assignment.leaseToken, { method: "POST", body });
        return;
      } catch (error) {
        if (!(error instanceof RunnerError) || error.category !== "RUNNER_CONTROL_PLANE_UNAVAILABLE" || attempt >= 2) throw error;
        await new Promise(resolve => setTimeout(resolve, 200 * (attempt + 1)));
      }
    }
  }

  async artifact(assignment: LeaseAssignment, bytes: Buffer, fileName: string, purpose: string,
                 sha256: string, caseId?: string, stepId?: string, contentType = "image/png"): Promise<ArtifactResponse> {
    const form = new FormData();
    const copy = Uint8Array.from(bytes);
    form.append("file", new Blob([copy.buffer], { type: contentType }), fileName);
    form.append("purpose", purpose);
    form.append("sha256", sha256);
    form.append("redacted", "true");
    if (caseId) form.append("caseId", caseId);
    if (stepId) form.append("stepId", stepId);
    const response = await this.request(`/lease/${assignment.leaseId}/artifact`, assignment.leaseToken, {
      method: "POST",
      body: form,
    }, false, false);
    return await response.json() as ArtifactResponse;
  }

  async resolveCredential(assignment: LeaseAssignment, referenceId: string): Promise<RuntimeCredential> {
    const { publicKey, privateKey } = generateKeyPairSync("rsa", { modulusLength: 3072,
      publicKeyEncoding: { type: "spki", format: "pem" }, privateKeyEncoding: { type: "pkcs8", format: "pem" } });
    const response = await this.request(`/tasks/${assignment.task.id}/credentials/${referenceId}/resolve`, assignment.leaseToken, {
      method: "POST", body: JSON.stringify({ leaseId: assignment.leaseId, purpose: "AUTOMATIC_LOGIN", runnerPublicKey: publicKey }),
    });
    const envelope = await response.json() as { algorithm: string; encryptedKey: string; iv: string; encryptedPayload: string; secretVersion?: string; expiresAt?: number };
    if (envelope.algorithm !== "RSA-OAEP-256+A256GCM") throw new RunnerError("CREDENTIAL_PROTOCOL_UNSUPPORTED", "不支持的凭据加密协议");
    const dataKey = privateDecrypt({ key: privateKey, padding: constants.RSA_PKCS1_OAEP_PADDING, oaepHash: "sha256" }, Buffer.from(envelope.encryptedKey, "base64"));
    const encrypted = Buffer.from(envelope.encryptedPayload, "base64");
    if (encrypted.length < 17) throw new RunnerError("CREDENTIAL_PAYLOAD_INVALID", "运行时凭据密文无效");
    const tag = encrypted.subarray(encrypted.length - 16); const ciphertext = encrypted.subarray(0, encrypted.length - 16);
    const decipher = createDecipheriv("aes-256-gcm", dataKey, Buffer.from(envelope.iv, "base64")); decipher.setAuthTag(tag);
    const plaintext = Buffer.concat([decipher.update(ciphertext), decipher.final()]); dataKey.fill(0);
    try { const parsed = JSON.parse(plaintext.toString("utf8")) as { username?: string; value?: string }; if (!parsed.value) throw new RunnerError("CREDENTIAL_PAYLOAD_INVALID", "运行时凭据内容无效"); return { username: parsed.username ?? "", value: parsed.value, secretVersion: envelope.secretVersion, expiresAt: envelope.expiresAt }; }
    finally { plaintext.fill(0); }
  }

  async acquireTestData(assignment: LeaseAssignment, datasetId: string, dataKey: string): Promise<TestDataLease> {
    const response = await this.request(`/tasks/${assignment.task.id}/test-data/leases`, assignment.leaseToken, {
      method: "POST", body: JSON.stringify({ leaseId: assignment.leaseId, datasetId, dataKey, ttlMs: 60_000 }),
    });
    return await response.json() as TestDataLease;
  }

  async testDataContent(assignment: LeaseAssignment, lease: TestDataLease): Promise<Buffer> {
    const response = await this.request(`/test-data/leases/${lease.id}/content?runnerLeaseId=${encodeURIComponent(assignment.leaseId)}`,
      assignment.leaseToken, { headers: { "X-Test-Data-Lease-Token": lease.leaseToken } }, false, false);
    return Buffer.from(await response.arrayBuffer());
  }

  async releaseTestData(assignment: LeaseAssignment, lease: TestDataLease): Promise<void> {
    await this.request(`/test-data/leases/${lease.id}/release?runnerLeaseId=${encodeURIComponent(assignment.leaseId)}`,
      assignment.leaseToken, { method: "POST", body: JSON.stringify({ leaseToken: lease.leaseToken }) });
  }

  async complete(assignment: LeaseAssignment, outcome: "COMPLETED" | "FAILED" | "CANCELED",
                 reason?: string): Promise<void> {
    await this.request(`/lease/${assignment.leaseId}/complete`, assignment.leaseToken, {
      method: "POST",
      body: JSON.stringify({ outcome, reason }),
    });
  }

  private async request(path: string, token: string, init: RequestInit = {}, returnResponse = false,
                        jsonContent = true): Promise<Response> {
    const headers = new Headers(init.headers);
    headers.set("Authorization", `Bearer ${token}`);
    if (jsonContent && init.body) headers.set("Content-Type", "application/json");
    let response: Response;
    try {
      response = await fetch(`${this.apiUrl}/internal/ai-runner/v1${path}`, { ...init, headers });
    } catch (error) {
      throw new RunnerError("RUNNER_CONTROL_PLANE_UNAVAILABLE", "无法连接 MeterSphere", error);
    }
    if (!response.ok && !(returnResponse && response.status === 204)) {
      const detail = sanitize(await response.text());
      throw new RunnerError("RUNNER_PROTOCOL_ERROR", `MeterSphere 返回 HTTP ${response.status}: ${detail}`);
    }
    return response;
  }
}

export class EventBuffer {
  private readonly pending: RunnerEvent[] = [];
  private nextSequence: number;

  constructor(private readonly client: RunnerClient, private readonly assignment: LeaseAssignment) {
    this.nextSequence = assignment.nextEventSequence;
  }

  add(event: Omit<RunnerEvent, "contractVersion" | "eventId" | "attempt" | "sequence" | "eventTime"> & { attempt?: number }): void {
    this.pending.push({ ...event, contractVersion: "v1", eventId: randomUUID(), attempt: event.attempt ?? 0,
      sequence: this.nextSequence++, eventTime: Date.now() });
  }

  async flush(): Promise<void> {
    while (this.pending.length > 0) {
      const batch = this.pending.slice(0, 100);
      await this.client.events(this.assignment, batch);
      this.pending.splice(0, batch.length);
    }
  }
}

function stateKey(): Buffer {
  const key = Buffer.from(process.env.MS_RUNNER_STATE_KEY ?? "", "base64");
  if (key.length !== 32) throw new RunnerError("RUNNER_STATE_KEY_REQUIRED", "敏感待发送事实需要配置本地加密密钥");
  return key;
}
function protectPending(body: string): string {
  if (sanitize(body) === body && !/"(?:password|passwd|token|secret|authorization|cookie)"\s*:/i.test(body)) return body;
  const iv = randomBytes(12); const cipher = createCipheriv("aes-256-gcm", stateKey(), iv);
  const encrypted = Buffer.concat([cipher.update(body, "utf8"), cipher.final()]);
  return JSON.stringify({ format: "pending-encrypted-v1", iv: iv.toString("base64"), tag: cipher.getAuthTag().toString("base64"), value: encrypted.toString("base64") });
}
function revealPending(stored: string): string {
  const envelope = JSON.parse(stored) as { format?: string; iv: string; tag: string; value: string };
  if (envelope.format !== "pending-encrypted-v1") return stored;
  const decipher = createDecipheriv("aes-256-gcm", stateKey(), Buffer.from(envelope.iv, "base64"));
  decipher.setAuthTag(Buffer.from(envelope.tag, "base64"));
  return Buffer.concat([decipher.update(Buffer.from(envelope.value, "base64")), decipher.final()]).toString("utf8");
}
