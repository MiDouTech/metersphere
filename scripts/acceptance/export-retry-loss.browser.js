async (page) => {
  await page.goto('http://127.0.0.1:15175/#/case-management/featureCase?orgId=100001&pId=100001100001');
  const historyResponse = page.waitForResponse(r => r.url().includes('/export/tasks/history?'));
  await page.getByRole('button', { name: '导出记录', exact: true }).click();
  const body = await (await historyResponse).json();
  const original = body.data?.list?.find(task => task.state === 'FAILED');
  if (!original) throw new Error('Run case-export E2E first to create an owned failed export fixture');
  const drawer = page.locator('.arco-drawer').filter({ hasText: '我的导出记录' });
  const retryButton = drawer.getByRole('row').filter({ hasText: original.taskId }).getByRole('button', { name: '重试', exact: true });
  const url = `**/api/functional/case/export/tasks/${original.taskId}/retry`;
  let created;
  let firstRequestId;
  let interceptionError;
  let release;
  const lostResponse = new Promise(resolve => { release = resolve; });
  const intercept = async (route) => {
    try {
      firstRequestId = route.request().postDataJSON().requestId;
      const response = await route.fetch(); // Real backend mutation, then deliberately lose its response.
      const value = await response.json();
      if (response.status() !== 200 || value.code !== 100200) throw new Error('Real retry creation failed');
      created = value.data;
    } catch (error) {
      interceptionError = error;
    } finally {
      await route.abort('failed');
      release();
    }
  };
  await page.route(url, intercept, { times: 1 });
  try {
    await retryButton.click();
    await lostResponse;
    if (interceptionError) throw interceptionError;
    if (!created?.taskId || created.sourceTaskId !== original.taskId) throw new Error('Missing real retry lineage');
    const replayResponse = page.waitForResponse(r => r.url().endsWith(`/export/tasks/${original.taskId}/retry`));
    await retryButton.click();
    const response = await replayResponse;
    const replay = await response.json();
    if (response.request().postDataJSON().requestId !== firstRequestId || replay.data?.taskId !== created.taskId)
      throw new Error('Browser created another task after losing the original response');
    await drawer.getByRole('row').filter({ hasText: created.taskId }).waitFor();
    await drawer.locator('.arco-drawer-close-btn').click();
    return { realBackendMutation: true, responseLossInjected: true, sameRequestId: true, sameTaskId: true, sourceTaskId: original.taskId, retryTaskId: created.taskId };
  } finally {
    await page.unroute(url, intercept);
  }
}
