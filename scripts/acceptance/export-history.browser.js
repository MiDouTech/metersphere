async (page) => {
  await page.goto('http://127.0.0.1:15175/#/case-management/featureCase?orgId=100001&pId=100001100001');
  const button = page.getByRole('button', { name: '导出记录', exact: true });
  await button.waitFor();
  if (!(await button.isEnabled())) throw new Error('History must be available without selecting a case');
  const responsePromise = page.waitForResponse(r => r.url().includes('/export/tasks/history?'));
  await button.click();
  const response = await responsePromise;
  const body = await response.json();
  if (response.status() !== 200 || body.code !== 100200 || body.data.pageSize !== 10)
    throw new Error('History must load the authenticated real API with default page size 10');
  const drawer = page.locator('.arco-drawer').filter({ hasText: '我的导出记录' });
  await drawer.waitFor();
  const headers = await page.evaluate(() => ({
    'X-AUTH-TOKEN': localStorage.getItem('sessionId') || '',
    'CSRF-TOKEN': localStorage.getItem('csrfToken') || '',
    PROJECT: '100001100001', ORGANIZATION: '100001',
  }));
  const ids = new Set();
  const total = body.data.total;
  for (let current = 1; current <= Math.max(1, Math.ceil(total / 10)); current += 1) {
    const result = await page.request.get(`http://127.0.0.1:15175/api/functional/case/export/tasks/history?projectId=100001100001&current=${current}&pageSize=10`, { headers });
    const value = await result.json();
    if (result.status() !== 200 || value.code !== 100200 || value.data.total !== total)
      throw new Error('History pagination returned an error or changed during the acceptance run');
    for (const task of value.data.list) {
      if (ids.has(task.taskId) || task.projectId !== '100001100001') throw new Error('Duplicated or foreign task');
      ids.add(task.taskId);
    }
  }
  if (ids.size !== total) throw new Error('History omitted records across pages');
  if (total > 10) {
    const next = page.waitForResponse(r => r.url().includes('/export/tasks/history?') && r.url().includes('current=2'));
    await drawer.locator('.arco-pagination-item').filter({ hasText: /^2$/ }).click();
    const value = await (await next).json();
    if (value.data.current !== 2) throw new Error('Visible page control did not request page 2');
    await drawer.getByText(value.data.list[0].taskId, { exact: true }).waitFor();
  }
  let previousSize = 10;
  for (const size of [20, 50, 100, 10]) {
    await drawer.getByTitle(`${previousSize} 条/页`, { exact: true }).click();
    const changed = page.waitForResponse(r => r.url().includes('/export/tasks/history?') && r.url().includes(`pageSize=${size}`));
    await page.locator('.arco-select-dropdown:visible').getByText(`${size} 条/页`, { exact: true }).click();
    const value = await (await changed).json();
    if (value.code !== 100200 || value.data.pageSize !== size || value.data.current !== 1 || value.data.total !== total)
      throw new Error('Visible page-size control must preserve total and reset to page 1');
    await drawer.getByTitle(`${size} 条/页`, { exact: true }).waitFor();
    if (value.data.list.length) await drawer.getByText(value.data.list[0].taskId, { exact: true }).waitFor();
    previousSize = size;
  }
  for (const width of [1024, 1280]) {
    await page.setViewportSize({ width, height: 768 });
    const bounds = await drawer.boundingBox();
    if (!bounds || bounds.x < -1 || bounds.x + bounds.width > width + 1) throw new Error('Drawer exceeds viewport');
    const pagination = await drawer.locator('.arco-pagination').boundingBox();
    if (!pagination || pagination.y < 0 || pagination.y + pagination.height > 768 || pagination.x < 0 || pagination.x + pagination.width > width)
      throw new Error('History pagination must remain visible in the drawer footer');
    const clipped = await drawer.locator('.arco-table button').evaluateAll(buttons => buttons.filter(button => {
      const rect = button.getBoundingClientRect();
      if (rect.left < 0 || rect.right > innerWidth) return true;
      for (let parent = button.parentElement; parent; parent = parent.parentElement) {
        const bounds = parent.getBoundingClientRect();
        if (/(hidden|clip|auto|scroll)/.test(getComputedStyle(parent).overflowX)
            && (rect.left < bounds.left - 1 || rect.right > bounds.right + 1)) return true;
      }
      return false;
    }).map(button => button.textContent));
    if (clipped.length) throw new Error(`History actions are clipped: ${clipped.join(',')}`);
    await page.screenshot({ path: `outputs/remediation-export-history-${width}.png` });
  }
  await drawer.locator('.arco-drawer-close-btn').click();
  await drawer.waitFor({ state: 'hidden' });
  return { zeroSelectionEntry: true, realPagination: true, visiblePagination: true, pageSizes: [10, 20, 50, 100], total, uniqueTasks: ids.size, widths: [1024, 1280] };
}
