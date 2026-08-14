import { expect, type Page, test } from '@playwright/test';

const project = {
  id: 'project-browser-1',
  num: 1001,
  name: '浏览器验收项目',
  organizationId: 'org-browser-1',
  organizationName: '浏览器验收组织',
  memberCount: 2,
  memberPreview: ['管理员', '成员'],
  enable: true,
  description: '用于浏览器验收',
  createUser: '管理员',
  createTime: 1_700_000_000_000,
  canAddMember: true,
};

test.beforeEach(async ({ page }) => {
  await page.addInitScript(() => {
    const adminRole = { id: 'admin', name: '管理员', type: 'SYSTEM', scopeId: 'global', enabled: true };
    localStorage.setItem('sessionId', 'playwright-session');
    localStorage.setItem('csrfToken', 'playwright-csrf');
    localStorage.setItem(
      'user',
      JSON.stringify({
        id: 'playwright-admin',
        name: '浏览器验收管理员',
        role: 'admin',
        lastOrganizationId: 'org-browser-1',
        lastProjectId: 'no_such_project',
        userRoles: [adminRole],
        userRolePermissions: [{ userRole: adminRole, userRolePermissions: [] }],
        userRoleRelations: [
          {
            id: 'admin-relation',
            userId: 'playwright-admin',
            roleId: 'admin',
            sourceId: 'system',
            organizationId: 'system',
            userRole: adminRole,
            userRolePermissions: [],
          },
        ],
        loginType: ['LOCAL'],
      })
    );
    localStorage.setItem('app', JSON.stringify({ currentOrgId: 'org-browser-1', currentProjectId: 'no_such_project' }));
  });
  await page.route('**/*', async (route) => {
    const request = route.request();
    const requestPath = new URL(request.url()).pathname;
    if (!/^\/(?:front|api)\//.test(requestPath)) {
      await route.continue();
      return;
    }
    const pathname = requestPath.replace(/^\/(?:front|api)/, '');
    let data: unknown = [];
    if (pathname === '/project/page' || pathname === '/project/case-asset/page') {
      data = { list: [project], total: 1 };
    } else if (pathname === '/functional/case/asset/page') {
      data = { list: [], total: 0 };
    } else if (pathname === '/permission-control/role/list') {
      data = [
        { id: 'admin', name: '管理员', internal: true, type: 'SYSTEM', scopeId: 'global', enabled: true },
        { id: 'permission_member', name: '成员', internal: false, type: 'SYSTEM', scopeId: 'global', enabled: true },
      ];
    } else if (pathname === '/personal/agent-tokens') {
      data = { list: [], total: 0 };
    } else if (pathname === '/personal/agent-package/manifest') {
      data = { available: true, fileName: 'metersphere-agent-skill.zip' };
    } else if (pathname === '/ai/user-agent/features') {
      data = { enabled: true };
    } else if (pathname === '/ai/agent-bridge/install-info') {
      data = { managedDownloadAvailable: false };
    } else if (pathname === '/ai/user-agent/connections' || pathname === '/ai/agent-bridge/devices') {
      data = [];
    } else if (pathname.includes('/license')) {
      data = false;
    }
    await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({ code: 0, data }) });
  });
});

async function openAuthenticated(page: Page, path: string) {
  await page.goto(path, { waitUntil: 'domcontentloaded' });
  await expect(page, `访问 ${path} 时被重定向到登录页；请通过 PW_STORAGE_STATE 提供已登录状态`).not.toHaveURL(
    /\/login(?:[/?#]|$)/
  );
}

test.describe.configure({ mode: 'serial' });

test('无当前项目时仍可进入项目管理，并展示完整项目列表字段与操作', async ({ page }) => {
  const responsePromise = page.waitForResponse(
    (response) => response.request().method() === 'POST' && new URL(response.url()).pathname.endsWith('/project/page')
  );
  await openAuthenticated(page, '/#/project-management/projects');
  const response = await responsePromise;
  expect(response.ok(), `项目分页接口失败：${response.status()} ${response.url()}`).toBeTruthy();
  await expect(page.getByRole('main').getByText('项目列表', { exact: true })).toBeVisible();
  await expect(
    page.getByRole('row', {
      name: '进入项目 项目 ID 项目名称 成员 状态 描述 创建人 创建时间 操作',
      exact: true,
    })
  ).toBeVisible();
  await expect(
    page
      .getByText('暂无可访问项目', { exact: true })
      .or(page.getByRole('button', { name: '进入项目', exact: true }).first())
  ).toBeVisible();
});

test('用例资产仅调用具备用例权限交集的项目分页接口，并保留可进入的系统空页', async ({ page }) => {
  const requestedPaths: string[] = [];
  page.on('request', (request) => requestedPaths.push(new URL(request.url()).pathname));
  await openAuthenticated(page, '/#/test-assets/cases/project');
  await expect.poll(() => requestedPaths.some((path) => path.endsWith('/project/case-asset/page'))).toBeTruthy();
  expect(requestedPaths.some((path) => path.endsWith('/project/page'))).toBeFalsy();
  await expect(page.getByText('项目', { exact: true })).toBeVisible();
  await page.goto('/#/test-assets/cases/system');
  await expect(page.getByText('系统级用例资产暂未开放', { exact: true })).toBeVisible();
});

test('权限控制只暴露角色入口，角色表头完整且管理员不可修改', async ({ page }) => {
  await openAuthenticated(page, '/#/setting/system/permission-control');
  await expect(page.getByText('角色设置', { exact: true })).toBeVisible();
  await expect(page.getByRole('row', { name: '角色名称 权限范围 启用状态 编辑', exact: true })).toBeVisible();
  const adminRow = page.getByRole('row').filter({ hasText: '管理员' }).first();
  await expect(adminRow).toBeVisible();
  await expect(adminRow.getByText('查看', { exact: true })).toBeVisible();
  await expect(page.getByText('用户组', { exact: true })).toHaveCount(0);
});

test('Agent 仅保留一套导航，Agent 集成权限与后端 Token 权限一致', async ({ page }) => {
  await openAuthenticated(page, '/#/agent/access');
  await expect(page.getByText('Agent 集成', { exact: true })).toHaveCount(1);
  await expect(page.getByText('接入配置', { exact: true })).toHaveCount(0);
  await expect(page.getByText('我的 Agent Token', { exact: true })).toBeVisible();
  await expect(
    page
      .getByRole('button', { name: '创建 Token', exact: true })
      .first()
      .or(page.getByText('当前账号没有个人 Agent 接入读取权限', { exact: true }))
  ).toBeVisible();
});
