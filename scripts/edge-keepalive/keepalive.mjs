import { chromium } from 'playwright';

const CDP_URL = process.env.EDGE_CDP_URL || 'http://127.0.0.1:9222';
const TARGET_HOST = 'weixin12315.com';
const HOME_SELECTOR = 'a[href="/home/default"]';

function now() {
  return new Date().toLocaleString('zh-CN', { hour12: false });
}

async function clickHomepage() {
  let browser;
  try {
    browser = await chromium.connectOverCDP(CDP_URL);
    const pages = browser.contexts().flatMap((ctx) => ctx.pages());
    const page = pages.find((p) => p.url().includes(TARGET_HOST));

    if (!page) {
      console.log(`[${now()}] 未找到 ${TARGET_HOST} 页面，跳过本次保活`);
      return { ok: false, reason: 'page_not_found' };
    }

    await page.bringToFront();
    const title = await page.title();
    const url = page.url();
    console.log(`[${now()}] 已连接页面: ${title} | ${url}`);

    const homeLink = page.locator(HOME_SELECTOR).first();
    const count = await homeLink.count();
    if (count === 0) {
      console.log(`[${now()}] 未找到首页链接，改为刷新当前页`);
      await page.reload({ waitUntil: 'domcontentloaded', timeout: 30000 });
      return { ok: true, action: 'reload' };
    }

    await homeLink.click({ timeout: 10000 });
    await page.waitForLoadState('domcontentloaded', { timeout: 15000 }).catch(() => {});
    console.log(`[${now()}] 已点击首页，当前地址: ${page.url()}`);
    return { ok: true, action: 'click_home' };
  } catch (error) {
    console.error(`[${now()}] 保活失败:`, error?.message || error);
    return { ok: false, reason: error?.message || String(error) };
  } finally {
    if (browser) {
      await browser.close().catch(() => {});
    }
  }
}

const once = process.argv.includes('--once');
if (once) {
  const result = await clickHomepage();
  process.exit(result.ok ? 0 : 1);
}

const intervalMinutes = Number(process.env.KEEPALIVE_INTERVAL_MIN || 5);
const intervalMs = intervalMinutes * 60 * 1000;

console.log(`[${now()}] 米多大数据引擎保活已启动，间隔 ${intervalMinutes} 分钟，CDP: ${CDP_URL}`);

while (true) {
  await clickHomepage();
  await new Promise((resolve) => setTimeout(resolve, intervalMs));
}
