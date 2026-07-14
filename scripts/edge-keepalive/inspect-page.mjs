import { chromium } from 'playwright';

const CDP_URL = 'http://127.0.0.1:9222';

async function main() {
  const browser = await chromium.connectOverCDP(CDP_URL);
  const contexts = browser.contexts();
  console.log('contexts:', contexts.length);

  for (const ctx of contexts) {
    for (const page of ctx.pages()) {
      const url = page.url();
      const title = await page.title();
      console.log('\n=== page ===');
      console.log('title:', title);
      console.log('url:', url);

      if (!url.includes('weixin12315.com')) continue;

      const navTexts = await page.evaluate(() => {
        const items = [];
        document.querySelectorAll('a, button, span, li, div').forEach((el) => {
          const text = (el.textContent || '').trim();
          if (!text || text.length > 30) return;
          if (text.includes('首页') || text.includes('大数据') || text.includes('米多')) {
            const rect = el.getBoundingClientRect();
            if (rect.width > 0 && rect.height > 0) {
              items.push({
                tag: el.tagName,
                text,
                className: el.className?.toString?.() || '',
                id: el.id || '',
                href: el.getAttribute?.('href') || '',
              });
            }
          }
        });
        return items.slice(0, 50);
      });
      console.log('nav candidates:', JSON.stringify(navTexts, null, 2));

      const menuItems = await page.evaluate(() => {
        const items = [];
        document.querySelectorAll('[class*="menu"], [class*="nav"], [class*="sidebar"], .el-menu-item, .ant-menu-item').forEach((el) => {
          const text = (el.textContent || '').trim().replace(/\s+/g, ' ');
          if (text && text.length < 40) {
            items.push({ tag: el.tagName, text, className: el.className?.toString?.() || '' });
          }
        });
        return items.slice(0, 30);
      });
      console.log('menu items:', JSON.stringify(menuItems, null, 2));
    }
  }

  await browser.close();
}

main().catch((err) => {
  console.error(err);
  process.exit(1);
});
