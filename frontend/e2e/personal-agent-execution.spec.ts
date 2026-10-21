import { expect, test } from '@playwright/test';

test.skip(process.env.MCP_CASE_LIBRARY_E2E !== 'true', 'Requires isolated acceptance backend');
test.use({ trace: 'off', video: 'off' });

test('personal token publishes reported verdicts with complete coverage and retains repeated and extra steps', async ({
  page,
  baseURL,
}) => {
  test.setTimeout(180000);
  expect(baseURL).toMatch(/^http:\/\/127\.0\.0\.1:(15173|15174)$/);
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
  const policyListing = await page.request.get('/api/system/quality/policies', { headers });
  const policyState = await policyListing.json();
  expect(policyState.code).toBe(100200);
  if (!policyState.data.currentPolicyId) {
    const draft = await page.request.post('/api/system/quality/policies', {
      headers,
      data: {
        rulesJson: JSON.stringify({
          schemaVersion: 'quality-policy.v1',
          name: key,
          rules: [
            {
              ruleId: 'Q-EVIDENCE-01',
              parameters: {
                requiredRoles: ['BEFORE_ACTION', 'AFTER_ACTION'],
                allowedMimeTypes: ['image/png'],
                maxArtifactBytes: 5242880,
              },
            },
            {
              ruleId: 'Q-ASSERT-01',
              parameters: { allowedOperators: ['EQUALS'], expectedSource: 'FROZEN_CONTRACT' },
            },
          ],
        }),
      },
    });
    const createdPolicy = await draft.json();
    expect(createdPolicy.code).toBe(100200);
    const publication = await page.request.post(`/api/system/quality/policies/${createdPolicy.data.id}/publish`, {
      headers,
      data: {
        expectedVersion: createdPolicy.data.rowVersion,
        expectedCurrentPolicyId: null,
        changeReason: 'Isolated direct coverage acceptance fixture',
      },
    });
    expect((await publication.json()).code).toBe(100200);
  }
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
      data: { projectId, moduleId, name: key, steps: [{ num: 1, desc: '打开测试用例列表', expected: '测试用例' }] },
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
    let png = await page.screenshot();
    const evidenceRule = contract.policy.rules.find((rule: { ruleId: string }) => rule.ruleId === 'Q-EVIDENCE-01');
    if (png.length > evidenceRule.parameters.maxArtifactBytes) {
      // A transport-only crop can fit an existing restrictive policy. No screenshot
      // content inference is made by the structure-only acceptance contract.
      png = await page.screenshot({ clip: { x: 1200, y: 650, width: 64, height: 64 } });
    }
    expect(png.length).toBeLessThanOrEqual(evidenceRule.parameters.maxArtifactBytes);
    const roles: string[] = evidenceRule.parameters.requiredRoles;
    const uploadFor = async (position: number, attempt: string) => {
      return Promise.all(
        roles.map(async (role) => {
          const uploaded = await call('execution.evidence.upload', {
            ...resource,
            requestId: `${key}-${attempt}-${position}-${role}`,
            stepNum: position,
            purpose: role === 'BEFORE_ACTION' ? 'BEFORE_STEP' : 'AFTER_STEP',
            fileName: `${key}-${attempt}-${position}-${role}.png`,
            contentType: 'image/png',
            base64: png.toString('base64'),
          });
          expect(uploaded.error).toBeUndefined();
          return uploaded.result.structuredContent.result.evidenceId as string;
        })
      );
    };
    const originalEvidence = await uploadFor(1, 'first');
    const extraEvidence = await uploadFor(2, 'first');
    const original = { stepNum: 1, actual: '用例列表已显示且能操作', result: 'SUCCESS', evidenceIds: originalEvidence };
    const extra = { stepNum: 2, actual: '补充检查列表状态', result: 'SUCCESS', evidenceIds: extraEvidence };
    const submission = {
      ...resource,
      policyHash: contract.policyHash,
      requestId: `${key}-submit`,
      steps: [extra, original, original],
    };
    const missing = await call('execution.submit', {
      ...submission,
      requestId: `${key}-missing`,
      steps: [extra, extra],
    });
    expect(missing.error?.data?.code).toBe('QUALITY_FINAL_FACTS_INCOMPLETE');
    const crossStep = await call('execution.submit', {
      ...submission,
      requestId: `${key}-cross`,
      steps: [{ ...original, evidenceIds: extraEvidence }],
    });
    expect(crossStep.error?.data?.code).toBe('QUALITY_EVIDENCE_INVALID');
    expect((await call('attachments.list', { projectId, caseId })).result.structuredContent.result).toHaveLength(0);
    const accepted = await call('execution.submit', submission);
    expect(accepted.error).toBeUndefined();
    expect(accepted.result.structuredContent.result.gateStatus).toBe('ACCEPTED');
    expect(accepted.result.structuredContent.result.publishStatus).toBe('PUBLISHED');
    expect(accepted.result.structuredContent.result.result).toBe('SUCCESS');
    expect(accepted.result.structuredContent.result.validationMode).toBe('STRUCTURE_ONLY_V1');
    const report = await call('execution.get', {
      projectId,
      caseId,
      submissionId: accepted.result.structuredContent.result.submissionId,
    });
    expect(report.result.structuredContent.result.facts.steps).toHaveLength(3);
    expect(report.result.structuredContent.result.resultSource).toBe('AGENT_REPORTED');
    const replay = await call('execution.submit', submission);
    expect(replay.result.structuredContent.result.submissionId).toBe(
      accepted.result.structuredContent.result.submissionId
    );
    expect((await call('attachments.list', { projectId, caseId })).result.structuredContent.result).toHaveLength(
      roles.length * 2
    );
    expect((await call('get', { caseId })).result.structuredContent.result.lastExecuteResult).toBe('SUCCESS');
    // Equality does not force success: retain an explicit failure even after a successful duplicate.
    const failureEvidence = await uploadFor(1, 'second');
    const second = await call('execution.submit', {
      ...submission,
      requestId: `${key}-failure`,
      steps: [
        { stepNum: 1, actual: '测试用例', result: 'ERROR', evidenceIds: failureEvidence },
        { stepNum: 1, actual: '测试用例', result: 'SUCCESS', evidenceIds: failureEvidence },
      ],
    });
    expect(second.error).toBeUndefined();
    expect(second.result.structuredContent.result.result).toBe('ERROR');
    expect((await call('get', { caseId })).result.structuredContent.result.lastExecuteResult).toBe('ERROR');
    await page.reload();
    const row = page.getByRole('row').filter({ hasText: key });
    await expect(row.getByText('失败', { exact: true })).toBeVisible();
    await row.getByText('详情', { exact: true }).click();
    await expect(page.getByText(`${key}-first-1-${roles[0]}.png`, { exact: true }).first()).toBeVisible();
    await page.screenshot({ path: '../.codex-tmp/direct-coverage-case.png', fullPage: true });
    await page.goto('/#/execution/quality-policy');
    await expect(page.getByText(/不比较实际结果与预期文字/).first()).toBeVisible();
    await page.screenshot({ path: '../.codex-tmp/direct-coverage-policy.png', fullPage: true });
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
