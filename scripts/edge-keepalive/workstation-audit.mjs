import { chromium } from 'playwright';
import { writeFileSync } from 'node:fs';
import { resolve, dirname } from 'node:path';
import { fileURLToPath } from 'node:url';

const CDP_URL = process.env.EDGE_CDP_URL || 'http://127.0.0.1:9222';
const BASE = 'https://msp.ebcone.net/#';
const QUERY = 'orgId=100001&pId=100001100001';
const ROUTES = [
  { name: '首页', path: `/workstation/home?${QUERY}` },
  { name: '我的待办', path: `/workstation/wait?${QUERY}` },
  { name: '我关注的', path: `/workstation/followed?${QUERY}` },
  { name: '我创建的', path: `/workstation/created?${QUERY}` },
];

const ERROR_PATTERNS = [
  /no\s+static/i,
  /###\s*ERROR/i,
  /Whitelabel\s+Error/i,
  /\b5\d{2}\b.*error/i,
  /Internal Server Error/i,
  /系统异常/,
  /服务异常/,
  /请求失败/,
  /加载失败/,
  /Not Found/i,
];

const SKIP_CLICK_RE =
  /退出|登出|删除|移除|清空|注销|logout|delete|remove|danger|submit.*删除/i;

const findings = {
  startedAt: new Date().toISOString(),
  cdpUrl: CDP_URL,
  targetUrl: `${BASE}/workstation/home?${QUERY}`,
  routesVisited: [],
  clicks: [],
  issues: [],
  consoleErrors: [],
  networkFailures: [],
  summary: {},
};

function addIssue(type, message, extra = {}) {
  const item = { type, message, at: new Date().toISOString(), ...extra };
  findings.issues.push(item);
  console.log(`[ISSUE:${type}] ${message}`);
}

function isErrorText(text) {
  if (!text || text.length > 5000) return false;
  return ERROR_PATTERNS.some((re) => re.test(text));
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
    return { popupTexts: [...new Set(texts)], bodyText };
  });

  for (const text of checks.popupTexts) {
    if (isErrorText(text)) {
      addIssue('popup', text.slice(0, 300), { context: contextLabel });
    }
  }
  if (isErrorText(checks.bodyText)) {
    for (const re of ERROR_PATTERNS) {
      const m = checks.bodyText.match(re);
      if (m) {
        addIssue('page-text', m[0], { context: contextLabel });
        break;
      }
    }
  }
}

async function closeOverlays(page) {
  const closeSelectors = [
    '.arco-modal-close-btn',
    '.arco-drawer-close-btn',
    '.arco-icon-hover[aria-label="Close"]',
    'button[aria-label="Close"]',
  ];
  for (const sel of closeSelectors) {
    const btn = page.locator(sel).first();
    if (await btn.isVisible().catch(() => false)) {
      await btn.click({ timeout: 1500 }).catch(() => {});
      await page.waitForTimeout(300);
    }
  }
  await page.keyboard.press('Escape').catch(() => {});
}

async function getClickableElements(page) {
  return page.evaluate(() => {
    const root =
      document.querySelector('.work-bench-content') ||
      document.querySelector('.layout-content') ||
      document.querySelector('main') ||
      document.body;

    const candidates = root.querySelectorAll(
      'button, a[href], [role="button"], .arco-btn, .arco-radio, .arco-tabs-tab, .arco-menu-item, .arco-select-view, .arco-tag, .cursor-pointer, [class*="cursor-pointer"]'
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
      const cls = (el.className || '').toString().slice(0, 120);
      const key = `${tag}|${text}|${Math.round(rect.x)}|${Math.round(rect.y)}`;
      if (seen.has(key)) continue;
      seen.add(key);

      items.push({
        text: text.slice(0, 80),
        tag,
        cls,
        x: rect.x + rect.width / 2,
        y: rect.y + rect.height / 2,
      });
    }
    return items.slice(0, 80);
  });
}

async function clickTopWorkbenchTabs(page) {
  const tabTexts = ['首页', '我的待办', '我关注的', '我创建的'];
  for (const label of tabTexts) {
    const tab = page.getByText(label, { exact: true }).first();
    if (await tab.isVisible().catch(() => false)) {
      await tab.click({ timeout: 5000 }).catch((e) => {
        addIssue('click-failed', `顶部Tab「${label}」点击失败: ${e.message}`, { label });
      });
      await page.waitForTimeout(1200);
      await scanVisibleErrors(page, `顶部Tab:${label}`);
      findings.clicks.push({ area: 'top-tab', label });
    }
  }
}

async function auditRoute(page, route) {
  const url = `${BASE}${route.path}`;
  console.log(`\n=== 巡检: ${route.name} => ${url}`);
  findings.routesVisited.push({ name: route.name, url });

  await page.goto(url, { waitUntil: 'domcontentloaded', timeout: 60000 });
  await page.waitForTimeout(2500);
  await scanVisibleErrors(page, `进入:${route.name}`);

  const elements = await getClickableElements(page);
  console.log(`  可见可点击元素: ${elements.length}`);

  for (const el of elements) {
    if (SKIP_CLICK_RE.test(el.text)) continue;

    try {
      await page.mouse.click(el.x, el.y, { timeout: 3000 });
      await page.waitForTimeout(700);
      await scanVisibleErrors(page, `${route.name} / 点击:${el.text || el.tag}`);
      findings.clicks.push({
        route: route.name,
        text: el.text,
        tag: el.tag,
      });

      const hasModal = await page
        .locator('.arco-modal, .arco-drawer')
        .first()
        .isVisible()
        .catch(() => false);
      if (hasModal) {
        await scanVisibleErrors(page, `${route.name} / 弹层:${el.text}`);
        await closeOverlays(page);
      }
    } catch (e) {
      addIssue('click-failed', `「${el.text || el.tag}」: ${e.message}`, { route: route.name });
    }
  }
}

async function main() {
  const browser = await chromium.connectOverCDP(CDP_URL);
  const context = browser.contexts()[0] || (await browser.newContext());
  const page = context.pages()[0] || (await context.newPage());

  page.on('console', (msg) => {
    if (msg.type() === 'error') {
      const text = msg.text();
      findings.consoleErrors.push(text);
      if (isErrorText(text) || /failed|exception|error/i.test(text)) {
        addIssue('console', text.slice(0, 300));
      }
    }
  });

  page.on('response', (resp) => {
    const status = resp.status();
    const url = resp.url();
    if (status >= 400 && /\/(api|system|project|workbench|dashboard)/i.test(url)) {
      const item = { status, url: url.slice(0, 300) };
      findings.networkFailures.push(item);
      if (status >= 500) {
        addIssue('network', `HTTP ${status} ${url.slice(0, 200)}`);
      }
    }
  });

  try {
    for (const route of ROUTES) {
      await auditRoute(page, route);
    }

    await page.goto(`${BASE}/workstation/home?${QUERY}`, { waitUntil: 'domcontentloaded', timeout: 60000 });
    await page.waitForTimeout(2000);
    await clickTopWorkbenchTabs(page);

    await scanVisibleErrors(page, '最终检查');
    await closeOverlays(page);
  } finally {
    findings.finishedAt = new Date().toISOString();
    findings.summary = {
      routesVisited: findings.routesVisited.length,
      clicks: findings.clicks.length,
      issues: findings.issues.length,
      consoleErrors: findings.consoleErrors.length,
      networkFailures: findings.networkFailures.length,
    };

    const out = resolve(dirname(fileURLToPath(import.meta.url)), 'workstation-audit-report.json');
    writeFileSync(out, JSON.stringify(findings, null, 2), 'utf8');
    console.log(`\n报告已写入: ${out}`);
    console.log('SUMMARY', findings.summary);
    await browser.close().catch(() => {});
  }
}

main().catch((err) => {
  console.error('AUDIT_FATAL', err);
  process.exit(1);
});
