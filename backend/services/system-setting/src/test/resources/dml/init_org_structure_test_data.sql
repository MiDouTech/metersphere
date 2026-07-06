# task005 组织架构查询 API 测试数据

DELETE FROM department WHERE organization_id = 'sys_default_organization_3';
DELETE FROM user_role_relation WHERE id LIKE 'org_structure_relation_%';
DELETE FROM user WHERE id LIKE 'org_structure_user_%';

INSERT INTO department(id, organization_id, name, parent_id, wecom_dept_id, sort_order, dept_status, sync_status, sync_time,
                       create_time, update_time, create_user, update_user)
VALUES ('dept_test_root', 'sys_default_organization_3', '总部', NULL, 1, 1, 1, 1, UNIX_TIMESTAMP() * 1000,
        UNIX_TIMESTAMP() * 1000, UNIX_TIMESTAMP() * 1000, 'admin', 'admin'),
       ('dept_test_child_a', 'sys_default_organization_3', '研发部', 'dept_test_root', 2, 1, 1, 1, UNIX_TIMESTAMP() * 1000,
        UNIX_TIMESTAMP() * 1000, UNIX_TIMESTAMP() * 1000, 'admin', 'admin'),
       ('dept_test_child_b', 'sys_default_organization_3', '市场部', 'dept_test_root', 3, 2, 1, 1, UNIX_TIMESTAMP() * 1000,
        UNIX_TIMESTAMP() * 1000, UNIX_TIMESTAMP() * 1000, 'admin', 'admin'),
       ('dept_test_grandchild', 'sys_default_organization_3', '后端组', 'dept_test_child_a', 4, 1, 1, 1, UNIX_TIMESTAMP() * 1000,
        UNIX_TIMESTAMP() * 1000, UNIX_TIMESTAMP() * 1000, 'admin', 'admin'),
       ('dept_test_disabled', 'sys_default_organization_3', '停用部门', 'dept_test_root', 5, 3, 0, 0, NULL,
        UNIX_TIMESTAMP() * 1000, UNIX_TIMESTAMP() * 1000, 'admin', 'admin');

INSERT INTO user(id, name, email, password, enable, create_time, update_time, language, last_organization_id, phone, source,
                 last_project_id, create_user, update_user, deleted, cft_token, wecom_userid, department_id, position,
                 sync_status, sync_time)
VALUES ('org_structure_user_1', 'OrgUser1', 'orguser1@metersphere.io', MD5('calong'), 1, UNIX_TIMESTAMP() * 1000,
        UNIX_TIMESTAMP() * 1000, NULL, 'sys_default_organization_3', '13800138001', 'LOCAL', NULL, 'admin', 'admin', 0,
        'NONE', 'wx_user_001', 'dept_test_child_a', '工程师', 1, UNIX_TIMESTAMP() * 1000),
       ('org_structure_user_2', 'OrgUser2', 'orguser2@metersphere.io', MD5('calong'), 1, UNIX_TIMESTAMP() * 1000,
        UNIX_TIMESTAMP() * 1000, NULL, 'sys_default_organization_3', '13900139002', 'LOCAL', NULL, 'admin', 'admin', 0,
        'NONE', 'wx_user_002', 'dept_test_child_a', '工程师', 1, UNIX_TIMESTAMP() * 1000),
       ('org_structure_user_3', 'OrgUser3', 'orguser3@metersphere.io', MD5('calong'), 1, UNIX_TIMESTAMP() * 1000,
        UNIX_TIMESTAMP() * 1000, NULL, 'sys_default_organization_3', '13700137003', 'LOCAL', NULL, 'admin', 'admin', 0,
        'NONE', 'wx_user_003', 'dept_test_grandchild', '后端工程师', 1, UNIX_TIMESTAMP() * 1000),
       ('org_structure_user_4', 'OrgUser4', 'orguser4@metersphere.io', MD5('calong'), 1, UNIX_TIMESTAMP() * 1000,
        UNIX_TIMESTAMP() * 1000, NULL, 'sys_default_organization_3', '13600136004', 'LOCAL', NULL, 'admin', 'admin', 0,
        'NONE', 'wx_user_004', 'dept_test_child_b', '市场专员', 1, UNIX_TIMESTAMP() * 1000);

INSERT INTO user_role_relation(id, user_id, role_id, source_id, organization_id, create_time, create_user)
VALUES ('org_structure_relation_1', 'org_structure_user_1', 'sys_default_org_role_id_3', 'sys_default_organization_3',
        'sys_default_organization_3', UNIX_TIMESTAMP() * 1000, 'admin'),
       ('org_structure_relation_2', 'org_structure_user_2', 'sys_default_org_role_id_3', 'sys_default_organization_3',
        'sys_default_organization_3', UNIX_TIMESTAMP() * 1000, 'admin'),
       ('org_structure_relation_3', 'org_structure_user_3', 'sys_default_org_role_id_3', 'sys_default_organization_3',
        'sys_default_organization_3', UNIX_TIMESTAMP() * 1000, 'admin'),
       ('org_structure_relation_4', 'org_structure_user_4', 'sys_default_org_role_id_3', 'sys_default_organization_3',
        'sys_default_organization_3', UNIX_TIMESTAMP() * 1000, 'admin');
