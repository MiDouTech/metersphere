import { chromium } from 'playwright';

const CDP_URL = 'http://127.0.0.1:9222';
const HOME = 'https://msp.ebcone.net/#/workstation/home?orgId=100001&pId=100001100001';

async function main() {
  // 防止深度脚本因抽屉/弹窗交互卡死：全局超时 + 只做采样，不做批量点击
  const HARD_TIMEOUT_MS = Number(process.env.HARD_TIMEOUT_MS || 60_000);
  const hardTimeout = setTimeout(() => {
    // eslint-disable-next-line no-console
    console.error(`HARD_TIMEOUT after ${HARD_TIMEOUT_MS}ms`);
    process.exit(1);
  }, HARD_TIMEOUT_MS).unref();

  const browser = await chromium.connectOverCDP(CDP_URL);
  const context = browser.contexts()[0];
  const page = context.pages()[0] || (await context.newPage());

  const apiBodies = [];
  page.on('response', async (resp) => {
    const url = resp.url();
    if (url.includes('/api/') && resp.status() >= 400) {
      let body = '';
      try {
        body = (await resp.text()).slice(0, 500);
      } catch {}
      apiBodies.push({ status: resp.status(), url, body });
    }
  });

  await page.goto(HOME, { waitUntil: 'networkidle', timeout: 90000 });
  await page.waitForTimeout(2000);

  const homeActions = ['近三天', '近七天', '自定义', '卡片设置'];
  for (const label of homeActions) {
    const el = page.getByText(label, { exact: true }).first();
    if (await el.isVisible().catch(() => false)) {
      await el.click().catch(() => {});
      await page.waitForTimeout(1000);
      console.log(`clicked: ${label}`);
    }
  }

  const drawer = page.locator('.arco-drawer');
  if (await drawer.isVisible().catch(() => false)) {
    const drawerButtons = drawer.locator('button:visible');
    const count = await drawerButtons.count();
    console.log(`drawer buttons: ${count}`);
    // 只采样 1 次点击（优先点“退出编辑”避免副作用），其余不点
    const sample = drawer.getByText('退出编辑', { exact: true }).first();
    if (await sample.isVisible().catch(() => false)) {
      await sample.click().catch(() => {});
      await page.waitForTimeout(800);
      console.log('drawer click: 退出编辑');
    }
    await page.keyboard.press('Escape').catch(() => {});
  }

  const visibleText = await page.evaluate(() => {
    const msgs = [...document.querySelectorAll('.arco-message, .arco-modal, .arco-notification')]
      .map((e) => e.textContent?.trim())
      .filter(Boolean);
    return {
      title: document.title,
      hash: location.hash,
      messages: msgs,
      hasNoStatic: /no\s+static/i.test(document.body.innerText),
      bodySnippet: document.body.innerText.slice(0, 1500),
    };
  });

  console.log('\n=== API failures ===');
  for (const item of apiBodies) console.log(JSON.stringify(item, null, 2));

  console.log('\n=== Page state ===');
  console.log(JSON.stringify(visibleText, null, 2));

  await page.screenshot({ path: 'workstation-home-audit.png', fullPage: true });
  console.log('\nscreenshot: workstation-home-audit.png');
  await browser.close().catch(() => {});
  clearTimeout(hardTimeout);
}

main().catch((e) => {
  console.error(e);
  process.exit(1);
});
