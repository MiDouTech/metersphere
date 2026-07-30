import { chromium } from 'playwright';
import { mkdirSync } from 'node:fs';
import { resolve, dirname } from 'node:path';
import { fileURLToPath } from 'node:url';

const CDP_URL = process.env.EDGE_CDP_URL || 'http://127.0.0.1:9222';
const LOGIN_URL = 'https://msp.ebcone.net/#/login';
const MEMBER_URL = 'https://msp.ebcone.net/#/setting/organization/member?orgId=100001';
const __dirname = dirname(fileURLToPath(import.meta.url));
const OUT_DIR = resolve(__dirname, '../../output/playwright/org-setting-20260717');
const MAX_WAIT_MS = Number(process.env.MAX_WAIT_MS || 8 * 60 * 1000);
const POLL_MS = Number(process.env.POLL_MS || 7000);

mkdirSync(OUT_DIR, { recursive: true });

function isLoginPage(url, bodyText) {
  const u = String(url || '');
  if (/#\/login\b/i.test(u) || /\/login(?:\?|$)/i.test(u)) return true;
  return /账号登录|请输入用户名/.test(bodyText || '');
}

async function pickPage(context) {
  const pages = context.pages();
  const hit = pages.find((p) => /msp\.ebcone\.net/i.test(p.url()));
  if (hit) return hit;
  if (pages[0]) return pages[0];
  return context.newPage();
}

async function pageState(page) {
  const url = page.url();
  const bodyText = await page.evaluate(() => (document.body?.innerText || '').slice(0, 8000)).catch(() => '');
  return { url, bodyText, onLogin: isLoginPage(url, bodyText) };
}

async function main() {
  console.log(`Connecting CDP ${CDP_URL} ...`);
  const browser = await chromium.connectOverCDP(CDP_URL);
  const context = browser.contexts()[0] || (await browser.newContext());
  const page = await pickPage(context);

  await page.bringToFront().catch(() => {});
  console.log(`Navigate login: ${LOGIN_URL}`);
  await page.goto(LOGIN_URL, { waitUntil: 'domcontentloaded', timeout: 60000 });
  await page.waitForTimeout(1500);
  await page.bringToFront().catch(() => {});

  const waitShot = resolve(OUT_DIR, 'wait-manual-login.png');
  await page.screenshot({ path: waitShot, fullPage: false });
  console.log(`WAIT_MANUAL_LOGIN screenshot=${waitShot}`);
  console.log('请在 Edge 前台窗口手工完成登录（本脚本不会自动填密码）');

  const started = Date.now();
  let loggedIn = false;
  while (Date.now() - started < MAX_WAIT_MS) {
    await page.waitForTimeout(POLL_MS);
    const st = await pageState(page);
    console.log(`[poll ${(Math.round((Date.now() - started) / 1000))}s] onLogin=${st.onLogin} url=${st.url}`);
    if (!st.onLogin) {
      loggedIn = true;
      break;
    }
  }

  if (!loggedIn) {
    console.error('LOGIN_TIMEOUT: 未在时限内检测到登录成功');
    await browser.close().catch(() => {});
    process.exit(2);
  }

  const loggedShot = resolve(OUT_DIR, 'logged-in.png');
  await page.screenshot({ path: loggedShot, fullPage: false });
  console.log(`LOGGED_IN screenshot=${loggedShot}`);

  // 尽量落到组织成员页，确认会话可用
  await page.goto(MEMBER_URL, { waitUntil: 'domcontentloaded', timeout: 60000 }).catch(() => {});
  await page.waitForTimeout(1500);
  const after = await pageState(page);
  if (after.onLogin) {
    console.error('LOGIN_LOST: 跳转成员页后又回到登录');
    await page.screenshot({ path: resolve(OUT_DIR, 'login-lost.png'), fullPage: false }).catch(() => {});
    await browser.close().catch(() => {});
    process.exit(3);
  }

  console.log('LOGIN_OK ready for audit');
  await browser.close().catch(() => {});
  process.exit(0);
}

main().catch((err) => {
  console.error('WAIT_LOGIN_FATAL', err);
  process.exit(1);
});
