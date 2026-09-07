import { readFileSync } from 'node:fs';
import { resolve } from 'node:path';

const frontendRoot = process.cwd();
const readFrontend = (file) => readFileSync(resolve(frontendRoot, file), 'utf8');

const failures = [];
function check(condition, message) {
  if (!condition) failures.push(message);
}

const pageLayout = readFrontend('src/layout/page-layout.vue');
const defaultLayout = readFrontend('src/layout/default-layout.vue');
const shareLayout = readFrontend('src/layout/share-layout.vue');
const fullPageLayout = readFrontend('src/layout/full-page-layout.vue');
const singleLogoLayout = readFrontend('src/layout/single-logo-layout.vue');

check(pageLayout.includes('@apply overflow-auto;'), 'standard routed pages must scroll on both axes');
check(
  defaultLayout.includes('@apply flex flex-col  overflow-auto;'),
  'default layout must retain its scroll container'
);
check(defaultLayout.includes('overflow: visible;'), 'default layout content must not clip overflowing page elements');
check(shareLayout.includes('@apply h-full w-full overflow-auto p-4;'), 'shared pages must scroll on both axes');
check(shareLayout.includes('overflow: visible;'), 'shared layout content must not clip overflowing page elements');
check(fullPageLayout.includes('@apply overflow-auto;'), 'full-page routes must scroll on both axes');
check(singleLogoLayout.includes('height: calc(100vh - 56px);'), 'single-logo page scroll area must fit below navbar');
check(singleLogoLayout.includes('overflow: auto;'), 'single-logo pages must scroll on both axes');

if (failures.length) {
  console.error('Layout overflow acceptance failed:');
  failures.forEach((failure) => console.error(`- ${failure}`));
  process.exit(1);
}

console.log(
  'Layout overflow acceptance passed: standard, shared, full-page and single-logo layouts preserve overflow.'
);
