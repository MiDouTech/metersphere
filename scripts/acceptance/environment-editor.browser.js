async (page) => {
  await page.goto('http://127.0.0.1:15175/#/execution/platforms?orgId=100001&pId=100001100001');
  await page.getByRole('button', { name: '新增配置', exact: true }).click();
  const modal = page.locator('.arco-modal');
  const name = `binding-ui-acceptance-${Date.now()}`;
  await modal.getByText('个人直连无需绑定。受管任务请选择已发布的环境资产；不改变原凭据范围。', { exact: true }).waitFor();
  await modal.locator('input').first().fill(name);
  await modal.getByPlaceholder('https://test.example.com', { exact: true }).fill('https://example.com');
  await modal.locator('textarea').first().fill('https://example.com');
  const saved = page.waitForResponse(r => r.url().endsWith('/ai/environment-profiles') && r.request().method() === 'POST');
  await modal.getByRole('button', { name: '确定', exact: true }).click();
  const response = await saved;
  const body = await response.json();
  const profile = body.data || body;
  if (response.status() !== 200 || !profile.id || profile.environmentAssetId || profile.name !== name) {
    throw new Error('Personal profile must save through the real form without an environment asset');
  }
  await page.reload();
  await page.getByText(name, { exact: true }).waitFor();
  const row = page.getByText(name, { exact: true }).locator('xpath=ancestor::tr');
  await row.getByText('未绑定 · 个人直连', { exact: true }).waitFor();
  const disabled = page.waitForResponse(r => r.url().endsWith(`/ai/environment-profiles/${profile.id}/disable`));
  await row.getByRole('button', { name: '停用', exact: true }).click();
  if ((await disabled).status() !== 200) throw new Error('Failed to deactivate the test profile');
  await row.getByRole('button', { name: '启用', exact: true }).waitFor();
  await page.screenshot({ path: 'outputs/remediation-environment-editor-saved.png' });
  return { realFormSave: true, persistedAfterReload: true, optionalAsset: true, fixtureDisabled: true, fixtureProfileId: profile.id };
}
