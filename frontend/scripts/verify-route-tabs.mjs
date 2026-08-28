import { readFileSync } from 'node:fs';
import { resolve } from 'node:path';

const frontendRoot = process.cwd();
const repositoryRoot = resolve(frontendRoot, '..');
const readFrontend = (file) => readFileSync(resolve(frontendRoot, file), 'utf8');
const readRepository = (file) => readFileSync(resolve(repositoryRoot, file), 'utf8');

const failures = [];
function check(condition, message) {
  if (!condition) failures.push(message);
}
function includesAll(source, values, label) {
  values.forEach((value) => check(source.includes(value), `${label} missing: ${value}`));
}
function excludesAll(source, values, label) {
  values.forEach((value) => check(!source.includes(value), `${label} must not contain: ${value}`));
}

const projectRoutes = readFrontend('src/router/routes/modules/projectManagement.ts');
const projectList = readFrontend('src/views/project-management/projectList/index.vue');
const permissionHook = readFrontend('src/hooks/usePermission.ts');
includesAll(projectRoutes, ["path: 'projects'", "resourceCode: 'PROJECT_LIST_PAGE'", 'roles: []'], 'project list route');
excludesAll(permissionHook, ["route.name === 'projectManagement'", "lastProjectId === 'no_such_project'"], 'project entry permission hook');
includesAll(projectList, [
  '进入项目', '项目 ID', '项目名称', '成员', '状态', '描述', '创建人', '创建时间', '添加成员',
  'PROJECT_LIST_ENTER_BUTTON', 'PROJECT_LIST_ADD_MEMBER_BUTTON', 'PROJECT_LIST_COPY_ID_BUTTON',
], 'project list page');

const testAssetRoutes = readFrontend('src/router/routes/modules/testAsset.ts');
const caseAssets = readFrontend('src/views/test-asset/cases.vue');
includesAll(testAssetRoutes, [
  "path: 'cases'", "path: 'cases/project'", "path: 'cases/system'",
  "resourceCode: 'TEST_ASSET_CASE_PROJECT_TAB'", "resourceCode: 'TEST_ASSET_CASE_SYSTEM_TAB'",
], 'test asset routes');
includesAll(caseAssets, ['pageCaseAssetCatalogs', 'catalogQuery', 'caseQuery', '用例项目仅是资产分类目录', 'CaseAssetFileImport'], 'case asset page');
excludesAll(caseAssets, ['pageAccessibleProjects', '系统级用例资产暂未开放'], 'case asset page');

const agentRoutes = readFrontend('src/router/routes/modules/agent.ts');
const agentAccess = readFrontend('src/views/agent/access.vue');
const agentIntegration = readFrontend('src/views/setting/system/agentIntegration/index.vue');
includesAll(agentRoutes, [
  "icon: 'icon-icon_robot'", "path: 'access'", "locale: 'Agent 集成'",
  "roles: ['SYSTEM_PERSONAL_AI_AGENT:READ']", "resourceCode: 'AGENT_INTEGRATION_PAGE'",
], 'Agent routes');
check(!agentAccess.includes('AgentTabs'), 'Agent pages must not render a second same-name tab row');
includesAll(agentIntegration, [
  'if (!canReadTokens) return', 'AGENT_TOKEN_CREATE_BUTTON', 'AGENT_TOKEN_DOWNLOAD_BUTTON',
  'AGENT_TOKEN_UPDATE_BUTTON', 'AGENT_TOKEN_DELETE_BUTTON',
], 'Agent integration permission gates');

const testAssetPage = readFrontend('src/views/test-asset/components/TestAssetPage.vue');
check(!testAssetPage.includes('TestAssetTabs'), 'test asset pages must not render a second same-name tab row');

const caseRoutes = readFrontend('src/router/routes/modules/caseManagement.ts');
excludesAll(caseRoutes, ['FUNCTIONAL_CASE_PROJECT_TAB', 'FUNCTIONAL_CASE_SYSTEM_TAB'], 'test case routes');

const routeEnum = readFrontend('src/enums/routeEnum.ts');
const pathMap = readFrontend('src/config/pathMap.ts');
const projectPermissionMenu = readFrontend('src/views/project-management/projectAndPermission/index.vue');
for (const legacyEntry of ['PROJECT_MANAGEMENT_PERMISSION_USER_GROUP', 'SETTING_ORGANIZATION_USER_GROUP', 'SETTING_SYSTEM_USER_GROUP']) {
  check(!routeEnum.includes(legacyEntry), `legacy user-group route enum remains: ${legacyEntry}`);
  check(!pathMap.includes(legacyEntry), `legacy user-group path map entry remains: ${legacyEntry}`);
  check(!projectPermissionMenu.includes(legacyEntry), `legacy user-group product menu remains: ${legacyEntry}`);
}

const systemUserTemplate = readFrontend('src/views/setting/system/user/index.vue').split('<script')[0];
const organizationMemberTemplate = readFrontend('src/views/setting/organization/member/index.vue').split('<script')[0];
const projectMemberTemplate = readFrontend('src/views/project-management/projectAndPermission/member/components/memberTable.vue').split('<script')[0];
const inviteMemberTemplate = readFrontend('src/views/setting/system/components/inviteModal.vue').split('<script')[0];
const organizationMemberModalTemplate = readFrontend('src/views/setting/organization/member/components/addMemberModal.vue').split('<script')[0];
const projectMemberModalTemplate = readFrontend('src/views/project-management/projectAndPermission/member/components/addMemberModal.vue').split('<script')[0];
excludesAll(systemUserTemplate, ['#userGroup', 'field="userGroup"', 'user-group-options'], 'system user legacy group controls');
excludesAll(organizationMemberTemplate, ['#userRoleIdNameMap', 'add-user-group', 'user-group-options'], 'organization member legacy group controls');
excludesAll(projectMemberTemplate, ['#userRoles', 'add-user-group', 'user-group-options'], 'project member legacy group controls');
excludesAll(inviteMemberTemplate, ['field="userGroup"'], 'member invite legacy group controls');
excludesAll(organizationMemberModalTemplate, ['field="userRoleIds"'], 'organization add-member legacy group controls');
excludesAll(projectMemberModalTemplate, ['field="roleIds"'], 'project add-member legacy group controls');

const rolePage = readFrontend('src/views/setting/system/permissionControl/index.vue');
const roleEditor = readFrontend('src/views/setting/system/permissionControl/role/editor.vue');
includesAll(roleEditor, [
  'savePermissionControlRole({', '<a-select',
  '角色名称', '权限范围', '启用状态',
], 'permission control role editor page');
includesAll(rolePage, [
  'getPermissionControlRoleMemberScopeOptions', 'pagePermissionControlRoleMembers',
  'addPermissionControlRoleMembers', 'removePermissionControlRoleMembers',
  "name: 'settingSystemPermissionControlRoleDetail'", "name: 'settingSystemPermissionControlRoleCreate'",
], 'permission control role entry and member modal');
excludesAll(rolePage, [':on-before-ok="saveRole"', 'addPermissionControlRole(', 'updatePermissionControlRole(', 'savePermissionControlRolePermissions('], 'atomic role page');

const roleController = readRepository('backend/services/system-setting/src/main/java/io/metersphere/system/controller/GlobalUserRoleController.java');
const roleService = readRepository('backend/services/system-setting/src/main/java/io/metersphere/system/service/PermissionControlService.java');
const userService = readRepository('backend/services/system-setting/src/main/java/io/metersphere/system/service/SimpleUserService.java');
includesAll(roleController, ['permissionControlService.saveRoleMetadata(request)', 'permissionControlService.saveRolePermission(request)'], 'legacy role controller delegation');
includesAll(roleService, [
  'return saveRole(saveRequest)', 'assertTargetScopeMemberPermission(role, resolveMemberSourceId(role, sourceId), mutable)',
  'ORGANIZATION_USER_ROLE_READ', 'PROJECT_GROUP_READ', '目标项目不存在或已停用',
], 'role service scope validation');
includesAll(userService, [
  'private static final String SYSTEM_MEMBER_ROLE_ID = "member"',
  'userCreateDTO.setUserRoleIdList(List.of(SYSTEM_MEMBER_ROLE_ID))',
  'request.setUserRoleIds(List.of(SYSTEM_MEMBER_ROLE_ID))',
], 'default member assignment');
check(!/updateUser\([\s\S]*?updateUserSystemGlobalRole/.test(userService), 'system user edit must not retain a second role-membership write path');

const projectController = readRepository('backend/services/project-management/src/main/java/io/metersphere/project/controller/ProjectController.java');
const projectMapper = readRepository('backend/services/project-management/src/main/java/io/metersphere/project/mapper/ExtProjectMapper.xml');
includesAll(projectController, ['/case-asset/page', 'pageCaseAssetProject'], 'case asset project endpoint');
includesAll(projectMapper, ['pageCaseAssetProject', 'user_role_permission', 'urp.permission_id = #{permissionId}'], 'case asset project permission intersection');

const actionMigration = readRepository('backend/framework/domain/src/main/resources/migration/3.7.2/dml/V3.7.2_58__project_list_and_agent_action_resources.sql');
includesAll(actionMigration, [
  'PROJECT_LIST_ENTER_BUTTON', 'PROJECT_LIST_ADD_MEMBER_BUTTON', 'PROJECT_LIST_COPY_ID_BUTTON',
  'AGENT_TOKEN_CREATE_BUTTON', 'AGENT_TOKEN_DOWNLOAD_BUTTON', 'AGENT_TOKEN_UPDATE_BUTTON', 'AGENT_TOKEN_DELETE_BUTTON',
  "'PROJECT_ADD_BUTTON', 'PROJECT_ARCHIVE_BUTTON'",
  "SET scope_type = 'SYSTEM'", "SET permission_id = 'SYSTEM_PERSONAL_AI_AGENT:READ'",
], 'action resource migration');
for (const agentButton of ['AGENT_TOKEN_CREATE_BUTTON', 'AGENT_TOKEN_DOWNLOAD_BUTTON', 'AGENT_TOKEN_UPDATE_BUTTON', 'AGENT_TOKEN_DELETE_BUTTON']) {
  check(
    new RegExp(`\\('${agentButton}'[^\\n]+, 'SYSTEM', 'AGENT_INTEGRATION_PAGE'`).test(actionMigration),
    `Agent action resource must use SYSTEM scope: ${agentButton}`
  );
}

if (failures.length) {
  console.error('Information architecture acceptance failed:');
  failures.forEach((failure) => console.error(`- ${failure}`));
  process.exit(1);
}
console.log('Information architecture acceptance passed: routes, entries, permission intersections, scope validation and button gates are aligned.');
