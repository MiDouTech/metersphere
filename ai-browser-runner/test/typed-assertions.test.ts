import test from "node:test";
import assert from "node:assert/strict";
import { compareTyped, typedActual } from "../src/typed-assertions.js";
import type { WebAssertion } from "../src/types.js";
const amount: WebAssertion = { contractVersion: "v2", assertionId: "a1", type: "TEXT", valueType: "DECIMAL", precision: 38, scale: 2, operator: "EQUALS", expected: "9007199254740993.10", timeoutMs: 1 };
test("v2 decimals do not pass through Number and honor exact range boundaries", () => {
  assert.equal(compareTyped("9007199254740993.1", amount), true);
  assert.equal(compareTyped("9007199254740993.11", amount), false);
  assert.equal(compareTyped("9007199254740993.11", { ...amount, tolerance: "0.01" }), true);
  assert.equal(compareTyped("9007199254740993.10", { ...amount, operator: "IN_RANGE", expected: '["9007199254740993.10","9007199254740993.20"]', lowerInclusive: false }), false);
  for (const invalid of ["NaN", "Infinity", "1e3", "1.001"]) assert.throws(() => typedActual(invalid, amount));
});
test("v2 boolean integer and date types reject coercion and invalid dates", () => {
  assert.equal(typedActual("true", { ...amount, valueType: "BOOLEAN" }), true);
  assert.throws(() => typedActual("TRUE", { ...amount, valueType: "BOOLEAN" }));
  assert.throws(() => typedActual("1.0", { ...amount, valueType: "INTEGER" }));
  assert.throws(() => typedActual("2026-02-30", { ...amount, valueType: "DATE" }));
});
