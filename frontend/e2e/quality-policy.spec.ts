import { expect as baseExpect, test } from '@playwright/test';
import { execFileSync } from 'node:child_process';
import { readFileSync } from 'node:fs';

const expect = baseExpect.configure({ timeout: 20000 });

// Opt in explicitly: these tests create real drafts in the isolated acceptance project.
test.skip(process.env.QUALITY_E2E !== 'true', 'Requires the isolated quality acceptance environment');

test.beforeAll(async ({ browser, baseURL }) => {
  expect(baseURL).toBe('http://127.0.0.1:15173');
  const context = await browser.newContext({ baseURL });
  const page = await context.newPage();
  try {
    await page.goto('/#/login/admin');
    await page.getByRole('textbox', { name: '请输入用户名' }).fill(process.env.QUALITY_USER || 'admin');
    await page.getByRole('textbox', { name: '请输入密码' }).fill(process.env.QUALITY_PASSWORD || 'metersphere');
    await page.getByRole('button', { name: '登录', exact: true }).click();
    await expect(page).not.toHaveURL(/login\/admin/);
    const headers = await page.evaluate(() => ({
      'X-AUTH-TOKEN': localStorage.getItem('sessionId') || '',
      'CSRF-TOKEN': localStorage.getItem('csrfToken') || '',
      'PROJECT': '100001100001',
      'ORGANIZATION': '100001',
    }));
    await Promise.all(
      ['reader', 'editor', 'publisher', 'pure-admin'].map(async (role) => {
        const email = `quality-${role}-api@quality.invalid`;
        const found = await page.request.get(`/api/system/user/get/${email}`, { headers });
        const existing = await found.json();
        if (existing.data?.id) return;
        const response = await page.request.post('/api/system/user/add', {
          headers,
          data: {
            userInfoList: [{ name: `Quality ${role}`, email }],
            userRoleIdList: role === 'pure-admin' ? ['admin'] : ['member'],
          },
        });
        const result = await response.json();
        expect(result.data?.successList?.length).toBe(1);
      })
    );
    execFileSync(
      'docker',
      [
        'exec',
        '-i',
        'msp-quality-verify-mysql-1',
        'sh',
        '-c',
        'MYSQL_PWD=$MYSQL_ROOT_PASSWORD mysql -uroot metersphere_quality',
      ],
      {
        input: readFileSync('../deploy/quality-verify/global-rbac-fixture.sql', 'utf8'),
        encoding: 'utf8',
      }
    );
    const rulesJson = JSON.stringify({
      schemaVersion: 'quality-policy.v1',
      name: 'Global fixture',
      rules: [
        {
          ruleId: 'Q-EVIDENCE-01',
          parameters: { requiredRoles: ['AFTER_ACTION'], allowedMimeTypes: ['image/png'], maxArtifactBytes: 1024 },
        },
        { ruleId: 'Q-ASSERT-01', parameters: { allowedOperators: ['EQUALS'], expectedSource: 'FROZEN_CONTRACT' } },
      ],
    });
    const seed = await page.request.post('/api/system/quality/policies', { headers, data: { rulesJson } });
    expect(seed.status()).toBe(200);
  } finally {
    await context.close();
  }
});

test.beforeEach(async ({ page, baseURL }, info) => {
  expect(baseURL).toBe('http://127.0.0.1:15173');
  const role = info.title.startsWith('role:') ? info.title.split(':')[1] : undefined;
  await page.goto('/#/login/admin');
  await page
    .getByRole('textbox', { name: '请输入用户名' })
    .fill(role ? `quality-${role}-api@quality.invalid` : process.env.QUALITY_USER || 'admin');
  await page
    .getByRole('textbox', { name: '请输入密码' })
    .fill(role ? `quality-${role}-api@quality.invalid` : process.env.QUALITY_PASSWORD || 'metersphere');
  await page.getByRole('button', { name: '登录', exact: true }).click();
  await expect(page).not.toHaveURL(/login\/admin/);
  await page.goto('/#/execution/quality-policy');
  await expect(page.getByRole('heading', { name: '门禁策略', exact: true })).toBeVisible();
  if (!role) await expect(page.getByRole('button', { name: '新建草稿', exact: true })).toBeEnabled();
});

test('slow validation cannot change the save target, publication exposes audit facts', async ({ page }) => {
  const name = `quality-race-${Date.now()}`;
  await page.getByRole('button', { name: '新建草稿', exact: true }).click();
  await page.locator('form').getByRole('textbox').fill(name);
  let release!: () => void;
  const delayed = new Promise<void>((resolve) => {
    release = resolve;
  });
  let reached!: () => void;
  const requested = new Promise<void>((resolve) => {
    reached = resolve;
  });
  await page.route('**/quality/policies/validate', async (route) => {
    reached();
    await delayed;
    await route.continue(); // Delay only; never mock the validation response.
  });
  const writes: string[] = [];
  page.on('request', (request) => {
    if (request.method() === 'PUT') writes.push(request.url());
  });
  await page.getByRole('button', { name: '保存草稿', exact: true }).click();
  await requested;
  await expect(page.locator('.arco-modal-close-btn:visible')).toHaveCount(0);
  await page.keyboard.press('Escape');
  await expect(page.getByRole('button', { name: '保存草稿', exact: true })).toBeVisible();
  release();
  await expect(page.getByRole('button', { name: '保存草稿', exact: true })).toBeHidden();
  expect(writes).toEqual([]);
  await page.unroute('**/quality/policies/validate');
  const row = page.locator('tbody tr').first();
  await row.getByRole('button', { name: '查看', exact: true }).click();
  await expect(page.getByLabel('门禁策略 JSON').getByRole('textbox')).toHaveValue(new RegExp(name));
  await page.getByRole('button', { name: '关闭', exact: true }).click();
  await row.getByRole('button', { name: '发布', exact: true }).click();
  await page.getByPlaceholder('填写发布或回退原因').fill('Automated isolated regression');
  await page.getByRole('button', { name: '确认发布', exact: true }).click();
  await expect(page.getByRole('button', { name: '确认发布', exact: true })).toBeHidden();
  await page.getByRole('button', { name: '查看当前版本', exact: true }).click();
  await expect(page.getByText('发布审计', { exact: true })).toBeVisible();
  await expect(page.getByText('Automated isolated regression', { exact: true }).last()).toBeVisible();
});

['reader', 'editor', 'publisher'].forEach((role) => {
  test(`role:${role}:real session enforces separate permissions`, async ({ page }) => {
    const headers = await page.evaluate(() => ({
      'X-Auth-Token': localStorage.getItem('sessionId') || '',
      'CSRF-TOKEN': localStorage.getItem('csrfToken') || '',
      'PROJECT': '100001100001',
      'ORGANIZATION': '100001',
    }));
    const base = '/api/system/quality/policies';
    const listing = await page.request.get(`${base}`, { headers });
    expect(listing.status()).toBe(200);
    const body = await listing.json();
    const data = body.data || body;
    const draft = data.items.find((item: { status: string }) => item.status === 'DRAFT');
    expect(draft).toBeTruthy();
    const validation = await page.request.post(`${base}/validate`, {
      headers,
      data: { rulesJson: draft.rulesJson },
    });
    expect(validation.status()).toBe(role === 'editor' ? 200 : 403);
    const create = await page.request.post(base, {
      headers,
      data: { rulesJson: draft.rulesJson },
    });
    expect(create.status()).toBe(role === 'editor' ? 200 : 403);
    if (role === 'editor') {
      await expect(page.getByRole('button', { name: '新建草稿', exact: true })).toBeVisible();
      await expect(page.getByRole('button', { name: '发布', exact: true })).toHaveCount(0);
    } else {
      await expect(page.getByRole('button', { name: '新建草稿', exact: true })).toHaveCount(0);
    }
    const publish = await page.request.post(`${base}/${draft.id}/publish`, {
      headers,
      data: {
        expectedVersion: draft.rowVersion,
        expectedCurrentPolicyId: data.currentPolicyId,
        changeReason: 'Isolated role regression',
      },
    });
    expect(publish.status()).toBe(role === 'publisher' ? 200 : 403);
    const foreign = await page.request.get(`${base}/${draft.id}?projectId=other-project`, { headers });
    expect(foreign.status()).toBe(400);
  });
});

test('disabled selected project does not restrict global policy management', async ({ page }) => {
  const headers = await page.evaluate(() => ({
    'X-AUTH-TOKEN': localStorage.getItem('sessionId') || '',
    'CSRF-TOKEN': localStorage.getItem('csrfToken') || '',
    'PROJECT': '100001100001',
    'ORGANIZATION': '100001',
  }));
  const sql = (input: string) =>
    execFileSync(
      'docker',
      [
        'exec',
        '-i',
        'msp-quality-verify-mysql-1',
        'sh',
        '-c',
        'MYSQL_PWD=$MYSQL_ROOT_PASSWORD mysql -uroot -N metersphere_quality',
      ],
      { input, encoding: 'utf8' }
    );
  const original = sql('SELECT CAST(enable AS UNSIGNED) FROM project WHERE id="100001100001";').trim();
  expect(original).toMatch(/^[01]$/);
  try {
    sql('UPDATE project SET enable=0 WHERE id="100001100001";');
    const read = await page.request.get('/api/system/quality/policies', { headers });
    expect(read.status()).toBe(200);
    const body = await read.json();
    const data = body.data || body;
    const write = await page.request.post('/api/system/quality/policies', {
      headers,
      data: { rulesJson: data.items[0].rulesJson },
    });
    expect(write.status()).toBe(200);
  } finally {
    sql(`UPDATE project SET enable=${original} WHERE id="100001100001";`);
  }
});

test('invalid nested parameter keeps JSON and identifies its exact path', async ({ page }) => {
  await page.getByRole('button', { name: '新建草稿', exact: true }).click();
  await page.getByRole('button', { name: 'JSON 编辑 / 粘贴导入', exact: true }).click();
  const json = page.getByLabel('门禁策略 JSON').getByRole('textbox');
  const doc = JSON.parse(await json.inputValue());
  doc.rules[0].parameters.maxArtifactBytes = 99999999;
  const raw = JSON.stringify(doc);
  await json.fill(raw);
  await page.getByRole('button', { name: '可视化表单', exact: true }).click();
  await expect(page.getByText(/\/rules\/0\/parameters\/maxArtifactBytes/)).toBeVisible();
  await expect(json).toHaveValue(raw);
});

test('role:pure-admin:no project binding is required for global access', async ({ page }) => {
  await expect(page).toHaveURL(/setting\/system\/quality-policy/);
  await expect(page.getByRole('button', { name: '新建草稿', exact: true })).toBeEnabled();
  const headers = await page.evaluate(() => ({
    'X-AUTH-TOKEN': localStorage.getItem('sessionId') || '',
    'CSRF-TOKEN': localStorage.getItem('csrfToken') || '',
  }));
  expect((await page.request.get('/api/system/quality/policies', { headers })).status()).toBe(200);
});

test('legacy import creates an idempotent draft without switching the global effective policy', async ({ page }) => {
  const headers = await page.evaluate(() => ({
    'X-AUTH-TOKEN': localStorage.getItem('sessionId') || '',
    'CSRF-TOKEN': localStorage.getItem('csrfToken') || '',
  }));
  const beforeResponse = await page.request.get('/api/system/quality/policies', { headers });
  const beforeBody = await beforeResponse.json();
  const before = beforeBody.data || beforeBody;
  await page.getByRole('button', { name: '查看旧项目策略归档' }).click();
  await expect(page.getByRole('button', { name: '导入为全局草稿' }).first()).toBeVisible();
  const archivedResponse = await page.request.get('/api/system/quality/legacy-policies', { headers });
  const archivedBody = await archivedResponse.json();
  const archived = archivedBody.data || archivedBody;
  expect(archived.total).toBeGreaterThan(0);
  const path = `/api/system/quality/legacy-policies/${archived.items[0].id}/import`;
  const importedResponse = await page.request.post(path, { headers });
  expect(importedResponse.status()).toBe(200);
  const importedBody = await importedResponse.json();
  const imported = importedBody.data || importedBody;
  expect(imported.status).toBe('DRAFT');
  const repeatedResponse = await page.request.post(path, { headers });
  const repeatedBody = await repeatedResponse.json();
  expect((repeatedBody.data || repeatedBody).id).toBe(imported.id);
  const afterResponse = await page.request.get('/api/system/quality/policies', { headers });
  const afterBody = await afterResponse.json();
  expect((afterBody.data || afterBody).currentPolicyId).toBe(before.currentPolicyId);
});

test('role:editor:revocation during editing returns local 403 and preserves the draft', async ({ page }) => {
  await page.getByRole('button', { name: '新建草稿', exact: true }).click();
  const name = `revoked-draft-${Date.now()}`;
  const input = page.locator('form').getByRole('textbox');
  await input.fill(name);
  const sql = (statement: string) =>
    execFileSync(
      'docker',
      [
        'exec',
        '-i',
        'msp-quality-verify-mysql-1',
        'sh',
        '-c',
        'MYSQL_PWD=$MYSQL_ROOT_PASSWORD mysql -uroot -N metersphere_quality',
      ],
      { input: statement, encoding: 'utf8' }
    );
  try {
    sql(
      "DELETE FROM user_role_permission WHERE role_id='quality-global-editor' AND permission_id='SYSTEM_QUALITY:MANAGE';"
    );
    const rejected = page.waitForResponse((response) => response.url().endsWith('/quality/policies/validate'));
    await page.getByRole('button', { name: '保存草稿', exact: true }).click();
    expect((await rejected).status()).toBe(403);
    await expect(input).toHaveValue(name);
    await expect(page.getByRole('button', { name: '保存草稿', exact: true })).toBeEnabled();
    await expect(page).toHaveURL(/setting\/system\/quality-policy/);
  } finally {
    sql(
      "INSERT IGNORE INTO user_role_permission(id,role_id,permission_id) VALUES ('qg-editor-manage','quality-global-editor','SYSTEM_QUALITY:MANAGE');"
    );
  }
  await page.getByRole('button', { name: '保存草稿', exact: true }).click();
  await expect(page.getByRole('button', { name: '保存草稿', exact: true })).toBeHidden();
});
