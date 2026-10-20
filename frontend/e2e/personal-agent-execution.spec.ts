import { expect, test } from '@playwright/test';

test.skip(process.env.MCP_CASE_LIBRARY_E2E !== 'true', 'Requires isolated acceptance backend');
test.use({ trace: 'off', video: 'off' });

test('personal token preserves a pending report without publishing when independent review is unavailable', async ({
  page,
  baseURL,
}) => {
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
  const key = `direct-core-${Date.now()}`;
  let tokenId: string | undefined;
  let caseId: string | undefined;
  let moduleId: string | undefined;
  try {
    const createdToken = await page.request.post('/api/personal/agent-tokens', {
      headers,
      data: { name: key, projectIds: [projectId], scopes: 'AGENT_ALL', expireTime: Date.now() + 600000 },
    });
    const tokenBody = await createdToken.json();
    expect(tokenBody.code).toBe(100200);
    tokenId = tokenBody.data.id;
    const agentHeaders = { 'Authorization': `Bearer ${tokenBody.data.token}`, 'X-MS-PROJECT': projectId };
    const call = async (name: string, args: Record<string, unknown>) => {
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
    };
    const module = await page.request.post('/api/agent/v1/functional/module/create', {
      headers: agentHeaders,
      data: { projectId, name: key },
    });
    moduleId = (await module.json()).data.id;
    const created = await page.request.post('/api/agent/v1/functional/case/create', {
      headers: agentHeaders,
      data: { projectId, moduleId, name: key, steps: [{ num: 1, desc: '打开测试页面', expected: '页面显示内容' }] },
    });
    const createdBody = await created.json();
    expect(createdBody.code).toBe(100200);
    caseId = createdBody.data.caseId;
    const limited = await page.request.patch(`/api/personal/agent-tokens/${tokenId}`, {
      headers,
      data: {
        name: key,
        projectIds: [projectId],
        scopes: 'PROJECT_READ;FUNCTIONAL_READ;FUNCTIONAL_SUBMIT;CASE_ATTACHMENT',
      },
    });
    expect((await limited.json()).code).toBe(100200);
    const contractResponse = await call('execution.contract', { projectId, caseId });
    expect(contractResponse.error).toBeUndefined();
    const contract = contractResponse.result.structuredContent.result;
    expect(contract.case.steps).toHaveLength(1);
    const resource = { projectId, caseId, contentHash: contract.contentHash };
    await page.goto('/#/case-management/featureCase');
    await expect(page.locator('.arco-table').first()).toBeVisible();
    const tiny = await page.screenshot({ clip: { x: 0, y: 0, width: 2, height: 2 } });
    const invalidImage = await call('execution.evidence.upload', {
      ...resource,
      requestId: `${key}-tiny`,
      stepNum: 1,
      purpose: 'AFTER_STEP',
      fileName: 'tiny.png',
      contentType: 'image/png',
      base64: tiny.toString('base64'),
    });
    expect(invalidImage.error?.data?.code).toBe('QUALITY_EVIDENCE_INVALID');
    // Capture a small empty corner of the actual page: it meets the transport limits
    // but deliberately cannot substantiate the business step. This is a negative fixture.
    const png = await page.screenshot({ clip: { x: 1200, y: 650, width: 64, height: 64 } });
    const evidenceRule = contract.policy.rules.find((rule: { ruleId: string }) => rule.ruleId === 'Q-EVIDENCE-01');
    expect(png.length).toBeLessThanOrEqual(evidenceRule.parameters.maxArtifactBytes);
    const uploaded = await call('execution.evidence.upload', {
      ...resource,
      requestId: `${key}-image`,
      stepNum: 1,
      purpose: 'AFTER_STEP',
      fileName: `${key}.png`,
      contentType: 'image/png',
      base64: png.toString('base64'),
    });
    expect(uploaded.error).toBeUndefined();
    const { evidenceId } = uploaded.result.structuredContent.result;
    const before = await call('get', { caseId });
    const initialResult = before.result.structuredContent.result.lastExecuteResult;
    const submission = {
      ...resource,
      policyHash: contract.policyHash,
      requestId: `${key}-submit`,
      steps: [{ stepNum: 1, actual: '页面显示内容', result: 'SUCCESS', evidenceIds: [evidenceId] }],
    };
    const rejected = await call('execution.submit', {
      ...submission,
      steps: [{ ...submission.steps[0], evidenceIds: ['nonexistent'] }],
    });
    expect(rejected.error?.data?.code).toBe('QUALITY_EVIDENCE_INVALID');
    const rejectedVerdict = await call('execution.submit', {
      ...submission,
      steps: [{ ...submission.steps[0], actual: '页面未显示内容' }],
    });
    expect(rejectedVerdict.error?.data?.code).toBe('QUALITY_FAILURE_FACTS_MISMATCH');
    expect((await call('get', { caseId })).result.structuredContent.result.lastExecuteResult).toBe(initialResult);
    expect((await call('attachments.list', { projectId, caseId })).result.structuredContent.result).toHaveLength(0);
    const accepted = await call('execution.submit', submission);
    expect(accepted.error).toBeUndefined();
    expect(accepted.result.structuredContent.result.gateStatus).toBe('NEEDS_REVIEW');
    expect(accepted.result.structuredContent.result.publishStatus).toBe('NOT_PUBLISHED');
    expect(accepted.result.structuredContent.result.result).toBe('UNKNOWN');
    const report = await call('execution.get', {
      projectId,
      caseId,
      submissionId: accepted.result.structuredContent.result.submissionId,
    });
    expect(report.result.structuredContent.result.facts.steps[0].evidenceIds).toContain(evidenceId);
    expect(report.result.structuredContent.result.gateStatus).toBe('NEEDS_REVIEW');
    expect(report.result.structuredContent.result.publishStatus).toBe('NOT_PUBLISHED');
    const replay = await call('execution.submit', submission);
    expect(replay.result.structuredContent.result.submissionId).toBe(
      accepted.result.structuredContent.result.submissionId
    );
    expect((await call('get', { caseId })).result.structuredContent.result.lastExecuteResult).toBe(initialResult);
    const attachments = (await call('attachments.list', { projectId, caseId })).result.structuredContent.result;
    expect(attachments).toHaveLength(0);
    await page.reload();
    const row = page.getByRole('row').filter({ hasText: key });
    await expect(row.getByText('成功', { exact: true })).toHaveCount(0);
    await row.getByText('详情', { exact: true }).click();
    await expect(page.getByText(`${key}.png`, { exact: true })).toHaveCount(0);
    const disabled = await page.request.post(`/api/personal/agent-tokens/${tokenId}/disable`, { headers });
    expect((await disabled.json()).code).toBe(100200);
    expect((await call('execution.submit', submission)).httpStatus).toBeGreaterThanOrEqual(400);
  } finally {
    if (caseId) {
      await page.request.post('/api/functional/case/delete', {
        headers,
        data: { id: caseId, projectId, deleteAll: false },
      });
      await page.request.get(`/api/functional/case/trash/delete/${caseId}`, { headers });
    }
    if (moduleId) await page.request.get(`/api/functional/case/module/delete/${moduleId}`, { headers });
    if (tokenId) await page.request.delete(`/api/personal/agent-tokens/${tokenId}`, { headers });
  }
});
