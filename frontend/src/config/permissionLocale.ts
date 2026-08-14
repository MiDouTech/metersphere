const roleScopeMap: Record<string, string> = {
  SYSTEM: '系统',
  ORGANIZATION: '组织',
  PROJECT: '项目',
};

const resourceTypeMap: Record<string, string> = {
  MENU: '菜单',
  PAGE: '页面',
  TAB: '页签',
  BUTTON: '按钮',
  API: '接口权限',
};

const actionMap: Record<string, string> = {
  READ: '查看',
  ADD: '创建',
  UPDATE: '编辑',
  DELETE: '删除',
  RECOVER: '恢复',
  ADD_MEMBER: '添加成员',
  INVITE: '邀请成员',
  INVITE_MEMBER: '邀请成员',
  DELETE_MEMBER: '移除成员',
  REMOVE_MEMBER: '移除成员',
  UPDATE_MEMBER: '编辑成员',
  COMMENT: '评论',
  IMPORT: '导入',
  EXPORT: '导出',
  UPLOAD: '上传',
  DOWNLOAD: '下载',
  ENABLE: '启用',
  EXECUTE: '执行',
  EXEC: '执行',
  STOP: '停止',
  GENERATE: '生成',
  CONFIG: '配置',
  CONNECT: '连接',
  REVOKE: '撤销',
  SHARE: '分享',
  REVIEW: '评审',
  RELEVANCE: '关联',
  DEBUG: '调试',
  MINDER: '脑图编辑',
  SAVE: '保存',
  ADMIN: '管理',
  ASSOCIATION: '关联',
  CANCEL: '取消',
  LOGIN: '登录',
  RUN: '运行',
};

export interface UnknownPermissionDiagnostic {
  kind: string;
  code: string;
  context?: string;
}

let unknownPermissionReporter: ((diagnostic: UnknownPermissionDiagnostic) => void) | undefined;
const reportedUnknownPermissions = new Set<string>();

export function setUnknownPermissionReporter(reporter?: (diagnostic: UnknownPermissionDiagnostic) => void) {
  unknownPermissionReporter = reporter;
}

function unknownPermission(kind: string, code: string, context?: string) {
  // 未知编码需要显式告警，避免英文编码悄悄进入正常业务文案。
  // eslint-disable-next-line no-console
  console.warn(`[权限中文化] 未配置${kind}中文名称`, { code, context });
  const diagnostic = { kind, code: code || '', context };
  const key = `${kind}|${diagnostic.code}|${context || ''}`;
  if (!reportedUnknownPermissions.has(key)) {
    reportedUnknownPermissions.add(key);
    unknownPermissionReporter?.(diagnostic);
  }
  return '未配置中文名称';
}

export function getRoleScopeText(type: string) {
  return roleScopeMap[type] || unknownPermission('权限范围', type);
}

export function getResourceTypeText(type: string) {
  return resourceTypeMap[type] || unknownPermission('资源类型', type);
}

export function getResourceNameText(name: string) {
  if (!name) return unknownPermission('资源名称', name);
  return name
    .replace(/\bTab\b/gi, '页签')
    .replace(/\bPage\b/gi, '页面')
    .replace(/\bButton\b/gi, '按钮')
    .replace(/\bAPI\b/g, '接口');
}

export function getPermissionText(permissionId?: string, resourceName?: string, context?: string) {
  if (!permissionId) return '-';
  const actionPart = permissionId.split(':')[1];
  if (!actionPart) return unknownPermission('权限编码', permissionId, context);
  const codes = actionPart.split('+');
  const actions = codes.map((code) => {
    if (code === 'READ' && codes.length === 1) return '只读';
    return actionMap[code] || unknownPermission('操作编码', code, context || permissionId);
  });
  return `${resourceName || '权限'}：${actions.join('、')}`;
}

export function isWritePermission(permissionId: string) {
  return permissionId.includes(':') && !permissionId.endsWith(':READ');
}
