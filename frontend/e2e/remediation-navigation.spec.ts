import { expect, test } from '@playwright/test';
import { execFileSync } from 'node:child_process';

test.skip(process.env.MCP_CASE_LIBRARY_E2E !== 'true', 'Requires isolated acceptance backend');
test.use({ trace: 'off', video: 'off', screenshot: 'off' });

test('retired navigation is absent and real personal and quality pages are migrated', async ({ page, baseURL }) => {
  expect(baseURL).toBe('http://127.0.0.1:15173');
  await page.goto('/#/login/admin');
  await page.getByRole('textbox', { name: '请输入用户名' }).fill('admin');
  await page.getByRole('textbox', { name: '请输入密码' }).fill('metersphere');
  await page.getByRole('button', { name: '登录', exact: true }).click();
  await expect(page).not.toHaveURL(/login\/admin/);
  await page.goto('/#/setting/personal-access/access');
  await expect(page.getByText('Agent配置', { exact: true })).toBeVisible();
  await expect(page.getByRole('button', { name: '创建 Token', exact: true })).toBeVisible();
  await expect(page.getByText('前往个人接入', { exact: true })).toHaveCount(0);
  await page.getByRole('button', { name: '创建 Token', exact: true }).click();
  await expect(page.getByRole('button', { name: /个人 Agent 测试闭环/ })).toBeVisible();
  await page.getByRole('button', { name: '取消', exact: true }).click();
  await page.goto('/#/setting/system/quality-policy');
  await expect(page).toHaveURL(/\/execution\/quality-policy/);
  await Promise.all(
    ['项目执行设置', '系统运行维护', '扩展能力', '个人接入'].map(async (label) => {
      await expect(page.getByText(label, { exact: true })).toHaveCount(0);
    })
  );
  await expect(page.getByText('平台质量门禁', { exact: true }).first()).toBeVisible();
  await page.goto('/#/setting/execution-settings/environment-profile');
  await expect(page).toHaveURL(/\/execution\/platforms/);
  await page.getByRole('button', { name: '新增配置', exact: true }).click();
  await expect(page.getByText('环境 ID', { exact: true })).toHaveCount(0);
  await page.getByRole('button', { name: '取消', exact: true }).click();
  await page.goto('/#/setting/extensions/model-profile');
  await expect(page.getByText('此入口已停用', { exact: true })).toBeVisible();
});

test('task list uses a real backend response', async ({ page }) => {
  await page.goto('/#/login/admin');
  await page.getByRole('textbox', { name: '请输入用户名' }).fill('admin');
  await page.getByRole('textbox', { name: '请输入密码' }).fill('metersphere');
  await page.getByRole('button', { name: '登录', exact: true }).click();
  await expect(page).not.toHaveURL(/login\/admin/);
  const responsePromise = page.waitForResponse(
    (response) => response.url().includes('/ai/execution/task/search') && response.request().method() === 'POST'
  );
  await page.goto('/#/execution/tasks');
  const response = await responsePromise;
  const body = await response.json();
  expect(response.status(), JSON.stringify({ code: body.code, traceId: body.traceId })).toBe(200);
  expect(body.code, JSON.stringify({ code: body.code, traceId: body.traceId })).toBe(100200);
  expect(body.data).toEqual(expect.objectContaining({ items: expect.any(Array), total: expect.any(Number) }));
  await expect(page.getByText(/任务加载失败/)).toHaveCount(0);
});

test('platform creation generates an association ID and editing preserves it', async ({ page }) => {
  await page.goto('/#/login/admin');
  await page.getByRole('textbox', { name: '请输入用户名' }).fill('admin');
  await page.getByRole('textbox', { name: '请输入密码' }).fill('metersphere');
  await page.getByRole('button', { name: '登录', exact: true }).click();
  await expect(page).not.toHaveURL(/login\/admin/);
  await page.goto('/#/execution/platforms');
  const name = `platform-remediation-${Date.now()}`;
  await page.getByRole('button', { name: '新增配置', exact: true }).click();
  const modal = page.locator('.arco-modal:visible');
  await modal.locator('input').first().fill(name);
  await modal.getByPlaceholder('https://test.example.com').fill('https://test.example.com');
  await modal.locator('textarea').fill('https://test.example.com');
  const createdPromise = page.waitForResponse(
    (response) => response.url().includes('/environment-profiles') && response.request().method() === 'POST'
  );
  await page.getByRole('button', { name: '确定', exact: true }).click();
  const created = await (await createdPromise).json();
  expect(created.code).toBe(100200);
  expect(created.data.environmentId).toEqual(expect.any(String));
  expect(created.data.environmentId.length).toBeGreaterThan(0);
  const row = page.getByRole('row').filter({ hasText: name });
  await row.getByRole('button', { name: '编辑', exact: true }).click();
  const updatedPromise = page.waitForResponse(
    (response) => response.url().includes('/environment-profiles/') && response.request().method() === 'PUT'
  );
  await page.getByRole('button', { name: '确定', exact: true }).click();
  const updated = await (await updatedPromise).json();
  expect(updated.code).toBe(100200);
  expect(updated.data.environmentId).toBe(created.data.environmentId);
  await row.getByRole('button', { name: '停用', exact: true }).click();
  await expect(row.getByRole('button', { name: '启用', exact: true })).toBeVisible();
});

test('personal configuration creates a real token and verifies MCP before revocation', async ({ page }) => {
  test.setTimeout(60000);
  await page.goto('/#/login/admin');
  await page.getByRole('textbox', { name: '请输入用户名' }).fill('admin');
  await page.getByRole('textbox', { name: '请输入密码' }).fill('metersphere');
  await page.getByRole('button', { name: '登录', exact: true }).click();
  await expect(page).not.toHaveURL(/login\/admin/);
  const headers = await page.evaluate(() => ({
    'X-AUTH-TOKEN': localStorage.getItem('sessionId') || '',
    'CSRF-TOKEN': localStorage.getItem('csrfToken') || '',
  }));
  let tokenId: string | undefined;
  const name = `navigation-token-${Date.now()}`;
  try {
    await page.goto('/#/setting/personal-access/access');
    await page.getByRole('button', { name: '创建 Token', exact: true }).click();
    await page.locator('.arco-modal:visible input').first().fill(name);
    await page.getByRole('button', { name: /个人 Agent 测试闭环/ }).click();
    const creation = page.waitForResponse(
      (response) => response.url().endsWith('/personal/agent-tokens') && response.request().method() === 'POST'
    );
    await page.getByRole('button', { name: '确定', exact: true }).click();
    const created = (await (await creation).json()).data;
    tokenId = created?.id;
    const discovery = page.waitForResponse(
      (response) => response.url().endsWith('/api/mcp') && response.request().method() === 'POST'
    );
    await page.getByRole('button', { name: '验证 MCP 接入', exact: true }).click();
    const discovered = await (await discovery).json();
    // Close the one-time secret dialog before making assertions that can emit DOM snapshots.
    await page.locator('.arco-modal:visible .arco-modal-close-btn').click();
    expect(tokenId).toBeTruthy();
    expect((discovered.data || discovered).result.tools.length).toBeGreaterThan(0);
    const attachmentBytes = Buffer.from('isolated ordinary attachment verification');
    const uploadResponse = await page.request.post('/api/agent/v1/attachment/upload', {
      headers: { Authorization: `Bearer ${created.token}` },
      multipart: {
        projectId: '100001100001',
        purpose: 'CASE_DETAIL',
        file: { name: 'acceptance.txt', mimeType: 'text/plain', buffer: attachmentBytes },
      },
    });
    const upload = await uploadResponse.json();
    expect(upload.code).toBe(100200);
    const downloaded = await page.request.get(upload.data.downloadPath, {
      headers: { Authorization: `Bearer ${created.token}` },
    });
    expect(downloaded.status()).toBe(200);
    expect(await downloaded.body()).toEqual(attachmentBytes);
    const foreign = await page.request.get(`/api/agent/v1/attachment/download/foreign-project/${upload.data.fileId}`, {
      headers: { Authorization: `Bearer ${created.token}` },
    });
    expect(foreign.status()).toBeGreaterThanOrEqual(400);
    if (process.env.MCP_INSTALLED_E2E === 'true') {
      const output = execFileSync(process.execPath, ['scripts/verify-installed-mcp.mjs'], {
        cwd: '..',
        env: {
          ...process.env,
          MS_BASE_URL: 'http://127.0.0.1:18081',
          MS_AGENT_TOKEN: created.token,
          MS_PROJECT_ID: '100001100001',
        },
        timeout: 30000,
        encoding: 'utf8',
        stdio: ['ignore', 'pipe', 'pipe'],
      });
      expect(output).toContain('PASS: installed MCP 0.7.0');
    }
    await page.getByRole('row').filter({ hasText: name }).getByText('删除', { exact: true }).click();
    await page
      .getByRole('button', { name: /确定|删除/ })
      .last()
      .click();
    await expect(page.getByRole('row').filter({ hasText: name })).toHaveCount(0);
    const revoked = await page.request.post('/api/mcp', {
      headers: { Authorization: `Bearer ${created.token}` },
      data: { jsonrpc: '2.0', id: 1, method: 'tools/list', params: {} },
    });
    expect(revoked.status()).toBeGreaterThanOrEqual(400);
  } finally {
    if (tokenId) await page.request.delete(`/api/personal/agent-tokens/${tokenId}`, { headers });
  }
});
