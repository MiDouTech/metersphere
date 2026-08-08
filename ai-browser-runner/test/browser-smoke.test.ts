import assert from "node:assert/strict";
import test from "node:test";
import { chromium } from "playwright";

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
