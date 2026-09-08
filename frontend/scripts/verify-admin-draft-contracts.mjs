import { readFileSync } from 'node:fs';
import { resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const root = resolve(fileURLToPath(new URL('.', import.meta.url)), '..');
const read = (file) => readFileSync(resolve(root, file), 'utf8');
const failures = [];

const requireText = (source, expected, message) => {
  if (!source.includes(expected)) failures.push(message);
};

const permission = read('src/hooks/usePermission.ts');
requireText(
  permission,
  'route.meta?.adminOnly && !userStore.isAdmin',
  'Admin-only routes must be rejected by the shared route and menu permission check.'
);

const agentRoutes = read('src/router/routes/modules/agent.ts');
requireText(agentRoutes, 'adminOnly: true', 'The Agent module must be restricted to system administrators.');

const assetRoutes = read('src/router/routes/modules/testAsset.ts');
requireText(assetRoutes, 'adminOnly: true', 'The Test Asset module must be restricted to system administrators.');

const caseRoutes = read('src/router/routes/modules/caseManagement.ts');
['CASE_MANAGEMENT_CASE_GENERATE', 'CASE_MANAGEMENT_AUTOMATION_EXECUTION'].forEach((routeName) => {
  const routeStart = caseRoutes.indexOf(`name: CaseManagementRouteEnum.${routeName}`);
  const routeEnd = caseRoutes.indexOf('\n    },', routeStart);
  const routeSource = routeStart >= 0 ? caseRoutes.slice(routeStart, routeEnd) : '';
  requireText(routeSource, 'adminOnly: true', `${routeName} must be restricted to system administrators.`);
});

const drawer = read('src/views/case-management/components/addDefectDrawer/index.vue');
requireText(
  drawer,
  ':unmount-on-close="!props.preserveDraft"',
  'The defect form must stay mounted while a draft-preserving drawer is passively closed.'
);
requireText(
  drawer,
  'props.preserveDraft && draftInitialized.value && activeDraftKey.value === nextDraftKey',
  'Reopening the same case must reuse the initialized defect draft.'
);
requireText(drawer, '@footer-cancel="handleDrawerExplicitCancel"', 'Explicit cancel must clear the defect draft.');
requireText(drawer, '() => props.draftKey', 'A defect draft must be invalidated when the source case changes.');

const detail = read('src/views/case-management/caseManagementFeature/components/tabContent/tabDetail.vue');
requireText(detail, 'preserve-draft', 'Case execution details must opt in to defect draft preservation.');
requireText(detail, ':draft-key="detailForm.id"', 'Defect drafts must be isolated by case ID.');

const baseDrawer = read('src/components/pure/ms-drawer/index.vue');
requireText(baseDrawer, "emit('footerCancel')", 'The base drawer must distinguish explicit footer cancellation.');

if (failures.length) {
  // eslint-disable-next-line no-console
  console.error(`Admin and defect-draft contract verification failed:\n- ${failures.join('\n- ')}`);
  process.exit(1);
}

// eslint-disable-next-line no-console
console.log('Admin and defect-draft contract verification passed.');
