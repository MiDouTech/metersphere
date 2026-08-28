import { createHash } from "node:crypto";
import { access } from "node:fs/promises";
import { chromium, type Browser, type Locator, type Page } from "playwright";
import { EventBuffer, RunnerClient } from "./client.js";
import { parseAction, parseAssertions } from "./contract.js";
import { assertAllowedUrl, resolveUploadPath, resolveValue, RunnerError, sanitize } from "./security.js";
import type {
  ExecutionCase, ExecutionStep, LeaseAssignment, RunnerConfig, TestDataLease, WebAction, WebAssertion, WebLocator,
} from "./types.js";

interface EvidencePolicy {
  screenshotMode: "FAILURE_ONLY" | "AFTER_STEP";
  fullPage: boolean;
  sensitiveSelectors: string[];
}

export async function executeAssignment(config: RunnerConfig, client: RunnerClient,
                                        assignment: LeaseAssignment): Promise<void> {
  const events = new EventBuffer(client, assignment);
  let browser: Browser | undefined;
  let leaseHeartbeat: NodeJS.Timeout | undefined;
  let page: Page | undefined;
  let leaseFailures = 0;
  const dataLeases: TestDataLease[] = [];
  try {
    events.add(info("TASK_ACCEPTED", "Runner 已接受任务"));
    await events.flush();
    leaseHeartbeat = setInterval(() => void client.renew(assignment)
      .then(() => { leaseFailures = 0; })
      .catch(async () => {
        leaseFailures += 1;
        if (leaseFailures >= 2) await browser?.close().catch(() => undefined);
      }), 20_000);
    browser = await chromium.launch({ headless: config.headless });
    const context = await browser.newContext({
      viewport: { width: 1440, height: 900 },
      locale: "zh-CN",
      timezoneId: "Asia/Shanghai",
      acceptDownloads: false,
    });
    page = await context.newPage();
    page.setDefaultTimeout(10_000);
    page.on("pageerror", error => events.add(errorEvent("PAGE_ERROR", sanitize(error.message))));
    page.on("console", message => {
      if (message.type() === "error") events.add(errorEvent("CONSOLE_ERROR", sanitize(message.text())));
    });
    events.add(info("BROWSER_READY", "Chromium Context 已就绪"));

    if (assignment.task.targetUrl) {
      const url = assertAllowedUrl(assignment.task.targetUrl, config.allowedOrigins);
      await page.goto(url.toString(), { waitUntil: "domcontentloaded", timeout: 30_000 });
      assertAllowedUrl(page.url(), config.allowedOrigins);
    }
    if ((assignment.task.loginMode ?? "").toUpperCase() === "MANUAL") {
      await client.state(assignment, "WAITING_LOGIN", "等待用户在隔离浏览器中完成登录/MFA");
      events.add({ ...info("LOGIN_REQUIRED", "需要人工完成登录或 MFA"), level: "WARN" });
      await events.flush();
      await waitUntilRunnable(client, assignment);
    } else {
      await automaticLogin(page, client, assignment, config, events);
      await client.state(assignment, "RUNNING", "浏览器准备完成");
    }

    const datasetValues = await prepareDatasetValues(client, assignment, dataLeases);
    const runtimeConfig: RunnerConfig = { ...config, values: { ...config.values, ...datasetValues } };
    const policy = evidencePolicy(assignment.task.policySnapshot, runtimeConfig.sensitiveSelectors);
    for (const executionCase of assignment.task.cases ?? []) {
      if (executionCase.status && !["CREATED", "PENDING"].includes(executionCase.status)) continue;
      await safePoint(client, assignment);
      await executeCase(page, executionCase, runtimeConfig, client, assignment, events, policy);
    }
    events.add(info("TASK_EXECUTION_COMPLETED", "浏览器执行阶段完成，等待服务端回写与对账"));
    await events.flush();
    await client.state(assignment, "WRITING_BACK", "浏览器执行完成");
    await client.complete(assignment, "COMPLETED");
  } catch (cause) {
    const error = asRunnerError(cause);
    if (error.category === "RUNNER_HUMAN_HANDOFF") {
      events.add({ ...info("LOGIN_REQUIRED", "自动登录被 MFA 或验证码阻塞，已保存检查点并通知责任人"), level: "WARN" });
      await events.flush().catch(() => undefined);
      return;
    }
    events.add(errorEvent("RUNNER_FAILED", `${error.category}: ${sanitize(error.message)}`));
    await events.flush().catch(() => undefined);
    const canceled = await isCanceled(client, assignment);
    if (canceled) {
      await client.complete(assignment, "CANCELED", error.category).catch(() => undefined);
    } else {
      await client.state(assignment, "FAILED", error.category).catch(() => undefined);
      await client.complete(assignment, "FAILED", error.category).catch(() => undefined);
    }
  } finally {
    if (leaseHeartbeat) clearInterval(leaseHeartbeat);
    for (const lease of dataLeases) await client.releaseTestData(assignment, lease).catch(() => undefined);
    await page?.context().close().catch(() => undefined);
    await browser?.close().catch(() => undefined);
  }
}

async function prepareDatasetValues(client: RunnerClient, assignment: LeaseAssignment,
                                    leases: TestDataLease[]): Promise<Record<string, string>> {
  const references = new Set<string>();
  for (const executionCase of assignment.task.cases ?? []) for (const step of executionCase.steps ?? []) {
    try {
      const action = parseAction(step.actionJson);
      if (action.valueRef?.startsWith("dataset:")) references.add(action.valueRef);
      for (const assertion of parseAssertions(step.assertionJson)) {
        if (assertion.expected?.startsWith("dataset:")) references.add(assertion.expected);
      }
    } catch { /* contract parsing is repeated in the normal execution path with a user-safe error */ }
  }
  const values: Record<string,string> = {};
  for (const reference of references) {
    const match = /^dataset:([^:]+):(.+)$/.exec(reference);
    if (!match) throw new RunnerError("TEST_DATA_REFERENCE_INVALID", `测试数据引用格式无效: ${reference}`);
    const lease = await client.acquireTestData(assignment, match[1], match[2]);
    leases.push(lease);
    const content = await client.testDataContent(assignment, lease);
    values[reference] = resolveDatasetValue(content, match[2]);
    content.fill(0);
  }
  return values;
}

function resolveDatasetValue(content: Buffer, key: string): string {
  const text = content.toString("utf8").replace(/^\uFEFF/, "");
  try {
    let value: unknown = JSON.parse(text);
    for (const part of key.split(".")) {
      if (value === null || typeof value !== "object" || !(part in value)) throw new Error();
      value = (value as Record<string,unknown>)[part];
    }
    return typeof value === "string" ? value : JSON.stringify(value);
  } catch {
    const rows = parseCsv(text);
    if (rows.length < 2) throw new RunnerError("TEST_DATA_KEY_NOT_FOUND", `测试数据键不存在: ${key}`);
    const dot = /^(\d+)\.(.+)$/.exec(key); const rowIndex = dot ? Number(dot[1]) + 1 : 1; const column = dot ? dot[2] : key;
    const columnIndex = rows[0].indexOf(column);
    if (columnIndex < 0 || !rows[rowIndex] || rows[rowIndex][columnIndex] === undefined) throw new RunnerError("TEST_DATA_KEY_NOT_FOUND", `测试数据键不存在: ${key}`);
    return rows[rowIndex][columnIndex];
  }
}

function parseCsv(text: string): string[][] {
  const rows: string[][]=[];let row:string[]=[];let value="";let quoted=false;
  for(let i=0;i<text.length;i+=1){const c=text[i];if(c==='"'){if(quoted&&text[i+1]==='"'){value+='"';i+=1;}else quoted=!quoted;}else if(c===','&&!quoted){row.push(value);value="";}else if((c==='\n'||c==='\r')&&!quoted){if(c==='\r'&&text[i+1]==='\n')i+=1;row.push(value);if(row.some(cell=>cell.length>0))rows.push(row);row=[];value="";}else value+=c;}
  row.push(value);if(row.some(cell=>cell.length>0))rows.push(row);return rows;
}

interface LoginProfileSnapshot {
  loginType: "FORM" | "TOKEN"; loginUrl: string; usernameLocator: WebLocator; passwordLocator: WebLocator;
  submitLocator: WebLocator; successAssertion: WebAssertion; sessionValidation?: WebAssertion;
  mfaPolicy: "BLOCK" | "CHECKPOINT"; timeoutMs: number;
}

async function automaticLogin(page: Page, client: RunnerClient, assignment: LeaseAssignment,
                              config: RunnerConfig, events: EventBuffer): Promise<void> {
  const profile = parseExecutionParameters(assignment.task.executionParameterSnapshot).loginProfile as LoginProfileSnapshot | undefined;
  if (!profile) return;
  if (!assignment.task.credentialReferenceId) throw new RunnerError("AUTH_CREDENTIAL_REQUIRED", "自动登录缺少凭据引用");
  if (profile.loginType !== "FORM") throw new RunnerError("AUTH_LOGIN_TYPE_UNSUPPORTED", "当前 Runner 仅支持表单自动登录");
  const credential = await client.resolveCredential(assignment, assignment.task.credentialReferenceId);
  try {
    await page.goto(assertAllowedUrl(profile.loginUrl, config.allowedOrigins).toString(), { waitUntil: "domcontentloaded", timeout: profile.timeoutMs });
    await (await uniqueLocator(page, profile.usernameLocator)).fill(credential.username, { timeout: profile.timeoutMs });
    await (await uniqueLocator(page, profile.passwordLocator)).fill(credential.value, { timeout: profile.timeoutMs });
    credential.value = "";
    await (await uniqueLocator(page, profile.submitLocator)).click({ timeout: profile.timeoutMs });
    if (await page.locator("input[autocomplete='one-time-code'], input[name*='otp' i], input[name*='captcha' i], iframe[src*='captcha' i]").count() > 0) {
      await client.state(assignment, "WAITING_HUMAN", "MFA_OR_CAPTCHA_REQUIRED");
      throw new RunnerError("RUNNER_HUMAN_HANDOFF", "MFA_OR_CAPTCHA_REQUIRED");
    }
    await executeAssertion(page, { ...profile.successAssertion, timeoutMs: profile.timeoutMs }, config);
    events.add(info("ACTION_COMPLETED", `自动登录成功，凭据版本 ${credential.secretVersion ?? "unknown"}`));
    await events.flush();
  } catch (cause) {
    if (cause instanceof RunnerError && cause.category === "RUNNER_HUMAN_HANDOFF") throw cause;
    throw new RunnerError("AUTH_LOGIN_FAILED", "自动登录或会话断言失败", cause);
  } finally { credential.username = ""; credential.value = ""; }
}

function parseExecutionParameters(raw?: string): Record<string, unknown> {
  if (!raw) return {};
  try { const value = JSON.parse(raw); if (!value || typeof value !== "object" || Array.isArray(value)) throw new Error(); return value as Record<string, unknown>; }
  catch { throw new RunnerError("EXECUTION_PARAMETER_SNAPSHOT_INVALID", "执行参数快照格式无效"); }
}

async function executeCase(page: Page, executionCase: ExecutionCase, config: RunnerConfig,
                           client: RunnerClient, assignment: LeaseAssignment, events: EventBuffer,
                           policy: EvidencePolicy): Promise<void> {
  events.add({ ...info("CASE_STARTED", `开始执行用例: ${executionCase.caseName ?? executionCase.caseId}`),
    caseId: executionCase.caseId });
  let failed = false;
  for (const step of executionCase.steps ?? []) {
    await safePoint(client, assignment);
    if (step.status === "NEEDS_REVIEW" || step.status === "SKIPPED_REVIEW_REQUIRED") {
      events.add({ ...errorEvent("STEP_COMPLETED", "步骤不可执行，需要人工补充"),
        caseId: executionCase.caseId, stepId: step.id });
      failed = true;
      break;
    }
    events.add({ ...info("STEP_STARTED", `执行步骤 ${step.pos}`), caseId: executionCase.caseId,
      stepId: step.id, attempt: step.attempt ?? 0 });
    try {
      const action = parseAction(step.actionJson);
      const assertions = parseAssertions(step.assertionJson);
      await executeWithHealing(page, action, config, client, assignment, events, executionCase, step, policy);
      for (const assertion of assertions) await executeAssertion(page, assertion, config);
      const artifactIds = policy.screenshotMode === "AFTER_STEP"
        ? await captureEvidence(page, client, assignment, executionCase, step, "AFTER_STEP", policy) : [];
      events.add({ ...info("STEP_COMPLETED", "步骤执行与断言成功"), caseId: executionCase.caseId,
        stepId: step.id, attempt: step.attempt ?? 0, artifactIds });
    } catch (cause) {
      failed = true;
      const error = asRunnerError(cause);
      const artifactIds = await captureEvidence(page, client, assignment, executionCase, step, "FAILURE", policy)
        .catch(() => []);
      events.add({ ...errorEvent("ASSERTION_FAILED", `${error.category}: ${sanitize(error.message)}`),
        caseId: executionCase.caseId, stepId: step.id, attempt: step.attempt ?? 0, artifactIds,
        sanitizedMetadata: JSON.stringify({ failureCategory: error.category }) });
      break;
    } finally {
      await events.flush();
    }
  }
  events.add({ ...(failed
      ? { ...errorEvent("CASE_COMPLETED", "用例执行失败或需人工处理"), level: "WARN" as const }
      : info("CASE_COMPLETED", "用例执行成功")), caseId: executionCase.caseId });
  await events.flush();
}

async function executeWithHealing(page: Page, action: WebAction, config: RunnerConfig, client: RunnerClient,
                                  assignment: LeaseAssignment, events: EventBuffer, executionCase: ExecutionCase,
                                  step: ExecutionStep, policy: EvidencePolicy): Promise<void> {
  try {
    await executeAction(page, action, config);
  } catch (cause) {
    const error = asRunnerError(cause);
    if (!action.retryable || action.riskLevel === "HIGH" || !error.category.startsWith("LOCATOR_")) throw error;
    const candidate = await findHealingCandidate(page, action.target);
    if (!candidate) throw error;
    const before = await captureEvidence(page, client, assignment, executionCase, step, "HEALING_BEFORE", policy)
      .catch(() => []);
    events.add({ ...info("HEALING_STARTED", `使用唯一语义候选进行有限自愈: ${candidate.description}`),
      level: "WARN", caseId: executionCase.caseId, stepId: step.id, attempt: step.attempt ?? 0, artifactIds: before,
      sanitizedMetadata: JSON.stringify({ confidence: 0.85, failureType: error.category,
        originalLocator: action.target, selectedLocator: candidate.description }) });
    await events.flush();
    await executeAction(page, action, config, candidate.locator);
    const after = await captureEvidence(page, client, assignment, executionCase, step, "HEALING_AFTER", policy)
      .catch(() => []);
    events.add({ ...info("HEALING_COMPLETED", "自愈动作成功；原始用例与预期未修改"),
      caseId: executionCase.caseId, stepId: step.id, attempt: step.attempt ?? 0, artifactIds: after,
      sanitizedMetadata: JSON.stringify({ confidence: 0.85, failureType: error.category,
        originalLocator: action.target, selectedLocator: candidate.description }) });
  }
}

async function executeAction(page: Page, action: WebAction, config: RunnerConfig,
                             override?: Locator): Promise<void> {
  const timeout = action.timeoutMs;
  if (action.type === "NAVIGATE") {
    const value = resolveValue(action.value, action.valueRef, config.values);
    const url = assertAllowedUrl(value, config.allowedOrigins);
    await page.goto(url.toString(), { waitUntil: "domcontentloaded", timeout });
    assertAllowedUrl(page.url(), config.allowedOrigins);
    return;
  }
  if (action.type === "WAIT") {
    await page.waitForTimeout(Math.min(Number(action.value) || timeout, timeout));
    return;
  }
  if (action.type === "SCROLL") {
    const delta = action.value?.toUpperCase() === "TOP" ? -800 : Number(action.value) || 800;
    await page.mouse.wheel(0, delta);
    return;
  }
  const locator = override ?? await uniqueLocator(page, action.target);
  switch (action.type) {
    case "CLICK": await locator.click({ timeout }); break;
    case "FILL": await locator.fill(resolveValue(action.value, action.valueRef, config.values), { timeout }); break;
    case "SELECT": await locator.selectOption(resolveValue(action.value, action.valueRef, config.values), { timeout }); break;
    case "CHECK": await locator.check({ timeout }); break;
    case "KEYBOARD": await locator.press(resolveValue(action.value, action.valueRef, config.values), { timeout }); break;
    case "UPLOAD": {
      const upload = resolveUploadPath(resolveValue(action.value, action.fileRef ?? action.valueRef, config.values), config);
      await access(upload);
      await locator.setInputFiles(upload, { timeout });
      break;
    }
  }
  if (page.url() !== "about:blank") assertAllowedUrl(page.url(), config.allowedOrigins);
}

async function executeAssertion(page: Page, assertion: WebAssertion, config: RunnerConfig): Promise<void> {
  const deadline = Date.now() + assertion.timeoutMs;
  let actual = "";
  do {
    try {
      if (assertion.type === "URL") actual = page.url();
      else if (assertion.type === "TITLE") actual = await page.title();
      else {
        const locator = assertion.type === "COUNT"
          ? locatorFor(page, assertion.target!) : await uniqueLocator(page, assertion.target);
        switch (assertion.type) {
          case "TEXT": actual = (await locator.textContent()) ?? ""; break;
          case "VISIBLE": if (await locator.isVisible()) return; actual = "false"; break;
          case "ENABLED": if (await locator.isEnabled()) return; actual = "false"; break;
          case "CHECKED": if (await locator.isChecked()) return; actual = "false"; break;
          case "ATTRIBUTE": actual = (await locator.getAttribute(assertion.attribute!)) ?? ""; break;
          case "COUNT": actual = String(await locator.count()); break;
        }
      }
      const expected = assertion.expected?.startsWith("dataset:")
        ? resolveValue(undefined, assertion.expected, config.values) : assertion.expected ?? "";
      if (["TEXT", "ATTRIBUTE", "COUNT", "URL", "TITLE"].includes(assertion.type)
          && compare(actual, expected, assertion.operator)) return;
    } catch (cause) {
      actual = asRunnerError(cause).message;
    }
    await page.waitForTimeout(200);
  } while (Date.now() < deadline);
  throw new RunnerError("ASSERTION_MISMATCH", `断言 ${assertion.type} 失败，actual=${sanitize(actual)}`);
}

function compare(actual: string, expected: string, operator: WebAssertion["operator"] = "CONTAINS"): boolean {
  switch (operator) {
    case "EQUALS": return actual === expected;
    case "NOT_EQUALS": return actual !== expected;
    case "CONTAINS": return actual.includes(expected);
    case "MATCHES": throw new RunnerError("UNSUPPORTED_CONTRACT_VALUE", "Runner 不执行模型提供的正则表达式");
    default: throw new RunnerError("UNSUPPORTED_CONTRACT_VALUE", "断言 operator 不受支持");
  }
}

async function uniqueLocator(page: Page, target: WebLocator | undefined): Promise<Locator> {
  if (!target) throw new RunnerError("INVALID_LOCATOR", "动作缺少定位器");
  const locator = locatorFor(page, target);
  const count = await locator.count();
  if (count === 0) throw new RunnerError("LOCATOR_NOT_FOUND", `未找到元素: ${target.strategy}`);
  if (count !== 1) throw new RunnerError("LOCATOR_NOT_UNIQUE", `定位结果不唯一: ${count}`);
  await locator.waitFor({ state: "visible", timeout: 10_000 });
  return locator;
}

function locatorFor(page: Page, target: WebLocator): Locator {
  switch (target.strategy) {
    case "TEST_ID": return page.getByTestId(target.testId!);
    case "ROLE": return page.getByRole(target.role as Parameters<Page["getByRole"]>[0], { name: target.name!, exact: true });
    case "LABEL": return page.getByLabel(target.label!, { exact: true });
    case "PLACEHOLDER": return page.getByPlaceholder(target.placeholder!, { exact: true });
    case "TEXT": return page.getByText(target.text!, { exact: true });
    case "CSS": return page.locator(target.selector!);
  }
}

async function findHealingCandidate(page: Page, target: WebLocator | undefined): Promise<{ locator: Locator; description: string } | undefined> {
  if (!target) return undefined;
  const value = target.name ?? target.label ?? target.placeholder ?? target.text;
  if (!value) return undefined;
  const candidates: Array<{ locator: Locator; description: string }> = [
    { locator: page.getByText(value, { exact: true }), description: "exact text" },
    { locator: page.getByLabel(value, { exact: true }), description: "exact label" },
    { locator: page.getByPlaceholder(value, { exact: true }), description: "exact placeholder" },
  ];
  const unique: Array<{ locator: Locator; description: string }> = [];
  for (const candidate of candidates) {
    if (await candidate.locator.count() === 1 && await candidate.locator.isVisible()) unique.push(candidate);
  }
  return unique.length === 1 ? unique[0] : undefined;
}

async function captureEvidence(page: Page, client: RunnerClient, assignment: LeaseAssignment,
                               executionCase: ExecutionCase, step: ExecutionStep, purpose: string,
                               policy: EvidencePolicy): Promise<string[]> {
  const masks: Locator[] = [page.locator("input[type=password], input[autocomplete='current-password'], input[autocomplete='new-password']")];
  for (const selector of policy.sensitiveSelectors) masks.push(page.locator(selector));
  const bytes = await page.screenshot({ type: "png", fullPage: policy.fullPage, mask: masks, maskColor: "#000000" });
  const sha256 = createHash("sha256").update(bytes).digest("hex");
  const artifact = await client.artifact(assignment, bytes, `evidence-${step.id}-${purpose}.png`, purpose,
    sha256, executionCase.caseId, step.id);
  return [artifact.artifactId];
}

async function safePoint(client: RunnerClient, assignment: LeaseAssignment): Promise<void> {
  let control = await client.control(assignment);
  if (control.command === "CANCEL") throw new RunnerError("RUNNER_TASK_CANCELED", "任务已取消");
  while (control.command === "PAUSE" || control.command === "WAIT_LOGIN") {
    await delay(2_000);
    control = await client.control(assignment);
    if (control.command === "CANCEL") throw new RunnerError("RUNNER_TASK_CANCELED", "任务已取消");
  }
}

async function waitUntilRunnable(client: RunnerClient, assignment: LeaseAssignment): Promise<void> {
  for (;;) {
    const control = await client.control(assignment);
    if (control.command === "CONTINUE") return;
    if (control.command === "CANCEL") throw new RunnerError("RUNNER_TASK_CANCELED", "登录等待期间任务已取消");
    await delay(2_000);
  }
}

async function isCanceled(client: RunnerClient, assignment: LeaseAssignment): Promise<boolean> {
  try { return (await client.control(assignment)).command === "CANCEL"; } catch { return false; }
}

function evidencePolicy(raw: string | undefined, defaults: readonly string[]): EvidencePolicy {
  const policy: EvidencePolicy = { screenshotMode: "AFTER_STEP", fullPage: true, sensitiveSelectors: [...defaults] };
  if (!raw) return policy;
  try {
    const parsed = JSON.parse(raw) as Record<string, unknown>;
    if (parsed.screenshotMode === "AFTER_STEP" || parsed.screenshotMode === "FAILURE_ONLY") {
      policy.screenshotMode = parsed.screenshotMode;
    }
    if (typeof parsed.fullPage === "boolean") policy.fullPage = parsed.fullPage;
    if (Array.isArray(parsed.sensitiveSelectors)) {
      policy.sensitiveSelectors.push(...parsed.sensitiveSelectors.filter((item): item is string => typeof item === "string").slice(0, 20));
    }
  } catch {
    throw new RunnerError("UNSUPPORTED_CONTRACT_VALUE", "policySnapshot 不是合法 JSON");
  }
  return policy;
}

function asRunnerError(cause: unknown): RunnerError {
  if (cause instanceof RunnerError) return cause;
  const message = cause instanceof Error ? cause.message : String(cause);
  if (/strict mode violation/i.test(message)) return new RunnerError("LOCATOR_NOT_UNIQUE", message, cause);
  if (/waiting for|locator|not found|timeout/i.test(message)) return new RunnerError("LOCATOR_NOT_FOUND", message, cause);
  return new RunnerError("RUNNER_UNEXPECTED_ERROR", message, cause);
}

function info(eventType: string, message: string) {
  return { level: "INFO" as const, eventType, message };
}

function errorEvent(eventType: string, message: string) {
  return { level: "ERROR" as const, eventType, message };
}

function delay(ms: number): Promise<void> {
  return new Promise(resolve => setTimeout(resolve, ms));
}
