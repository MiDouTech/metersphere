import type { WebAssertion } from "./types.js";
import { RunnerError } from "./security.js";
const invalid = () => new RunnerError("QUALITY_ASSERTION_INVALID", "断言类型或精度不符合冻结合同");
function decimal(raw: unknown, rule: WebAssertion): bigint {
  if (typeof raw !== "string" || !/^-?(0|[1-9][0-9]*)(\.[0-9]+)?$/.test(raw)) throw invalid();
  const scale = rule.valueType === "INTEGER" ? 0 : rule.scale;
  const precision = rule.precision ?? 38;
  if (scale === undefined || !Number.isInteger(scale) || scale < 0 || scale > 18 || precision < 1 || precision > 38 || scale > precision) throw invalid();
  const negative = raw.startsWith("-");
  const [whole, fraction = ""] = (negative ? raw.slice(1) : raw).split(".");
  const significant = `${whole}${fraction}`.replace(/^0+/, "").length || 1;
  if (fraction.length > scale || significant > precision) throw invalid();
  return BigInt(`${negative ? "-" : ""}${whole}${fraction.padEnd(scale, "0")}`);
}
export function typedActual(actual: string, rule: WebAssertion): string | boolean {
  if (rule.valueType === "BOOLEAN") {
    if (!["true", "false"].includes(actual)) throw invalid();
    return actual === "true";
  }
  if (rule.valueType === "DECIMAL" || rule.valueType === "INTEGER") decimal(actual, rule);
  if (rule.valueType === "DATE" && (!/^\d{4}-\d{2}-\d{2}$/.test(actual) || Number.isNaN(Date.parse(`${actual}T00:00:00Z`)) || new Date(`${actual}T00:00:00Z`).toISOString().slice(0, 10) !== actual)) throw invalid();
  return actual;
}
export function compareTyped(actual: string | boolean, rule: WebAssertion): boolean {
  if (!["EQUALS", "IN_RANGE"].includes(rule.operator ?? "") || rule.expected === undefined) throw invalid();
  if (rule.valueType === "DECIMAL" || rule.valueType === "INTEGER") {
    const value = decimal(actual, rule);
    if (rule.operator === "EQUALS") {
      const expected = decimal(rule.expected, rule); const tolerance = decimal(rule.tolerance ?? "0", rule);
      if (tolerance < 0n) throw invalid();
      const difference = value - expected;
      return (difference < 0n ? -difference : difference) <= tolerance;
    }
    let range: unknown;
    try { range = JSON.parse(rule.expected); } catch { throw invalid(); }
    if (!Array.isArray(range) || range.length !== 2) throw invalid();
    const low = decimal(range[0], rule); const high = decimal(range[1], rule);
    if (low > high) throw invalid();
    return (rule.lowerInclusive === false ? value > low : value >= low) && (rule.upperInclusive === false ? value < high : value <= high);
  }
  if (rule.operator !== "EQUALS") throw invalid();
  if (rule.valueType === "BOOLEAN") {
    if (typeof actual !== "boolean" || !["true", "false"].includes(rule.expected)) throw invalid();
    return actual === (rule.expected === "true");
  }
  if (!["STRING", "DATE"].includes(rule.valueType ?? "") || typeof actual !== "string") throw invalid();
  if (rule.valueType === "DATE") typedActual(rule.expected, rule);
  return actual === rule.expected;
}
