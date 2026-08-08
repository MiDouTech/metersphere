import type { WebAction, WebAssertion, WebLocator } from "./types.js";
import { RunnerError } from "./security.js";

const ACTIONS = new Set(["NAVIGATE", "CLICK", "FILL", "SELECT", "CHECK", "UPLOAD", "KEYBOARD", "WAIT", "SCROLL"]);
const LOCATORS = new Set(["TEST_ID", "ROLE_NAME", "LABEL", "PLACEHOLDER", "TEXT", "SEMANTIC", "CSS", "XPATH"]);
const ASSERTIONS = new Set(["TEXT", "VISIBLE", "ENABLED", "CHECKED", "ATTRIBUTE", "COUNT", "URL", "TITLE"]);

export function parseAction(value: string | undefined): WebAction {
  const action = parseObject(value, "action") as unknown as WebAction;
  if (action.contractVersion !== "v1" || !ACTIONS.has(action.type)) {
    throw new RunnerError("UNSUPPORTED_CONTRACT_VALUE", "动作契约版本或类型不受支持");
  }
  validateTimeout(action.timeoutMs);
  if (!["NAVIGATE", "WAIT", "SCROLL"].includes(action.type)) validateLocator(action.target);
  if (action.type === "FILL" && !action.valueRef && action.value === undefined) {
    throw new RunnerError("INVALID_ACTION", "FILL 缺少 value/valueRef");
  }
  if (action.riskLevel === "HIGH" && action.retryable) {
    throw new RunnerError("SECURITY_HIGH_RISK_RETRY", "高风险动作禁止自动重试");
  }
  if (action.riskLevel === "HIGH") {
    throw new RunnerError("SECURITY_HIGH_RISK_ACTION_BLOCKED", "第一阶段禁止 Runner 执行高风险动作");
  }
  return action;
}

export function parseAssertions(value: string | undefined): WebAssertion[] {
  if (!value) throw new RunnerError("SCOPE_STEP_NOT_STANDARDIZED", "步骤缺少 assertionJson");
  let parsed: unknown;
  try {
    parsed = JSON.parse(value);
  } catch {
    throw new RunnerError("INVALID_ASSERTION", "assertionJson 不是合法 JSON");
  }
  const assertions = (Array.isArray(parsed) ? parsed : [parsed]) as WebAssertion[];
  if (assertions.length === 0 || assertions.length > 20) {
    throw new RunnerError("INVALID_ASSERTION", "断言数量必须为 1..20");
  }
  for (const assertion of assertions) {
    if (assertion.contractVersion !== "v1" || !ASSERTIONS.has(assertion.type)) {
      throw new RunnerError("UNSUPPORTED_CONTRACT_VALUE", "断言契约版本或类型不受支持");
    }
    validateTimeout(assertion.timeoutMs);
    if (!["URL", "TITLE"].includes(assertion.type)) validateLocator(assertion.target);
    if (["TEXT", "ATTRIBUTE", "COUNT", "URL", "TITLE"].includes(assertion.type)
        && assertion.expected === undefined) {
      throw new RunnerError("INVALID_ASSERTION", `${assertion.type} 缺少 expected`);
    }
    if (assertion.type === "ATTRIBUTE" && !assertion.attribute) {
      throw new RunnerError("INVALID_ASSERTION", "ATTRIBUTE 缺少 attribute");
    }
  }
  return assertions;
}

export function validateLocator(locator: WebLocator | undefined): asserts locator is WebLocator {
  if (!locator || !LOCATORS.has(locator.strategy)) {
    throw new RunnerError("UNSUPPORTED_CONTRACT_VALUE", "定位策略不受支持");
  }
  const valid = (() => {
    switch (locator.strategy) {
      case "TEST_ID": return Boolean(locator.testId);
      case "ROLE_NAME": return Boolean(locator.role && locator.name);
      case "LABEL": return Boolean(locator.label);
      case "PLACEHOLDER": return Boolean(locator.placeholder);
      case "TEXT": case "SEMANTIC": return Boolean(locator.text);
      case "CSS": case "XPATH": return Boolean(locator.selector);
    }
  })();
  if (!valid) throw new RunnerError("INVALID_LOCATOR", `定位策略 ${locator.strategy} 缺少参数`);
}

function parseObject(value: string | undefined, field: string): Record<string, unknown> {
  if (!value) throw new RunnerError("SCOPE_STEP_NOT_STANDARDIZED", `步骤缺少 ${field}Json`);
  try {
    const parsed: unknown = JSON.parse(value);
    if (!parsed || typeof parsed !== "object" || Array.isArray(parsed)) throw new Error("not object");
    return parsed as Record<string, unknown>;
  } catch {
    throw new RunnerError(`INVALID_${field.toUpperCase()}`, `${field}Json 不是合法 JSON 对象`);
  }
}

function validateTimeout(timeout: number): void {
  if (!Number.isInteger(timeout) || timeout < 1 || timeout > 60_000) {
    throw new RunnerError("INVALID_TIMEOUT", "timeoutMs 必须为 1..60000");
  }
}
