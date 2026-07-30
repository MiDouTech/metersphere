import { chromium } from 'playwright';
import { mkdirSync, writeFileSync, readFileSync, existsSync } from 'node:fs';
import { resolve, dirname } from 'node:path';
import { fileURLToPath } from 'node:url';

const CDP_URL = process.env.EDGE_CDP_URL || 'http://127.0.0.1:9222';
const BASE = 'https://msp.ebcone.net/#';
const ORG_ID = '100001';
const Q = `orgId=${ORG_ID}`;
const __dirname = dirname(fileURLToPath(import.meta.url));
const OUT_DIR = resolve(__dirname, '../../output/playwright/org-setting-20260717');
const HARD_TIMEOUT_MS = Number(process.env.HARD_TIMEOUT_MS || 180_000);

const ROUTES = [
  { name: '模板', path: `/setting/organization/template?${Q}` },
  { name: '任务中心', path: `/setting/organization/taskCenter?${Q}` },
  { name: '日志', path: `/setting/organization/log?${Q}` },
  // 复扫已知问题页，补截图
  { name: '成员', path: `/setting/organization/member?${Q}`, light: true },
  { name: '组织架构', path: `/setting/organization/org-structure?${Q}`, light: true },
  { name: '项目', path: `/setting/organization/project?${Q}`, light: true },
];

const ERROR_PATTERNS = [
  /no\s+static/i,
  /###\s*ERROR/i,
  /Whitelabel\s+Error/i,
  /Internal Server Error/i,
  /系统异常/,
  /服务异常/,
  /请求失败/,
  /加载失败/,
  /Not Found/i,
  /用户认证失败/,
  /无权限/,
  /服务器错误/,
  /not found/i,
];

const SKIP_CLICK_RE =
  /退出|登出|删除|移除|清空|注销|停用|禁用|logout|delete|remove|danger/i;

const findings = {
  startedAt: new Date().toISOString(),
  cdpUrl: CDP_URL,
  mode: 'resume-remaining',
  routesVisited: [],
  clicks: [],
  issues: [],
  screenshots: [],
  consoleErrors: [],
  networkFailures: [],
  priorConsoleIssues: [
    '成员: HTTP 500 /api/organization/log/user/list + ### Error',
    '组织架构: popup not found',
    '项目: HTTP 500 /api/organization/project/user-admin-list + SQL ORDER BY DISTINCT',
  ],
  summary: {},
};

mkdirSync(OUT_DIR, { recursive: true });

function addIssue(type, message, extra = {}) {
  const item = { type, message, at: new Date().toISOString(), ...extra };
  findings.issues.push(item);
  console.log(`[ISSUE:${type}] ${message}`);
}

function isErrorText(text) {
  if (!text || text.length > 5000) return false;
  return ERROR_PATTERNS.some((re) => re.test(text));
}

async function shot(page, name) {
  const file = resolve(OUT_DIR, `${String(findings.screenshots.length + 1).padStart(2, '0')}-${name}.png`);
  await page.screenshot({ path: file, fullPage: false }).catch(() => {});
  findings.screenshots.push(file);
  console.log(`SHOT ${file}`);
  return file;
}

async function scanVisibleErrors(page, contextLabel) {
  const checks = await page.evaluate(() => {
    const selectors = [
      '.arco-message',
      '.arco-notification',
      '.arco-modal',
      '.arco-drawer',
      '.arco-alert',
      '.arco-result',
      '.ms-toast',
      '[class*="error"]',
      '[class*="Error"]',
    ];
    const texts = [];
    for (const sel of selectors) {
      for (const el of document.querySelectorAll(sel)) {
        const style = window.getComputedStyle(el);
        if (style.display === 'none' || style.visibility === 'hidden' || style.opacity === '0') continue;
        const rect = el.getBoundingClientRect();
        if (rect.width < 2 || rect.height < 2) continue;
        const text = (el.innerText || el.textContent || '').trim();
        if (text) texts.push(text.slice(0, 800));
      }
    }
    const bodyText = (document.body?.innerText || '').slice(0, 12000);
    const loggedOut = /账号登录|请输入用户名/.test(bodyText);
    return { popupTexts: [...new Set(texts)], bodyText, loggedOut };
  });

  if (checks.loggedOut) {
    addIssue('auth', '页面已跳转登录页（会话失效）', { context: contextLabel });
    await shot(page, 'auth-login');
    return true;
  }

  let found = false;
  for (const text of checks.popupTexts) {
    if (isErrorText(text)) {
      addIssue('popup', text.slice(0, 300), { context: contextLabel });
      found = true;
    }
  }
  if (isErrorText(checks.bodyText)) {
    for (const re of ERROR_PATTERNS) {
      const m = checks.bodyText.match(re);
      if (m) {
        addIssue('page-text', m[0], { context: contextLabel });
        found = true;
        break;
      }
    }
  }
  if (found) {
    await shot(page, `issue-${contextLabel.replace(/[^\w\u4e00-\u9fa5-]+/g, '_').slice(0, 40)}`);
  }
  return false;
}

async function closeOverlays(page) {
  for (const sel of ['.arco-modal-close-btn', '.arco-drawer-close-btn', 'button[aria-label="Close"]']) {
    const btn = page.locator(sel).first();
    if (await btn.isVisible().catch(() => false)) {
      await btn.click({ timeout: 1500 }).catch(() => {});
      await page.waitForTimeout(200);
    }
  }
  await page.keyboard.press('Escape').catch(() => {});
}

async function getClickableElements(page) {
  return page.evaluate(() => {
    const root =
      document.querySelector('.layout-content') ||
      document.querySelector('.ms-main-container') ||
      document.querySelector('main') ||
      document.body;
    const candidates = root.querySelectorAll(
      'button, a[href], [role="button"], .arco-btn, .arco-radio, .arco-tabs-tab, .arco-menu-item, .arco-select-view'
    );
    const items = [];
    const seen = new Set();
    for (const el of candidates) {
      const style = window.getComputedStyle(el);
      if (style.display === 'none' || style.visibility === 'hidden' || style.pointerEvents === 'none') continue;
      if (el.disabled || el.getAttribute('aria-disabled') === 'true') continue;
      const rect = el.getBoundingClientRect();
      if (rect.width < 8 || rect.height < 8) continue;
      if (rect.bottom < 0 || rect.top > window.innerHeight) continue;
      const text = (el.innerText || el.getAttribute('aria-label') || el.getAttribute('title') || '').trim();
      const tag = el.tagName.toLowerCase();
      const key = `${tag}|${text}|${Math.round(rect.x)}|${Math.round(rect.y)}`;
      if (seen.has(key)) continue;
      seen.add(key);
      items.push({ text: text.slice(0, 80), tag, x: rect.x + rect.width / 2, y: rect.y + rect.height / 2 });
    }
    return items.slice(0, 12);
  });
}

async function auditRoute(page, route) {
  const url = `${BASE}${route.path}`;
  console.log(`\n=== 巡检: ${route.name} => ${url}`);
  findings.routesVisited.push({ name: route.name, url, light: !!route.light });
  await page.goto(url, { waitUntil: 'domcontentloaded', timeout: 60000 });
  await page.waitForTimeout(1800);
  await shot(page, `enter-${route.name}`);
  const loggedOut = await scanVisibleErrors(page, `进入:${route.name}`);
  if (loggedOut) return false;

  if (route.light) {
    await closeOverlays(page);
    return true;
  }

  const elements = await getClickableElements(page);
  console.log(`  可见可点击元素: ${elements.length}`);
  for (const el of elements) {
    if (SKIP_CLICK_RE.test(el.text)) continue;
    try {
      await page.mouse.click(el.x, el.y);
      await page.waitForTimeout(500);
      findings.clicks.push({ route: route.name, text: el.text, tag: el.tag });
      const stop = await scanVisibleErrors(page, `${route.name}/${el.text || el.tag}`);
      if (stop) return false;
      const hasModal = await page.locator('.arco-modal, .arco-drawer').first().isVisible().catch(() => false);
      if (hasModal) {
        await scanVisibleErrors(page, `${route.name}/弹层:${el.text}`);
        await closeOverlays(page);
      }
    } catch (e) {
      addIssue('click-failed', `「${el.text || el.tag}」: ${e.message}`, { route: route.name });
    }
  }
  return true;
}

async function main() {
  const hardTimeout = setTimeout(() => {
    console.error(`HARD_TIMEOUT after ${HARD_TIMEOUT_MS}ms`);
    try {
      findings.finishedAt = new Date().toISOString();
      findings.summary = {
        routesVisited: findings.routesVisited.length,
        clicks: findings.clicks.length,
        issues: findings.issues.length,
        uniqueIssueMessages: [...new Set(findings.issues.map((i) => i.message))],
        screenshots: findings.screenshots.length,
        timedOut: true,
      };
      writeFileSync(resolve(OUT_DIR, 'org-setting-audit-report.json'), JSON.stringify(findings, null, 2), 'utf8');
    } catch {}
    process.exit(1);
  }, HARD_TIMEOUT_MS).unref();

  const browser = await chromium.connectOverCDP(CDP_URL);
  const context = browser.contexts()[0] || (await browser.newContext());
  const pages = context.pages();
  const page = pages.find((p) => /msp\.ebcone\.net/i.test(p.url())) || pages[0] || (await context.newPage());

  page.on('console', (msg) => {
    if (msg.type() === 'error') {
      const text = msg.text();
      findings.consoleErrors.push(text);
      if (isErrorText(text) || /no\s+static|###\s*ERROR|exception/i.test(text)) {
        addIssue('console', text.slice(0, 300));
      }
    }
  });
  page.on('response', (resp) => {
    const status = resp.status();
    const url = resp.url();
    if (status >= 400 && /\/api\//i.test(url)) {
      findings.networkFailures.push({ status, url: url.slice(0, 300) });
      if (status >= 500) addIssue('network', `HTTP ${status} ${url.slice(0, 200)}`);
    }
  });

  try {
    for (const route of ROUTES) {
      const ok = await auditRoute(page, route);
      if (!ok) break;
    }
  } finally {
    clearTimeout(hardTimeout);
    findings.finishedAt = new Date().toISOString();
    findings.summary = {
      routesVisited: findings.routesVisited.length,
      clicks: findings.clicks.length,
      issues: findings.issues.length,
      uniqueIssueMessages: [...new Set(findings.issues.map((i) => i.message))],
      consoleErrors: findings.consoleErrors.length,
      networkFailures: findings.networkFailures.length,
      screenshots: findings.screenshots.length,
    };
    const out = resolve(OUT_DIR, 'org-setting-audit-report.json');
    writeFileSync(out, JSON.stringify(findings, null, 2), 'utf8');
    console.log(`\n报告已写入: ${out}`);
    console.log('SUMMARY', JSON.stringify(findings.summary, null, 2));
    await browser.close().catch(() => {});
  }
}

main().catch((err) => {
  console.error('AUDIT_FATAL', err);
  process.exit(1);
});
