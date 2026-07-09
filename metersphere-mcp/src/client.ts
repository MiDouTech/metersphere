import fs from "node:fs";
import path from "node:path";

export interface MsConfig {
  baseUrl: string;
  agentToken: string;
  projectId: string;
  testPlanId?: string;
}

export interface MsResultHolder<T = unknown> {
  code?: number;
  data?: T;
  message?: string;
}

export class MsApiError extends Error {
  status: number;
  body: string;

  constructor(status: number, body: string) {
    super(`MeterSphere API error ${status}: ${body}`);
    this.status = status;
    this.body = body;
    this.name = "MsApiError";
  }
}

export function loadConfig(): MsConfig {
  const baseUrl = process.env.MS_BASE_URL?.replace(/\/$/, "");
  const agentToken = process.env.MS_AGENT_TOKEN;
  const projectId = process.env.MS_PROJECT_ID;
  const testPlanId = process.env.MS_TEST_PLAN_ID;

  const missing: string[] = [];
  if (!baseUrl) missing.push("MS_BASE_URL");
  if (!agentToken) missing.push("MS_AGENT_TOKEN");
  if (!projectId) missing.push("MS_PROJECT_ID");
  if (missing.length > 0) {
    throw new Error(`Missing required environment variables: ${missing.join(", ")}`);
  }

  return {
    baseUrl: baseUrl!,
    agentToken: agentToken!,
    projectId: projectId!,
    testPlanId,
  };
}

export class MeterSphereClient {
  constructor(private readonly config: MsConfig) {}

  private headers(contentType = "application/json"): Record<string, string> {
    return {
      Authorization: `Bearer ${this.config.agentToken}`,
      "X-MS-PROJECT": this.config.projectId,
      PROJECT: this.config.projectId,
      "Content-Type": contentType,
      Accept: "application/json",
    };
  }

  private async request<T>(method: string, path: string, body?: unknown): Promise<T> {
    const url = `${this.config.baseUrl}${path}`;
    const response = await fetch(url, {
      method,
      headers: this.headers(),
      body: body === undefined ? undefined : JSON.stringify(body),
    });

    const text = await response.text();
    if (!response.ok) {
      throw new MsApiError(response.status, text);
    }

    if (!text) {
      return undefined as T;
    }

    const parsed = JSON.parse(text) as MsResultHolder<T> | T;
    if (parsed && typeof parsed === "object" && "data" in parsed && "code" in parsed) {
      return (parsed as MsResultHolder<T>).data as T;
    }
    return parsed as T;
  }

  searchFunctionalCases(params: {
    query?: string;
    testPlanId?: string;
    includeSteps?: boolean;
    current?: number;
    pageSize?: number;
    filters?: {
      priority?: string[];
      lastExecuteResult?: string[];
      tags?: string[];
      moduleIds?: string[];
    };
  }) {
    const payload = {
      ...params,
      testPlanId: params.testPlanId ?? this.config.testPlanId,
    };
    return this.request("POST", "/api/agent/v1/functional/search", payload);
  }

  getFunctionalCase(caseId: string, includeSteps = true, testPlanId?: string) {
    const query = new URLSearchParams();
    query.set("includeSteps", String(includeSteps));
    if (testPlanId ?? this.config.testPlanId) {
      query.set("testPlanId", (testPlanId ?? this.config.testPlanId)!);
    }
    return this.request("GET", `/api/agent/v1/functional/${encodeURIComponent(caseId)}?${query}`);
  }

  listModules(projectId?: string) {
    const pid = projectId ?? this.config.projectId;
    const query = new URLSearchParams({ projectId: pid });
    return this.request("GET", `/api/agent/v1/functional/modules?${query}`);
  }

  submitFunctionalResult(payload: {
    projectId?: string;
    caseId: string;
    testPlanId?: string;
    testPlanCaseId?: string;
    lastExecResult: string;
    executedBy?: string;
    steps?: Array<{
      id?: string;
      num?: number;
      desc?: string;
      expected?: string;
      actualResult?: string;
      executeResult?: string;
    }>;
    content?: string;
    attachmentIds?: string[];
  }) {
    const body = {
      ...payload,
      projectId: payload.projectId ?? this.config.projectId,
      testPlanId: payload.testPlanId ?? this.config.testPlanId,
    };
    return this.request("POST", "/api/agent/v1/functional/submit", body);
  }

  submitFunctionalResultsBatch(payload: {
    projectId?: string;
    testPlanId?: string;
    executedBy?: string;
    failFast?: boolean;
    results: Array<{
      caseId: string;
      testPlanCaseId?: string;
      lastExecResult: string;
      steps?: Array<Record<string, unknown>>;
      content?: string;
      attachmentIds?: string[];
    }>;
  }) {
    const body = {
      ...payload,
      projectId: payload.projectId ?? this.config.projectId,
      testPlanId: payload.testPlanId ?? this.config.testPlanId,
    };
    return this.request("POST", "/api/agent/v1/functional/submit/batch", body);
  }

  async uploadExecutionAttachment(payload: {
    projectId?: string;
    filePath: string;
    stepNum?: number;
  }) {
    const projectId = payload.projectId ?? this.config.projectId;
    const absolutePath = path.resolve(payload.filePath);
    const buffer = fs.readFileSync(absolutePath);
    const fileName = path.basename(absolutePath);
    const form = new FormData();
    form.append("file", new Blob([buffer]), fileName);
    form.append("projectId", projectId);
    if (payload.stepNum !== undefined) {
      form.append("stepNum", String(payload.stepNum));
    }

    const response = await fetch(`${this.config.baseUrl}/api/agent/v1/functional/attachment/upload`, {
      method: "POST",
      headers: {
        Authorization: `Bearer ${this.config.agentToken}`,
        "X-MS-PROJECT": projectId,
        PROJECT: projectId,
        Accept: "application/json",
      },
      body: form,
    });
    const text = await response.text();
    if (!response.ok) {
      throw new MsApiError(response.status, text);
    }
    const parsed = JSON.parse(text) as MsResultHolder<unknown> | unknown;
    if (parsed && typeof parsed === "object" && "data" in parsed && "code" in parsed) {
      return (parsed as MsResultHolder<unknown>).data;
    }
    return parsed;
  }
}
