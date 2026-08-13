import { existsSync, readFileSync } from 'node:fs';
import { resolve } from 'node:path';

const root = process.cwd();

function read(relativePath) {
  return readFileSync(resolve(root, relativePath), 'utf8');
}

function assert(condition, message) {
  if (!condition) throw new Error(message);
}

function verifyModule({ routeFile, tabsFile, routePrefix, tabs, viewDir }) {
  const routeSource = read(routeFile);
  const tabsSource = read(tabsFile);
  for (const tab of tabs) {
    assert(routeSource.includes(`path: '${tab}'`), `${routeFile} missing child route: ${tab}`);
    assert(existsSync(resolve(root, viewDir, `${tab}.vue`)), `${viewDir}/${tab}.vue does not exist`);
    assert(tabsSource.includes(`key: '${tab}'`), `${tabsFile} missing tab: ${tab}`);
  }
  assert(tabsSource.includes(`router.push({ path: \`${routePrefix}/\${target}\` })`),
    `${tabsFile} must navigate to an independent route for every tab`);
}

verifyModule({
  routeFile: 'src/router/routes/modules/agent.ts',
  tabsFile: 'src/views/agent/components/AgentTabs.vue',
  routePrefix: '/agent',
  tabs: ['list', 'capability', 'queue', 'evaluation', 'access'],
  viewDir: 'src/views/agent',
});

verifyModule({
  routeFile: 'src/router/routes/modules/testAsset.ts',
  tabsFile: 'src/views/test-asset/components/TestAssetTabs.vue',
  routePrefix: '/test-assets',
  tabs: ['documents', 'versions', 'relations'],
  viewDir: 'src/views/test-asset',
});

console.log('Route-tab verification passed: Agent 5/5, Test Assets 3/3.');
