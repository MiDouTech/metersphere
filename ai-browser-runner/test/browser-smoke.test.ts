import assert from "node:assert/strict";
import test from "node:test";
import { chromium } from "playwright";
import { executeAssertion } from "../src/executor.js";
import type { RunnerConfig } from "../src/types.js";

test("creates isolated Chromium contexts and performs a UI action", {
  skip: !process.env.MS_RUNNER_SMOKE_EXECUTABLE,
}, async () => {
  const browser = await chromium.launch({ headless: true, executablePath: process.env.MS_RUNNER_SMOKE_EXECUTABLE });
  try {
    const first = await browser.newContext();
    const second = await browser.newContext();
    const html = "<button aria-label='Save'>Save</button><input type='password'><div id='result'></div>"
      + "<script>document.querySelector('button').onclick=()=>document.querySelector('#result').textContent='done'</script>";
    await first.route("https://example.test/**", route => route.fulfill({ status: 200, contentType: "text/html", body: html }));
    await second.route("https://example.test/**", route => route.fulfill({ status: 200, contentType: "text/html", body: html }));
    const firstPage = await first.newPage();
    const secondPage = await second.newPage();
    await firstPage.goto("https://example.test/");
    await secondPage.goto("https://example.test/");
    await firstPage.evaluate(() => localStorage.setItem("context-secret", "isolated"));
    await firstPage.getByRole("button", { name: "Save", exact: true }).click();
    assert.equal(await firstPage.locator("#result").textContent(), "done");
    assert.equal(await secondPage.evaluate(() => localStorage.getItem("context-secret")), null);
    const screenshot = await firstPage.screenshot({ mask: [firstPage.locator("input[type=password]")] });
    assert.ok(screenshot.length > 100);
    await first.close();
    await second.close();
  } finally {
    await browser.close();
  }
});


test("reports actual browser observations and enforces numeric ranges", {
  skip: !process.env.MS_RUNNER_SMOKE_EXECUTABLE,
}, async () => {
  const browser = await chromium.launch({ headless: true, executablePath: process.env.MS_RUNNER_SMOKE_EXECUTABLE });
  try {
    const page = await browser.newPage();
    await page.setContent('<title>verified</title><div data-testid="count">12</div>');
    const config = { values: {} } as RunnerConfig;
    assert.equal(await executeAssertion(page, { contractVersion: "v1", type: "TITLE", operator: "EQUALS",
      expected: "verified", timeoutMs: 1 }, config), "verified");
    assert.equal(await executeAssertion(page, { contractVersion: "v1", type: "TEXT", operator: "IN_RANGE",
      target: { strategy: "TEST_ID", testId: "count" }, expected: "[10,12]", timeoutMs: 1 }, config), 12);
    await assert.rejects(executeAssertion(page, { contractVersion: "v1", type: "TEXT", operator: "IN_RANGE",
      target: { strategy: "TEST_ID", testId: "count" }, expected: "[0,11]", timeoutMs: 1 }, config));
    assert.equal(await executeAssertion(page, { contractVersion: "v1", type: "VISIBLE", operator: "EQUALS",
      target: { strategy: "TEST_ID", testId: "count" }, expected: "true", timeoutMs: 1 }, config), "true");
  } finally { await browser.close(); }
});
