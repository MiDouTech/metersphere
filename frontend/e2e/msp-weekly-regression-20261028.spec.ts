import { expect, test } from '@playwright/test';

// Read-only replay of retained weekly fixtures. Actual live execution used ego.
// Baseline/evidence: docs/testing/msp-weekly-regression-20261028/scope.md.
// This spec has not been executed with Playwright; do not count discovery as a pass.
const context = 'orgId=100001&pId=1704667279923322880';
test.use({ viewport: { width: 1366, height: 900 } });
test.beforeAll(() => {
  expect(process.env.PW_BASE_URL).toBe('https://msp.ebcone.net');
  expect(process.env.PW_STORAGE_STATE).toBeTruthy();
});

test('NEW-02 only one associated plan is displayed', async ({ page }) => {
  await page.goto(`/#/case-management/featureCase?${context}`);
  await expect(page.locator('.case-overview-plan-select')).toHaveCount(1);
  await expect(page.locator('.case-plan-progress')).toHaveCount(1);
  await page.locator('.case-overview-plan-select').click();
  const options = page.locator('.arco-select-dropdown:visible .arco-select-option');
  await expect(options.first()).toBeVisible();
  await options.first().click();
  await expect(page.locator('.case-plan-progress')).toHaveCount(1);
});

test('D02 token enable and action columns remain inside the personal window', async ({ page }) => {
  await page.goto(`/#/case-management/featureCase?${context}`);
  await page.locator('.arco-menu-item .one-line-text').click();
  await page.getByText('个人中心', { exact: true }).click();
  await page.getByText('Agent配置', { exact: true }).click();
  const personal = page.locator('.ms-personal-page-window');
  await Promise.all(
    ['启用', '操作'].map(async (name) => {
      const header = personal.getByRole('columnheader', { name, exact: true });
      await expect(header).toBeVisible();
      const rect = await header.boundingBox();
      expect(rect).not.toBeNull();
      expect(rect!.x + rect!.width, `${name} must not be clipped`).toBeLessThanOrEqual(1366);
    })
  );
});

test('NEW-04 multiple long bug tags do not overlap the expected date', async ({ page }) => {
  await page.goto(`/#/bug-management/index?${context}`);
  await page.getByRole('button', { name: 'WEEKLY-20261028-多标签布局验收', exact: true }).click();
  const tags = page.locator('.bug-tags-field');
  await expect(tags).toContainText('第六个标签');
  const date = page.locator('.arco-form-item').filter({ has: page.getByPlaceholder('请选择日期') });
  const tagBounds = await tags.boundingBox();
  const dateBounds = await date.boundingBox();
  expect(tagBounds).not.toBeNull();
  expect(dateBounds).not.toBeNull();
  expect(tagBounds!.y).toBeGreaterThanOrEqual(dateBounds!.y + dateBounds!.height);
});

test('AC-006 legacy generation URL redirects to the existing case module', async ({ page }) => {
  await page.goto(`/#/case-management/caseGenerate?${context}`);
  await expect(page).toHaveURL(/case-management\/featureCase/);
  await expect(page.getByText('执行用例', { exact: true })).toBeVisible();
});

test('AC-036 imported plan and review names navigate to their actual details', async ({ page }) => {
  await page.goto(`/#/test-plan/testPlanIndex?${context}`);
  await page.getByRole('button', { name: 'WEEKLY-20261028-导入计划', exact: true }).click();
  await expect(page).toHaveURL(/id=1712308215721533445/);
  await expect(page.getByText('测试计划文档', { exact: true })).toBeVisible();
  await expect(page.getByText('生成报告', { exact: true })).toHaveCount(0);
  await page.goto(`/#/test-plan/caseReview?${context}`);
  await page.getByRole('button', { name: 'WEEKLY-20261028-导入评审', exact: true }).click();
  await expect(page).toHaveURL(/id=1712308267261140992/);
});
