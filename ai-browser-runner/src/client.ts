import { randomUUID } from "node:crypto";
import type {
  ArtifactResponse, LeaseAssignment, RunnerControl, RunnerEvent,
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

  async artifact(assignment: LeaseAssignment, bytes: Buffer, fileName: string, purpose: string,
                 sha256: string, caseId?: string, stepId?: string): Promise<ArtifactResponse> {
    const form = new FormData();
    const copy = Uint8Array.from(bytes);
    form.append("file", new Blob([copy.buffer], { type: "image/png" }), fileName);
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
