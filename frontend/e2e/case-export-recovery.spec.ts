import { expect, test } from '@playwright/test';
import { execFileSync } from 'node:child_process';
import { createHash } from 'node:crypto';
import { writeFile } from 'node:fs/promises';

test.skip(process.env.CASE_EXPORT_E2E !== 'true', 'Requires the isolated export acceptance deployment');
test.use({ trace: 'off', video: 'off' });

test('real UI export creates without WS and both formats download valid content', async ({
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
    page.on('websocket', () => {
      sockets += 1;
    });
    await page.goto('/#/case-management/featureCase');
    const row = page.getByRole('row').filter({ hasText: name }).first();
    await expect(row).toBeVisible();
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
  } finally {
    await page.request.delete(`/api/personal/agent-tokens/${token.id}`, { headers });
  }
});
