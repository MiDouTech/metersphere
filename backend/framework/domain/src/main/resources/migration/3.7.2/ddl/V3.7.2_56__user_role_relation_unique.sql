-- 角色成员关系幂等保护：保留每个用户、角色、作用域的一条关系，再增加数据库唯一约束。
DELETE duplicate_relation
FROM user_role_relation duplicate_relation
INNER JOIN user_role_relation retained_relation
        ON retained_relation.user_id = duplicate_relation.user_id
       AND retained_relation.role_id = duplicate_relation.role_id
       AND retained_relation.source_id = duplicate_relation.source_id
       AND retained_relation.id < duplicate_relation.id;

ALTER TABLE user_role_relation
    ADD UNIQUE KEY uk_user_role_relation_user_role_source (user_id, role_id, source_id);
