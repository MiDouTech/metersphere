import { expect, test } from '@playwright/test';
import { execFileSync } from 'node:child_process';
import { createHash } from 'node:crypto';
import { writeFileSync } from 'node:fs';

test.skip(process.env.MCP_BUG_E2E !== 'true', 'Requires isolated local acceptance backend');
test.use({ trace: 'off', video: 'off' });
test('MCP creates bugs and imports XLSX with replay safety, exports a downloadable workbook', async ({
  page,
  baseURL,
}) => {
  test.setTimeout(360000);
  expect(baseURL).toMatch(/^http:\/\/127\.0\.0\.1:15174$/);
  await page.goto('/#/login/admin');
  await page.getByRole('textbox', { name: '请输入用户名' }).fill('admin');
  await page.getByRole('textbox', { name: '请输入密码' }).fill('metersphere');
  await page.getByRole('button', { name: '登录', exact: true }).click();
  await expect
    .poll(() => page.evaluate(() => Boolean(localStorage.getItem('sessionId'))), { timeout: 30000 })
    .toBe(true);
  await page.goto(`/?mcpAcceptance=${Date.now()}#/bug-management/index?orgId=100001&pId=100001100001`);
  await expect(page).not.toHaveURL(/login\/admin/, { timeout: 30000 });
  const projectId = '100001100001';
  const key = `bug-transfer-${Date.now()}`;
  const headers = await page.evaluate(
    (project) => ({
      'X-AUTH-TOKEN': localStorage.getItem('sessionId') || '',
      'CSRF-TOKEN': localStorage.getItem('csrfToken') || '',
      'PROJECT': project,
      'ORGANIZATION': '100001',
    }),
    projectId
  );
  const tokenResponse = await page.request.post('/api/personal/agent-tokens', {
    headers,
    data: { name: key, projectIds: [projectId], scopes: 'AGENT_ALL', expireTime: Date.now() + 600000 },
  });
  const token = (await tokenResponse.json()).data;
  expect(token?.token).toBeTruthy();
  const auth = { Authorization: `Bearer ${token.token}` };
  const created: string[] = [];
  const evidence: unknown[] = [];
  const call = async (name: string, args: Record<string, unknown>) => {
    const r = await page.request.post('/api/mcp', {
      headers: auth,
      data: {
        jsonrpc: '2.0',
        id: 1,
        method: 'tools/call',
        params: { name: `metersphere.bug.${name}`, arguments: args },
      },
    });
    const body = await r.json();
    const rpc = body.data || body;
    evidence.push({ name, error: rpc.error, result: rpc.result?.structuredContent?.result });
    return rpc;
  };
  const result = (rpc: any) => {
    expect(rpc.error, JSON.stringify(rpc.error)).toBeUndefined();
    return rpc.result.structuredContent.result;
  };
  try {
    expect((await call('not-existing', { projectId })).error.data.code).toBe('MCP_TOOL_NOT_FOUND');
    const template = result(await call('template.get', { projectId }));
    expect(template.customFields.some((f: any) => f.fieldId === 'handleUser')).toBeTruthy();
    const fields: Record<string, string> = {};
    template.customFields.forEach((field: any) => {
      if (field.fieldId !== 'status' && field.required) {
        fields[field.fieldId] = field.defaultValue || field.options?.[0]?.value;
        expect(fields[field.fieldId], field.fieldId).toBeTruthy();
      }
    });
    const createArgs = {
      projectId,
      title: key,
      description: 'Real MCP creation acceptance',
      customFields: fields,
      requestId: `${key}-create`,
    };
    const bug = result(await call('create', createArgs));
    created.push(bug.id);
    expect(result(await call('get', { bugId: bug.id })).title).toBe(key);
    expect(result(await call('create', createArgs)).id).toBe(bug.id);
    expect(result(await call('search', { projectId, query: key })).total).toBe(1);
    expect((await call('create', { ...createArgs, title: `${key}-changed` })).error.data.code).toBe(
      'IDEMPOTENCY_CONFLICT'
    );
    expect(
      (
        await call('create', {
          ...createArgs,
          requestId: `${key}-bad`,
          customFields: { ...fields, handleUser: 'foreign' },
        })
      ).error.data.code
    ).toBe('BUG_FIELD_VALUE_INVALID');
    const columns = [
      'title',
      'description',
      'handleUser',
      ...Object.keys(fields)
        .filter((f) => f !== 'handleUser')
        .map((f) => `custom:${f}`),
    ];
    const row = [
      `${key}-xlsx`,
      'Imported through native MCP',
      fields.handleUser,
      ...Object.keys(fields)
        .filter((f) => f !== 'handleUser')
        .map((f) => fields[f]),
    ];
    const base64 = execFileSync(
      'python',
      [
        '-c',
        'import sys,json,io,base64;from openpyxl import Workbook;v=json.loads(sys.argv[1]);w=Workbook();s=w.active;s.append(v[0]);s.append(v[1]);b=io.BytesIO();w.save(b);print(base64.b64encode(b.getvalue()).decode())',
        JSON.stringify([columns, row]),
      ],
      { encoding: 'utf8' }
    ).trim();
    const input = { projectId, fileName: 'bugs.xlsx', base64 };
    const preview = result(await call('import.preview', input));
    expect(preview.validCount).toBe(1);
    const commit = { ...input, previewHash: preview.previewHash, requestId: `${key}-import` };
    const imported = result(await call('import.commit', commit));
    expect(imported.status).toBe('SUCCEEDED');
    created.push(imported.rows[0].bugId);
    expect(result(await call('import.commit', commit)).rows[0].bugId).toBe(imported.rows[0].bugId);
    expect(result(await call('search', { projectId, query: `${key}-xlsx` })).total).toBe(1);
    expect((await call('import.commit', { ...commit, previewHash: '0'.repeat(64) })).error.data.code).toBe(
      'BUG_IMPORT_PREVIEW_CHANGED'
    );
    const partialInput = {
      projectId,
      rows: [{ title: `${key}-partial`, customFields: fields }, { title: `${key}-invalid` }],
    };
    const partialPreview = result(await call('import.preview', partialInput));
    expect(partialPreview.validCount).toBe(1);
    const partialArgs = { ...partialInput, previewHash: partialPreview.previewHash, requestId: `${key}-partial` };
    const partial = result(await call('import.commit', partialArgs));
    expect(partial.status).toBe('PARTIAL');
    expect(partial.created).toBe(1);
    created.push(partial.rows[0].bugId);
    expect(result(await call('import.commit', partialArgs)).rows[0].bugId).toBe(partial.rows[0].bugId);
    result(await call('export.columns', { projectId }));
    expect(
      (
        await call('export', {
          projectId,
          bugIds: [bug.id],
          requestId: `${key}-invalid-column`,
          exportColumns: [{ key: 'not-a-field', columnType: 'system' }],
        })
      ).error.data.code
    ).toBe('AGENT_VALIDATION_ERROR');
    const exported = result(
      await call('export', {
        projectId,
        bugIds: [bug.id, imported.rows[0].bugId],
        requestId: `${key}-export`,
        exportColumns: [
          { key: 'name', columnType: 'system' },
          { key: 'content', columnType: 'system' },
        ],
      })
    );
    const download = await page.request.get(exported.downloadPath, { headers: auth });
    expect(download.status()).toBe(200);
    const bytes = await download.body();
    expect(bytes.subarray(0, 2).toString()).toBe('PK');
    expect(createHash('sha256').update(bytes).digest('hex')).toBe(exported.sha256);
    writeFileSync('../.codex-tmp/mcp-bugs-export.zip', bytes);
    const workbook = JSON.parse(
      execFileSync(
        'python',
        [
          '-c',
          'import sys,zipfile,io,json;from openpyxl import load_workbook;z=zipfile.ZipFile(sys.argv[1]);f=[n for n in z.namelist() if n.endswith(".xlsx")][0];w=load_workbook(io.BytesIO(z.read(f)));print(json.dumps(list(w.active.values),ensure_ascii=True))',
          '../.codex-tmp/mcp-bugs-export.zip',
        ],
        { encoding: 'utf8' }
      )
    );
    expect(JSON.stringify(workbook)).toContain(key);
    expect(JSON.stringify(workbook)).toContain('Imported through native MCP');
    const listing = page.waitForResponse(
      (response) => response.url().includes('/api/bug/page') && response.request().method() === 'POST',
      { timeout: 60000 }
    );
    await page.goto(`/?mcpAcceptance=${Date.now()}#/bug-management/index?orgId=100001&pId=${projectId}`);
    const listed = await listing;
    evidence.push({ name: 'UI.bug.page', request: listed.request().postDataJSON(), result: await listed.json() });
    await expect(page.getByText(key, { exact: true }).first()).toBeVisible({ timeout: 30000 });
    await page.screenshot({ path: '../.codex-tmp/mcp-bugs-visible.png', fullPage: true });
    // Download revalidates the current token permission, including after export.
    await page.request.patch(`/api/personal/agent-tokens/${token.id}`, {
      headers,
      data: { name: key, projectIds: [projectId], scopes: 'PROJECT_READ' },
    });
    const deniedDownload = await page.request.get(exported.downloadPath, { headers: auth });
    expect(deniedDownload.status()).toBe(403);
    const deniedBody = await deniedDownload.json();
    expect((deniedBody.data || deniedBody).code).toBe('SCOPE_DENIED');
  } catch (error) {
    await page.screenshot({ path: '../.codex-tmp/mcp-bugs-failed-before-cleanup.png', fullPage: true });
    throw error;
  } finally {
    writeFileSync('../.codex-tmp/mcp-bug-e2e-results.json', JSON.stringify(evidence, null, 2));
    await Promise.all(created.map((id) => page.request.delete(`/api/bug/delete/${id}`, { headers })));
    await page.request.delete(`/api/personal/agent-tokens/${token.id}`, { headers });
  }
});
