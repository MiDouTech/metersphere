INSERT INTO permission_resource
(id, code, name, type, scope_type, parent_code, route_name, permission_id, visible_default, operable_default, sort, enabled, description)
SELECT 'BUG_MANAGEMENT_PAGE', 'BUG_MANAGEMENT_PAGE', '缺陷管理', 'PAGE', 'PROJECT', NULL, 'bugManagementIndex',
       'PROJECT_BUG:READ', b'1', b'0', 1000, b'1', '缺陷管理列表页面'
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM permission_resource WHERE code = 'BUG_MANAGEMENT_PAGE');

INSERT INTO permission_resource
(id, code, name, type, scope_type, parent_code, route_name, permission_id, visible_default, operable_default, sort, enabled, description)
SELECT 'BUG_DETAIL_PAGE', 'BUG_DETAIL_PAGE', '缺陷详情', 'PAGE', 'PROJECT', 'BUG_MANAGEMENT_PAGE', 'bugManagementIndex',
       'PROJECT_BUG:READ', b'1', b'0', 1010, b'1', '缺陷详情抽屉页面'
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM permission_resource WHERE code = 'BUG_DETAIL_PAGE');

INSERT INTO permission_resource
(id, code, name, type, scope_type, parent_code, route_name, permission_id, visible_default, operable_default, sort, enabled, description)
SELECT 'BUG_DETAIL_EDIT_BUTTON', 'BUG_DETAIL_EDIT_BUTTON', '编辑缺陷', 'BUTTON', 'PROJECT', 'BUG_DETAIL_PAGE', NULL,
       'PROJECT_BUG:READ+UPDATE', b'1', b'0', 1020, b'1', '缺陷详情编辑按钮'
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM permission_resource WHERE code = 'BUG_DETAIL_EDIT_BUTTON');

INSERT INTO permission_resource
(id, code, name, type, scope_type, parent_code, route_name, permission_id, visible_default, operable_default, sort, enabled, description)
SELECT 'BUG_DETAIL_SHARE_BUTTON', 'BUG_DETAIL_SHARE_BUTTON', '分享缺陷', 'BUTTON', 'PROJECT', 'BUG_DETAIL_PAGE', NULL,
       'PROJECT_BUG:READ', b'1', b'0', 1030, b'1', '缺陷详情分享按钮；当前仅控制 UI 可见与操作，后端安全边界沿用缺陷读取权限'
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM permission_resource WHERE code = 'BUG_DETAIL_SHARE_BUTTON');

INSERT INTO permission_resource
(id, code, name, type, scope_type, parent_code, route_name, permission_id, visible_default, operable_default, sort, enabled, description)
SELECT 'BUG_DETAIL_FOLLOW_BUTTON', 'BUG_DETAIL_FOLLOW_BUTTON', '关注缺陷', 'BUTTON', 'PROJECT', 'BUG_DETAIL_PAGE', NULL,
       'PROJECT_BUG:READ', b'1', b'0', 1040, b'1', '缺陷详情关注按钮；当前仅控制 UI 可见与操作，后端安全边界沿用缺陷读取权限'
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM permission_resource WHERE code = 'BUG_DETAIL_FOLLOW_BUTTON');

INSERT INTO permission_resource
(id, code, name, type, scope_type, parent_code, route_name, permission_id, visible_default, operable_default, sort, enabled, description)
SELECT 'BUG_DETAIL_COPY_BUTTON', 'BUG_DETAIL_COPY_BUTTON', '复制缺陷', 'BUTTON', 'PROJECT', 'BUG_DETAIL_PAGE', NULL,
       'PROJECT_BUG:READ+ADD', b'1', b'0', 1050, b'1', '缺陷详情复制按钮'
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM permission_resource WHERE code = 'BUG_DETAIL_COPY_BUTTON');

INSERT INTO permission_resource
(id, code, name, type, scope_type, parent_code, route_name, permission_id, visible_default, operable_default, sort, enabled, description)
SELECT 'BUG_DETAIL_DELETE_BUTTON', 'BUG_DETAIL_DELETE_BUTTON', '删除缺陷', 'BUTTON', 'PROJECT', 'BUG_DETAIL_PAGE', NULL,
       'PROJECT_BUG:READ+DELETE', b'1', b'0', 1060, b'1', '缺陷详情删除按钮'
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM permission_resource WHERE code = 'BUG_DETAIL_DELETE_BUTTON');

INSERT INTO permission_resource
(id, code, name, type, scope_type, parent_code, route_name, permission_id, visible_default, operable_default, sort, enabled, description)
SELECT 'BUG_DETAIL_COMMENT_BUTTON', 'BUG_DETAIL_COMMENT_BUTTON', '评论缺陷', 'BUTTON', 'PROJECT', 'BUG_DETAIL_PAGE', NULL,
       'PROJECT_BUG:READ+COMMENT', b'1', b'0', 1070, b'1', '缺陷详情评论入口'
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM permission_resource WHERE code = 'BUG_DETAIL_COMMENT_BUTTON');
