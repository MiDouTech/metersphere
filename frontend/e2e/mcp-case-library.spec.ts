import { expect, test } from '@playwright/test';

test.skip(process.env.MCP_CASE_LIBRARY_E2E !== 'true', 'Requires isolated case-library backend');
test.use({ trace: 'off', video: 'off' });

test('repository paging, detail, module version and explicit grants through real MCP', async ({ page, baseURL }) => {
  test.setTimeout(180000);
  expect(baseURL).toBe('http://127.0.0.1:15173');
  await page.goto('/#/login/admin');
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
  const key = `library-${Date.now()}`;
  const tokens: string[] = [];
  const cases: string[] = [];
  let moduleId: string | undefined;
  async function token(scopes: string) {
    const response = await page.request.post('/api/personal/agent-tokens', {
      headers,
      data: { name: key, projectIds: [projectId], scopes, expireTime: Date.now() + 600000 },
    });
    const body = await response.json();
    expect(body.code).toBe(100200);
    tokens.push(body.data.id);
    return { 'Authorization': `Bearer ${body.data.token}`, 'X-MS-PROJECT': projectId };
  }
  async function call(agentHeaders: Record<string, string>, name: string, args: Record<string, unknown>) {
    const response = await page.request.post('/api/mcp', {
      headers: agentHeaders,
      data: {
        jsonrpc: '2.0',
        id: 1,
        method: 'tools/call',
        params: { name: `metersphere.functional.${name}`, arguments: args },
      },
    });
    const body = await response.json();
    return { ...(body.data || body), httpStatus: response.status() };
  }
  try {
    await page.goto('/#/case-management/featureCase');
    await expect(page.locator('.arco-table').first()).toBeVisible();
    await expect(page.getByRole('button', { name: /AI\s*自动化执行/ })).toHaveCount(0);
    const legacy = await token('AGENT_ALL');
    const granted = await token('AGENT_ALL;CASE_MODULE_WRITE_V2;CASE_COMMENT_EDIT_V2;CASE_COMMENT_DELETE_V2');
    const managementDenied = await page.request.get('/api/personal/agent-tokens', {
      headers: { ...headers, ...granted },
    });
    expect(managementDenied.status()).toBe(403);
    const invalidCredential = await page.request.post('/api/mcp', {
      headers: { ...headers, Authorization: 'Bearer msat_invalid' },
      data: { jsonrpc: '2.0', id: 1, method: 'ping' },
    });
    expect(invalidCredential.status()).toBe(401);
    const created = await page.request.post('/api/agent/v1/functional/module/create', {
      headers: granted,
      data: { projectId, name: key },
    });
    const moduleBody = await created.json();
    expect(moduleBody.code).toBe(100200);
    moduleId = moduleBody.data.id;
    await [0, 1, 2].reduce(async (previous, i) => {
      await previous;
      const response = await page.request.post('/api/agent/v1/functional/case/create', {
        headers: granted,
        data: {
          projectId,
          moduleId,
          name: `${key}-${i}`,
          tags: [key],
          description: 'repository fixture',
          steps: [{ num: 1, desc: '打开页面', expected: '显示内容' }],
        },
      });
      const body = await response.json();
      expect(body.code).toBe(100200);
      cases.push(body.data.caseId);
    }, Promise.resolve());
    const first = await call(granted, 'search', { projectId, filters: { tags: [key] }, pageSize: 2 });
    expect(first.error).toBeUndefined();
    const { result } = first.result.structuredContent;
    expect(result.total).toBe(3);
    expect(result.cases).toHaveLength(2);
    expect(result.hasMore).toBe(true);
    expect(result.cases[0].testPlanCaseId).toBeFalsy();
    expect(result.cases[0].steps).toHaveLength(1);
    // Respect the documented 300 ms per-token search throttle.
    await page.waitForTimeout(350);
    const second = await call(granted, 'search', { projectId, filters: { tags: [key] }, current: 2, pageSize: 2 });
    const rest = second.result.structuredContent.result;
    expect(rest.total).toBe(3);
    expect(rest.cases).toHaveLength(1);
    expect(rest.hasMore).toBe(false);
    expect(new Set([...result.cases, ...rest.cases].map((item) => item.caseId)).size).toBe(3);
    const detail = await call(granted, 'get', { caseId: cases[0] });
    expect(detail.result.structuredContent.result.projectId).toBe(projectId);
    expect(detail.result.structuredContent.result.modulePath).toContain(key);
    expect(typeof detail.result.structuredContent.result.updateTime).toBe('number');
    const comment = await call(granted, 'comment.create', {
      projectId,
      caseId: cases[0],
      content: 'original comment',
      requestId: `${key}-comment`,
    });
    expect(comment.error).toBeUndefined();
    const savedComment = comment.result.structuredContent.result;
    const commentPatch = {
      projectId,
      caseId: cases[0],
      commentId: savedComment.id,
      content: 'edited comment',
      expectedUpdateTime: savedComment.updateTime,
      requestId: `${key}-comment-edit`,
    };
    expect((await call(legacy, 'comment.update', commentPatch)).error).toBeTruthy();
    const editedComment = await call(granted, 'comment.update', commentPatch);
    expect(editedComment.error).toBeUndefined();
    expect(editedComment.result.structuredContent.result.content).toBe('edited comment');
    const staleComment = await call(granted, 'comment.update', { ...commentPatch, requestId: `${key}-comment-stale` });
    expect(staleComment.error?.data?.code).toBe('VERSION_CONFLICT');
    const comments = await call(granted, 'comments.list', { caseId: cases[0] });
    expect(comments.result.structuredContent.result.list.find((item) => item.id === savedComment.id)?.content).toBe(
      'edited comment'
    );
    const deletion = { projectId, caseId: cases[0], commentId: savedComment.id,
      expectedUpdateTime: editedComment.result.structuredContent.result.updateTime, requestId: `${key}-comment-delete` };
    expect((await call(legacy, 'comment.delete', deletion)).error).toBeTruthy();
    const deletedComment = await call(granted, 'comment.delete', deletion);
    expect(deletedComment.error).toBeUndefined();
    expect(deletedComment.result.structuredContent.result.deleted).toBe(true);
    const tombstone = await call(granted, 'comments.list', { caseId: cases[0] });
    expect(tombstone.result.structuredContent.result.list.find((item) => item.id === savedComment.id)).toMatchObject({ content: '', deleted: true });
    const metadata = await call(granted, 'module.get', { projectId, moduleId });
    const mutation = {
      projectId,
      moduleId,
      name: `${key}-renamed`,
      expectedUpdateTime: metadata.result.structuredContent.result.updateTime,
      requestId: `${key}-rename`,
    };
    expect((await call(legacy, 'module.update', mutation)).error).toBeTruthy();
    const renamed = await call(granted, 'module.update', mutation);
    expect(renamed.error).toBeUndefined();
    expect(renamed.result.structuredContent.result.name).toBe(`${key}-renamed`);
    const replay = await call(granted, 'module.update', mutation);
    expect(replay.result.structuredContent.result.updateTime).toBe(renamed.result.structuredContent.result.updateTime);
    const conflict = await call(granted, 'module.update', { ...mutation, requestId: `${key}-stale` });
    expect(conflict.error?.data?.code).toBe('VERSION_CONFLICT');
    const cycle = await call(granted, 'module.move', {
      projectId,
      moduleId,
      parentId: moduleId,
      expectedUpdateTime: renamed.result.structuredContent.result.updateTime,
      requestId: `${key}-cycle`,
    });
    expect(cycle.error?.data?.code).toBe('AGENT_VALIDATION_ERROR');
    const disabled = await page.request.post(`/api/personal/agent-tokens/${tokens[1]}/disable`, { headers });
    expect((await disabled.json()).code).toBe(100200);
    const denied = await call(granted, 'module.update', mutation);
    expect(denied.httpStatus).toBeGreaterThanOrEqual(400);
    expect(denied.result).toBeUndefined();
  } finally {
    await Promise.all(
      cases.filter(Boolean).map(async (id) => {
        await page.request.post('/api/functional/case/delete', { headers, data: { id, projectId, deleteAll: false } });
        await page.request.get(`/api/functional/case/trash/delete/${id}`, { headers });
      })
    );
    if (moduleId) await page.request.get(`/api/functional/case/module/delete/${moduleId}`, { headers });
    await Promise.all(tokens.map((id) => page.request.delete(`/api/personal/agent-tokens/${id}`, { headers })));
  }
});
