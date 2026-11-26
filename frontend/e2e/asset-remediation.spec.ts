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

test('MSP38 attachment upload/delete preserves draft and save survives reload', async ({ page }) => {
  let detail: Record<string, unknown> = {
    name: 'MSP38 saved',
    description: 'saved note',
    updateTime: 1,
    caseEditType: 'STEP',
    steps: '[]',
    attachments: [],
  };
  const writes: Record<string, unknown>[] = [];
  await page.route('**/case-asset/**', async (route) => {
    const request = route.request();
    const path = new URL(request.url()).pathname;
    if (path.endsWith('/attachment') && request.method() === 'POST')
      detail.attachments = [{ id: 'attachment', fileName: 'draft.txt', local: true }];
    else if (path.endsWith('/attachment/attachment') && request.method() === 'DELETE') detail.attachments = [];
    else if (request.method() === 'PUT') {
      const body = request.postDataJSON();
      writes.push(body);
      detail = { ...detail, ...body, updateTime: 2 };
    }
    await route.fulfill({ json: { code: 100200, data: detail } });
  });
  await open(page, 'cases/ui-catalog/detail-fixture');
  const title = page
    .locator('.arco-form-item')
    .filter({ has: page.getByText('用例名称', { exact: true }) })
    .locator('input');
  const note = page
    .locator('.arco-form-item')
    .filter({ has: page.getByText('备注', { exact: true }) })
    .locator('textarea');
  await title.fill('MSP38 draft');
  await note.fill('draft note');
  await page
    .locator('input[type=file]')
    .setInputFiles({ name: 'draft.txt', mimeType: 'text/plain', buffer: Buffer.from('MSP38') });
  await expect(page.getByText('draft.txt', { exact: true })).toBeVisible();
  await expect(title).toHaveValue('MSP38 draft');
  await expect(note).toHaveValue('draft note');
  await page.getByText('删除', { exact: true }).click();
  await expect(page.getByText('draft.txt', { exact: true })).toBeHidden();
  await expect(title).toHaveValue('MSP38 draft');
  await page.getByRole('button', { name: '保存', exact: true }).click();
  await expect.poll(() => writes.length).toBe(1);
  expect(writes[0]).toMatchObject({ name: 'MSP38 draft', description: 'draft note', expectedUpdateTime: 1 });
  await page.reload();
  await expect(title).toHaveValue('MSP38 draft');
  await expect(note).toHaveValue('draft note');
});

test('MSP38 project/system navigation and classification keep the same case identity', async ({ page }) => {
  let assigned = false;
  const assignments: unknown[] = [];
  await page.route('**/case-asset/**', async (route) => {
    const request = route.request();
    const endpoint = new URL(request.url()).pathname;
    let data: unknown = [];
    if (endpoint.endsWith('/systems'))
      data = {
        systems: [
          {
            id: 's',
            name: '订单系统',
            caseCount: assigned ? 1 : 0,
            modules: [{ id: 'm', name: '订单模块', parentId: 'NONE', caseCount: assigned ? 1 : 0 }],
          },
        ],
        unclassifiedCount: assigned ? 0 : 1,
      };
    else if (endpoint.endsWith('/system-classification')) {
      assignments.push(request.postDataJSON());
      assigned = Boolean(request.postDataJSON().systemId);
      data = { count: 1 };
    } else if (endpoint.endsWith('/case/page')) {
      const query = request.postDataJSON();
      data = {
        list:
          query.unclassifiedSystem && assigned
            ? []
            : [
                {
                  id: 'same-asset',
                  num: 1125,
                  name: '同一用例',
                  assetCatalogId: 'ui-catalog',
                  assetCatalogName: '验收目录',
                },
              ],
        total: query.unclassifiedSystem && assigned ? 0 : 1,
      };
    } else if (endpoint.endsWith('/catalog/page')) data = { list: [catalog], total: 1 };
    await route.fulfill({ json: { code: 100200, data } });
  });
  await open(page, 'cases/project');
  await page.getByRole('link', { name: '系统目录', exact: true }).click();
  await expect(page).toHaveURL(/cases\/system/);
  await expect(page.getByText('同一用例', { exact: true })).toBeVisible();
  await page.getByText('分类 / 移动', { exact: true }).click();
  const dialog = page.locator('.arco-modal:visible');
  await dialog.getByLabel('业务系统', { exact: true }).selectOption('s');
  await dialog.getByLabel('系统模块', { exact: true }).selectOption('m');
  await dialog.getByRole('button', { name: '确定', exact: true }).click();
  await expect(dialog).toBeHidden();
  expect(assignments).toEqual([{ ids: ['same-asset'], systemId: 's', moduleId: 'm' }]);
  await page.getByText('待人工分类 (0)', { exact: true }).click();
  await expect(page.getByText('同一用例', { exact: true })).toBeHidden();
  await page.getByRole('link', { name: '项目目录', exact: true }).click();
  await expect(page).toHaveURL(/cases\/project/);
  await expect(page.getByRole('button', { name: '新建用例项目', exact: true })).toBeVisible();
});

test('MSP38 directory editor retains failed draft and creates real system/module requests', async ({ page }) => {
  const systems: Array<{
    id: string;
    name: string;
    caseCount: number;
    modules: Array<{ id: string; name: string; parentId: string; caseCount: number }>;
  }> = [];
  let failFirst = true;
  const writes: unknown[] = [];
  await page.route('**/case-asset/**', async (route) => {
    const req = route.request();
    const path = new URL(req.url()).pathname;
    let data: unknown = [];
    if (path.endsWith('/systems') && req.method() === 'GET') data = { systems, unclassifiedCount: 0 };
    else if (path.endsWith('/systems') && req.method() === 'POST') {
      if (failFirst) {
        failFirst = false;
        await route.fulfill({ status: 503, json: { message: '稍后重试' } });
        return;
      }
      const body = req.postDataJSON();
      writes.push(body);
      const system = { id: 'created-system', name: body.name, caseCount: 0, modules: [] };
      systems.push(system);
      data = system;
    } else if (path.endsWith('/modules') && req.method() === 'POST') {
      const body = req.postDataJSON();
      writes.push(body);
      const module = { id: 'created-module', name: body.name, parentId: body.parentId, caseCount: 0 };
      systems[0].modules.push(module);
      data = module;
    } else if (path.endsWith('/case/page') || path.endsWith('/catalog/page')) data = { list: [], total: 0 };
    await route.fulfill({ json: { code: 100200, data } });
  });
  await open(page, 'cases/system');
  await page.getByRole('button', { name: '新建系统', exact: true }).click();
  const dialog = page.locator('.arco-modal:visible');
  await dialog.getByLabel('名称', { exact: true }).fill('MSP38系统');
  await dialog.getByRole('button', { name: '确定', exact: true }).click();
  await expect(dialog.getByLabel('名称', { exact: true })).toHaveValue('MSP38系统');
  await expect(dialog.getByRole('button', { name: '确定', exact: true })).toBeEnabled();
  await dialog.getByRole('button', { name: '确定', exact: true }).click();
  await expect(dialog).toBeHidden();
  await page.getByText('MSP38系统 (0)', { exact: true }).click();
  await page.getByRole('button', { name: '新建模块', exact: true }).click();
  await dialog.getByLabel('名称', { exact: true }).fill('MSP38模块');
  await dialog.getByRole('button', { name: '确定', exact: true }).click();
  await expect(dialog).toBeHidden();
  expect(writes).toEqual([{ name: 'MSP38系统' }, { name: 'MSP38模块', parentId: 'NONE' }]);
});
