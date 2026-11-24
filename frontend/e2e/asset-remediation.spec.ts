import { expect, type Page, test } from '@playwright/test';

const projectId = 'asset-ui-fixture';
const catalog = { id: 'ui-catalog', name: '验收目录' };
const dataset = { id: 'data-fixture', assetType: 'DATASET', assetName: '测试数据', creationSource: 'UNKNOWN' };
const version = { ...dataset, id: 'version-fixture', assetId: dataset.id, versionNo: 1, status: 'PUBLISHED' };

async function open(page: Page, path: string) {
  await page.goto(`/#/test-assets/${path}${path.includes('?') ? '&' : '?'}orgId=100001&pId=${projectId}`);
}

test.beforeEach(async ({ page, baseURL }) => {
  // All application requests are serviced locally. This suite requires no user account.
  expect(new URL(baseURL!).hostname).toBe('127.0.0.1');
  await page.addInitScript((project) => {
    const role = { id: 'admin', name: '管理员', type: 'SYSTEM', enabled: true };
    localStorage.setItem('MS-locale', 'zh-CN');
    localStorage.setItem('sessionId', 'ui-fixture');
    localStorage.setItem('csrfToken', 'ui-fixture');
    localStorage.setItem(
      'user',
      JSON.stringify({
        id: 'ui-fixture',
        name: 'Fixture',
        role: 'admin',
        lastOrganizationId: '100001',
        lastProjectId: project,
        userRoles: [role],
        userRolePermissions: [{ userRole: role, userRolePermissions: [] }],
        userRoleRelations: [{ roleId: 'admin', sourceId: 'system', userRole: role, userRolePermissions: [] }],
        loginType: ['LOCAL'],
      })
    );
    localStorage.setItem('app', JSON.stringify({ currentOrgId: '100001', currentProjectId: project }));
  }, projectId);
  await page.route('**/*', async (route) => {
    const url = new URL(route.request().url());
    if (url.hostname !== '127.0.0.1') return route.abort();
    if (!/^\/(api|front)\//.test(url.pathname)) return route.continue();
    const endpoint = url.pathname.replace(/^\/(api|front)/, '');
    let data: unknown = [];
    if (endpoint.endsWith('/default-locale')) data = 'zh-CN';
    else if (endpoint === '/case-asset/catalog/page') data = { list: [catalog], total: 1 };
    else if (endpoint === '/case-asset/case/detail-fixture') data = { name: '详情数据', caseEditType: 'STEP' };
    else if (endpoint === '/test-assets/versions') data = { list: [version], total: 1 };
    else if (endpoint === '/test-assets/catalog') data = { list: [dataset], total: 1 };
    else if (endpoint.endsWith('/page')) data = { list: [], total: 0 };
    else if (endpoint.includes('license')) data = false;
    await route.fulfill({ json: { code: 100200, data } });
  });
});
test('blank catalog stays open for correction; failed save preserves input', async ({ page }) => {
  await open(page, 'cases');
  await page.getByRole('button', { name: '新建用例项目' }).click();
  const dialog = page.locator('.arco-modal:visible');
  const ok = dialog.getByRole('button', { name: '确定' });
  const endpoint = /\/(api|front)\/case-asset\/catalog(?:\?|$)/;
  const writes: string[] = [];
  page.on('request', (request) => {
    if (request.method() === 'POST' && endpoint.test(request.url())) writes.push(request.postDataJSON().name);
  });
  await ok.click();
  await expect(page.getByText('请输入目录名称')).toBeVisible();
  await expect(page.getByText('请输入目录名称')).toBeHidden();
  await expect(dialog).toBeVisible();
  await dialog.getByRole('textbox').fill('取消的目录');
  await dialog.getByRole('button', { name: '取消' }).click();
  await expect(dialog).toBeHidden();
  expect(writes).toEqual([]);
  await page.getByRole('button', { name: '新建用例项目' }).click();
  await expect(dialog.getByRole('textbox')).toHaveValue('');
  await dialog.getByRole('textbox').fill('本轮目录');
  await page.route(endpoint, (r) => r.fulfill({ status: 503, json: { message: '保存失败' } }));
  await ok.click();
  await expect(dialog.getByRole('textbox')).toHaveValue('本轮目录');
  await expect(ok).toBeEnabled();
  await page.unroute(endpoint);
  await ok.click();
  await expect(dialog).toBeHidden();
  expect(writes).toEqual(['本轮目录', '本轮目录']);
});
test('source governance requires evidence before closing', async ({ page }) => {
  await open(page, 'datasets');
  await page.getByText('治理来源', { exact: true }).click();
  const modal = page.locator('.arco-modal:visible');
  const confirm = modal.getByRole('button', { name: '确定' });
  await confirm.click();
  await expect(page.getByText('请填写可审计的来源依据')).toBeVisible();
  await expect(page.getByText('请填写可审计的来源依据')).toBeHidden();
  await expect(modal).toBeVisible();
  await modal.locator('textarea').fill('本地验收记录');
  await confirm.click();
  await expect(modal).toBeHidden();
});
test('configuration fields have native labels that focus their input', async ({ page }) => {
  await open(page, 'configuration');
  await page.getByRole('button', { name: '新增配置' }).click();
  const dialog = page.locator('.arco-modal:visible');
  async function checkLabel(name: string) {
    const field = dialog.getByRole('textbox', { name: new RegExp(name) });
    await expect(field).toHaveCount(1);
    await dialog.locator('label').filter({ hasText: name }).click();
    await expect(field).toBeFocused();
  }
  await checkLabel('配置名称');
  await checkLabel('系统名称');
  await checkLabel('环境名称');
  await checkLabel('配置内容');
});
test('case toolbar places create before search at desktop widths', async ({ page }) => {
  await page.setViewportSize({ width: 1920, height: 930 });
  await open(page, 'cases');
  const create = page.getByRole('button', { name: '新建用例', exact: true });
  const search = page.getByPlaceholder('搜索用例 ID 或名称');
  await expect(create).toBeVisible();
  await expect(search).toBeVisible();
  expect((await create.boundingBox())!.x).toBeLessThan((await search.boundingBox())!.x);
  await page.setViewportSize({ width: 720, height: 800 });
  const narrowCreate = (await create.boundingBox())!;
  const narrowSearch = (await search.boundingBox())!;
  expect(narrowSearch.y).toBeGreaterThanOrEqual(narrowCreate.y + narrowCreate.height);
  expect(narrowSearch.x + narrowSearch.width).toBeLessThanOrEqual(720);
});
test('save center receives a pointer hit inside the scrolling detail view', async ({ page }) => {
  await page.setViewportSize({ width: 1920, height: 930 });
  await open(page, 'cases/ui-catalog/detail-fixture');
  const save = page.getByRole('button', { name: '保存', exact: true });
  await expect(save).toBeVisible();
  await save.scrollIntoViewIfNeeded();
  await page.locator('.ms-card-container').hover();
  await expect
    .poll(() =>
      save.evaluate((el) => {
        const r = el.getBoundingClientRect();
        return el.contains(document.elementFromPoint(r.x + r.width / 2, r.y + r.height / 2));
      })
    )
    .toBe(true);
  const track = page.locator('.ms-card-container .arco-scrollbar-track-direction-vertical').first();
  await expect(track).toBeAttached();
  const buttonBox = (await save.boundingBox())!;
  const trackBox = (await track.boundingBox())!;
  expect(buttonBox.y + buttonBox.height <= trackBox.y || buttonBox.x + buttonBox.width <= trackBox.x).toBe(true);
  const update = page.waitForRequest(
    (req) => req.method() === 'PUT' && new URL(req.url()).pathname.endsWith('/case-asset/case')
  );
  await page.mouse.click(buttonBox.x + buttonBox.width / 2, buttonBox.y + buttonBox.height / 2);
  expect((await update).postDataJSON()).toMatchObject({ id: 'detail-fixture', catalogId: catalog.id });
  await page.setViewportSize({ width: 720, height: 800 });
  await expect
    .poll(() =>
      save.evaluate((el) => {
        const r = el.getBoundingClientRect();
        return (
          r.right <= document.documentElement.clientWidth &&
          el.contains(document.elementFromPoint(r.x + r.width / 2, r.y + r.height / 2))
        );
      })
    )
    .toBe(true);
});
test('deprecate sends the project and asset identity as request parameters', async ({ page }) => {
  await open(page, `versions?assetType=DATASET&assetId=${dataset.id}`);
  await page.getByText('停用', { exact: true }).click();
  const req = page.waitForRequest((r) => r.url().includes('/version-fixture/deprecate'));
  await page.getByRole('button', { name: '确定', exact: true }).click();
  const url = new URL((await req).url());
  expect(Object.fromEntries(url.searchParams)).toMatchObject({ projectId, assetType: 'DATASET', assetId: dataset.id });
});
