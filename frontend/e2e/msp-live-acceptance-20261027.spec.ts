import { expect, test } from '@playwright/test';

// Requirements and actual ego execution: docs/testing/msp-acceptance-20261027/report.md.
// Read-only replay against retained, explicitly named acceptance fixtures. No login secrets here.
// Not executed with a Playwright browser in the ego run; discovery alone is not acceptance.
const project = '1704667279923322880';
const contextQuery = `orgId=100001&pId=${project}`;
const planName = 'ACCEPTANCE-20261027-自动刷新与证据计划';
const reviewName = 'ACCEPTANCE-20261027-导入联动评审';

test.use({ viewport: { width: 1908, height: 895 } });
test.beforeAll(() => {
  expect(process.env.PW_BASE_URL, 'Explicit test deployment required').toBe('https://msp.ebcone.net');
  expect(process.env.PW_STORAGE_STATE, 'Prepare private authenticated storage state separately').toBeTruthy();
});

test('AC-012 AC-035 buttons remain visible and select-all follows export', async ({ page }) => {
  await page.goto(`/#/case-management/featureCase?${contextQuery}`);
  await expect(page.getByText('执行用例', { exact: true })).toBeVisible();
  const allPages = page.getByRole('button', { name: '全选所有页', exact: true });
  const exportButton = page.getByRole('button', { name: '导出', exact: true });
  await expect(allPages).toBeVisible();
  await expect(exportButton).toBeVisible();
  await expect(page.getByRole('button', { name: '批量修改执行结果', exact: true })).toBeDisabled();
  const exportBounds = await exportButton.boundingBox();
  const allBounds = await allPages.boundingBox();
  expect(exportBounds).not.toBeNull();
  expect(allBounds).not.toBeNull();
  expect(allBounds!.x).toBeGreaterThanOrEqual(exportBounds!.x + exportBounds!.width);
  expect(Math.abs(allBounds!.y - exportBounds!.y)).toBeLessThan(4);
});

test('AC-005 AC-036 plan name enters correct detail without report generation', async ({ page }) => {
  await page.goto(`/#/test-plan/testPlanIndex?${contextQuery}`);
  await page.getByRole('button', { name: planName, exact: true }).click();
  await expect(page).toHaveURL(/id=1710858750158446592/);
  await expect(page.getByText('测试计划文档', { exact: true })).toBeVisible();
  await expect(page.getByText('生成报告', { exact: true })).toHaveCount(0);
});

test('AC-036 imported review name enters its linked case list', async ({ page }) => {
  await page.goto(`/#/test-plan/caseReview?${contextQuery}`);
  await page.getByRole('button', { name: reviewName, exact: true }).click();
  await expect(page).toHaveURL(/id=1710884932279083008/);
  await expect(page.getByText('ACCEPTANCE-20261027-导入联动B', { exact: true })).toBeVisible();
});

test('REG-UI-01 D02 token controls stay within the visible personal window', async ({ page }, testInfo) => {
  await page.goto(`/#/case-management/featureCase?${contextQuery}`);
  await page.getByText('陈启芬', { exact: true }).first().click();
  await page.getByText('个人中心', { exact: true }).click();
  await page.getByText('Agent配置', { exact: true }).click();
  const window = page.locator('.ms-personal-page-window');
  await expect(window.getByText('我的 Agent Token', { exact: true })).toBeVisible();
  const bounds = await window.boundingBox();
  expect(bounds).not.toBeNull();
  await page.screenshot({ path: testInfo.outputPath('token-controls.png') });
  const positions = await Promise.all(
    ['启用', '操作'].map(async (name) => {
      const cell = window.getByRole('columnheader', { name, exact: true });
      await expect(cell).toBeVisible();
      return { name, position: await cell.boundingBox() };
    })
  );
  positions.forEach(({ name, position }) => {
    expect(position).not.toBeNull();
    expect(position!.x + position!.width, `${name} must be reachable without parent clipping`).toBeLessThanOrEqual(
      Math.min(1908, bounds!.x + bounds!.width)
    );
  });
});
