// Real disposable-CI browser/API/MCP smoke. No fixtures are written to shared environments.
import assert from 'node:assert/strict';
import { createRequire } from 'node:module';
import { mkdir, writeFile } from 'node:fs/promises';
import { writeFileSync } from 'node:fs';
import path from 'node:path';

assert.equal(process.env.GITHUB_ACTIONS, 'true', 'Restricted to the disposable CI runner');
assert.match(process.env.GITHUB_RUN_ID || '', /^[0-9]+$/, 'A real CI run identifier is required');
const origin = 'http://127.0.0.1:15173';
const require = createRequire(path.resolve('frontend/package.json'));
const { chromium, expect } = require('@playwright/test');
const output = path.resolve('.ci-core-smoke');
await mkdir(output, { recursive: true });
const evidence = { commit: process.env.GITHUB_SHA, viewport: { width: 1366, height: 900 }, checks: [] };
let checkpoint = 'browser-launch';
const recordCheckpoint = value => {
  checkpoint = value;
  evidence.status = 'running';
  evidence.currentCheckpoint = value;
  writeFileSync(path.join(output, 'summary.json'), JSON.stringify(evidence, null, 2));
};
const deadline = setTimeout(() => {
  evidence.status = 'failed';
  evidence.failedCheckpoint = checkpoint;
  evidence.failureCode = 'CORE_SMOKE_TIMEOUT';
  writeFileSync(path.join(output, 'summary.json'), JSON.stringify(evidence, null, 2));
  process.exit(124);
}, 240000);
deadline.unref();
let browser;
let page;
const passed = (id, details = {}) => evidence.checks.push({ id, status: 'passed', ...details });
try {
  recordCheckpoint('browser-launch');
  browser = await chromium.launch({ channel: 'chrome', headless: true });
  const context = await browser.newContext({ viewport: evidence.viewport, locale: 'zh-CN', timezoneId: 'Asia/Shanghai' });
  page = await context.newPage();
  page.setDefaultTimeout(45000);
  recordCheckpoint('login');
  await page.goto(`${origin}/#/login/admin`);
  await page.getByPlaceholder('请输入用户名', { exact: true }).fill('admin');
  await page.getByPlaceholder('请输入密码', { exact: true }).fill('metersphere');
  await page.getByRole('button', { name: '登录', exact: true }).click();
  await expect(page).not.toHaveURL(/login\/admin/);
  const headers = await page.evaluate(() => ({
    'X-AUTH-TOKEN': localStorage.getItem('sessionId') || '',
    'CSRF-TOKEN': localStorage.getItem('csrfToken') || '',
    ORGANIZATION: '100001',
  }));
  assert.ok(headers['X-AUTH-TOKEN']);
  passed('CORE-LOGIN');
  const api = async (method, url, data) => {
    const response = await page.request.fetch(`${origin}/api${url}`, { method, headers, data });
    const body = await response.json();
    assert.equal(response.status(), 200, `${method} ${url}: HTTP ${response.status()}`);
    assert.equal(body.code, 100200, `${method} ${url}: business code ${body.code}`);
    return body.data;
  };
  recordCheckpoint('isolated-project');
  const key = `CI-${process.env.GITHUB_RUN_ID}`;
  const project = await api('POST', '/organization/project/add', {
    organizationId: '100001', name: key, enable: true, userIds: ['admin'],
  });
  const projectId = project.id;
  assert.ok(projectId);
  headers.PROJECT = projectId;
  const policyState = await api('GET', '/system/quality/policies');
  if (!policyState.currentPolicyId) {
    const draft = await api('POST', '/system/quality/policies', {
      rulesJson: JSON.stringify({ schemaVersion: 'quality-policy.v1', name: key, rules: [
        { ruleId: 'Q-EVIDENCE-01', parameters: { requiredRoles: ['BEFORE_ACTION', 'AFTER_ACTION'], allowedMimeTypes: ['image/png'], maxArtifactBytes: 5242880 } },
        { ruleId: 'Q-ASSERT-01', parameters: { allowedOperators: ['EQUALS'], expectedSource: 'FROZEN_CONTRACT' } },
      ] }),
    });
    await api('POST', `/system/quality/policies/${draft.id}/publish`, {
      expectedVersion: draft.rowVersion, expectedCurrentPolicyId: null, changeReason: 'Disposable CI core-path verification',
    });
  }
  const token = await api('POST', '/personal/agent-tokens', {
    name: key, projectIds: [projectId], scopes: 'AGENT_ALL', expireTime: Date.now() + 900000,
  });
  const agentHeaders = { Authorization: `Bearer ${token.token}`, 'X-MS-PROJECT': projectId };
  let rpcId = 0;
  const rpc = async (method, params) => {
    const response = await page.request.post(`${origin}/api/mcp`, {
      headers: agentHeaders, data: { jsonrpc: '2.0', id: ++rpcId, method, params },
    });
    const body = await response.json();
    return { reply: body.data || body, status: response.status() };
  };
  const toolRaw = async (name, args) => {
    const { reply, status } = await rpc('tools/call', {
      name: name.startsWith('metersphere.') ? name : `metersphere.functional.${name}`, arguments: args,
    });
    assert.equal(status, 200, `MCP ${name}: HTTP ${status}`);
    assert.equal(reply.error, undefined, `MCP ${name}: error code ${reply.error?.code}`);
    assert.notEqual(reply.result?.isError, true, `MCP ${name}: tool failure`);
    return reply.result;
  };
  const tool = async (name, args) => (await toolRaw(name, args)).structuredContent.result;
  recordCheckpoint('mcp-create-and-replay');
  const module = await tool('module.create', { projectId, name: `${key}-执行目录`, requestId: `${key}-module` });
  const creation = { projectId, moduleId: module.id, requestId: `${key}-case`, name: `${key}-核心用例`, steps: [
    { num: 1, desc: '查看测试用例列表', expected: '本次创建的用例可见' },
  ] };
  const created = await tool('case.create', creation);
  const caseId = created.caseId;
  assert.ok(caseId);
  assert.equal((await tool('case.create', creation)).caseId, caseId);
  passed('CORE-MCP-CREATE-IDEMPOTENT');
  recordCheckpoint('associate-two-plans');
  const shortName = `${key}-短计划`;
  const longName = `${key}-用于验证名称长度自适应和进度条对齐的长名称测试计划`;
  const plans = [];
  for (const name of [shortName, longName]) {
    const plan = await api('POST', '/test-plan/add', { projectId, name });
    plans.push(plan.id);
    await api('POST', '/test-plan/functional/case/associate', {
      projectId, testPlanId: plan.id, selectAll: false, selectIds: [caseId],
    });
  }
  await page.goto(`${origin}/#/case-management/featureCase?orgId=100001&pId=${projectId}`);
  // Fixtures were created through APIs after the initial login; load a fresh application context.
  await page.reload();
  await expect(page.getByRole('row').filter({ hasText: creation.name })).toBeVisible();
  const selector = page.locator('.case-overview-plan-select');
  const progress = page.locator('.case-plan-progress');
  await expect(selector).toHaveCount(1);
  await expect(progress).toHaveCount(1);
  const choose = async (name) => {
    await selector.click();
    await page.locator('.arco-select-dropdown:visible .arco-select-option').filter({ hasText: name }).click();
    await expect(selector).toContainText(name);
    const selectBox = await selector.boundingBox();
    const progressBox = await progress.boundingBox();
    assert.ok(selectBox && progressBox);
    assert.ok(Math.abs(selectBox.y + selectBox.height / 2 - progressBox.y - progressBox.height / 2) <= 3,
      'Plan selector and progress must share a horizontal centre');
    return selectBox.width;
  };
  recordCheckpoint('plan-summary-layout');
  const shortWidth = await choose(shortName);
  const longWidth = await choose(longName);
  assert.ok(longWidth > shortWidth, 'Selector must adapt to the selected plan name');
  await page.screenshot({ path: path.join(output, 'plan-summary.png'), fullPage: true });
  const planBounds = { selector: await selector.boundingBox(), progress: await progress.boundingBox() };
  await page.evaluate(bounds => {
    const overlay = document.createElement('div'); overlay.id = 'ci-evidence-annotation';
    overlay.style.cssText = 'position:fixed;inset:0;z-index:2147483647;pointer-events:none';
    for (const [index, box] of Object.values(bounds).entries()) {
      const mark = document.createElement('div');
      mark.style.cssText = `position:absolute;left:${box.x}px;top:${box.y}px;width:${box.width}px;height:${box.height}px;border:2px solid #d03050;color:#d03050;font-size:12px;box-sizing:border-box`;
      mark.textContent = `#${index + 1}`; overlay.append(mark);
    }
    document.body.append(overlay);
  }, planBounds);
  await page.screenshot({ path: path.join(output, 'plan-summary-annotated.png'), fullPage: true });
  await page.locator('#ci-evidence-annotation').evaluate(element => element.remove());
  passed('CORE-PLAN-ONE-SELECTOR-AUTO-WIDTH-ALIGNED', { shortWidth, longWidth, planBounds, annotations: { 1: 'selector', 2: 'progress' } });
  recordCheckpoint('select-all-toggle');
  await page.getByRole('button', { name: '全选所有页', exact: true }).click();
  await expect(page.getByRole('button', { name: '取消全选所有页', exact: true })).toBeVisible();
  await expect(page.getByRole('row').filter({ hasText: creation.name }).getByRole('checkbox')).toBeChecked();
  await page.getByRole('button', { name: '取消全选所有页', exact: true }).click();
  await expect(page.getByRole('button', { name: '全选所有页', exact: true })).toBeVisible();
  await expect(page.getByRole('row').filter({ hasText: creation.name }).getByRole('checkbox')).not.toBeChecked();
  passed('CORE-SELECT-ALL-TOGGLE');
  recordCheckpoint('case-evidence-publish');
  const contract = await tool('execution.contract', { projectId, caseId });
  assert.equal(contract.case.steps.length, 1);
  const png = await page.screenshot({ path: path.join(output, 'execution-evidence.png') });
  const uploaded = await tool('execution.evidence.upload', {
    projectId, caseId, requestId: `${key}-image`, fileName: `${key}.png`, contentType: 'image/png', base64: png.toString('base64'),
  });
  const submission = { projectId, caseId, requestId: `${key}-submit`, evidenceIds: [uploaded.evidenceId], steps: [
    { stepNum: 1, actual: '浏览器中的真实用例列表已显示该用例', result: 'SUCCESS' },
  ] };
  const published = await tool('execution.submit', submission);
  assert.equal(published.result, 'SUCCESS'); assert.equal(published.publishStatus, 'PUBLISHED');
  assert.equal((await tool('execution.submit', submission)).submissionId, published.submissionId);
  const report = await tool('execution.get', { projectId, caseId, submissionId: published.submissionId });
  assert.equal(report.resultSource, 'AGENT_REPORTED');
  assert.equal((await tool('get', { caseId })).lastExecuteResult, 'SUCCESS');
  const image = await toolRaw('attachment.read', { projectId, caseId, fileId: uploaded.evidenceId });
  const downloaded = image.content.find(item => item.type === 'image');
  assert.ok(downloaded); assert.ok(Buffer.from(downloaded.data, 'base64').equals(png), 'Downloaded evidence bytes differ');
  for (const testPlanId of plans) {
    const rows = await api('POST', '/test-plan/functional/case/page', { projectId, testPlanId, current: 1, pageSize: 10 });
    assert.equal(rows.list.length, 1); assert.equal(rows.list[0].lastExecResult, 'SUCCESS');
  }
  passed('CORE-MCP-CASE-IMAGE-PUBLISH-REPLAY-DOWNLOAD-PLAN-SYNC');
  recordCheckpoint('plan-detail-refresh-and-switch');
  for (const [index, testPlanId] of plans.entries()) {
    await page.goto(`${origin}/#/test-plan/testPlanIndexDetail?id=${testPlanId}&orgId=100001&pId=${projectId}`);
    await expect(page.getByText([shortName, longName][index], { exact: false }).first()).toBeVisible();
    await page.getByRole('tab', { name: /测试用例/ }).click();
    const row = page.getByRole('row').filter({ hasText: creation.name });
    await expect(row).toBeVisible();
    await expect(row.getByText('成功', { exact: true })).toBeVisible();
    await page.screenshot({ path: path.join(output, `plan-detail-${index + 1}.png`), fullPage: true });
  }
  passed('CORE-PLAN-REENTER-FRESH-STATE-AND-SWITCH');
  recordCheckpoint('bug-tag-layout');
  const template = await tool('metersphere.bug.template.get', { projectId });
  const customFields = {};
  for (const field of template.customFields) {
    if (!field.required || field.serverOwnedOnCreate) continue;
    if (field.fieldId === 'handleUser' || field.type === 'MEMBER') customFields[field.fieldId] = 'admin';
    else if (field.defaultValue) customFields[field.fieldId] = field.defaultValue;
    else if (field.options?.length) {
      const value = field.options[0].value;
      customFields[field.fieldId] = field.type.startsWith('MULTIPLE_') ? JSON.stringify([value]) : String(value);
    } else if (['INPUT', 'TEXT', 'TEXTAREA'].includes(field.type)) customFields[field.fieldId] = key;
    else assert.fail(`Unsupported required CI fixture field: ${field.fieldId}`);
  }
  const tags = ['第一个验收标签组合有礼', '第二个验收标签资金记账', '第三个验收标签商户信息',
    '第四个验收标签系统版本', '第五个验收标签长内容', '第六个验收标签布局'];
  const bug = await tool('metersphere.bug.create', {
    projectId, requestId: `${key}-bug`, title: `${key}-多标签布局验收`,
    description: '独立CI临时缺陷，仅验证长标签布局', templateId: template.id, tags, customFields,
  });
  await page.goto(`${origin}/#/bug-management/index?orgId=100001&pId=${projectId}&id=${bug.id}`);
  const tagField = page.locator('.bug-tags-field');
  await expect(tagField).toContainText(tags.at(-1));
  const expectedDate = page.locator('.arco-form-item:visible').filter({ has: page.getByPlaceholder('请选择日期') });
  await expect(expectedDate).toHaveCount(1);
  const tagBounds = await tagField.boundingBox();
  const dateBounds = await expectedDate.boundingBox();
  const metaBounds = await page.locator('.meta-block:visible').boundingBox();
  assert.ok(tagBounds && dateBounds && metaBounds);
  assert.ok(tagBounds.height > 48, 'Fixture must exercise multiline tags');
  assert.ok(tagBounds.y >= dateBounds.y + dateBounds.height - 1, 'Tags overlap expected resolution date');
  assert.ok(metaBounds.y >= tagBounds.y + tagBounds.height - 1, 'Tags overlap following metadata');
  await page.screenshot({ path: path.join(output, 'bug-tags-layout.png'), fullPage: true });
  passed('CORE-BUG-LONG-TAGS-NO-OVERLAP', { tagBounds, dateBounds, metaBounds });
  recordCheckpoint('personal-token-columns-and-controls');
  await page.goto(`${origin}/#/case-management/featureCase?orgId=100001&pId=${projectId}`);
  await page.getByText('Administrator', { exact: true }).click();
  await page.getByText('个人中心', { exact: true }).click();
  await page.getByText('Agent配置', { exact: true }).click();
  const personal = page.locator('.ms-personal-page-window');
  const panelBounds = await personal.boundingBox();
  assert.ok(panelBounds);
  const tokenColumnBounds = {};
  for (const title of ['启用', '操作']) {
    const column = personal.getByRole('columnheader', { name: title, exact: true });
    await expect(column).toBeVisible();
    const box = await column.boundingBox(); assert.ok(box);
    assert.ok(box.x >= panelBounds.x && box.x + box.width <= panelBounds.x + panelBounds.width + 1,
      `${title} column must remain inside personal window`);
    tokenColumnBounds[title] = box;
  }
  const tokenRow = personal.getByRole('row').filter({ hasText: key });
  const tokenSwitch = tokenRow.getByRole('switch');
  await expect(tokenSwitch).toHaveAttribute('aria-checked', 'true');
  await tokenSwitch.click();
  await expect(tokenSwitch).toHaveAttribute('aria-checked', 'false');
  const uiDenied = await rpc('tools/call', { name: 'metersphere.functional.get', arguments: { caseId } });
  assert.ok(uiDenied.status >= 400 || uiDenied.reply.error, 'UI disable must revoke MCP access');
  await tokenSwitch.click();
  await expect(tokenSwitch).toHaveAttribute('aria-checked', 'true');
  assert.equal((await tool('get', { caseId })).caseId, caseId);
  await tokenRow.getByRole('button', { name: '设置', exact: true }).click();
  await expect(page.getByRole('dialog')).toBeVisible();
  await page.getByRole('dialog').getByRole('button', { name: '取消', exact: true }).click();
  await page.screenshot({ path: path.join(output, 'personal-token-controls.png'), fullPage: true });
  passed('CORE-PERSONAL-TOKEN-COLUMNS-AND-REAL-CONTROLS', { panelBounds, tokenColumnBounds });
  recordCheckpoint('token-revocation');
  await api('POST', `/personal/agent-tokens/${token.id}/disable`);
  const denied = await rpc('tools/call', { name: 'metersphere.functional.get', arguments: { caseId } });
  assert.ok(denied.status >= 400 || denied.reply.error, 'Disabled token must not read cases');
  passed('CORE-MCP-REVOKED-TOKEN-DENIED');
  evidence.status = 'passed';
} catch (error) {
  evidence.status = 'failed'; evidence.failedCheckpoint = checkpoint;
  // Only synthetic CI data are visible. Never persist token values or network traces.
  if (page) await page.screenshot({ path: path.join(output, 'failure.png'), fullPage: true }).catch(() => {});
  throw error;
} finally {
  await writeFile(path.join(output, 'summary.json'), JSON.stringify(evidence, null, 2));
  checkpoint = 'browser-close';
  if (browser) await browser.close();
  clearTimeout(deadline);
}
