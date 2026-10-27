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
  readonly status: number;
  readonly code: string;
  readonly traceId?: string;
  constructor(status: number, body: string) {
    let value: Record<string, unknown> = {};
    try { const parsed = JSON.parse(body); if (parsed && typeof parsed === "object") value = parsed; } catch { /* opaque server failure */ }
    // Older deployments wrap Agent error DTOs in ResultHolder even for HTTP 4xx/5xx.
    if (value.data && typeof value.data === "object" && !Array.isArray(value.data)) {
      const nested = value.data as Record<string, unknown>;
      if (typeof nested.code === "string") value = nested;
    }
    const code = typeof value.code === "string" && /^[A-Z][A-Z0-9_]{2,80}$/.test(value.code) ? value.code : "MCP_HTTP_ERROR";
    const hints: Record<string,string>={
      PERSONAL_EXECUTION_WRITE_DISABLED:"个人执行写入暂时关闭，请稍后使用原 requestId 查询或重试",MCP_BUSINESS_WRITE_DISABLED:"MCP 业务写入暂时关闭，请稍后使用原 requestId 查询或重试",
      EVIDENCE_EXPIRED:"图片暂存已过期，请重新上传",ATTACHMENT_EXPIRED:"附件已过期，请重新上传",
      VERSION_CONFLICT:"用例内容已变化，请重新查询并执行",IDEMPOTENCY_CONFLICT:"相同请求标识对应的参数不同，请先核对原操作",
      QUALITY_EVIDENCE_REQUIRED:"请上传至少一张本次用例执行图片",QUALITY_EVIDENCE_INVALID:"图片无效、过期或不属于当前用例，请核对后重新上传",
      REQUEST_IN_PROGRESS:"请求仍在处理中，请稍后使用原 requestId 查询或重试",
      IDEMPOTENCY_LEGACY_RECONCILIATION_REQUIRED:"历史请求需要核对，请先查询原资源，不要重复创建",
      AGENT_FACTS_INVALID:"历史执行事实暂时无法读取，请提供 traceId 排查",
      CASE_CONTENT_MISSING:"当前用例类型或步骤不满足个人执行要求，请查询 contract 的限制",
    };
    const message = hints[code] ?? (status === 401 ? "身份已失效，请重新配置令牌" : status === 403 ? "没有此操作权限，请检查用户角色和令牌范围"
      : status === 409 ? "请求与当前数据冲突，请先查询结果或刷新版本" : "请求失败，请检查参数、权限或服务状态；响应不明确时先查询结果");
    super(message);
    this.status = status; this.code = code;
    this.traceId = typeof value.traceId === "string" && /^[A-Za-z0-9_-]{1,64}$/.test(value.traceId) ? value.traceId : undefined;
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

  async callNativeTool(name: string, args: Record<string, unknown>): Promise<{
    content: Array<{ type: "text"; text: string } | { type: "image"; data: string; mimeType: string }>;
    isError?: boolean;
  }> {
    try {
      const response = await this.request<{
        result?: { content: Array<{ type: "text"; text: string } | { type: "image"; data: string; mimeType: string }>; isError?: boolean };
        error?: { code: number; message: string; data?: unknown };
      }>("POST", "/api/mcp", {
        jsonrpc: "2.0", id: crypto.randomUUID(), method: "tools/call", params: { name, arguments: args },
      });
      if (response.error) {
        const safe = new MsApiError(400, JSON.stringify(response.error.data ?? {}));
        return { isError: true, content: [{ type: "text", text: JSON.stringify({
          code: response.error.code, message: safe.message,
          data: { code: safe.code, ...(safe.traceId ? { traceId: safe.traceId } : {}) },
        }) }] };
      }
      if (!response.result || !Array.isArray(response.result.content)) {
        throw new Error("MeterSphere MCP response is invalid");
      }
      return response.result;
    } catch (error) {
      return { isError: true, content: [{ type: "text", text: JSON.stringify({
        code: error instanceof MsApiError ? error.code : "MCP_TRANSPORT_ERROR",
        message: error instanceof MsApiError ? error.message : "请求结果未知，请使用原 requestId 查询或重试",
        ...(error instanceof MsApiError && error.traceId ? {traceId:error.traceId} : {}),
        ...(error instanceof MsApiError ? { status: error.status } : {}),
      }) }] };
    }
  }

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
      signal: AbortSignal.timeout(30_000),
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
      const holder = parsed as MsResultHolder<T>;
      if (holder.code !== 100200) throw new MsApiError(400, text);
      return holder.data as T;
    }
    return parsed as T;
  }

  searchFunctionalCases(params: {
    versionId?: string;
    refId?: string;
    identityType?: "ID" | "NUMBER" | "NAME";
    projectId?: string;
    query?: string;
    testPlanId?: string;
    includeSteps?: boolean;
    current?: number;
    pageSize?: number;
    filters?: {
      keyword?: string;
      includeDescendants?: boolean;
      tagMatch?: "ANY" | "ALL";
      reviewStatus?: string[];
      priority?: string[];
      lastExecuteResult?: string[];
      tags?: string[];
      moduleIds?: string[];
    };
  }) {
    const payload = {
      ...params,
      testPlanId: params.testPlanId,
    };
    return this.request("POST", "/api/agent/v1/functional/search", payload);
  }

  getFunctionalCase(caseId: string, includeSteps = true, testPlanId?: string) {
    const query = new URLSearchParams();
    query.set("includeSteps", String(includeSteps));
    if (testPlanId) {
      query.set("testPlanId", testPlanId);
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
    executionTaskId?: string;
    idempotencyKey?: string;
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
    executionTaskId?: string;
    failFast?: boolean;
    results: Array<{
      caseId: string;
      testPlanCaseId?: string;
      lastExecResult: string;
      executionTaskId?: string;
      idempotencyKey?: string;
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

  createProject(payload: {
    organizationId: string;
    name: string;
    description?: string;
    userIds: string[];
    moduleIds?: string[];
    resourcePoolIds?: string[];
    allResourcePool?: boolean;
  }) {
    return this.request("POST", "/api/agent/v1/project/create", payload);
  }

  addProjectMembers(payload: {
    projectId: string;
    userIds: string[];
    userRoleIds?: string[];
  }) {
    return this.request("POST", "/api/agent/v1/project/members/add", payload);
  }

  getProject(projectId: string) {
    return this.request("GET", `/api/agent/v1/project/${encodeURIComponent(projectId)}`);
  }

  searchProjects(payload: {
    keyword?: string;
    limit?: number;
  }) {
    const query = new URLSearchParams();
    if (payload.keyword) {
      query.set("keyword", payload.keyword);
    }
    if (payload.limit) {
      query.set("limit", String(payload.limit));
    }
    const suffix = query.toString() ? `?${query.toString()}` : "";
    return this.request("GET", `/api/agent/v1/project/search${suffix}`);
  }

  createFunctionalModule(payload: {
    requestId: string;
    projectId?: string;
    name?: string;
    parentId?: string;
    modulePath?: string;
  }) {
    return this.request("POST", "/api/agent/v1/functional/module/create", {
      ...payload,
      projectId: payload.projectId ?? this.config.projectId,
    });
  }

  createFunctionalCase(payload: Record<string, unknown>) {
    return this.request("POST", "/api/agent/v1/functional/case/create", {
      ...payload,
      projectId: payload.projectId ?? this.config.projectId,
    });
  }

  batchCreateFunctionalCases(payload: {
    requestId: string;
    projectId?: string;
    moduleId?: string;
    modulePath?: string;
    templateId?: string;
    cases: Array<Record<string, unknown>>;
    failFast?: boolean;
  }) {
    return this.request("POST", "/api/agent/v1/functional/case/batch-create", {
      ...payload,
      projectId: payload.projectId ?? this.config.projectId,
    });
  }

  createTestPlan(payload: {
    requestId: string;
    projectId?: string;
    name: string;
    description?: string;
    moduleId?: string;
    caseIds?: string[];
    automaticStatusUpdate?: boolean;
    repeatCase?: boolean;
    passThreshold?: number;
  }) {
    return this.request("POST", "/api/agent/v1/test-plan/create", {
      ...payload,
      projectId: payload.projectId ?? this.config.projectId,
    });
  }

  associateTestPlanCases(payload: {
    requestId: string;
    projectId?: string;
    testPlanId: string;
    caseIds: string[];
    collectionId?: string;
  }) {
    return this.request("POST", "/api/agent/v1/test-plan/associate-cases", {
      ...payload,
      projectId: payload.projectId ?? this.config.projectId,
    });
  }

  getTestPlan(testPlanId: string) {
    return this.request("GET", `/api/agent/v1/test-plan/${encodeURIComponent(testPlanId)}`);
  }

  searchTestPlans(payload: {
    projectId?: string;
    keyword?: string;
    status?: string;
    includeArchived?: boolean;
    page?: number;
    pageSize?: number;
  }) {
    return this.request("POST", "/api/agent/v1/test-plan/search", {
      ...payload,
      projectId: payload.projectId ?? this.config.projectId,
    });
  }

  getTestPlanCases(payload: {
    projectId?: string;
    testPlanId: string;
    current?: number;
    pageSize?: number;
    includeSteps?: boolean;
  }) {
    const query = new URLSearchParams();
    query.set("projectId", payload.projectId ?? this.config.projectId);
    if (payload.current) query.set("current", String(payload.current));
    if (payload.pageSize) query.set("pageSize", String(payload.pageSize));
    if (payload.includeSteps !== undefined) query.set("includeSteps", String(payload.includeSteps));
    return this.request(
      "GET",
      `/api/agent/v1/test-plan/${encodeURIComponent(payload.testPlanId)}/cases?${query.toString()}`
    );
  }

  resolveExecutionScope(payload: Record<string, unknown>) {
    return this.request("POST", "/api/ai/execution/resolve", {
      ...payload,
      projectId: (payload.projectId as string | undefined) ?? this.config.projectId,
    });
  }

  createExecutionTask(payload: {
    projectId?: string;
    testPlanId?: string;
    caseIds?: string[];
    source?: string;
    environmentId?: string;
    targetUrl?: string;
    browserType?: string;
    loginMode?: string;
    providerId?: string;
    runnerId?: string;
    executedBy?: string;
    idempotencyKey?: string;
    confirmed?: boolean;
  }) {
    return this.request("POST", "/api/ai/execution/task", {
      ...payload,
      projectId: payload.projectId ?? this.config.projectId,
      testPlanId: payload.testPlanId ?? this.config.testPlanId,
    });
  }

  getExecutionTask(executionTaskId: string) {
    return this.request("GET", `/api/ai/execution/task/${encodeURIComponent(executionTaskId)}`);
  }

  getExecutionEvents(payload: {
    executionTaskId: string;
    cursor?: number;
    limit?: number;
  }) {
    const query = new URLSearchParams();
    if (payload.cursor !== undefined) query.set("cursor", String(payload.cursor));
    if (payload.limit !== undefined) query.set("limit", String(payload.limit));
    const suffix = query.toString() ? `?${query.toString()}` : "";
    return this.request("GET", `/api/ai/execution/task/${encodeURIComponent(payload.executionTaskId)}/events${suffix}`);
  }

  cancelExecutionTask(payload: { executionTaskId: string; reason?: string }) {
    return this.request("POST", `/api/ai/execution/task/${encodeURIComponent(payload.executionTaskId)}/cancel`, {
      reason: payload.reason,
    });
  }

  resumeExecutionTask(payload: { executionTaskId: string; reason?: string }) {
    return this.request("POST", `/api/ai/execution/task/${encodeURIComponent(payload.executionTaskId)}/login-ready`, {
      reason: payload.reason,
    });
  }

  createCaseReview(payload: {
    requestId: string;
    projectId?: string;
    name: string;
    moduleId?: string;
    reviewPassRule?: string;
    reviewers?: string[];
    description?: string;
    caseIds?: string[];
    tags?: string[];
  }) {
    return this.request("POST", "/api/agent/v1/case-review/create", {
      ...payload,
      projectId: payload.projectId ?? this.config.projectId,
    });
  }

  associateCaseReviewCases(payload: {
    requestId: string;
    projectId?: string;
    reviewId: string;
    caseIds: string[];
    reviewers?: string[];
  }) {
    return this.request("POST", "/api/agent/v1/case-review/associate-cases", {
      ...payload,
      projectId: payload.projectId ?? this.config.projectId,
    });
  }

  getCaseReview(reviewId: string) {
    return this.request("GET", `/api/agent/v1/case-review/${encodeURIComponent(reviewId)}`);
  }

  createBug(payload: {
    requestId: string;
    projectId?: string;
    title: string;
    description?: string;
    templateId?: string;
    tags?: string[];
    caseId?: string;
    caseType?: string;
    testPlanId?: string;
    testPlanCaseId?: string;
    customFields?: Record<string, string>;
  }) {
    return this.request("POST", "/api/agent/v1/bug/create", {
      ...payload,
      projectId: payload.projectId ?? this.config.projectId,
    });
  }

  searchBugs(payload: {
    projectId?: string;
    query?: string;
    status?: string[];
    handleUserIds?: string[];
    current?: number;
    pageSize?: number;
  }) {
    return this.request("POST", "/api/agent/v1/bug/search", {
      ...payload,
      projectId: payload.projectId ?? this.config.projectId,
    });
  }

  getBug(bugId: string) {
    return this.request("GET", `/api/agent/v1/bug/${encodeURIComponent(bugId)}`);
  }

  updateBug(payload: {
    projectId?: string;
    bugId: string;
    title?: string;
    description?: string;
    tags?: string[];
    templateId?: string;
    customFields?: Record<string, string>;
  }) {
    return this.request("POST", "/api/agent/v1/bug/update", {
      ...payload,
      projectId: payload.projectId ?? this.config.projectId,
    });
  }

  relateBugCase(payload: {
    projectId?: string;
    bugId: string;
    caseIds: string[];
    caseType?: string;
  }) {
    return this.request("POST", "/api/agent/v1/bug/relate-case", {
      ...payload,
      projectId: payload.projectId ?? this.config.projectId,
    });
  }

  getExecLog(id: string) {
    return this.request("GET", `/api/agent/v1/functional/exec-log/${encodeURIComponent(id)}`);
  }
}
