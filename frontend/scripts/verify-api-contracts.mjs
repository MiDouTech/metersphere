import { readFileSync } from 'node:fs';
import { resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const root = resolve(fileURLToPath(new URL('.', import.meta.url)), '..');
const read = (file) => readFileSync(resolve(root, file), 'utf8');

const failures = [];
const requireText = (source, expected, message) => {
  if (!source.includes(expected)) failures.push(message);
};
const forbidText = (source, forbidden, message) => {
  if (source.includes(forbidden)) failures.push(message);
};
const functionSource = (source, name) => {
  const marker = `export function ${name}(`;
  const start = source.indexOf(marker);
  if (start < 0) {
    failures.push(`API function is missing: ${name}`);
    return '';
  }
  const next = source.indexOf('\nexport function ', start + marker.length);
  return source.slice(start, next < 0 ? source.length : next);
};

const permissionControl = read('src/api/modules/setting/permissionControl.ts');
requireText(
  functionSource(permissionControl, 'getPermissionControlRole'),
  '/permission-control/role/get/',
  'Permission-control role detail path must match the backend /permission-control prefix.'
);
forbidText(
  permissionControl,
  '/permission/control/',
  'Legacy /permission/control path found; backend endpoints use /permission-control.'
);
[
  'deletePermissionControlRole',
  'deletePermissionControlFlow',
  'publishPermissionControlFlow',
  'deletePermissionControlFlowRole',
].forEach((name) => {
  requireText(
    functionSource(permissionControl, name),
    'joinParamsToUrl: true',
    `${name} must send Spring @RequestParam values in the query string.`
  );
});

const featureCase = read('src/api/modules/case-management/featureCase.ts');
['deleteCaseAsset', 'deleteCaseAssetAttachment'].forEach((name) => {
  requireText(
    functionSource(featureCase, name),
    'joinParamsToUrl: true',
    `${name} must send Spring @RequestParam values in the query string.`
  );
});

const aiExecution = read('src/api/modules/ai-execution.ts');
[
  'publishAiPromptTemplateVersion',
  'rollbackAiPromptTemplateVersion',
  'acknowledgeAiExecutionAlert',
  'publishTestAssetCatalog',
].forEach((name) => {
  requireText(
    functionSource(aiExecution, name),
    'joinParamsToUrl: true',
    `${name} must send Spring @RequestParam values in the query string.`
  );
});

const appError = read('src/utils/appError.ts');
requireText(appError, 'payload?.traceId', 'Nested Agent errors must preserve their traceId.');
requireText(appError, 'payload?.message ?? body?.message', 'Nested Agent errors must expose their safe message.');
requireText(appError, 'payload?.code ?? body?.code', 'Nested Agent errors must expose their stable code.');

const permissionControlView = read('src/views/setting/system/permissionControl/index.vue');
requireText(
  permissionControlView,
  'positionOrganizationOptions.value = await getPermissionControlPositionOrganizationOptions()',
  'Position-based role assignment must load real organization options.'
);
const permissionControlController = read(
  '../backend/services/system-setting/src/main/java/io/metersphere/system/controller/PermissionControlController.java'
);
requireText(
  permissionControlController,
  '@GetMapping("/role/member/position-organization/options")',
  'Position-based role assignment must have a permission-protected organization-options endpoint.'
);

const credentialReferenceView = read('src/views/agent/credential-reference.vue');
requireText(
  credentialReferenceView,
  'listAiEnvironmentProfiles(app.currentProjectId)',
  'Credential references must select from existing environment profiles.'
);
requireText(
  credentialReferenceView,
  '^env:\\/\\/[A-Z][A-Z0-9_]{1,127}$',
  'Credential references must validate ENV reference URIs before submission.'
);

const modelProfileView = read('src/views/agent/model-profile.vue');
requireText(
  modelProfileView,
  '^env:\\/\\/[A-Z][A-Z0-9_]{1,127}$',
  'Model profiles must validate service-key reference URIs before submission.'
);

const bugApi = read('src/api/modules/bug-management/index.ts');
requireText(functionSource(bugApi, 'deleteComment'), 'MSR.delete(', 'Deleting a bug comment must use HTTP DELETE.');
requireText(
  functionSource(bugApi, 'cancelAssociation'),
  'MSR.delete(',
  'Removing a bug-case association must use HTTP DELETE.'
);

const bugUrls = read('src/api/requrls/bug-management.ts');
requireText(
  bugUrls,
  "getTransferTreeUrl = '/bug/attachment/transfer/options';",
  'Bug attachment transfer options URL must not end with a slash before appending projectId.'
);

const bugCommentController = read(
  '../backend/services/bug-management/src/main/java/io/metersphere/bug/controller/BugCommentController.java'
);
requireText(
  bugCommentController,
  '@DeleteMapping("/delete/{commentId}")',
  'Bug comment deletion backend mapping must use HTTP DELETE.'
);

const bugRelateCaseController = read(
  '../backend/services/bug-management/src/main/java/io/metersphere/bug/controller/BugRelateCaseController.java'
);
requireText(
  bugRelateCaseController,
  '@DeleteMapping("/un-relate/{id}")',
  'Bug-case un-relate backend mapping must use HTTP DELETE.'
);

if (failures.length) {
  console.error(`API contract verification failed:\n- ${failures.join('\n- ')}`);
  process.exit(1);
}

console.log('API contract verification passed.');
