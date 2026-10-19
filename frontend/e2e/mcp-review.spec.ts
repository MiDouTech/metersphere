import { expect, test } from '@playwright/test';

// Requires the newly built isolated backend with migration 101. Never run against shared environments.
test.skip(process.env.MCP_REMEDIATION_E2E !== 'true', 'Requires isolated MCP remediation environment');
test.use({ trace: 'off', video: 'off' });

test('real token: native create and HTTP retry share one review; revocation blocks replay', async ({
  page,
  baseURL,
}) => {
  test.setTimeout(90000);
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
  const key = `mcp-review-${Date.now()}`;
  const tokenResponse = await page.request.post('/api/personal/agent-tokens', {
    headers,
    data: {
      name: key,
      projectIds: [projectId],
      scopes: 'REVIEW_WRITE;FUNCTIONAL_READ',
      expireTime: Date.now() + 600000,
    },
  });
  const tokenBody = await tokenResponse.json();
  expect(tokenBody.code).toBe(100200);
  const token = tokenBody.data;
  const agentHeaders = { 'Authorization': `Bearer ${token.token}`, 'X-MS-PROJECT': projectId };
  let reviewId: string | undefined;
  try {
    const request = { requestId: key, projectId, name: key, caseIds: [] };
    const native = await page.request.post('/api/mcp', {
      headers: agentHeaders,
      data: {
        jsonrpc: '2.0',
        id: 1,
        method: 'tools/call',
        params: { name: 'metersphere.case_review.create', arguments: request },
      },
    });
    const raw = await native.json();
    const rpc = raw.data || raw;
    expect(rpc.error?.data?.code).toBeUndefined();
    reviewId = rpc.result.structuredContent.result.id;
    expect(typeof reviewId).toBe('string');
    const retry = await page.request.post('/api/agent/v1/case-review/create', { headers: agentHeaders, data: request });
    const retried = await retry.json();
    expect(retried.data?.id).toBe(reviewId);
    const get = await page.request.get(`/api/agent/v1/case-review/${reviewId}`, { headers: agentHeaders });
    expect((await get.json()).data?.id).toBe(reviewId);
    const changed = await page.request.post('/api/agent/v1/case-review/create', {
      headers: agentHeaders,
      data: { ...request, name: `${key}-changed` },
    });
    expect(changed.status()).toBe(409);
    const conflictBody = await changed.json();
    expect((conflictBody.data || conflictBody).code).toBe('IDEMPOTENCY_CONFLICT');
    const disabled = await page.request.post(`/api/personal/agent-tokens/${token.id}/disable`, { headers });
    expect((await disabled.json()).code).toBe(100200);
    const denied = await page.request.post('/api/agent/v1/case-review/create', {
      headers: agentHeaders,
      data: request,
    });
    expect(denied.ok()).toBe(false);
  } finally {
    if (reviewId) await page.request.get(`/api/case/review/delete/${projectId}/${reviewId}`, { headers });
    await page.request.delete(`/api/personal/agent-tokens/${token.id}`, { headers });
  }
});
