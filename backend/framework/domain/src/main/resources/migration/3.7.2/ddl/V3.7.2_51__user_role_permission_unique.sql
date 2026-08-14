-- 权限关系数据库级幂等保护。先保留每组关系的一条记录，再增加联合唯一约束。
DELETE duplicate_permission
FROM user_role_permission duplicate_permission
INNER JOIN user_role_permission retained_permission
        ON retained_permission.role_id = duplicate_permission.role_id
       AND retained_permission.permission_id = duplicate_permission.permission_id
       AND retained_permission.id < duplicate_permission.id;

ALTER TABLE user_role_permission
    ADD UNIQUE KEY uk_user_role_permission_role_permission (role_id, permission_id);

CREATE TABLE IF NOT EXISTS permission_member_initialization
(
    role_id          varchar(64) NOT NULL COMMENT '成员角色 ID',
    init_version     varchar(64) NOT NULL COMMENT '初始化版本',
    initialized_time bigint      NOT NULL COMMENT '初始化完成时间',
    PRIMARY KEY (role_id, init_version)
) COMMENT '成员角色权威权限源初始化记录';
