import { expect, test } from '@playwright/test';
import { execFileSync } from 'node:child_process';
import { createHash } from 'node:crypto';
import { writeFile } from 'node:fs/promises';

test.skip(process.env.CASE_EXPORT_E2E !== 'true', 'Requires the isolated export acceptance deployment');
test.use({ trace: 'off', video: 'off' });

test('requirement-based pagination and batch actions, then real export without WS in both formats', async ({
  page,
  baseURL,
}, testInfo) => {
  test.setTimeout(180000);
  page.setDefaultTimeout(20000);
  expect(baseURL).toMatch(/^http:\/\/127\.0\.0\.1:15175$/);
  await page.goto('/#/login/admin');
  await expect(page.getByRole('textbox', { name: '请输入用户名' })).toBeVisible({ timeout: 15000 });
  await page.getByRole('textbox', { name: '请输入用户名' }).fill('admin');
  await page.getByRole('textbox', { name: '请输入密码' }).fill('metersphere');
  await page.getByRole('button', { name: '登录', exact: true }).click();
  await expect(page).not.toHaveURL(/login\/admin/);
  const projectId = '100001100001';
  const headers = await page.evaluate(
    (project) => ({
      'X-AUTH-TOKEN': localStorage.getItem('sessionId') || '',
      'CSRF-TOKEN': localStorage.getItem('csrfToken') || '',
      'PROJECT': project,
      'ORGANIZATION': '100001',
    }),
    projectId
  );
  const name = `导出恢复-${Date.now()}`;
  const tokenResponse = await page.request.post('/api/personal/agent-tokens', {
    headers,
    data: { name, projectIds: [projectId], scopes: 'AGENT_ALL', expireTime: Date.now() + 600000 },
  });
  const token = (await tokenResponse.json()).data;
  const agentHeaders = { 'Authorization': `Bearer ${token.token}`, 'X-MS-PROJECT': projectId };
  try {
    const moduleResponse = await page.request.post('/api/agent/v1/functional/module/create', {
      headers: agentHeaders,
      data: { projectId, name },
    });
    const moduleId = (await moduleResponse.json()).data.id;
    const createdResponse = await page.request.post('/api/agent/v1/functional/case/create', {
      headers: agentHeaders,
      data: { projectId, moduleId, name, steps: [{ num: 1, desc: '打开真实导出', expected: '包含中文用例' }] },
    });
    const created = await createdResponse.json();
    expect(created.code).toBe(100200);
    const { caseId } = created.data;
    let sockets = 0;
    const notificationResponses: unknown[] = [];
    page.on('response', async (response) => {
      if (!response.url().endsWith('/ws-ticket')) return;
      const value = await response.json().catch(() => ({}));
      notificationResponses.push({ status: response.status(), code: value.code, enabled: value.data?.enabled,
        protocol: value.data?.protocol, ticketLength: value.data?.ticket?.length, taskId: value.data?.taskId });
    });
    // Acceptance of the mandatory HTTP fallback, even when notification acceleration is enabled.
    const installBlockedWebSocket = () => {
      window.WebSocket = class {
        constructor() {
          const state = window as unknown as { blockedExportSockets?: number };
          state.blockedExportSockets = (state.blockedExportSockets || 0) + 1;
          throw new Error('WebSocket blocked for HTTP fallback acceptance');
        }
      } as unknown as typeof WebSocket;
    };
    await page.addInitScript(installBlockedWebSocket);
    // The next navigation changes only the SPA hash and does not execute init scripts again.
    await page.evaluate(installBlockedWebSocket);
    page.on('websocket', () => {
      sockets += 1;
    });
    // Hold one real status response so a fast one-case export cannot dispose the optional
    // connection before the blocked-transport branch is exercised. No status data is mocked.
    let realStatusResponseDelayed = false;
    let releaseStatusResponse: () => void = () => undefined;
    const statusResponseGate = new Promise<void>((resolve) => { releaseStatusResponse = resolve; });
    await page.route(/\/api\/functional\/case\/export\/tasks\/[0-9a-f-]{36}(?:\?.*)?$/, async (route) => {
      const response = await route.fetch();
      realStatusResponseDelayed = true;
      await statusResponseGate;
      await route.fulfill({ response });
    }, { times: 1 });
    await page.goto('/#/case-management/featureCase');
    await page.locator('.ms-tree').getByText(name, { exact: true }).click();
    await expect(page.getByText('共 1 条', { exact: true })).toBeVisible();
    const exportButton = page.getByRole('button', { name: '导出', exact: true });
    await expect(exportButton).toBeVisible();
    await expect(exportButton).toBeDisabled();
    const historyButton = page.getByRole('button', { name: '导出记录', exact: true });
    await expect(historyButton).toBeEnabled();
    const historyResponse = page.waitForResponse((response) => response.url().includes('/export/tasks/history?'));
    await historyButton.click();
    const initialHistory = await (await historyResponse).json();
    expect(initialHistory.code).toBe(100200);
    expect(initialHistory.data.pageSize).toBe(10);
    const historyDrawer = page.locator('.arco-drawer').filter({ hasText: '我的导出记录' });
    await expect(historyDrawer).toBeVisible();
    await expect(historyDrawer.locator('.arco-pagination-total')).toContainText(String(initialHistory.data.total));
    await historyDrawer.locator('.arco-drawer-close-btn').click();
    await expect(historyDrawer).not.toBeVisible();
    const batchButtons = page.locator('#ms-table-footer-wrapper button');
    const unselectedActionLabels = await batchButtons.allTextContents();
    await expect(page.locator('#ms-table-footer-wrapper').getByTitle('10 条/页', { exact: true })).toBeVisible();
    const assertFooterVisible = async () => {
      await expect(page.locator('#ms-table-footer-wrapper button')).not.toHaveCount(0);
      await expect
        .poll(async () =>
          page.locator('#ms-table-footer-wrapper').evaluate((root) => {
            return [...root.querySelectorAll('button, .arco-select-view')]
              .filter((element) => element.getClientRects().length)
              .filter((element) => {
                const rect = element.getBoundingClientRect();
                if (rect.left < 0 || rect.right > window.innerWidth || rect.top < 0 || rect.bottom > window.innerHeight)
                  return true;
                for (let parent = element.parentElement; parent; parent = parent.parentElement) {
                  const style = getComputedStyle(parent);
                  const bounds = parent.getBoundingClientRect();
                  if (
                    /(hidden|clip|auto|scroll)/.test(style.overflowX) &&
                    (rect.left < bounds.left - 1 || rect.right > bounds.right + 1)
                  )
                    return true;
                  if (
                    /(hidden|clip|auto|scroll)/.test(style.overflowY) &&
                    (rect.top < bounds.top - 1 || rect.bottom > bounds.bottom + 1)
                  )
                    return true;
                }
                return false;
              })
              .map((element) => element.textContent?.trim());
          })
        )
        .toEqual([]);
    };
    await page.setViewportSize({ width: 1024, height: 768 });
    await assertFooterVisible();
    const row = page.getByRole('row').filter({ hasText: name }).first();
    await expect(row).toBeVisible();
    let previousSize = 10;
    /* eslint-disable no-await-in-loop, no-restricted-syntax -- Each pagination change depends on the previous selection. */
    for (const size of [20, 50, 100, 10]) {
      await row.locator('label.arco-checkbox').click();
      await expect(exportButton).toBeEnabled();
      expect(await batchButtons.allTextContents()).toEqual(unselectedActionLabels);
      await assertFooterVisible();
      await page.locator('#ms-table-footer-wrapper').getByTitle(`${previousSize} 条/页`, { exact: true }).click();
      const changed = page.waitForResponse(
        (response) => response.url().includes('/functional/case/page') && response.request().method() === 'POST'
      );
      await page.getByText(`${size} 条/页`, { exact: true }).click();
      const request = (await changed).request().postDataJSON();
      expect(request.pageSize).toBe(size);
      expect(request.current).toBe(1);
      await expect(exportButton).toBeDisabled();
      expect(await batchButtons.allTextContents()).toEqual(unselectedActionLabels);
      await expect(page.locator('#ms-table-footer-wrapper').getByTitle(`${size} 条/页`, { exact: true })).toBeVisible();
      previousSize = size;
    }
    /* eslint-enable no-await-in-loop, no-restricted-syntax */
    await page.screenshot({ path: testInfo.outputPath('batch-actions-1024.png') });
    await page.setViewportSize({ width: 1280, height: 768 });
    await assertFooterVisible();
    await row.locator('label.arco-checkbox').click();
    await expect(row.locator('input[type="checkbox"]')).toBeChecked();
    await page.getByText('导出', { exact: true }).last().click();
    await page.getByText(/导出 Excel 格式/).click();
    const submitted = page.waitForResponse(
      (response) => response.url().includes('/functional/case/export/excel') && response.request().method() === 'POST'
    );
    await page.locator('.arco-drawer:visible').getByRole('button', { name: '导出', exact: true }).click();
    const body = await (await submitted).json();
    expect(body.code).toBe(100200);
    expect(sockets).toBe(0);
    await expect.poll(() => realStatusResponseDelayed).toBe(true);
    try {
      await expect.poll(() => page.evaluate(() => (window as unknown as { blockedExportSockets?: number }).blockedExportSockets || 0),
        { timeout: 10000 }).toBeGreaterThan(0);
    } catch (error) {
      throw new Error(`Blocked transport was not exercised; safe ticket diagnostics: ${JSON.stringify(notificationResponses)}`, { cause: error });
    } finally {
      releaseStatusResponse();
    }
    expect(sockets).toBe(0);

    const waitTask = async (id: string) => {
      let task: any;
      await expect
        .poll(
          async () => {
            const response = await page.request.get(`/api/functional/case/export/tasks/${id}`, { headers });
            const result = await response.json();
            expect(result.code).toBe(100200);
            task = result.data;
            return task.state;
          },
          { timeout: 60000 }
        )
        .not.toBe('RUNNING');
      expect(task.state, JSON.stringify(task)).toBe('SUCCEEDED');
      return task;
    };
    const excel = await waitTask(body.data);
    await expect(page.getByText('导出成功', { exact: true }).first()).toBeVisible();
    await historyButton.click();
    const exportedRow = historyDrawer.getByRole('row').filter({ hasText: excel.taskId });
    await expect(exportedRow.getByRole('button', { name: '停止', exact: true })).toBeDisabled();
    const historicalDownload = page.waitForEvent('download');
    await exportedRow.getByRole('button', { name: '下载', exact: true }).click();
    expect(await (await historicalDownload).failure()).toBeNull();
    await historyDrawer.locator('.arco-drawer-close-btn').click();
    const native = async (action: string, args: Record<string, unknown>) => {
      const response = await page.request.post('/api/mcp', {
        headers: agentHeaders,
        data: {
          jsonrpc: '2.0',
          id: 1,
          method: 'tools/call',
          params: { name: `metersphere.functional.export.${action}`, arguments: { projectId, ...args } },
        },
      });
      const envelope = await response.json();
      const rpc = envelope.data || envelope;
      expect(rpc.error).toBeUndefined();
      expect(rpc.result.isError).not.toBe(true);
      return rpc.result.structuredContent.result;
    };
    const nativeRequest = {
      format: 'xlsx',
      requestId: `native-${Date.now()}`,
      request: {
        selectIds: [caseId],
        selectAll: false,
        systemFields: [{ id: 'name', name: '用例名称' }],
        customFields: [],
        otherFields: [],
      },
    };
    const nativeTask = await native('start', nativeRequest);
    expect((await native('start', nativeRequest)).taskId).toBe(nativeTask.taskId);
    await expect.poll(async () => (await native('status', { taskId: nativeTask.taskId })).state).toBe('SUCCEEDED');
    const nativeChunks: Buffer[] = [];
    let offset = 0;
    /* eslint-disable no-await-in-loop -- Each chunk depends on the previous response offset. */
    for (let index = 0; index < 1024; index += 1) {
      const chunk = await native('download', { taskId: nativeTask.taskId, offset, length: 1024 });
      nativeChunks.push(Buffer.from(chunk.base64, 'base64'));
      expect(chunk.nextOffset).toBe(offset + nativeChunks[index].length);
      offset = chunk.nextOffset;
      if (chunk.eof) break;
      expect(index).toBeLessThan(1023);
    }
    /* eslint-enable no-await-in-loop */
    const sameFile = await page.request.get(`/api/functional/case/download/file/${projectId}/${nativeTask.fileId}`, {
      headers,
    });
    expect(Buffer.concat(nativeChunks).equals(await sameFile.body())).toBe(true);
    expect((await native('stop', { taskId: nativeTask.taskId, requestId: `stop-${nativeTask.taskId}` })).state).toBe(
      'SUCCEEDED'
    );
    execFileSync(process.execPath, ['scripts/verify-case-export-stdio.mjs'], {
      cwd: '..',
      timeout: 60000,
      env: {
        ...process.env,
        MS_BASE_URL: baseURL,
        MS_AGENT_TOKEN: token.token,
        MS_PROJECT_ID: projectId,
        MSP_MCP_INSTALL_ROOT: '.codex-tmp/case-export-installed',
        CASE_EXPORT_REQUEST_JSON: JSON.stringify(nativeRequest),
        CASE_EXPORT_TASK_ID: nativeTask.taskId,
        CASE_EXPORT_SHA256: createHash('sha256').update(Buffer.concat(nativeChunks)).digest('hex'),
      },
    });
    /* eslint-disable no-await-in-loop, no-restricted-syntax -- Verify each export lifecycle sequentially against the shared acceptance deployment. */
    for (const format of ['xlsx', 'xmind']) {
      let task = excel;
      if (format === 'xmind') {
        const response = await page.request.post('/api/functional/case/export/xmind', {
          headers,
          data: {
            projectId,
            requestId: `xmind-${Date.now()}`,
            selectIds: [caseId],
            selectAll: false,
            systemFields: [{ id: 'name', name: '用例名称' }],
            customFields: [],
            otherFields: [],
          },
        });
        const result = await response.json();
        expect(result.code).toBe(100200);
        task = await waitTask(result.data);
      }
      const file = await page.request.get(`/api/functional/case/download/file/${projectId}/${task.fileId}`, {
        headers,
      });
      expect(file.status()).toBe(200);
      const bytes = await file.body();
      expect(bytes.subarray(0, 2).toString()).toBe('PK');
      const output = testInfo.outputPath(`export.${format}`);
      await writeFile(output, bytes);
      execFileSync('python', [
        '-c',
        'import zipfile,sys; z=zipfile.ZipFile(sys.argv[1]); text="\\n".join(z.read(n).decode("utf-8",errors="ignore") for n in z.namelist() if n.endswith((".xml",".json"))); assert sys.argv[2] in text',
        output,
        name,
      ]);
      expect(task.count).toBe(1);
      const stop = await page.request.post(`/api/functional/case/export/tasks/${task.taskId}/stop`, { headers });
      expect((await stop.json()).data.state).toBe('SUCCEEDED');
    }
    /* eslint-enable no-await-in-loop, no-restricted-syntax */
    let frozenName = name;
    /* eslint-disable no-await-in-loop, no-restricted-syntax -- Each mutation must happen after creation and before download. */
    for (const format of ['xlsx', 'xmind']) {
      const response = await page.request.post(`/api/functional/case/export/${format === 'xlsx' ? 'excel' : 'xmind'}`, {
        headers,
        data: {
          projectId,
          requestId: `snapshot-${format}-${Date.now()}`,
          selectIds: [caseId],
          selectAll: false,
          systemFields: [{ id: 'name', name: '用例名称' }],
          customFields: [],
          otherFields: [],
        },
      });
      const started = await response.json();
      expect(started.code).toBe(100200);
      const detail = await page.request.get(`/api/agent/v1/functional/${caseId}`, { headers: agentHeaders });
      const currentCase = (await detail.json()).data;
      const changedName = `${frozenName}-changed-${format}`;
      const updateResponse = await page.request.post('/api/mcp', {
        headers: agentHeaders,
        data: {
          jsonrpc: '2.0',
          id: 4,
          method: 'tools/call',
          params: {
            name: 'metersphere.functional.case.update',
            arguments: {
              projectId,
              caseId,
              expectedUpdateTime: currentCase.updateTime,
              patch: { name: changedName },
              requestId: `snapshot-update-${format}-${Date.now()}`,
            },
          },
        },
      });
      const updateEnvelope = await updateResponse.json();
      const updateRpc = updateEnvelope.data || updateEnvelope;
      expect(updateRpc.error).toBeUndefined();
      expect(updateRpc.result.isError).not.toBe(true);
      const frozenTask = await waitTask(started.data);
      const file = await page.request.get(`/api/functional/case/download/file/${projectId}/${frozenTask.fileId}`, {
        headers,
      });
      expect(file.status()).toBe(200);
      const output = testInfo.outputPath(`frozen.${format}`);
      await writeFile(output, await file.body());
      execFileSync('python', [
        '-c',
        'import zipfile,sys; z=zipfile.ZipFile(sys.argv[1]); text="\\n".join(z.read(n).decode("utf-8",errors="ignore") for n in z.namelist() if n.endswith((".xml",".json"))); assert sys.argv[2] in text; assert sys.argv[3] not in text',
        output,
        frozenName,
        changedName,
      ]);
      frozenName = changedName;
    }
    /* eslint-enable no-await-in-loop, no-restricted-syntax */
    const empty = await page.request.post('/api/functional/case/export/excel', {
      headers,
      data: {
        projectId,
        requestId: `empty-${Date.now()}`,
        selectIds: [],
        selectAll: false,
        systemFields: [{ id: 'name', name: '用例名称' }],
        customFields: [],
        otherFields: [],
      },
    });
    const emptyId = (await empty.json()).data;
    await expect
      .poll(async () => {
        const response = await page.request.get(`/api/functional/case/export/tasks/${emptyId}`, { headers });
        return (await response.json()).data.errorCode;
      })
      .toBe('EXPORT_EMPTY');
    await historyButton.click();
    const failedRow = historyDrawer.getByRole('row').filter({ hasText: emptyId });
    const uiRetryResponse = page.waitForResponse((response) =>
      response.url().endsWith(`/export/tasks/${emptyId}/retry`)
    );
    await failedRow.getByRole('button', { name: '重试', exact: true }).click();
    const uiRetry = await (await uiRetryResponse).json();
    expect(uiRetry.code).toBe(100200);
    expect(uiRetry.data.sourceTaskId).toBe(emptyId);
    await expect(historyDrawer.getByRole('row').filter({ hasText: uiRetry.data.taskId })).toContainText('关联重试任务');
    await expect
      .poll(async () => (await native('status', { taskId: uiRetry.data.taskId })).errorCode)
      .toBe('EXPORT_EMPTY');
    await historyDrawer.locator('.arco-drawer-close-btn').click();
    const retried = await native('retry', { taskId: emptyId, requestId: `retry-${emptyId}` });
    expect(retried.taskId).not.toBe(emptyId);
    expect(retried.sourceTaskId).toBe(emptyId);
    expect((await native('retry', { taskId: emptyId, requestId: `retry-${emptyId}` })).taskId).toBe(retried.taskId);
    await expect.poll(async () => (await native('status', { taskId: retried.taskId })).errorCode).toBe('EXPORT_EMPTY');
    const disabled = await page.request.post(`/api/personal/agent-tokens/${token.id}/disable`, { headers });
    expect((await disabled.json()).code).toBe(100200);
    const revokedDownload = await page.request.post('/api/mcp', {
      headers: agentHeaders,
      data: {
        jsonrpc: '2.0',
        id: 2,
        method: 'tools/call',
        params: {
          name: 'metersphere.functional.export.download',
          arguments: { projectId, taskId: nativeTask.taskId },
        },
      },
    });
    expect(revokedDownload.status()).toBeGreaterThanOrEqual(400);
    const revokedRetry = await page.request.post('/api/mcp', {
      headers: agentHeaders,
      data: {
        jsonrpc: '2.0',
        id: 3,
        method: 'tools/call',
        params: {
          name: 'metersphere.functional.export.retry',
          arguments: { projectId, taskId: emptyId, requestId: `revoked-${emptyId}` },
        },
      },
    });
    expect(revokedRetry.status()).toBeGreaterThanOrEqual(400);
  } finally {
    await page.request.delete(`/api/personal/agent-tokens/${token.id}`, { headers });
  }
});
